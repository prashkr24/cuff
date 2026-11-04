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
    val oxygenTrend: Trend? = null,
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

    val lastNight: Night? get() = nights.maxByOrNull { it.day }

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
     * writes -- blood oxygen, on a phone where Google Health holds no
     * write permission for it -- is left out of the count rather than
     * counted as out of range.
     */
    val vitals: List<Trend>
        get() = listOfNotNull(restingTrend, hrvTrend, breathingTrend, skinTrend, oxygenTrend)
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
     */
    var loading by mutableStateOf(false)
        private set

    private var tilesLoaded = false
    private var goalsLoaded = false
    private var windowDays = WINDOW_DAYS

    /** How many days the last read covered, for a screen that offers a choice. */
    val days: Int get() = windowDays

    /**
     * Reads further back, when a detail screen asks for a longer window.
     *
     * One way only. A ninety-day read is a superset of a thirty-day one, so
     * widening costs one read and narrowing again would cost another for
     * nothing -- the screens that show less simply take the tail they want.
     */
    fun widen(context: Context, days: Int) {
        if (days <= windowDays) return
        windowDays = days
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
        if (loading) return
        val age = System.currentTimeMillis() - loadedAt
        if (!force && loadedAt != 0L && age < FRESH_MILLIS) return
        loading = true
        own.launch {
            try {
                val next = withContext(Dispatchers.IO) { load(context) }
                data = next
                loadedAt = System.currentTimeMillis()
            } finally {
                // In a finally, because a read that is cancelled or throws
                // must still clear the flag. Left set, it refuses every
                // later refresh and the tab never loads again.
                loading = false
            }
        }
    }

    private suspend fun load(context: Context): HealthData =
        loadHealthData(context, reader, windowDays)

    private companion object {
        /** What the tab opens with: a month, which every tile's window fits in. */
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

    val snapshot = reader.read(context, days = days)
    return HealthData(
        stage = HealthStage.Granted,
        snapshot = snapshot,
        restingTrend = trendOf(snapshot.restingHeartRate),
        hrvTrend = trendOf(snapshot.hrv),
        breathingTrend = trendOf(snapshot.respiratoryRate),
        skinTrend = trendOf(snapshot.skinTemperature),
        oxygenTrend = trendOf(snapshot.oxygenSaturation),
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
}

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
