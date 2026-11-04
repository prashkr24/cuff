package com.srineere.airbridge.health

/**
 * What the band is asked to do about steps.
 *
 * Two different promises. The daily goal fires once, when you cross it. The
 * hourly nudge fires ten minutes *before* the hour, because a buzz at the
 * turn of the hour reports an hour you can no longer do anything about.
 */
data class StepGoalSettings(
    val goal: Int = 10_000,
    val buzzOnGoal: Boolean = false,
    val hourlyNudge: Boolean = false,
    val hourlyGoal: Int = 250,
    /** Hours the nudge is allowed to fire, inclusive of [activeFrom]. */
    val activeFrom: Int = 9,
    val activeUntil: Int = 21,
    /**
     * Whether the hourly nudge also lands on the phone.
     *
     * Off unless asked for. This app exists to move notifications off the
     * phone and onto the wrist, so one that puts them back is a choice
     * somebody makes rather than a default they inherit. The step goal has
     * no equivalent on purpose: it fires once a day and crossing it is not
     * news you need twice.
     */
    val nudgeOnPhone: Boolean = false,
)

/** What has already been sent, so nothing fires twice. */
data class StepGoalHistory(
    /** The epoch day the daily goal last buzzed, or -1. */
    val goalFiredDay: Long = -1,
    /** Epoch day and hour the nudge last fired, or -1. */
    val nudgeFiredDay: Long = -1,
    val nudgeFiredHour: Int = -1,
)

/**
 * Whether crossing the goal should buzz right now.
 *
 * Silence wins over everything, exactly as it does for notifications: a goal
 * is not more important than quiet hours, and a wrist that buzzes at 2am
 * because you walked to the bathroom is the fastest way to have the feature
 * turned off for good. Crossing while silenced is simply missed rather than
 * queued, because by the time the quiet ends the news is stale.
 */
fun shouldBuzzForGoal(
    settings: StepGoalSettings,
    history: StepGoalHistory,
    stepsToday: Long,
    today: Long,
    silenced: Boolean,
): Boolean = settings.buzzOnGoal &&
    !silenced &&
    settings.goal > 0 &&
    stepsToday >= settings.goal &&
    history.goalFiredDay != today

/**
 * Whether the hourly nudge is due.
 *
 * Fires in the last ten minutes of an hour, once, and only when the hour is
 * still short. [minute] is the minute of the hour now.
 */
fun shouldNudgeForHour(
    settings: StepGoalSettings,
    history: StepGoalHistory,
    stepsThisHour: Long,
    today: Long,
    hour: Int,
    minute: Int,
    silenced: Boolean,
): Boolean = settings.hourlyNudge &&
    !silenced &&
    hour in settings.activeFrom..settings.activeUntil &&
    minute >= NUDGE_FROM_MINUTE &&
    stepsThisHour < settings.hourlyGoal &&
    !(history.nudgeFiredDay == today && history.nudgeFiredHour == hour)

/** Ten minutes is one lap of an office and leaves the hour fixable. */
const val NUDGE_FROM_MINUTE = 50

/**
 * Whether the band can be pulsed at all right now.
 *
 * One rule, in one place, because the bug it replaces was two copies of it
 * that disagreed: the step check tested some of these and the pulse itself
 * tested more, and the "already fired today" record was written in the gap
 * between them. Crossing the step goal while a live heart rate reading held
 * the band therefore marked the goal fired, buzzed nothing, and never tried
 * again that day.
 *
 * [measuring] is the one that actually fired: a reading holds the band's
 * single session for up to two minutes, and the goal is checked every
 * fifteen.
 */
fun canPulseBand(
    ready: Boolean,
    stopping: Boolean,
    bandLinked: Boolean,
    measuring: Boolean,
): Boolean = ready && !stopping && bandLinked && !measuring
