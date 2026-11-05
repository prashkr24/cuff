package com.srineere.airbridge.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.width
import com.srineere.airbridge.health.Night
import com.srineere.airbridge.health.Reading
import com.srineere.airbridge.health.SleepStage
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.health.TileKind
import com.srineere.airbridge.health.Trend
import com.srineere.airbridge.ui.HealthStage
import com.srineere.airbridge.ui.asHoursMinutes
import com.srineere.airbridge.ui.toDailyMinutes
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What a tile says, before anything has decided how big to draw it.
 *
 * Built once per widget, outside composition, so that every face of a kind
 * is drawing the same arithmetic -- and read by the catalogue under Device,
 * so a preview there is the widget's own reading rather than a second
 * drawing of it that can drift -- the only thing a size changes is how
 * much of this is shown. That is the promise the pick sheets make: the
 * label never moves, and a swipe or a resize changes the reading, not the
 * layout.
 */
internal class Face(
    val kicker: String,
    val figure: String,
    val sub: String,
    val unit: String? = null,
    val detail: String? = null,
    val chart: Chart? = null,
    val meter: Float? = null,
    val chip: String? = null,
)

internal sealed interface Chart {
    /**
     * Equal-width columns: the only chart Glance draws without a bitmap.
     *
     * [blankZeros] is for a series whose slots exist whether or not they
     * hold a reading -- the hours of today. A gap there means "nothing was
     * recorded then", which is not the same claim as a bar of zero height.
     */
    class Bars(
        val values: List<Float>,
        val blankZeros: Boolean = false,
        /** What a full-height bar means, when the series has a ceiling of its own. */
        val ceiling: Float? = null,
        /**
         * Whether the newest bar is picked out.
         *
         * False for a series where every bar is a finished thing: seven
         * scored nights are seven equals, and brightening the last one says
         * "this is today" about a night that ended hours ago.
         */
        val highlightLast: Boolean = true,
    ) : Chart

    /** A night, in proportion. Keeps the sleep palette whatever colour the tile is. */
    class Stages(val parts: List<Pair<Float, SleepStage>>) : Chart

    /** Two or three shares that mean different things, each with its own colour. */
    class Split(val parts: List<Pair<Float, TileColour>>) : Chart

    /** Several readings that none of them fills a tile alone. */
    class Rows(val rows: List<Triple<String, String, TileColour>>) : Chart

    /**
     * Concentric arcs. The one chart here that cannot be made of
     * rectangles, so the one that costs a bitmap.
     */
    class Arcs(val parts: List<Pair<Float, TileColour>>) : Chart

    /** Arcs side by side, each with its figure in the middle. */
    class Dials(val parts: List<DialReading>) : Chart
}

@Composable
internal fun TileWidget(
    kind: TileKind,
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    val colour = snapshot.colourFor(kind)
    if (!snapshot.granted) {
        Empty(colours, kind.short, notConnected(snapshot), onClick)
        return
    }
    val face = faceFor(kind, snapshot)
    if (face == null) {
        Empty(colours, kind.short, "Nothing writes this to Health Connect yet", onClick)
        return
    }
    DrawFace(face, size, colour, colours, onClick, snapshot.showTrends)
}

private fun notConnected(snapshot: WidgetSnapshot): String = when (snapshot.health.stage) {
    HealthStage.NotConnected -> "Open the app to connect Health Connect"
    HealthStage.NeedsUpdate -> "Health Connect needs an update"
    else -> "Not read yet"
}

// ----------------------------------------------------------------- drawing

@Composable
private fun DrawFace(
    face: Face,
    size: WidgetSize,
    colour: TileColour,
    colours: WidgetColours,
    onClick: Action?,
    showTrends: Boolean,
) {
    val padding = 11.dp
    val tint = colours.tile(colour)
    // A launcher hands out whatever cell it likes, and the nearest declared
    // size is often a little shorter than the space. Centred, so a strip in a
    // slightly taller cell sits in it rather than clinging to the top edge.
    val alignment =
        if (size == WidgetSize.Strip || size == WidgetSize.Row) Alignment.CenterVertically
        else Alignment.Top
    WidgetCard(colours, onClick, padding, alignment) {
        when (size) {
            WidgetSize.Strip -> {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Kicker(face.kicker, tint, size = 9)
                    // Two columns leave room for an arrow and nothing else,
                    // and the figure beside it would be cut in half. The
                    // direction is the part that survives at this width.
                    if (showTrends && face.chip != null) {
                        Spacer(GlanceModifier.defaultWeight())
                        Sub(arrowOf(face.chip), tint, size = 11)
                    }
                }
                Gap(4.dp)
                FigureAndUnit(face, 20, colours)
            }

            WidgetSize.Row -> {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Kicker(face.kicker, tint, size = 9)
                    Spacer(GlanceModifier.width(9.dp))
                    FigureAndUnit(face, 19, colours)
                    Spacer(GlanceModifier.defaultWeight())
                    Sub(face.chip ?: face.sub, colours.muted, size = 10)
                }
            }

            WidgetSize.Square -> {
                Head(face, tint, colours, showTrends)
                Gap(7.dp)
                FigureAndUnit(face, 26, colours)
                Gap(3.dp)
                Sub(face.sub, colours.muted, size = 10)
                face.meter?.let {
                    Gap(7.dp)
                    Meter(it, innerWidth(padding), tint, colours.track, height = 4.dp)
                }
                Grow()
                DrawChart(
                    face.chart, colour, colours,
                    // What is left under the figure, rather than a constant
                    // that is right in one cell and wrong in every other.
                    height = (innerHeight(padding).value - SQUARE_ABOVE_CHART)
                        .coerceIn(20f, 72f).dp,
                    width = innerWidth(padding),
                )
            }

            WidgetSize.Wide, WidgetSize.Tall -> {
                Head(face, tint, colours, showTrends)
                Gap(6.dp)
                // All of it. A cap here was what left a field of empty card
                // under the chart: a launcher's 4x4 is taller than the 300dp
                // the face was drawn for, and a widget should spend the
                // space it was given.
                val chartHeight = (innerHeight(padding).value - WIDE_ABOVE_CHART)
                    .coerceIn(44f, 400f).dp
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(modifier = GlanceModifier.width(FIGURE_COLUMN)) {
                        FigureAndUnit(face, if (size == WidgetSize.Tall) 30 else 25, colours)
                        Gap(3.dp)
                        Sub(face.sub, colours.tile(colour), size = 10)
                        face.detail?.let {
                            Gap(4.dp)
                            Sub(it, colours.faint, size = 9, lines = 2)
                        }
                        face.meter?.let {
                            Gap(7.dp)
                            Meter(it, FIGURE_COLUMN, tint, colours.track, height = 4.dp)
                        }
                    }
                    Spacer(GlanceModifier.width(12.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        DrawChart(
                            face.chart, colour, colours,
                            height = chartHeight,
                            width = innerWidth(padding).value.minus(FIGURE_COLUMN.value + 12).dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Head(
    face: Face,
    tint: androidx.glance.unit.ColorProvider,
    colours: WidgetColours,
    showTrends: Boolean,
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Kicker(face.kicker, tint)
        if (showTrends && face.chip != null) {
            Spacer(GlanceModifier.defaultWeight())
            Sub(face.chip, tint, size = 10)
        }
    }
}

@Composable
private fun FigureAndUnit(face: Face, size: Int, colours: WidgetColours) {
    if (face.unit == null) {
        Figure(face.figure, size, colours.onBackground)
        return
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Figure(face.figure, size, colours.onBackground)
        Spacer(GlanceModifier.width(4.dp))
        Sub(face.unit, colours.muted, size = (size / 2.4f).roundToInt().coerceIn(9, 13))
    }
}

@Composable
private fun DrawChart(
    chart: Chart?,
    colour: TileColour,
    colours: WidgetColours,
    height: androidx.compose.ui.unit.Dp,
    width: androidx.compose.ui.unit.Dp,
) {
    when (chart) {
        null -> Unit
        is Chart.Bars -> Bars(
            chart.values,
            colours.tile(colour),
            colours.tileDim(colour),
            height,
            blankZeros = chart.blankZeros,
            top = chart.ceiling,
            highlightLast = chart.highlightLast,
        )
        is Chart.Stages -> Stripe(
            chart.parts.map { (share, stage) -> share to colours.sleepStage(stage) },
            height = height.value.coerceAtMost(STRIPE_MAX).dp,
            width = width,
        )
        is Chart.Split -> Stripe(
            chart.parts.map { (share, each) -> share to colours.tile(each) },
            height = height.value.coerceAtMost(STRIPE_MAX).dp,
            width = width,
        )
        is Chart.Arcs -> RingImage(chart.parts, colours, height)

        // Only the thumbnail draws this; the widget lays its own
        // dials out, because it has room for their labels.
        is Chart.Dials -> RingImage(
            chart.parts.map { it.fraction to it.colour }, colours, height,
        )

        is Chart.Rows -> Column(modifier = GlanceModifier.fillMaxWidth()) {
            chart.rows.forEach { (name, value, each) ->
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Sub(name, colours.muted, size = 10)
                    Spacer(GlanceModifier.defaultWeight())
                    Sub(value, colours.tile(each), size = 13)
                }
                Gap(5.dp)
            }
        }
    }
}

// ------------------------------------------------------------- the reading

internal fun faceFor(kind: TileKind, snapshot: WidgetSnapshot): Face? {
    val health = snapshot.health
    val data = health.snapshot
    return when (kind) {
        TileKind.Heart -> health.restingTrend?.let { trend ->
            Face(
                kicker = "Resting",
                figure = trend.latest.roundToInt().toString(),
                unit = "bpm",
                sub = "usual ${trend.usual.low.roundToInt()}–${trend.usual.high.roundToInt()}",
                chart = Chart.Bars(trend.readings.tail(14)),
                chip = chipOf(trend, "bpm"),
            )
        }

        TileKind.Zones -> data.heartToday.takeIf { it.isNotEmpty() }?.let { samples ->
            val latest = samples.maxByOrNull { it.atMillis }?.bpm ?: return@let null
            val low = samples.minOf { it.bpm }
            val high = samples.maxOf { it.bpm }
            Face(
                kicker = "Heart",
                figure = latest.toString(),
                unit = "bpm",
                sub = "$low–$high today",
                chart = Chart.Bars(samples.byHour(), blankZeros = true),
            )
        }

        TileKind.Sleep -> health.lastNight?.let { night ->
            val score = health.sleepScore?.total
            Face(
                kicker = "Sleep",
                figure = night.asleep.asHoursMinutes(),
                sub = if (score != null) "score $score" else "no score yet",
                detail = "woke " + night.end.atZone(java.time.ZoneId.systemDefault())
                    .toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) },
                chart = night.stageChart(),
                chip = health.sleepTrend?.let { chipOfMinutes(it) },
            )
        }

        TileKind.Steps -> {
            val goal = snapshot.goals.steps
            Face(
                kicker = "Steps",
                figure = "%,d".format(health.stepsToday),
                sub = if (goal > 0) "of %,d".format(goal) else "today",
                detail = if (goal > 0 && health.stepsToGo(goal) > 0) {
                    "%,d to go".format(health.stepsToGo(goal))
                } else if (goal > 0) "goal met" else null,
                chart = Chart.Bars(data.steps.tail(7)),
                meter = if (goal > 0) health.stepProgress(goal) else null,
                chip = health.stepsTrend?.let { chipOf(it, "", decimals = 0) },
            )
        }

        TileKind.Energy -> {
            val balance = health.energyBalance ?: return null
            val eaten = data.eatenToday ?: 0.0
            val burned = data.burnedToday ?: 0.0
            Face(
                kicker = "Energy",
                figure = signed(balance.roundToInt()),
                unit = "kcal",
                sub = if (balance < 0) "under" else "over",
                detail = "%,d in · %,d out".format(eaten.roundToInt(), burned.roundToInt()),
                chart = Chart.Split(
                    listOf(
                        eaten.toFloat().coerceAtLeast(1f) to TileColour.Hrv,
                        burned.toFloat().coerceAtLeast(1f) to TileColour.Energy,
                    ),
                ),
                chip = health.energyTrend?.let { chipOf(it, "kcal", decimals = 0) },
            )
        }

        TileKind.Activity -> {
            val daily = data.sessions.toDailyMinutes()
            val today = daily.lastOrNull()?.value?.roundToInt() ?: 0
            Face(
                kicker = "Activity",
                figure = today.toString(),
                unit = "min",
                sub = sessionNote(data.sessions.size),
                chart = Chart.Bars(daily.tail(7)),
                chip = health.activityTrend?.let { chipOf(it, "min", decimals = 0) },
            )
        }

        TileKind.Vitals -> {
            val rows = listOfNotNull(
                data.respiratoryRate.lastOrNull()
                    ?.let { Triple("Breathing", it.value.roundToInt().toString(), TileColour.Activity) },
                data.skinTemperature.lastOrNull()
                    ?.let { Triple("Skin", signedDecimal(it.value) + "°", TileColour.Energy) },
                data.oxygenSaturation.lastOrNull()
                    ?.let { Triple("SpO₂", "${it.value.roundToInt()}%", TileColour.Sleep) },
            )
            if (rows.isEmpty()) return null
            Face(
                kicker = "Vitals",
                figure = rows.first().second,
                sub = rows.first().first.lowercase(),
                chart = Chart.Rows(rows),
            )
        }

        TileKind.Hrv -> health.hrvTrend?.let { trend ->
            Face(
                kicker = "HRV",
                figure = trend.latest.roundToInt().toString(),
                unit = "ms",
                sub = "usual ${trend.usual.low.roundToInt()}–${trend.usual.high.roundToInt()}",
                chart = Chart.Bars(trend.readings.tail(14)),
                chip = chipOf(trend, "ms"),
            )
        }

        TileKind.Nutrition -> {
            val eaten = data.eatenToday ?: return null
            val protein = data.proteinToday ?: 0.0
            val carbs = data.carbsToday ?: 0.0
            val fat = data.fatToday ?: 0.0
            Face(
                kicker = "Food",
                figure = "%,d".format(eaten.roundToInt()),
                unit = "kcal",
                sub = mealNote(data.mealsDaily.values.lastOrNull() ?: 0),
                detail = "P ${protein.roundToInt()} · C ${carbs.roundToInt()} · F ${fat.roundToInt()}",
                chart = if (protein + carbs + fat <= 0) Chart.Bars(data.eatenDaily.tail(7)) else Chart.Split(
                    listOf(
                        protein.toFloat() to TileColour.Activity,
                        carbs.toFloat() to TileColour.Hrv,
                        fat.toFloat() to TileColour.Heart,
                    ),
                ),
                chip = health.nutritionTrend?.let { chipOf(it, "kcal", decimals = 0) },
            )
        }

        TileKind.Weight -> health.weightTrend?.let { trend ->
            Face(
                kicker = "Weight",
                figure = "%.1f".format(trend.latest),
                unit = "kg",
                sub = "usual %.1f–%.1f".format(trend.usual.low, trend.usual.high),
                chart = Chart.Bars(trend.readings.tail(14)),
                chip = chipOf(trend, "kg", decimals = 1),
            )
        }

        TileKind.Breathing -> health.breathingTrend?.let { trend ->
            Face(
                kicker = "Breathing",
                figure = "%.1f".format(trend.latest),
                unit = "br/min",
                sub = "usual %.1f–%.1f".format(trend.usual.low, trend.usual.high),
                chart = Chart.Bars(trend.readings.tail(14)),
                chip = chipOf(trend, "", decimals = 1),
            )
        }

        TileKind.Skin -> health.skinTrend?.let { trend ->
            Face(
                kicker = "Skin temp",
                figure = signedDecimal(trend.latest),
                unit = "°C",
                sub = "vs your baseline",
                chart = Chart.Bars(trend.readings.tail(14)),
                chip = chipOf(trend, "", decimals = 1),
            )
        }

        TileKind.Oxygen -> health.oxygenTrend?.let { trend ->
            Face(
                kicker = "SpO₂",
                figure = trend.latest.roundToInt().toString(),
                unit = "%",
                sub = "overnight",
                chart = Chart.Bars(trend.readings.tail(14)),
                chip = chipOf(trend, "", decimals = 0),
            )
        }
    }
}

// ------------------------------------------------------------------ pieces

private const val STRIPE_MAX = 14f

/** The kicker, the figure and its two lines, above a square's chart. */
private const val SQUARE_ABOVE_CHART = 78f

/** The kicker and its gap: the rest of a wide card is the chart's row. */
private const val WIDE_ABOVE_CHART = 26f
private val FIGURE_COLUMN = 96.dp

private fun List<Reading>.tail(count: Int): List<Float> =
    takeLast(count).map { it.value.toFloat() }

/**
 * Today's beats, one column an hour, each the highest reading in it.
 *
 * The tab draws a line through every sample; rectangles in a Row cannot
 * make a line, so the widget bins instead. The highest rather than the mean,
 * because the point of the shape is where the day asked something of you.
 */
private fun List<com.srineere.airbridge.health.HeartSample>.byHour(): List<Float> {
    if (isEmpty()) return emptyList()
    val calendar = java.util.Calendar.getInstance()
    val byHour = groupBy {
        calendar.timeInMillis = it.atMillis
        calendar.get(java.util.Calendar.HOUR_OF_DAY)
    }
    // The whole day, always: twenty-four slots, most of them empty at
    // breakfast. Binning only the hours that hold a reading drew three
    // columns the width of a thumb at two in the morning, which says nothing
    // about a day. Three thin columns at the left of twenty-four says it is
    // early, which is the true thing about that chart.
    return (0..23).map { hour -> byHour[hour]?.maxOf { it.bpm }?.toFloat() ?: 0f }
}

/** Last night in proportion, or null for a night written as one block. */
private fun Night.stageChart(): Chart? {
    if (segments.isEmpty()) {
        // No hypnogram: the three totals still say what the night was made
        // of, which is more than one undifferentiated bar would.
        val parts = listOf(
            deep.toMinutes().toFloat() to SleepStage.Deep,
            light.toMinutes().toFloat() to SleepStage.Light,
            rem.toMinutes().toFloat() to SleepStage.Rem,
            awake.toMinutes().toFloat() to SleepStage.Awake,
        ).filter { it.first > 0f }
        return parts.takeIf { it.isNotEmpty() }?.let { Chart.Stages(it) }
    }
    // Sixty offset boxes would compose and would be a grey smear under
    // 150dp, so the night is reduced to its longest stretches.
    return Chart.Stages(
        segments.take(MAX_SEGMENTS).map { it.length.toMinutes().toFloat().coerceAtLeast(1f) to it.stage },
    )
}

private const val MAX_SEGMENTS = 14

private fun sessionNote(count: Int): String = when (count) {
    0 -> "no sessions"
    1 -> "1 session"
    else -> "$count sessions"
}

private fun mealNote(count: Int): String = when (count) {
    0 -> "nothing logged"
    1 -> "1 meal"
    else -> "$count meals"
}

private fun signed(value: Int): String =
    if (value < 0) "−${abs(value)}" else "+$value"

private fun signedDecimal(value: Double): String =
    if (value < 0) "−%.1f".format(abs(value)) else "+%.1f".format(value)

/**
 * The change chip, in the metric's own units.
 *
 * [Trend.change] is the oldest third against the newest, as a difference and
 * not a percentage, which is what the tab's chip shows too. Below the point
 * where it would round away it says Steady rather than an arrow pointing at
 * nothing.
 */
private fun chipOf(trend: Trend, unit: String, decimals: Int = 0): String {
    val change = trend.change
    val shown = if (decimals == 0) abs(change).roundToInt().toString() else "%.${decimals}f".format(abs(change))
    if (shown.toDoubleOrNull()?.takeIf { it > 0.0 } == null) return "Steady"
    val arrow = if (change > 0) "↑" else "↓"
    return if (unit.isEmpty()) "$arrow $shown" else "$arrow $shown $unit"
}

/** The direction on its own, for the width that cannot hold the figure. */
private fun arrowOf(chip: String): String = chip.take(1).takeIf { it == "↑" || it == "↓" } ?: ""

/** Sleep's trend is in minutes, which reads as hours and minutes. */
private fun chipOfMinutes(trend: Trend): String {
    val minutes = trend.change.roundToInt()
    if (abs(minutes) < 5) return "Steady"
    val arrow = if (minutes > 0) "↑" else "↓"
    val size = abs(minutes)
    return if (size < 60) "$arrow ${size}m" else "$arrow ${size / 60}h ${size % 60}m"
}
