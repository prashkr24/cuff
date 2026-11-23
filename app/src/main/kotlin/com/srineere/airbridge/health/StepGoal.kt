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
    /**
     * The hours the nudge is allowed to fire in: [activeFrom] inclusive,
     * [activeUntil] **exclusive**.
     *
     * The asymmetry is not a convention, it falls out of what the nudge
     * says. It fires at HH:50 and reports the hour HH, which is still
     * running -- so firing in hour [activeUntil] would be commenting on
     * an hour that ends after the window the person set. With the
     * defaults, 9 and 21, the first nudge is at 09:50 and the last at
     * 20:50, both inside the "09:00–21:00" the Alerts tab prints.
     *
     * The end was inclusive until it was found on a phone, buzzing at
     * 21:50 under a label that said the window closed at 21:00. The
     * label was right; the range was wrong. Anything that widens either
     * end has to answer the same question: is the hour being reported
     * inside what the label promises?
     */
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
 * Whether the daily goal buzz is a thing that can happen at all.
 *
 * It is a figure set on the Steps screen and read against a whole day, so
 * it goes with the Health tab. Somebody who turns that tab off and leaves
 * `buzzOnGoal` true has a setting with nowhere left to turn it off, and a
 * buzz arriving from a screen that is no longer in the app is the worst
 * version of this. The stored flag is not cleared, so turning the tab
 * back on returns the goal they had.
 */
fun goalBuzzWanted(settings: StepGoalSettings, healthOn: Boolean): Boolean =
    settings.buzzOnGoal && healthOn

/**
 * Whether anything is still worth waking up to check.
 *
 * The hourly nudge is deliberately not conditioned on [healthOn]. It is
 * the one thing here that is not a Health tab feature wearing an Alerts
 * hat: it reads how many steps this hour has had and nothing else, asks
 * Health Connect for that permission on its own, and buzzes the wrist.
 * Tying it to a tab meant the price of a buzz every hour was thirteen
 * record types and a page nobody wanted.
 *
 * With neither wanted the relay drops its alarm entirely rather than
 * waking every quarter of an hour to read a setting that is off.
 */
fun stepWorkWanted(settings: StepGoalSettings, healthOn: Boolean): Boolean =
    goalBuzzWanted(settings, healthOn) || settings.hourlyNudge

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
 *
 * `until` and not `..`: the window's end is exclusive, because the buzz
 * reports the hour it lands in and that hour is still running. See
 * [StepGoalSettings.activeFrom].
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
    hour in settings.activeFrom until settings.activeUntil &&
    minute >= NUDGE_FROM_MINUTE &&
    stepsThisHour < settings.hourlyGoal &&
    !(history.nudgeFiredDay == today && history.nudgeFiredHour == hour)

/** Ten minutes is one lap of an office and leaves the hour fixable. */
const val NUDGE_FROM_MINUTE = 50

/**
 * How long until the next step check, in milliseconds.
 *
 * A plain quarter-hour timer samples four fixed minutes of every hour --
 * whichever minute the relay happened to start on, plus 15, 30 and 45 -- and
 * [shouldNudgeForHour] only answers yes in the last ten. Those four land
 * inside that window for ten of the fifteen possible phases and miss it
 * entirely for the other five: a relay started at two minutes past checks at
 * :02, :17, :32 and :47 for as long as it runs, so the hourly nudge never
 * fires once. Not rarely -- never, and silently.
 *
 * So when the nudge is on, the wait is shortened to land on [NUDGE_FROM_MINUTE]
 * when that comes first. The quarter hour is still the ceiling, because the
 * daily goal wants checking whether or not the hour does.
 *
 * [minute] and [second] are the clock now.
 */
fun nextStepCheckMillis(
    hourlyNudge: Boolean,
    minute: Int,
    second: Int,
    periodMillis: Long,
): Long {
    if (!hourlyNudge) return periodMillis
    val sinceHour = (minute * 60 + second) * 1000L
    val window = NUDGE_FROM_MINUTE * 60 * 1000L
    // Already inside the window: the next one is an hour from its start.
    val untilWindow = if (sinceHour < window) window - sinceHour else 3_600_000L + window - sinceHour
    return minOf(periodMillis, untilWindow)
}

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
