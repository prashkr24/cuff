package com.srineere.airbridge

/**
 * How old a notification may already be *when it reaches us* and still count
 * as news.
 *
 * This is a door, not a queue. In the ordinary case it is tested a few
 * milliseconds after the notification was posted and refuses nothing; what
 * it exists for is the reconnect, where the listener is handed back every
 * notification currently on the phone with its original `postTime`. Without
 * this line, plugging the listener back in would buzz the wrist once for
 * everything that arrived while it was away.
 *
 * **Deliberately not `ALERT_LIFETIME_MILLIS`**, which is ninety seconds and
 * governs something else entirely: how long an alert the relay has *already
 * accepted* may sit in a held burst and still be worth sending. The two were
 * both thirty seconds for a while and that was a coincidence. Raising this
 * one to match would mean a listener reconnect replaying a minute and a half
 * of old notifications onto somebody's wrist, which is the exact thing it
 * was written to prevent.
 */
private const val ARRIVAL_STALENESS_MILLIS = 30_000L

/** Metadata only. The legacy class name is internal to the original demo. */
internal class CalendarAlertFilter(
    private val startedAt: Long,
    existingKeys: Map<String, Long?> = emptyMap(),
    private val selectedPackages: Set<String> = setOf("com.google.android.calendar")
) {
    private val seen = existingKeys.entries.take(256).associate { it.key to it.value }.toMutableMap()

    fun accept(packageName: String, key: String, postedAt: Long, now: Long,
               ongoing: Boolean, summary: Boolean, messageTimestamp: Long? = null,
               kind: RelayAlertKind = RelayAlertKind.MESSAGE): Boolean {
        if (packageName !in selectedPackages ||
            postedAt < startedAt || postedAt > now ||
            now - postedAt >= ARRIVAL_STALENESS_MILLIS) return false
        // Everything below is message hygiene, and a ringing call is exempt
        // from all of it:
        //  - sticky: a ringing call holds its notification up by design;
        //  - summary: WhatsApp posts its call inside a notification group;
        //  - dedupe: a dialer reuses one key for every call and a call has no
        //    message timestamp, so `seen` would let the first call through and
        //    silence every later one.
        // Re-posts while a call rings are absorbed by the ring already running.
        if (kind == RelayAlertKind.CALL) return true
        if (ongoing || summary) return false
        if (seen.containsKey(key)) {
            // Messaging apps reuse chat IDs. Only a strictly newer message can buzz again.
            val previous = seen[key]
            if (messageTimestamp == null || messageTimestamp <= (previous ?: (startedAt - 1))) return false
        } else if (seen.size >= 256) return false
        if (messageTimestamp != null && messageTimestamp < startedAt) return false
        seen[key] = messageTimestamp
        return true
    }
}
