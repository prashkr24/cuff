package com.srineere.airbridge.health

import android.content.Context

/**
 * What the Health tab asks the band to do, kept apart from the relay's own
 * config on purpose.
 *
 * `relay_rules.xml` is versioned and migrated, and every field added to it
 * costs a version bump and a migration test. Nothing here changes how a
 * notification is matched or felt, so it does not belong in that file and
 * cannot break it.
 */
object HealthSettings {
    private const val PREFS = "air_health"
    private const val KEY_GOAL = "step_goal"
    private const val KEY_BUZZ = "buzz_on_goal"
    private const val KEY_NUDGE = "hourly_nudge"
    private const val KEY_NUDGE_PHONE = "hourly_nudge_phone"
    private const val KEY_HOURLY = "hourly_goal"
    private const val KEY_FROM = "active_from"
    private const val KEY_UNTIL = "active_until"
    private const val KEY_FIRED_DAY = "goal_fired_day"
    private const val KEY_NUDGE_DAY = "nudge_fired_day"
    private const val KEY_NUDGE_HOUR = "nudge_fired_hour"
    private const val KEY_TILES = "tiles"
    private const val KEY_TRENDS = "show_trends"
    private const val KEY_STEP_SOURCE = "step_source"
    private const val KEY_MORNING = "morning_on"
    private const val KEY_MORNING_AT = "morning_minute"
    private const val KEY_MORNING_DAY = "morning_sent_day"
    private const val KEY_MORNING_WAKE = "morning_after_waking"
    private const val KEY_JOURNAL = "journal_on"
    private const val KEY_JOURNAL_AT = "journal_minute"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): StepGoalSettings {
        val p = prefs(context)
        val fallback = StepGoalSettings()
        return StepGoalSettings(
            goal = p.getInt(KEY_GOAL, fallback.goal),
            buzzOnGoal = p.getBoolean(KEY_BUZZ, fallback.buzzOnGoal),
            hourlyNudge = p.getBoolean(KEY_NUDGE, fallback.hourlyNudge),
            hourlyGoal = p.getInt(KEY_HOURLY, fallback.hourlyGoal),
            activeFrom = p.getInt(KEY_FROM, fallback.activeFrom),
            activeUntil = p.getInt(KEY_UNTIL, fallback.activeUntil),
            nudgeOnPhone = p.getBoolean(KEY_NUDGE_PHONE, fallback.nudgeOnPhone),
        )
    }

    fun write(context: Context, settings: StepGoalSettings) {
        prefs(context).edit()
            .putInt(KEY_GOAL, settings.goal)
            .putBoolean(KEY_BUZZ, settings.buzzOnGoal)
            .putBoolean(KEY_NUDGE, settings.hourlyNudge)
            .putInt(KEY_HOURLY, settings.hourlyGoal)
            .putInt(KEY_FROM, settings.activeFrom)
            .putInt(KEY_UNTIL, settings.activeUntil)
            .putBoolean(KEY_NUDGE_PHONE, settings.nudgeOnPhone)
            .apply()
    }

    /** Whether the morning report is sent, and when. */
    fun morning(context: Context): MorningSettings {
        val p = prefs(context)
        val fallback = MorningSettings()
        return MorningSettings(
            on = p.getBoolean(KEY_MORNING, fallback.on),
            minute = p.getInt(KEY_MORNING_AT, fallback.minute).coerceIn(0, 1439),
            afterWaking = p.getBoolean(KEY_MORNING_WAKE, fallback.afterWaking),
        )
    }

    fun writeMorning(context: Context, settings: MorningSettings) {
        prefs(context).edit()
            .putBoolean(KEY_MORNING, settings.on)
            .putInt(KEY_MORNING_AT, settings.minute)
            .putBoolean(KEY_MORNING_WAKE, settings.afterWaking)
            .apply()
    }

    /**
     * The day the morning report last went out, or -1.
     *
     * Kept because WorkManager will run a booking it missed as soon as it
     * can, and a phone that was off at seven should not be told about its
     * night at noon and then again tomorrow morning.
     */
    fun morningSentDay(context: Context): Long = prefs(context).getLong(KEY_MORNING_DAY, -1)

    fun morningSent(context: Context, day: Long) {
        prefs(context).edit().putLong(KEY_MORNING_DAY, day).apply()
    }

    /** Whether the journal asks at the end of the day, and when. */
    fun journal(context: Context): JournalSettings {
        val p = prefs(context)
        val fallback = JournalSettings()
        return JournalSettings(
            on = p.getBoolean(KEY_JOURNAL, fallback.on),
            minute = p.getInt(KEY_JOURNAL_AT, fallback.minute).coerceIn(0, 1439),
        )
    }

    fun writeJournal(context: Context, settings: JournalSettings) {
        prefs(context).edit()
            .putBoolean(KEY_JOURNAL, settings.on)
            .putInt(KEY_JOURNAL_AT, settings.minute)
            .apply()
    }

    fun history(context: Context): StepGoalHistory {
        val p = prefs(context)
        return StepGoalHistory(
            goalFiredDay = p.getLong(KEY_FIRED_DAY, -1),
            nudgeFiredDay = p.getLong(KEY_NUDGE_DAY, -1),
            nudgeFiredHour = p.getInt(KEY_NUDGE_HOUR, -1),
        )
    }

    /**
     * Written before the buzz is sent, not after.
     *
     * A pulse costs a session with the band and can fail halfway; recording
     * only on success would retry every quarter of an hour for the rest of
     * the day. One missed buzz is a smaller fault than twelve.
     */
    fun recordGoalFired(context: Context, day: Long) {
        prefs(context).edit().putLong(KEY_FIRED_DAY, day).apply()
    }

    /**
     * The Health tab's layout, as one string.
     *
     * One key rather than a row of them: the order matters as much as the
     * sizes, and a layout half-written by a crash should fall back to the
     * default whole rather than to three tiles in the wrong order.
     */
    fun tiles(context: Context): List<Tile> =
        decodeTiles(prefs(context).getString(KEY_TILES, null))

    fun writeTiles(context: Context, tiles: List<Tile>) {
        prefs(context).edit().putString(KEY_TILES, encodeTiles(tiles)).apply()
    }

    /**
     * Whether the tiles wear their change chips.
     *
     * On by default: a figure with nothing to compare it against is the
     * thing this tab exists to improve on. Off for anyone who wants the
     * reading and not the commentary.
     */
    fun showTrends(context: Context): Boolean = prefs(context).getBoolean(KEY_TRENDS, true)

    fun writeShowTrends(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_TRENDS, show).apply()
    }

    /**
     * Which writers the step figures are counted from.
     *
     * Defaults to [SourceChoice.Wearables] rather than to everything, which
     * is a deliberate change in behaviour: the phone writes its own steps and
     * counting them alongside the band's double-counts a walk. Nobody is
     * asked, because there is a right answer for a band owner; the Goals
     * screen is where anybody who disagrees says so.
     */
    fun stepSource(context: Context): SourceChoice =
        SourceChoice.decode(prefs(context).getString(KEY_STEP_SOURCE, null))

    fun writeStepSource(context: Context, choice: SourceChoice) {
        prefs(context).edit().putString(KEY_STEP_SOURCE, SourceChoice.encode(choice)).apply()
        HealthSources.forget()
    }

    fun recordNudgeFired(context: Context, day: Long, hour: Int) {
        prefs(context).edit()
            .putLong(KEY_NUDGE_DAY, day)
            .putInt(KEY_NUDGE_HOUR, hour)
            .apply()
    }
}
