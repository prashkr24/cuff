package com.srineere.airbridge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.app.Notification
import android.app.NotificationChannel
import com.srineere.airbridge.widget.ROUTE_EXTRA
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.util.Log
import android.os.SystemClock
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import dev.airpulse.fitbit_air.AirLink
import com.srineere.airbridge.health.HealthReader
import com.srineere.airbridge.health.HealthSettings
import com.srineere.airbridge.health.canPulseBand
import com.srineere.airbridge.health.shouldBuzzForGoal
import com.srineere.airbridge.health.shouldNudgeForHour
import dev.airpulse.fitbit_air.AirResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.airpulse.fitbit_air.AirSink

/** User-started example relay; never starts work from a notification callback. */
class CalendarRelayService : Service() {
    private var air: AirLink? = null
    /**
     * The Air runs one secure session at a time, so band work is serialised
     * here. A message arriving mid-ring waits rather than failing BUSY, which
     * is what the Dart operation queue used to do.
     */
    private val airQueue = ArrayDeque<(AirLink) -> Unit>()
    private var airBusy = false
    private var heartStreaming = false
    private val healthScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stepCheck = Runnable { checkStepGoals() }
    private var lastStepCheck = 0L
    private var filter: CalendarAlertFilter? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var pending = 0
    private var ready = false
    private var stopping = false
    private var sequence = 0
    private val inFlight = mutableMapOf<String, Pair<String?, Long>>()
    private var selectedPackages = emptySet<String>()
    private var pulseStyle = "single"
    private val previewReplies = mutableMapOf<String, (RelayWorkerResult) -> Unit>()
    private val choices = mutableMapOf<String, RelayBurstChoice>()
    private var displayedSilence: String? = null
    private val silenceTick = Runnable { settingsChanged() }
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { settingsChanged() }
    }

    /** One call ringing on the Air. [key] is the notification that started it. */
    private class RingSession(
        val key: String,
        val packageName: String,
        val callerId: String?,
        val match: RelayMatch,
        val startedAt: Long,
        /**
         * The audio mode when the ring began. Some apps already hold the audio
         * session while they are still ringing, so only a *change* into an
         * in-call mode means the call was picked up.
         */
        val startMode: Int,
        /** Resolved when the ring starts, so settings cannot change under it. */
        val settings: RelayRingSettings,
    ) {
        /** The phone was heard ringing, so a return to silence means it ended. */
        var sawRingtone: Boolean = startMode == RelayRingWatch.MODE_RINGTONE
    }

    private var ring: RingSession? = null
    private var ringSettings = RelayRingSettings()

    /**
     * The last call that rang. Calls no longer go through the message dedupe,
     * so without this a phone still ringing after the duration ran out would
     * start a fresh ring on its next notification update, forever.
     */
    private var lastRingKey: String? = null
    private var lastRingStoppedAt = 0L
    private var lastRingTimedOut = false

    /**
     * Unlocking is NOT a reason to stop ringing. On a phone with face unlock,
     * an incoming call wakes the screen and unlocks it within a second, which
     * killed every ring that arrived while the phone was locked. Unlocking to
     * see who is calling is not the same as dealing with the call; answering
     * is caught by the audio mode and by the notification going away.
     */
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (ring != null) Log.i(TAG, "unlocked while ringing; ring continues")
        }
    }

    override fun onCreate() {
        super.onCreate()
        // onCreate, not onStartCommand: the five-second startForeground rule
        // means nothing may come before enterForeground() there.
        CrashLog.install(this)
        val filter = IntentFilter().apply { addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(clockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(clockReceiver, filter)
        val present = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(userPresentReceiver, present, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(userPresentReceiver, present)
    }

    fun settingsChanged() {
        scheduleStepCheck()
        val config = RelayRuleStore.read(this)
        val packages = config.selectedPackages
        ringSettings = config.ring
        if (packages != selectedPackages) {
            selectedPackages = packages
            if (ready) filter = CalendarAlertFilter(System.currentTimeMillis(),
                CalendarNotificationListener.existingKeys(packages), packages)
        }
        val now = System.currentTimeMillis()
        val reason = config.silence.reason(now)
        if (reason != null) cancelWaiting(reason)
        if (ready && reason != displayedSilence) {
            displayedSilence = reason
            getSystemService(NotificationManager::class.java).notify(902, buildNotification(reason))
        }
        // Once, when the answer can actually change, rather than every minute
        // for as long as the relay runs. Anything that edits the rules comes
        // back through here, and so does a change of clock or timezone.
        handler.removeCallbacks(silenceTick)
        if (!stopping) {
            config.silence.nextChange(now)?.let {
                handler.postDelayed(silenceTick, maxOf(1L, it - now))
            }
        }
    }

    /** The silence as it stands, for the paths that have not just read it. */
    private fun currentSilence(): String? =
        RelayRuleStore.read(this).silence.reason(System.currentTimeMillis())

    /**
     * [silence] is passed in by anything that has just read the config, so
     * that rewording the notification does not mean reading it twice.
     */
    private fun buildNotification(silence: String? = currentSilence()): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val stop = PendingIntent.getService(this, 0, Intent(this, CalendarRelayService::class.java).setAction(STOP), flags)
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), flags)
        val pause = PendingIntent.getService(this, 2, Intent(this, CalendarRelayService::class.java)
            .setAction(if (silence == "paused") RESUME else PAUSE), flags)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        builder.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Air Bridge")
            .setContentText(when {
                ring != null -> "Ringing your Air · tap Silence to stop"
                silence == "paused" -> "Paused · No notification vibrations"
                silence == "quiet_hours" -> "Quiet hours · No notification vibrations"
                else -> "Listening for your notification rules"
            })
            .setContentIntent(open).setOngoing(true)
        // Silencing a ring is the only useful action while the wrist is buzzing.
        if (ring != null) {
            val silenceRing = PendingIntent.getService(this, 3, Intent(this, CalendarRelayService::class.java).setAction(SILENCE), flags)
            return builder.addAction(Notification.Action.Builder(null, "Silence", silenceRing).build())
                .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
        }
        return builder
            .addAction(Notification.Action.Builder(null, if (silence == "paused") "Resume" else "Pause 15 min", pause).build())
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
    }

    private fun refreshNotification() {
        if (!ready) return
        runCatching { getSystemService(NotificationManager::class.java).notify(902, buildNotification()) }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val coalescer = RelayCoalescer()
    private var holdingFollowUp = false
    private val followUp = Runnable { sendFollowUp() }
    private val watchdog = Runnable {
        saveOutcome("Relay command exceeded its deadline; stopped. Delivery is uncertain; no retry.")
        stopSelf()
    }

    /**
     * Posts the ongoing notification. Android gives a service started with
     * startForegroundService about five seconds to do this and kills the whole
     * app if it does not -- stopping ourselves first is not an excuse -- so
     * every path through [onStartCommand] comes through here before it decides
     * anything, including deciding to quit.
     */
    private fun enterForeground(): Boolean = try {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Notifications to Air", NotificationManager.IMPORTANCE_LOW)
        )
        // Anything left over from a previous process. A nudge is about the
        // hour you are in and the process that could replace or cancel it is
        // gone, so starting fresh is the honest state -- and this is the one
        // moment that is guaranteed to run, where settingsChanged() only
        // fires when something actually changes.
        runCatching { manager.cancel(903) }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(902, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(902, notification)
        true
    } catch (error: Exception) {
        Log.w(TAG, "could not enter the foreground", error)
        false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        starting = false
        if (!enterForeground() && air == null) {
            saveOutcome("Android would not let the relay start. Allow notifications for Air Bridge, then start again.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (cancelStart) {
            cancelStart = false
            saveOutcome("Relay stopped.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == STOP) {
            // An explicit stop is the user's decision and must survive a
            // reboot, unlike a crash or an OEM kill.
            setWanted(this, false)
            requestStop("Relay stopped.")
            return START_NOT_STICKY
        }
        if (intent?.action == SILENCE) {
            stopRing("silenced")
            if (air == null) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == PAUSE || intent?.action == RESUME) {
            RelayRuleStore.update(this) { it.copy(silence = it.silence.copy(pauseUntil = if (intent.action == RESUME) 0 else System.currentTimeMillis() + 900_000)) }
            if (air == null) stopSelf()
            return START_NOT_STICKY
        }
        if (air != null) return START_STICKY
        selectedPackages = RelayRuleStore.read(this).selectedPackages
        pulseStyle = RelayAppSelection.pulseStyle(this)
        if (selectedPackages.isEmpty()) {
            saveOutcome("Select at least one notification app before starting.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasAccess(this)) {
            saveOutcome("Enable notification access, then start again.")
            stopSelf()
            return START_NOT_STICKY
        }
        active = this
        // After a reboot or an OEM restart the system rebinds the listener a
        // moment after this service starts, so a listener that is not up yet
        // is worth waiting for rather than a reason to give up.
        if (CalendarNotificationListener.connected) startWorker()
        else {
            saveOutcome("Waiting for notification access…")
            listenerWaitUntil = System.currentTimeMillis() + LISTENER_WAIT_MILLIS
            handler.postDelayed(listenerWait, LISTENER_POLL_MILLIS)
        }
        return START_STICKY
    }

    /** Waits for the notification listener before committing to an engine. */
    private var listenerWaitUntil = 0L
    private val listenerWait = object : Runnable {
        override fun run() {
            if (air != null || stopping) return
            if (CalendarNotificationListener.connected) { startWorker(); return }
            if (System.currentTimeMillis() > listenerWaitUntil) {
                requestStop("Notification access did not come back. Start the relay again.")
                return
            }
            handler.postDelayed(this, LISTENER_POLL_MILLIS)
        }
    }

    private fun startWorker() {
        try {
            saveOutcome("Starting notification relay…")
            check(hasAccess(this) && CalendarNotificationListener.connected)
            // Only one session to the band, ever: if the UI is holding a link
            // of its own, it lets go here.
            Band.close()
            air = AirLink(applicationContext)
            filter = CalendarAlertFilter(
                System.currentTimeMillis(),
                CalendarNotificationListener.existingKeys(selectedPackages), selectedPackages,
            )
            ready = true
            settingsChanged()
            handler.removeCallbacks(watchdog)
            saveOutcome("Listening for alerts from ${selectedPackages.size} selected app(s).")
            if (stopping) requestStop("Relay stopped.")
        } catch (error: IllegalStateException) {
            requestStop("Notification listener unavailable. Enable access and start again.")
        } catch (error: Exception) {
            saveOutcome("Relay startup failed: ${error.javaClass.simpleName}")
            stopSelf()
        }
    }

    /**
     * Runs [work] against the band once nothing else holds it. [work] must
     * call [airDone] when its operation completes, however it ends.
     *
     * False when there is no link to run against, so that the caller can
     * release what it has already taken. Returning silently left the wake
     * lock held and the alert sitting in flight until the watchdog fired.
     */
    private fun onAir(work: (AirLink) -> Unit): Boolean {
        val link = air ?: return false
        if (airBusy) { airQueue.addLast(work); return true }
        airBusy = true
        work(link)
        return true
    }

    private fun airDone() {
        val next = airQueue.removeFirstOrNull()
        val link = air
        if (next == null || link == null) { airBusy = false; return }
        next(link)
    }

    /** Builds the reply for one band operation, freeing the queue either way. */
    private fun airReply(onResult: (RelayWorkerResult) -> Unit) = object : AirResult {
        override fun success(result: Any?) {
            airDone()
            onResult(RelayWorkerResult("verified", null))
        }
        override fun error(code: String, message: String?, details: Any?) {
            airDone()
            onResult(RelayWorkerResult("failed", code.takeIf { it.matches(ERROR_CODE) } ?: "WORKER_ERROR"))
        }
    }

    fun acceptsPackage(packageName: String): Boolean = ready && !stopping && packageName in selectedPackages

    internal fun onAlert(packageName: String, key: String, postedAt: Long, ongoing: Boolean, summary: Boolean, messageTimestamp: Long?, conversationIds: Set<String>, kind: RelayAlertKind = RelayAlertKind.MESSAGE,
                           callerId: String? = null) {
        if (!ready || stopping) return
        val now = System.currentTimeMillis()
        if (filter?.accept(packageName, key, postedAt, now, ongoing, summary, messageTimestamp, kind) != true) {
            Log.i(TAG, "dropped pkg=$packageName kind=$kind by=filter " +
                "age=${now - postedAt}ms ongoing=$ongoing summary=$summary")
            return
        }
        val config = RelayRuleStore.read(this)
        val match = config.match(
            packageName, conversationIds, kind, callerId,
            dialer = RelayAppSelection.dialer(this),
        )
        if (match == null) {
            Log.i(TAG, "dropped pkg=$packageName kind=$kind by=rule (silent or app off)")
            record(nextId(), packageName, postedAt, "filtered")
            return
        }
        val silence = config.silence.reason(now)
        if (silence != null) {
            Log.i(TAG, "dropped pkg=$packageName kind=$kind by=$silence")
            cancelWaiting(silence)
            record(nextId(), packageName, postedAt, silence)
            return
        }
        // One ring at a time, and nothing else buzzes during it: a message pulse
        // in the middle of a ring is indistinguishable from the ring itself.
        Log.i(TAG, "alert pkg=$packageName kind=$kind ongoing=$ongoing preset=${match.preset.id}")
        if (ring != null) { record(nextId(), packageName, postedAt, "ringing"); return }
        if (match.preset == RelayPreset.RING) {
            if (rangAlready(key, now)) { record(nextId(), packageName, postedAt, "ring_done"); return }
            startRing(key, packageName, match, callerId)
            return
        }
        submit(packageName, postedAt, match, callerId = callerId)
    }

    /**
     * This call already had its ring: either it has only just stopped, or it
     * ran its full duration and the phone is still ringing. Either way the
     * wrist has said its piece until the call ends.
     */
    private fun rangAlready(key: String, now: Long): Boolean =
        key == lastRingKey && (lastRingTimedOut || now - lastRingStoppedAt < RING_REARM_MILLIS)

    /**
     * Polls the audio mode, because once the ringing notification has been
     * replaced there is nothing left to tell us how the call ended.
     */
    private val ringWatch = object : Runnable {
        override fun run() {
            val session = ring ?: return
            val mode = audioMode()
            if (mode == RelayRingWatch.MODE_RINGTONE) session.sawRingtone = true
            val reason = RelayRingWatch.reason(session.startMode, session.sawRingtone, mode)
            if (reason != null) stopRing(reason) else handler.postDelayed(this, RING_WATCH_MILLIS)
        }
    }

    /** A ring stops when the call is answered, declined, cleared, or times out. */
    internal fun onAlertRemoved(key: String) {
        if (ring?.key == key) {
            // A locked phone swaps the ringing notification for its full-screen
            // call UI. That is not the call ending, and the ringer says so.
            if (RelayRingWatch.stopOnRemoval(audioMode())) stopRing("cleared")
            else Log.i(TAG, "ringing notification replaced; phone still ringing")
        }
        // The call is over, so this key may ring again when it next comes in.
        if (key == lastRingKey) { lastRingKey = null; lastRingTimedOut = false }
    }

    private fun startRing(
        key: String,
        packageName: String,
        match: RelayMatch,
        callerId: String?,
    ) {
        val session = RingSession(key, packageName, callerId, match, System.currentTimeMillis(),
            audioMode(), RelayRuleStore.read(this).ringFor(packageName, callerId))
        ring = session
        Log.i(TAG, "ring start pkg=$packageName gap=${session.settings.gapMillis}ms max=${session.settings.maxSeconds}s mode=${session.startMode}")
        record(nextId(), packageName, session.startedAt, "ring_started", match = match,
            callerId = callerId)
        refreshNotification()
        // Nothing else buzzes during a ring, and a follow-up already scheduled
        // would have. It keeps waiting; stopRing picks it up when the call is
        // over.
        handler.removeCallbacks(followUp)
        handler.postDelayed(ringWatch, RING_WATCH_MILLIS)
        sendRing()
    }

    private fun stopRing(reason: String) {
        val session = ring ?: return
        ring = null
        handler.removeCallbacks(ringWatch)
        lastRingKey = session.key
        lastRingStoppedAt = System.currentTimeMillis()
        lastRingTimedOut = reason == "ring_timeout"
        Log.i(TAG, "ring stop reason=$reason after=${System.currentTimeMillis() - session.startedAt}ms")
        record(nextId(), session.packageName, session.startedAt, "ring_stopped", reason,
            session.match, session.callerId)
        // Releases the held session; the in-flight ring call then completes on
        // its own and does its own bookkeeping.
        // Deliberately not queued: the queue is busy holding the ring, and
        // this is what releases it.
        runCatching { air?.stopRing() }
        // Give Google Health the usual quiet window now that the ring is over,
        // and pick up anything that was waiting while it ran. Dropping this
        // answer left those alerts waiting for a follow-up nobody scheduled.
        val now = System.currentTimeMillis()
        coalescer.onFinished(now)?.let {
            if (!stopping) handler.postDelayed(followUp, maxOf(0, it - now))
        }
        refreshNotification()
    }

    private fun audioMode(): Int = runCatching {
        getSystemService(AudioManager::class.java).mode
    }.getOrDefault(AudioManager.MODE_NORMAL)

    /** True once this call was picked up, including WhatsApp and Instagram calls. */
    private fun answered(session: RingSession): Boolean =
        RelayRingWatch.reason(session.startMode, session.sawRingtone, audioMode()) != null

    /**
     * Starts one held ring. The worker keeps the secure session open and
     * pulses inside it, so this is a single long call rather than a pulse
     * schedule: stopping means telling the worker, not cancelling a timer.
     */
    private fun sendRing() {
        val session = ring ?: return
        if (stopping || !ready) { stopRing("cancelled"); return }
        if (answered(session)) { stopRing("answered"); return }
        RelayRuleStore.read(this).silence.reason(System.currentTimeMillis())?.let { stopRing(it); return }
        if (air == null) { stopRing("worker_unavailable"); return }
        val id = nextId()
        begin()
        refreshDeadline()
        inFlight[id] = session.packageName to session.startedAt
        record(id, session.packageName, session.startedAt, "queued", match = session.match,
            callerId = session.callerId)
        fun finished(result: RelayWorkerResult) {
            inFlight.remove(id)
            record(id, session.packageName, session.startedAt, result.stage, result.errorCode,
                session.match, session.callerId)
            Log.i(TAG, "ring result stage=${result.stage} code=${result.errorCode}")
            saveOutcome("Ring ${result.stage}${result.errorCode?.let { ": $it" } ?: ""}.")
            end()
            // Reaching the duration ends the ring without anyone asking.
            if (ring === session) stopRing("ring_finished")
        }
        val sent = onAir { link ->
            link.ring(
                intensity = session.match.preset.intensity,
                gapMillis = session.settings.gapMillis.toLong(),
                maxMillis = session.settings.maxMillis,
                pulses = null,
                result = airReply { finished(it) },
            )
        }
        if (!sent) finished(RelayWorkerResult("failed", "WORKER_UNAVAILABLE"))
    }

    private fun submit(sbnPackage: String?, postedAt: Long, match: RelayMatch,
                       completion: ((RelayWorkerResult) -> Unit)? = null, callerId: String? = null) {
        val id = nextId()
        if (choices.size >= 256) {
            record(id, sbnPackage, postedAt, "queue_full")
            completion?.invoke(RelayWorkerResult("failed", "QUEUE_FULL"))
            return
        }
        if (completion != null) previewReplies[id] = completion
        choices[id] = RelayBurstChoice(match, System.currentTimeMillis())
        record(id, sbnPackage, postedAt, "detected", match = match, callerId = callerId)
        val now = System.currentTimeMillis()
        when (val decision = coalescer.onAlert(id, now)) {
            RelayCoalescer.Decision.DispatchNow ->
                dispatch(id, sbnPackage, postedAt, postedAt + 30_000, match, callerId)
            is RelayCoalescer.Decision.Merged -> {
                if (!holdingFollowUp) { holdingFollowUp = true; begin() }
                inFlight[id] = sbnPackage to postedAt
                record(id, sbnPackage, postedAt, "merged", match = match, callerId = callerId)
                decision.scheduleAtMillis?.let { handler.postDelayed(followUp, maxOf(0, it - now)) }
            }
        }
    }

    private fun cancelWaiting(reason: String) {
        handler.removeCallbacks(followUp)
        coalescer.cancelFollowUp().forEach { id ->
            choices.remove(id)
            previewReplies.remove(id)?.invoke(RelayWorkerResult("failed", "SUPPRESSED"))
            inFlight.remove(id)?.let { (source, postedAt) -> record(id, source, postedAt, reason) }
        }
        if (holdingFollowUp) { holdingFollowUp = false; end() }
    }

    private fun sendFollowUp() {
        if (stopping || !coalescer.hasFollowUp) return
        // A ring started after this was scheduled. It keeps its hold and its
        // place; stopRing schedules it again once the call is over.
        if (ring != null) return
        val now = System.currentTimeMillis()
        val silence = RelayRuleStore.read(this).silence.reason(now)
        if (silence != null) { cancelWaiting(silence); return }
        val members = coalescer.takeFollowUp().mapNotNull { id ->
            val source = inFlight.remove(id)
            val choice = choices.remove(id)
            if (source == null || choice == null) null else Triple(id, source, choice)
        }
        val fresh = members.filter { now >= it.second.second && now - it.second.second < 30_000 }
        members.filterNot { it in fresh }.forEach {
            record(it.first, it.second.first, it.second.second, "expired")
            previewReplies.remove(it.first)?.invoke(RelayWorkerResult("failed", "EXPIRED"))
        }
        val winner = fresh.reduceOrNull { a, b -> if (a.third.prefer(b.third) === b.third) b else a }
        fresh.forEach { record(it.first, it.second.first, it.second.second, "combined", match = it.third.match) }
        if (winner != null) {
            val id = nextId()
            val replies = fresh.mapNotNull { previewReplies.remove(it.first) }
            if (replies.isNotEmpty()) previewReplies[id] = { result -> replies.forEach { it(result) } }
            dispatch(id, winner.second.first, winner.second.second, winner.second.second + 30_000, winner.third.match)
        } else coalescer.onFinished(now)
        holdingFollowUp = false
        end()
    }

    /**
     * Whether the band is linked right now. Unlike the readings below this
     * asks the adapter rather than the band, so it costs no session and does
     * not queue behind a notification.
     */
    internal fun readConnection(reply: AirResult) {
        val link = air
        if (link == null) reply.error("NOT_RUNNING", "The relay is not running.", null)
        else link.isAirConnected(reply)
    }

    internal fun readBattery(reply: AirResult) = readDeviceStatus(reply) { link, r -> link.readBattery(r) }
    internal fun readSignal(reply: AirResult) = readDeviceStatus(reply) { link, r -> link.readSignalStrength(r) }
    internal fun readDeviceInfo(reply: AirResult) = readDeviceStatus(reply) { link, r -> link.readDeviceInfo(r) }

    /**
     * Holds the band open for a live heart rate reading.
     *
     * Unlike every other band call this one does not finish on its own, so
     * it takes the same slot a notification would and keeps it: begin() so
     * the watchdog and wake lock cover it, onAir so alerts queue behind it
     * rather than fighting it for the one session the Air allows. The
     * watchdog bounds it at four minutes even if the screen never calls
     * stop, which is the backstop behind the UI's own two-minute limit.
     */
    /**
     * The step goals, checked on a timer rather than pushed.
     *
     * Health Connect notifies nobody when a figure changes, so this polls --
     * but it polls from the service that is already running for
     * notifications, so it costs one read a quarter of an hour rather than a
     * background job of its own. Both buzzes obey quiet hours and pause,
     * because a goal is not more important than the quiet.
     */
    private fun scheduleStepCheck() {
        handler.removeCallbacks(stepCheck)
        val settings = HealthSettings.read(this)
        // A nudge still sitting in the shade after the thing that posts it
        // was switched off is a notification about a setting that no longer
        // exists. Cleared here because this runs on every settings change.
        if (!settings.hourlyNudge || !settings.nudgeOnPhone) {
            runCatching { getSystemService(NotificationManager::class.java).cancel(903) }
        }
        if (!settings.buzzOnGoal && !settings.hourlyNudge) return
        handler.postDelayed(stepCheck, STEP_CHECK_MILLIS)

        // Check now as well as in a quarter of an hour. Someone who switches
        // this on having already walked ten thousand steps should not wait
        // fifteen minutes to hear about it. Throttled, because
        // settingsChanged() also fires on clock ticks and silence changes,
        // and Health Connect rate-limits callers.
        val now = SystemClock.elapsedRealtime()
        if (now - lastStepCheck >= STEP_CHECK_FLOOR_MILLIS) {
            lastStepCheck = now
            checkStepGoals(reschedule = false)
        }
    }

    private fun checkStepGoals(reschedule: Boolean = true) {
        if (reschedule) scheduleStepCheck()
        val settings = HealthSettings.read(this)
        if (!settings.buzzOnGoal && !settings.hourlyNudge) return
        // A missing band used to end this outright. It still does when the
        // wrist is the only destination -- but a nudge the phone is meant to
        // show has somewhere to go without one. Relaying being off still
        // ends it either way: that switch is what the rows promise holds
        // these, and a nudge that outlived it would make them liars.
        val phoneToo = settings.hourlyNudge && settings.nudgeOnPhone
        if (!ready || stopping) return
        if (!canPulseBand(ready, stopping, air != null, heartStreaming) && !phoneToo) return

        healthScope.launch {
            val steps = withContext(Dispatchers.IO) { HealthReader().stepsNow(this@CalendarRelayService) }
                ?: return@launch
            val (today, thisHour) = steps
            val now = java.time.ZonedDateTime.now()
            val day = now.toLocalDate().toEpochDay()
            val silenced = currentSilence() != null
            val history = HealthSettings.history(this@CalendarRelayService)

            // Recorded only once something has actually been handed over.
            //
            // The ordering the handover defends -- record before the result
            // comes back -- still holds, and for its original reason: a pulse
            // that is sent and fails to verify may still have been felt, and
            // retrying that every quarter hour for the rest of the day would
            // be worse than missing it. What has changed is that a pulse
            // which was never *sent* no longer counts. Those are different
            // failures and only the first is worth giving up on.
            if (shouldBuzzForGoal(settings, history, today, day, silenced)) {
                if (buzzForSteps("goal")) {
                    HealthSettings.recordGoalFired(this@CalendarRelayService, day)
                }
                return@launch
            }
            if (shouldNudgeForHour(settings, history, thisHour, day, now.hour, now.minute, silenced)) {
                // Delivered anywhere is delivered. The phone copy is enough
                // on its own, which is the whole point of it.
                if (settings.nudgeOnPhone) notifyNudge(thisHour, settings.hourlyGoal)
                val buzzed = buzzForSteps("nudge")
                if (settings.nudgeOnPhone || buzzed) {
                    HealthSettings.recordNudgeFired(this@CalendarRelayService, day, now.hour)
                }
            }
        }
    }

    /**
     * One pulse, in the shape the person already chose for everything else,
     * and recorded like every other buzz.
     *
     * The record is not bookkeeping. A step goal fires unprompted while the
     * phone is in a pocket, so "did it actually buzz?" has no other answer:
     * Recent is the only place that can say it reached the band, and the
     * only place a failure can be seen at all.
     */
    /**
     * The hourly nudge, on the phone as well as the wrist.
     *
     * Its own channel, so Android's own per-channel controls are what change
     * its sound and importance rather than settings this app would have to
     * build and keep. Low importance to begin with: the band is already
     * buzzing, and an hourly chime is the fastest way to have the whole
     * feature switched off. Anybody who wants it louder can say so where
     * every other app's notifications are configured.
     *
     * One id, so each hour replaces the last rather than stacking twelve of
     * them by evening.
     */
    private fun notifyNudge(stepsThisHour: Long, goal: Int) {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
                NotificationChannel(NUDGE_CHANNEL, "Hourly nudge", NotificationManager.IMPORTANCE_LOW)
            )
            val open = PendingIntent.getActivity(
                this,
                1,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(ROUTE_EXTRA, "healthsteps"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val short = (goal - stepsThisHour).coerceAtLeast(0)
            val builder = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, NUDGE_CHANNEL)
            } else {
                @Suppress("DEPRECATION") Notification.Builder(this)
            }
            manager.notify(
                903,
                builder
                    .setSmallIcon(android.R.drawable.ic_menu_directions)
                    .setContentTitle("Time to move")
                    .setContentText(
                        // The figure, not an exhortation. What is missing is
                        // the only part that is actually about you.
                        "$short more steps for this hour's $goal",
                    )
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(false)
                    .build(),
            )
        }
    }

    /**
     * Returns whether the pulse was handed to the band, which is what the
     * caller records. False means nothing was sent and the hour or the day
     * is still owed one; it does not mean the wrist felt nothing, because
     * the result of a sent pulse arrives later and is its own question.
     */
    private fun buzzForSteps(reason: String): Boolean {
        if (!canPulseBand(ready, stopping, air != null, heartStreaming)) return false
        // Each of the two has its own pulse, so a goal crossed and an hour
        // missed can be told apart without looking at the phone.
        val config = RelayRuleStore.read(this)
        val preset = if (reason == "goal") config.stepGoalPreset else config.hourlyNudgePreset
        // Silence is a choice about how this is delivered, not a failure to
        // deliver it, so it counts as done. Otherwise switching the preset
        // to a real pulse at two in the afternoon would buzz for a goal
        // crossed at ten in the morning.
        if (preset.silent) return true
        val at = System.currentTimeMillis()
        fun record(stage: String, code: String?) = runCatching {
            RelayHistory.add(
                this,
                RelayActivity(
                    commandId = "$reason-$at",
                    sourcePackage = null,
                    stage = stage,
                    atMillis = System.currentTimeMillis(),
                    latencyMillis = System.currentTimeMillis() - at,
                    errorCode = code,
                    preset = preset.id,
                ),
            )
        }
        begin()
        onAir { link ->
            link.tap(preset.intensity, object : AirResult {
                override fun success(result: Any?) {
                    record("verified", null)
                    airDone()
                    end()
                }

                override fun error(code: String, message: String?, details: Any?) {
                    record("failed", code)
                    airDone()
                    end()
                }
            })
        }
        return true
    }

    /**
     * Whether the service is actually holding the band's one link.
     *
     * The service object outlives its link -- it exists while starting, and
     * ColorOS brings it back after a force stop -- so "the service is alive"
     * and "the band is taken" are different questions. Only the second one
     * stops the UI opening a link of its own.
     */
    internal val holdsBand: Boolean get() = air != null

    internal fun startHeartRate(sink: AirSink): Boolean {
        if (!ready || stopping || pending != 0 || air == null || heartStreaming) return false
        heartStreaming = true
        begin()
        val held = object : AirSink {
            override fun success(event: Any?) = sink.success(event)
            override fun error(code: String, message: String?, details: Any?) {
                releaseHeartRate()
                sink.error(code, message, details)
            }
            override fun endOfStream() {
                releaseHeartRate()
                sink.endOfStream()
            }
        }
        onAir { link -> link.readHeartRate(held) }
        return true
    }

    internal fun stopHeartRate() {
        if (!heartStreaming) return
        air?.stopHeartRate()
        releaseHeartRate()
    }

    /** Frees the queue and the pending count exactly once. */
    private fun releaseHeartRate() {
        if (!heartStreaming) return
        heartStreaming = false
        airDone()
        end()
    }

    internal fun readDeviceStatus(reply: AirResult, operation: (AirLink, AirResult) -> Unit) {
        if (!ready || stopping || pending != 0 || air == null) {
            reply.error("BUSY", "The relay is delivering a notification. Try again shortly.", null)
            return
        }
        begin()
        onAir { link ->
            operation(link, object : AirResult {
                override fun success(result: Any?) { airDone(); end(); reply.success(result) }
                override fun error(code: String, message: String?, details: Any?) {
                    airDone(); end(); reply.error(code, message, details)
                }
            })
        }
    }

    private fun dispatch(id: String, sbnPackage: String?, postedAt: Long, expiresAt: Long,
                         match: RelayMatch, callerId: String? = null) {
        val preset = match.preset
        begin()
        refreshDeadline()
        inFlight[id] = sbnPackage to postedAt
        record(id, sbnPackage, postedAt, "queued", match = match, callerId = callerId)
        fun finished(result: RelayWorkerResult) {
            previewReplies.remove(id)?.invoke(result)
            choices.remove(id)
            inFlight.remove(id)
            record(id, sbnPackage, postedAt, result.stage, result.errorCode, match, callerId)
            saveOutcome("Haptic ${result.stage}${result.errorCode?.let { ": $it" } ?: ""}.")
            val now = System.currentTimeMillis()
            coalescer.onFinished(now)?.let { if (!stopping) handler.postDelayed(followUp, maxOf(0, it - now)) }
            end()
        }
        val silence = RelayRuleStore.read(this).silence.reason(System.currentTimeMillis())
        if (silence != null || System.currentTimeMillis() >= expiresAt) {
            finished(RelayWorkerResult(silence ?: "expired", null)); return
        }
        // More than one pulse is a short bounded ring: both pulses share one
        // session, so they land close enough together to read as a pattern,
        // and both play at the strength the rule asked for.
        val pulses = preset.pattern.pulses ?: 1
        val sent = onAir { link ->
            if (pulses > 1) {
                // A pattern is a short bounded ring: every pulse shares one
                // session, so they land close enough together to read as one
                // shape, and all play at the strength the rule asked for.
                link.ring(
                    intensity = preset.intensity,
                    gapMillis = PATTERN_GAP_MILLIS.toLong(),
                    maxMillis = 20_000L,
                    pulses = pulses,
                    result = object : AirResult {
                        override fun success(result: Any?) {
                            airDone()
                            val played = ((result as? Map<*, *>)?.get("pulses") as? Int) ?: 0
                            finished(
                                if (played >= pulses) RelayWorkerResult("verified", null)
                                else RelayWorkerResult("failed", "PARTIAL")
                            )
                        }
                        override fun error(code: String, message: String?, details: Any?) {
                            airDone()
                            finished(RelayWorkerResult("failed", code.takeIf { it.matches(ERROR_CODE) } ?: "WORKER_ERROR"))
                        }
                    },
                )
            } else {
                link.tap(preset.intensity, airReply { finished(it) })
            }
        }
        if (!sent) finished(RelayWorkerResult("failed", "WORKER_UNAVAILABLE"))
    }

    /** Running commands and a waiting follow-up each hold the wake lock and watchdog. */
    private fun begin() {
        if (pending++ == 0) refreshDeadline()
    }

    /** The watchdog bounds one hung command, so a long burst re-arms it per dispatch. */
    private fun refreshDeadline() {
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, 240_000)
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "air_bridge:Relay")
            .apply { setReferenceCounted(false) }
        wakeLock = lock
        lock.acquire(240_000)
    }

    private fun end() {
        if (--pending == 0) {
            handler.removeCallbacks(watchdog)
            releaseWakeLock()
            if (stopping) stopSelf()
        }
    }

    private fun nextId() = "relay-${System.currentTimeMillis()}-${++sequence}"

    private fun record(id: String, sbnPackage: String?, postedAt: Long, stage: String, code: String? = null,
                       match: RelayMatch? = null, callerId: String? = null) {
        val now = System.currentTimeMillis()
        RelayHistory.add(this, RelayActivity(id, sbnPackage, stage, now, maxOf(0, now - postedAt), code,
            match?.preset?.id, callerId))
    }

    fun requestStop(reason: String) {
        stopping = true
        stopRing("cancelled")
        saveOutcome(reason)
        runCatching { air?.close() }
        handler.removeCallbacks(silenceTick)
        cancelWaiting("cancelled")
        if (pending == 0) stopSelf()
    }

    /**
     * Swiping the app out of recents does not stop a foreground service on
     * stock Android, but several OEM builds kill the process anyway. Asking to
     * be started again costs nothing when we were not killed, and is the
     * difference between working and not when we were.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "task removed; relay wanted=${isWanted(this)} stopping=$stopping")
        if (!stopping && isWanted(this)) {
            runCatching {
                val again = Intent(this, CalendarRelayService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(again) else startService(again)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(stepCheck)
        healthScope.cancel()
        ready = false
        stopping = true
        val endedAt = System.currentTimeMillis()
        inFlight.forEach { (id, source) ->
            RelayHistory.add(this, RelayActivity(id, source.first, "interrupted", endedAt,
                maxOf(0, endedAt - source.second), "SERVICE_STOPPED"))
        }
        inFlight.clear()
        choices.clear()
        val replies = previewReplies.values.toList()
        previewReplies.clear()
        replies.forEach { it(RelayWorkerResult("failed", "SERVICE_STOPPED")) }
        ring = null
        handler.removeCallbacks(ringWatch)
        // Guarded like the rest of this method: a receiver that never
        // registered throws here, and taking onDestroy down with it loses
        // every clean-up below.
        runCatching { unregisterReceiver(clockReceiver) }
        runCatching { unregisterReceiver(userPresentReceiver) }
        handler.removeCallbacks(silenceTick)
        handler.removeCallbacks(listenerWait)
        if (active === this) active = null
        handler.removeCallbacks(watchdog)
        handler.removeCallbacks(followUp)
        airQueue.clear()
        airBusy = false
        air?.close()
        air = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun saveOutcome(value: String) {
        getSharedPreferences("calendar_relay", MODE_PRIVATE).edit().putString("outcome", value).apply()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /**
         * How often the step goals are checked. Health Connect batches step
         * writes about once a minute, so anything tighter than this spends
         * reads on a figure that has not moved.
         */
        private const val STEP_CHECK_MILLIS = 15 * 60 * 1000L

        /** The soonest two immediate checks may be run back to back. */
        private const val STEP_CHECK_FLOOR_MILLIS = 60 * 1000L

        private var starting = false
            set(value) { field = value; publishRunning() }
        private var cancelStart = false
        val isRunning: Boolean get() = active != null || starting

        private val _running = MutableStateFlow(false)

        /**
         * Whether the relay is up, as something a screen can watch. A snapshot
         * taken when the screen was built goes stale the moment the service
         * stops itself -- which it does whenever it cannot run -- and leaves
         * the screen claiming to relay something it is not.
         */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private fun publishRunning() { _running.value = active != null || starting }

        private const val PREFS = "calendar_relay"
        private const val LISTENER_WAIT_MILLIS = 90_000L
        private const val LISTENER_POLL_MILLIS = 1_500L

        /**
         * Whether the user wants the relay running. Android restarts a sticky
         * service by itself, but a reboot needs this to tell "the user stopped
         * it" apart from "something killed it".
         */
        fun setWanted(context: Context, wanted: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("wanted", wanted).apply()
        }

        fun isWanted(context: Context): Boolean = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("wanted", false)

        fun start(context: Context) {
            if (active != null || starting) return
            setWanted(context, true)
            starting = true
            cancelStart = false
            try {
                val intent = Intent(context, CalendarRelayService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (error: Exception) {
                starting = false
                throw error
            }
        }

        fun stop() {
            if (starting) cancelStart = true
            active?.let {
                setWanted(it, false)
                it.requestStop("Relay stopped.")
            }
        }

        private const val TAG = "AirBridge"
        private const val RING_REARM_MILLIS = 15_000L
        private const val RING_WATCH_MILLIS = 1_000L
        /** Spacing inside a fixed pattern, close enough to feel like one alert. */
        private const val PATTERN_GAP_MILLIS = 300
        /** Codes we pass through to the log; anything else becomes WORKER_ERROR. */
        private val ERROR_CODE = Regex("[A-Z0-9_]{1,64}")
        private const val CHANNEL = "calendar_relay"
        private const val NUDGE_CHANNEL = "hourly_nudge"
        private const val STOP = "calendar_relay.STOP"
        private const val SILENCE = "calendar_relay.SILENCE"
        private const val PAUSE = "calendar_relay.PAUSE"
        private const val RESUME = "calendar_relay.RESUME"
        var active: CalendarRelayService? = null
            private set(value) { field = value; publishRunning() }

        fun hasAccess(context: Context): Boolean {
            val expected = ComponentName(context, CalendarNotificationListener::class.java)
            return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?.split(':')?.any { ComponentName.unflattenFromString(it) == expected } == true
        }

        /**
         * Whether Android has been told to leave this app alone. Without it,
         * Doze and OEM battery management can stop the relay whenever the
         * screen has been off for a while, whatever the service asks for.
         */
        fun batteryExempt(context: Context): Boolean = runCatching {
            context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)

    }
}
