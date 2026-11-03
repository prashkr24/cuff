package com.srineere.airbridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.airpulse.fitbit_air.AirLink
import dev.airpulse.fitbit_air.AirResult

/**
 * The app's way to the band.
 *
 * The Air holds one secure session at a time, which is why everything the
 * relay sends goes through a single queue inside the service. That is a rule
 * about the band, though, not about the service: when the relay is down there
 * is nothing to collide with, and making someone turn on notification relaying
 * before they can read their own battery is a toll, not a safeguard.
 *
 * So when the relay is up this borrows its link and its queue, and when it is
 * not it holds a link of its own and lets it go again shortly after.
 */
internal object Band {

    /** Long enough to cover a sync and then a buzz; short enough to let go. */
    private const val IDLE_MILLIS = 20_000L
    private const val TAG = "AirBridge"

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Work>()
    private var link: AirLink? = null
    private var busy = false
    private val idle = Runnable { close() }

    /**
     * One queued operation, with the reply that is owed either way.
     *
     * The reply is held beside the work because dropping the queue -- which
     * closing does -- has to answer what is in it. Clearing it silently left
     * the screen that asked waiting on a result that was never coming.
     */
    private class Work(val reply: AirResult, val run: (AirLink) -> Unit)

    /**
     * Whether the band is linked. This asks the Bluetooth adapter rather than
     * the band, so it costs no session and never waits behind one.
     */
    fun isConnected(context: Context, reply: AirResult) {
        CalendarRelayService.active?.let { return it.readConnection(reply) }
        own(context).isAirConnected(reply)
    }

    fun readBattery(context: Context, reply: AirResult) =
        operate(context, reply) { air, r -> air.readBattery(r) }

    fun readSignal(context: Context, reply: AirResult) =
        operate(context, reply) { air, r -> air.readSignalStrength(r) }

    fun readDeviceInfo(context: Context, reply: AirResult) =
        operate(context, reply) { air, r -> air.readDeviceInfo(r) }

    /**
     * One buzz in the shape a notification would get, so that trying it tells
     * you something true about what the band will do tonight.
     *
     * It deliberately ignores a pause and quiet hours. Those silence the
     * relay's own decisions; this is the person in front of the screen asking
     * for a buzz now, and refusing them with "could not reach the band" was
     * both a refusal and a lie about the reason.
     */
    fun buzz(context: Context, reply: AirResult) {
        val preset = RelayRuleStore.read(context).defaultPreset
        if (preset.silent) {
            reply.error("SILENT", "Your default vibration is silent.", null)
            return
        }
        if (preset.callsOnly) {
            reply.error("CALLS_ONLY", "A repeat needs a call to stop it.", null)
            return
        }
        val pulses = preset.pattern.pulses ?: 1
        val at = System.currentTimeMillis()
        val recorded = object : AirResult {
            override fun success(result: Any?) {
                record(context, at, "verified", null, preset.id)
                reply.success(result)
            }
            override fun error(code: String, message: String?, details: Any?) {
                record(context, at, "failed", code, preset.id)
                reply.error(code, message, details)
            }
        }
        // More than one pulse is a short bounded ring, exactly as the relay
        // sends it: one session, so the pulses read as one shape.
        if (pulses > 1) {
            operate(context, recorded) { air, r ->
                air.ring(preset.intensity, PATTERN_GAP_MILLIS, 20_000L, pulses, r)
            }
        } else {
            operate(context, recorded) { air, r -> air.tap(preset.intensity, r) }
        }
    }

    /**
     * Drops our own link. The relay calls this before taking one of its own,
     * because two sessions to one band is the thing none of this may do.
     */
    fun close() {
        handler.removeCallbacks(idle)
        // Whatever was waiting is not going to happen, and the caller is owed
        // an answer: silence here is a spinner that never stops.
        val dropped = queue.toList()
        queue.clear()
        busy = false
        link?.let { runCatching { it.close() }.onFailure { e -> Log.w(TAG, "closing band link", e) } }
        link = null
        dropped.forEach { it.reply.error("CLOSED", "The link to the band was closed.", null) }
    }

    private fun own(context: Context): AirLink =
        link ?: AirLink(context.applicationContext).also { link = it }

    /** How long to keep waiting out a band that is busy with something else. */
    private const val RETRY_MILLIS = 1_500L
    private const val RETRIES = 3

    private fun operate(
        context: Context,
        reply: AirResult,
        attempt: Int = 0,
        operation: (AirLink, AirResult) -> Unit,
    ) {
        CalendarRelayService.active?.let { relay ->
            // The relay refuses while it is mid-delivery, and one screen
            // opening after another is enough to collide with its own earlier
            // read. Waiting a moment is a better answer than handing that
            // race to the person holding the phone.
            return relay.readDeviceStatus(object : AirResult {
                override fun success(result: Any?) = reply.success(result)
                override fun error(code: String, message: String?, details: Any?) {
                    if (code == "BUSY" && attempt < RETRIES) {
                        handler.postDelayed(
                            { operate(context, reply, attempt + 1, operation) },
                            RETRY_MILLIS,
                        )
                    } else reply.error(code, message, details)
                }
            }, operation)
        }

        handler.removeCallbacks(idle)
        val air = own(context)
        val work = Work(reply) { held ->
            operation(held, object : AirResult {
                override fun success(result: Any?) { done(); reply.success(result) }
                override fun error(code: String, message: String?, details: Any?) {
                    done(); reply.error(code, message, details)
                }
            })
        }
        if (busy) { queue.addLast(work); return }
        busy = true
        work.run(air)
    }

    private fun done() {
        val air = link
        if (air == null) {
            // The link went away under us, so everything still waiting is owed
            // the same answer closing would have given it.
            val dropped = queue.toList()
            queue.clear()
            busy = false
            dropped.forEach { it.reply.error("CLOSED", "The link to the band was closed.", null) }
            return
        }
        queue.removeFirstOrNull()?.let { it.run(air); return }
        busy = false
        // Holding the link open costs the band, and would be in the way of a
        // relay started a moment from now.
        handler.postDelayed(idle, IDLE_MILLIS)
    }

    /** Recorded like any other buzz, so Recent reads the same either way. */
    private fun record(context: Context, at: Long, stage: String, code: String?, preset: String) {
        runCatching {
            RelayHistory.add(
                context,
                RelayActivity(
                    commandId = "band-$at",
                    sourcePackage = null,
                    stage = stage,
                    atMillis = System.currentTimeMillis(),
                    latencyMillis = System.currentTimeMillis() - at,
                    errorCode = code,
                    preset = preset,
                ),
            )
        }
    }

    /** Spacing inside a fixed pattern, close enough to feel like one alert. */
    private const val PATTERN_GAP_MILLIS = 300L
}
