package com.srineere.airbridge.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.srineere.airbridge.health.HealthAvailability
import com.srineere.airbridge.health.HealthCache
import com.srineere.airbridge.health.HealthConnect
import com.srineere.airbridge.health.HeartSample
import com.srineere.airbridge.health.HealthReader
import com.srineere.airbridge.health.Goals
import com.srineere.airbridge.health.HealthGoals
import com.srineere.airbridge.health.HealthSettings
import com.srineere.airbridge.health.HealthSnapshot
import com.srineere.airbridge.health.Night
import com.srineere.airbridge.health.Reading
import com.srineere.airbridge.health.Session
import com.srineere.airbridge.health.SleepScore
import com.srineere.airbridge.health.sleepScoreOf
import com.srineere.airbridge.health.Tile
import com.srineere.airbridge.health.defaultTiles
import com.srineere.airbridge.health.Trend
import com.srineere.airbridge.health.trendOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZoneId
import java.time.LocalDate
import java.time.LocalTime
import com.srineere.airbridge.health.MorningMoment

/** Where the Health tab is between opening and having something to draw. */
enum class HealthStage { Loading, NotConnected, NeedsUpdate, Granted }

/**
 * Everything the Health tab draws, already reduced.
 *
 * Trends are computed once here rather than inside composables: a trend over
 * thirty readings is cheap, but recomposition is not something to hand a loop
 * to, and a tile that recalculates its own band while you scroll is a tile
 * that costs a frame for no reason.
 */
data class HealthData(
    val stage: HealthStage = HealthStage.Loading,
    val snapshot: HealthSnapshot = HealthSnapshot(),
    val restingTrend: Trend? = null,
    val hrvTrend: Trend? = null,
    val breathingTrend: Trend? = null,
    val skinTrend: Trend? = null,
    val weightTrend: Trend? = null,
    val stepsTrend: Trend? = null,
    val sleepTrend: Trend? = null,
    /** Eaten minus burned, a day at a time: which way the balance is going. */
    val energyTrend: Trend? = null,
    /** Kilocalories eaten a day, for the tile about what went in. */
    val nutritionTrend: Trend? = null,
    /** Minutes of recorded exercise a day. */
    val activityTrend: Trend? = null,
    val nights: List<Night> = emptyList(),
) {
    val stepsToday: Long get() = snapshot.stepsToday ?: 0L

    // The goal is passed in rather than held here. It lives in air_health.xml
    // and the band buzzes against that number, so a copy in the data layer is
    // a second goal waiting to disagree with the one that fires.
    fun stepsToGo(goal: Int): Long = (goal - stepsToday).coerceAtLeast(0L)

    fun stepProgress(goal: Int): Float =
        if (goal <= 0) 0f else (stepsToday.toFloat() / goal).coerceIn(0f, 1f)

    /** Today's balance, or null while either half is unknown. */
    val energyBalance: Double?
        get() {
            val burned = snapshot.burnedToday ?: return null
            val eaten = snapshot.eatenToday ?: return null
            return eaten - burned
        }

    /**
     * The newest night there is, whenever it fell.
     *
     * For the screens that chart history, where a night three days ago is
     * still the right thing to draw.
     */
    val newestNight: Night? get() = nights.maxByOrNull { it.day }

    /**
     * Last night, meaning the night that ended this morning -- and nothing
     * otherwise.
     *
     * This used to be whatever night was newest, which is right for most of
     * the day and wrong in the hours it matters. Health Connect files a
     * sleep session under the day it ended, so at two in the morning the
     * newest night is the one before, and every screen headed "last night"
     * was showing it: a sleep score, a sleeping heart rate and a set of
     * stages for a night you had already got up from, while the night you
     * were actually in had not been written yet.
     *
     * Google Health does not do this, and it is the reason: a figure about
     * last night is a figure about a night that has finished.
     */
    val lastNight: Night? get() = newestNight?.takeIf { it.day == LocalDate.now().toEpochDay() }

    /**
     * Whether the night is still out there rather than simply absent.
     *
     * Two different silences. Before the morning is over, no night means
     * one is coming -- you are in it, or the band has not synced it yet.
     * After that it means there isn't one, and saying "measuring" all
     * afternoon would be an excuse rather than a fact.
     *
     * Shares [MorningMoment.LATEST_HOUR] with the morning report, so the
     * hour the morning gives up has one definition.
     */
    val nightPending: Boolean
        get() = lastNight == null && LocalTime.now().hour < MorningMoment.LATEST_HOUR

    /**
     * Your heart rate while you were actually asleep, as the tenth
     * percentile of the samples inside the night.
     *
     * Not the average: a night holds a few minutes of lying awake and the
     * odd spike, and the mean of those is higher than anything your heart
     * did while sleeping. Not the minimum either, which is one stray
     * reading. The tenth percentile is the settled floor of the night.
     */
    val sleepingHeartRate: Int?
        get() = lastNight?.let { sleepingRate(snapshot.heartRecent, it) }

    /** Last night, scored against the nights around it. */
    val sleepScore: SleepScore? get() = lastNight?.let { sleepScoreOf(it, nights) }

    /**
     * Eaten minus burned, one figure a day, for the days where both halves
     * exist. A day with no food logged is left out rather than counted as a
     * deficit the size of everything you burned.
     */
    val balanceByDay: List<Reading>
        get() {
            val burned = snapshot.burnedDaily.associate { it.day to it.value }
            return snapshot.eatenDaily.mapNotNull { eaten ->
                burned[eaten.day]?.let { Reading(eaten.day, eaten.value - it) }
            }
        }

    /**
     * The vitals that actually have a trend behind them. A metric no app
     * writes is left out of the count rather than counted as out of range:
     * silence is not a reading in range.
     */
    val vitals: List<Trend>
        get() = listOfNotNull(restingTrend, hrvTrend, breathingTrend, skinTrend)
}

/**
 * Eaten minus burned, for the days that have both halves.
 *
 * A day with no food logged is left out rather than counted as a deficit the
 * size of everything you burned -- the same rule [HealthData.balanceByDay]
 * follows, kept here so the trend and the chart cannot disagree.
 */
internal fun balanceReadings(snapshot: HealthSnapshot): List<Reading> {
    val burned = snapshot.burnedDaily.associate { it.day to it.value }
    return snapshot.eatenDaily.mapNotNull { eaten ->
        burned[eaten.day]?.let { Reading(eaten.day, eaten.value - it) }
    }
}

/**
 * Sessions collapsed into minutes a day.
 *
 * Days with no session are left out, not zeroed: the question a trend
 * answers here is whether the days you train are getting longer, and a rest
 * day is not a short workout.
 */
internal fun List<Session>.toDailyMinutes(): List<Reading> =
    groupBy { it.start.atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() }
        .map { (day, sessions) ->
            Reading(day, sessions.sumOf { Duration.between(it.start, it.end).toMinutes() }.toDouble())
        }
        .sortedBy { it.day }

/**
 * Holds the Health tab's data for as long as the app is up.
 *
 * Reads are deliberately not automatic. Health Connect is an IPC call per
 * query and it rate-limits callers, so the tab loads once and then only when
 * something asks it to -- opening the tab after a while, or coming back from
 * the permission sheet. Nothing here polls.
 */
class HealthState(private val reader: HealthReader = HealthReader()) {

    var data by mutableStateOf(HealthData())
        private set

    /**
     * The tab's layout, held here rather than read by the grid.
     *
     * The edit screen and the tab are two destinations over one list: if each
     * read the file for itself, coming back from an edit would show the old
     * layout until something else forced a recomposition. Writing through one
     * state makes the tab change under the edit screen, which is the point.
     */
    var tiles by mutableStateOf(defaultTiles)
        private set

    /**
     * The targets the goal lines are drawn from.
     *
     * Held here for the same reason as the layout: a dozen screens draw a
     * goal line, and a goal read separately by each of them is a goal that
     * disagrees with itself the moment one is changed.
     */
    var goals by mutableStateOf(Goals())
        private set

    /**
     * The route a tile opened, and the colour of the tile that opened it.
     *
     * The route is half the pair because the colour belongs to one screen,
     * not to everything you visit afterwards: without it, opening a grey
     * Activity tile left every later screen grey.
     */
    var openedWith: Pair<String, androidx.compose.ui.graphics.Color>? = null

    private var loadedAt = 0L

    /**
     * True while a read is in flight.
     *
     * Public because an empty window and an unread window look identical on
     * screen, and only one of them means "you did not walk that week".
     *
     * Called `loading` until the tab learned to open on its last read.
     * The two things it was being asked had quietly become different
     * questions: "is a read running" and "is there anything to show". They
     * used to have the same answer, because the only moment anything was
     * running was the moment before the first result arrived. Now that a
     * cold start draws the kept snapshot first, a read is nearly always
     * running over a screen that is already full, and a screen keyed on
     * the old name would have flashed its empty state on every refresh.
     * "Nothing to show" is [HealthStage.Loading] on the data itself, which
     * is where it has always lived and where it should be asked.
     */
    var refreshing by mutableStateOf(false)
        private set

    private var tilesLoaded = false
    private var goalsLoaded = false
    private var restored = false
    private var windowDays by mutableStateOf(WINDOW_DAYS)

    /** How many days the last read covered, for a screen that offers a choice. */
    val days: Int get() = windowDays

    /**
     * How many days the data **in hand** covers, which is not always
     * [days]: between a widen and the read it starts, the window has moved
     * and the figures have not.
     *
     * Zero until something has been read or restored.
     */
    var loadedDays by mutableStateOf(0)
        private set

    /**
     * The longer window being read, while what is drawn is a shorter one.
     *
     * The whole of the missing feedback on the window controls. Tapping 90
     * used to do nothing visible for several seconds -- the chart kept its
     * thirty days, which is correct, and said nothing about the read that
     * was going to fill the rest of it in. Null when nothing longer is on
     * its way, so a bar can ask this and draw nothing the rest of the time.
     *
     * Null while nothing has been read at all, which is not the same
     * silence: the charts already say "Reading these days…" where their
     * own window is empty, and a bar saying it too would be the same
     * sentence twice on a first run.
     */
    val wideningTo: Int?
        get() = if (refreshing && loadedDays > 0 && windowDays > loadedDays) windowDays else null

    /**
     * Reads further back, when a detail screen asks for a longer window.
     *
     * One way only. A ninety-day read is a superset of a thirty-day one, so
     * widening costs one read and narrowing again would cost another for
     * nothing -- the screens that show less simply take the tail they want.
     * Which is also why 30 → 90 → 30 → 90 is one read and not four, and why
     * a cache keyed by window length would be a second answer to a question
     * that already has one.
     *
     * The figure asked for is rounded up by [readWindow], because the
     * screens do not ask in round numbers. Stepping a seven-day chart back
     * through the autumn asks for 35, then 42, then 49: three separate
     * reads of nearly the same ground, each one a fresh set of Health
     * Connect queries on an interface that rate-limits callers. Rounded,
     * those three are one.
     */
    fun widen(context: Context, days: Int) {
        if (days <= windowDays) return
        windowDays = readWindow(days)
        refresh(context, force = true)
    }

    /** Reads the stored layout once; later calls are free. */
    fun loadTiles(context: Context) {
        if (tilesLoaded) return
        tilesLoaded = true
        tiles = HealthSettings.tiles(context)
        showTrends = HealthSettings.showTrends(context)
    }

    /** Whether tiles show their change chips. Read with the layout. */
    var showTrends by mutableStateOf(true)
        private set

    fun saveShowTrends(context: Context, show: Boolean) {
        showTrends = show
        HealthSettings.writeShowTrends(context, show)
    }

    fun saveTiles(context: Context, next: List<Tile>) {
        tilesLoaded = true
        tiles = next
        HealthSettings.writeTiles(context, next)
    }

    /** Reads the stored goals once; later calls are free. */
    fun loadGoals(context: Context) {
        if (goalsLoaded) return
        goalsLoaded = true
        goals = HealthGoals.read(context)
    }

    fun saveGoals(context: Context, next: Goals) {
        goalsLoaded = true
        goals = next
        HealthGoals.write(context, next)
    }

    /** Loads unless a load is already running or the last one is still fresh. */
    /**
     * A scope of this state's own, not the caller's.
     *
     * A read used to run on whichever screen asked for it, which was fine
     * until a screen asked and then left: saving a food entry refreshed and
     * popped in the same breath, the scope died mid-read, and what landed in
     * [data] was a half-cancelled snapshot with nothing in it. The entry was
     * in Health Connect the whole time; the tab had simply overwritten
     * itself with the wreckage of its own cancelled read.
     *
     * This state lives as long as the app does, so its reads should too.
     */
    private val own = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun refresh(context: Context, force: Boolean = false) {
        restore(context)
        if (refreshing) return
        val age = System.currentTimeMillis() - loadedAt
        if (!force && loadedAt != 0L && age < FRESH_MILLIS) return
        refreshing = true
        own.launch {
            // The window this read is for. A widen that arrives while it is
            // running is dropped by the `refreshing` guard above, but
            // windowDays has already moved -- so the next widen to the same
            // figure early-returns at `days <= windowDays` and the longer
            // read never happens at all. Opening Readiness during the tab's
            // own first read used to leave it stuck on "30 of 60 nights"
            // until the snapshot went stale a minute later.
            val asked = windowDays
            try {
                val next = withContext(Dispatchers.IO) {
                    val read = loadHealthData(context, reader, asked)
                    // Kept from the thread the read already runs on. Forty
                    // kilobytes of JSON is a few milliseconds, which is
                    // nothing against the twenty queries above it and a
                    // visible stutter on the main thread. A read that came
                    // back without permission is not kept: it holds no
                    // readings, and writing it would throw away the ones
                    // somebody saw yesterday.
                    if (read.stage == HealthStage.Granted) {
                        runCatching { HealthCache.write(context, read.snapshot, asked) }
                    }
                    read
                }
                data = next
                loadedDays = asked
                loadedAt = System.currentTimeMillis()
            } finally {
                // In a finally, because a read that is cancelled or throws
                // must still clear the flag. Left set, it refuses every
                // later refresh and the tab never loads again.
                refreshing = false
            }
            if (windowDays != asked) refresh(context, force = true)
        }
    }

    /**
     * Draws the last read back, before anything is asked of Health Connect.
     *
     * Synchronous, and on the thread that asked. It is a preferences string
     * and a JSON parse -- a few milliseconds, once, at the moment the
     * alternative is a spinner -- and doing it off the main thread would
     * mean at least one frame of the empty state, which is the thing this
     * exists to remove.
     *
     * Three decisions worth keeping:
     *
     * - Only when there is nothing read yet. A restore that could overwrite
     *   a live snapshot is a restore that can put old figures over new ones.
     * - [loadedAt] is deliberately left at zero. A restored reading is
     *   *drawn*, not *fresh*: the freshness window exists to stop switching
     *   tabs costing a read, and a cold start is not switching tabs. The
     *   figures on screen are honest about their own age because the
     *   header's "N min ago" comes from `writtenAt`, which is an instant in
     *   the snapshot and not a measure of when we last asked.
     * - [windowDays] is **not** restored from the entry. It is what the
     *   next read will cover, not what this one did, and a cache claiming
     *   ninety days would make `widen(90)` early-return so the longer read
     *   never happened at all.
     */
    private fun restore(context: Context) {
        if (restored) return
        restored = true
        if (data.stage != HealthStage.Loading) return
        // A store that cannot be read is not a reason to show nothing: the
        // read below is about to produce the same figures anyway.
        val kept = runCatching { HealthCache.read(context) }.getOrNull() ?: return
        data = healthDataOf(kept.snapshot)
        loadedDays = kept.days
    }

    /**
     * Runs something that must finish on the scope the reads use.
     *
     * The same rule as [refresh], for the other direction. A write launched
     * on a screen's own `rememberCoroutineScope` dies when the screen leaves
     * -- and the two things this app writes, a food entry and its deletion,
     * are both reached from a screen that pops the moment the button is
     * pressed. The record may or may not have landed, and neither the error
     * line nor the refresh ever runs to say which.
     */
    fun write(block: suspend () -> Unit) {
        own.launch { block() }
    }

    private companion object {
        /**
         * What the tab opens with: a month, which every tile's window fits
         * in. The same thirty [readWindow] rounds to and [HealthCache.DAYS]
         * keeps: a read, a rounding step or a kept window shorter than what
         * the tab opens with would each be a window nothing ever wanted.
         * Change one and change all three.
         */
        const val WINDOW_DAYS = 30
        /** Long enough that switching tabs costs nothing, short enough to feel live. */
        const val FRESH_MILLIS = 60_000L
    }
}

/**
 * One read of Health Connect, reduced the way every screen expects it.
 *
 * A plain function rather than a method because the widgets need exactly
 * this and have no [HealthState] to ask: they run from a worker with no
 * composition, no tab and no scope. Two readers would be two sets of
 * arithmetic drifting apart, and the trend a tile shows differing from the
 * one its widget shows is the kind of bug nobody reports because nobody
 * believes it.
 */
internal suspend fun loadHealthData(
    context: Context,
    reader: HealthReader = HealthReader(),
    days: Int = 30,
): HealthData {
    when (HealthConnect.availability(context)) {
        HealthAvailability.NotInstalled ->
            return HealthData(stage = HealthStage.NotConnected)
        HealthAvailability.NeedsUpdate ->
            return HealthData(stage = HealthStage.NeedsUpdate)
        HealthAvailability.Ready -> Unit
    }
    val client = HealthConnect.clientOrNull(context)
        ?: return HealthData(stage = HealthStage.NotConnected)
    val granted = HealthConnect.granted(client)
    // Nothing granted is not an error, it is the first run: the tab shows
    // what it would give you and asks, rather than an empty grid.
    if (granted.intersect(HealthConnect.readPermissions).isEmpty()) {
        return HealthData(stage = HealthStage.NotConnected)
    }

    return healthDataOf(reader.read(context, days = days))
}

/**
 * A snapshot reduced to everything the tab draws.
 *
 * Split out from [loadHealthData] when the tab learned to open on its last
 * read, and it is the reason that cache keeps the snapshot rather than the
 * reduction. A restored frame runs this; a live read runs this; so the two
 * cannot produce a different usual band, a different balance or a
 * different count of nights, whatever either of them is given. Keeping the
 * reduced figures on disk instead would have been a second copy of every
 * number on the tab, waiting for somebody to change how a trend is counted
 * and not know it existed.
 */
internal fun healthDataOf(snapshot: HealthSnapshot): HealthData = HealthData(
    stage = HealthStage.Granted,
    snapshot = snapshot,
    restingTrend = trendOf(snapshot.restingHeartRate),
    hrvTrend = trendOf(snapshot.hrv),
    breathingTrend = trendOf(snapshot.respiratoryRate),
    skinTrend = trendOf(snapshot.skinTemperature),
    weightTrend = trendOf(snapshot.weight),
    stepsTrend = trendOf(snapshot.steps),
    sleepTrend = trendOf(snapshot.nights.toSleepReadings()),
    // These three are derived rather than read: Health Connect has
    // no "balance" or "minutes exercised a day" to ask for, so they
    // are built from the halves it does hold. Computed here with the
    // rest, not in a tile, because a trend recomputed on every
    // recomposition costs a frame to say the same thing.
    energyTrend = trendOf(balanceReadings(snapshot)),
    nutritionTrend = trendOf(snapshot.eatenDaily),
    activityTrend = trendOf(snapshot.sessions.toDailyMinutes()),
    nights = snapshot.nights.sortedBy { it.day },
)

/**
 * The window a read actually covers, for a screen that asked for an odd
 * number of days.
 *
 * Rounded up to the next whole month, because the screens ask in whatever
 * their window bar happens to need and Health Connect charges per round
 * trip rather than per day. A seven-day chart stepped back four times asks
 * for 35, 42, 49 and 56 days: four reads of ground that overlaps almost
 * entirely, and four waits. Rounded, that is one read of sixty days and
 * three steps that cost nothing.
 *
 * Thirty is the floor because it is what the tab opens with, and the two
 * windows anything asks for by name -- ninety for the charts, a hundred
 * and twenty for readiness and fitness age -- are both whole months
 * already, so neither of them reads a day more than it did before.
 */
internal fun readWindow(days: Int): Int {
    if (days <= READ_STEP) return READ_STEP
    return ((days + READ_STEP - 1) / READ_STEP) * READ_STEP
}

/** A month, which is the window the tab opens with. */
private const val READ_STEP = 30

/**
 * Your heart rate while you were actually asleep, as the tenth percentile of
 * the samples inside the night.
 *
 * Not the average: a night holds a few minutes of lying awake and the odd
 * spike, and the mean of those is higher than anything your heart did while
 * sleeping. Not the minimum either, which is one stray reading. The tenth
 * percentile is the settled floor of the night.
 */
internal fun sleepingRate(samples: List<HeartSample>, night: Night): Int? {
    val from = night.start.toEpochMilli()
    val until = night.end.toEpochMilli()
    val inside = samples.filter { it.atMillis in from..until }.map { it.bpm }.sorted()
    if (inside.size < 10) return null
    return inside[(inside.size * 0.1).toInt()]
}

/** Sleep as one figure a night, in minutes, for the same trend maths. */
internal fun List<Night>.toSleepReadings(): List<Reading> =
    map { Reading(it.day, it.asleep.toMinutes().toDouble()) }

/**
 * How a person says a length out loud: "6h 28m" for a night, but "34m" for
 * half an hour -- nobody says "0h 34m".
 */
internal fun Duration.asHoursMinutes(): String {
    val total = toMinutes()
    return if (total < 60) "${total}m" else "${total / 60}h ${"%02d".format(total % 60)}m"
}

val LocalHealthState = compositionLocalOf<HealthState> {
    error("No HealthState provided")
}

/**
 * The colour of the tile you came in through.
 *
 * A tile you have recoloured grey opening a screen full of its old colour
 * is the tab and the screen disagreeing about what the thing is. Null means
 * the screen was not reached from a tile, in which case it uses the colour
 * its metric teaches.
 */
val LocalTileColour = compositionLocalOf<androidx.compose.ui.graphics.Color?> { null }

@Composable
fun rememberHealthState(): HealthState = remember { HealthState() }

/**
 * Loads on first composition and whenever the tab is returned to, which is
 * what [HealthState.refresh]'s freshness window is there to make cheap.
 */
@Composable
internal fun healthData(context: Context): State<HealthData> {
    val state = LocalHealthState.current
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) { state.refresh(context) }
    return androidx.compose.runtime.rememberUpdatedState(state.data)
}
