package com.srineere.airbridge.widget

import androidx.compose.runtime.Composable
import androidx.glance.layout.height
import androidx.glance.layout.ContentScale
import androidx.glance.LocalContext
import androidx.glance.ImageProvider
import androidx.glance.Image
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.remember
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
internal data class Face(
    val kicker: String,
    val figure: String,
    val sub: String,
    val unit: String? = null,
    val detail: String? = null,
    val chart: Chart? = null,
    val meter: Float? = null,
    val chip: String? = null,
    /** The tab's head shows "\u00b7 30 days" once the card is wide enough. */
    val window: String? = null,
    /** The full name, for the widths that have room for it. */
    val longKicker: String? = null,
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

    /**
     * One dot a reading, over the band half of them land in.
     *
     * A bar says "this much" and a dot says "here, against where you
     * usually are" -- and six of the tiles say the second thing. Glance
     * cannot draw a dot, so this one costs a bitmap.
     */
    class Scatter(val values: List<Double>, val low: Double?, val high: Double?) : Chart

    /** Arcs side by side, each with its figure in the middle. */
    class Dials(val parts: List<DialReading>) : Chart

    /**
     * The same readings across rather than down, divided by hairlines.
     *
     * A separate type from [Rows] only because a thumbnail has to tell the
     * two apart: the Rail widget and the Day widget show the same four
     * readings and are otherwise nothing alike, and two identical pictures
     * in a catalogue are a catalogue you cannot choose from.
     */
    class Rail(val parts: List<Triple<String, String, TileColour>>) : Chart
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
                Head(face, colour, tint, colours, showTrends, wide = false)
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

            // The tall face stacks, as the tile's XL does: the figure across
            // the top and the chart under it with the whole width. Only the
            // wide face puts them side by side.
            WidgetSize.Tall -> {
                Head(face, colour, tint, colours, showTrends, wide = true)
                Gap(8.dp)
                FigureAndUnit(face, 32, colours)
                Gap(3.dp)
                Sub(face.sub, colours.muted, size = 11)
                face.detail?.let {
                    Gap(3.dp)
                    Sub(it, colours.faint, size = 10)
                }
                face.meter?.let {
                    Gap(8.dp)
                    Meter(it, innerWidth(padding), tint, colours.track, height = 5.dp)
                }
                Gap(10.dp)
                DrawChart(
                    face.chart, colour, colours,
                    height = (innerHeight(padding).value - TALL_ABOVE_CHART)
                        .coerceIn(40f, 260f).dp,
                    width = innerWidth(padding),
                )
            }

            WidgetSize.Wide -> {
                Head(face, colour, tint, colours, showTrends, wide = true)
                Gap(6.dp)
                // All of it. A cap here was what left a field of empty card
                // under the chart: a launcher's 4x4 is taller than the 300dp
                // the face was drawn for, and a widget should spend the
                // space it was given.
                val chartHeight = (innerHeight(padding).value - WIDE_ABOVE_CHART)
                    .coerceIn(44f, 400f).dp
                @Suppress("UNUSED_EXPRESSION") size
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

/**
 * The tile's own heading.
 *
 * The full name and the window appear together once the card is wide
 * enough, exactly as `TileHead` does it: of name, window and chip, the
 * window is the one the screen behind the tile repeats in full, so it goes
 * in before the long name does.
 */
@Composable
private fun Head(
    face: Face,
    colour: TileColour,
    tint: androidx.glance.unit.ColorProvider,
    colours: WidgetColours,
    showTrends: Boolean,
    wide: Boolean,
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Kicker(if (wide) face.longKicker ?: face.kicker else face.kicker, tint)
        if (wide) {
            face.window?.let {
                Spacer(GlanceModifier.width(4.dp))
                Sub("· $it", colours.faint, size = 10)
            }
        }
        if (showTrends && face.chip != null) {
            Spacer(GlanceModifier.defaultWeight())
            Chip(face.chip, tint, colours.tileWash(colour))
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
        is Chart.Bars -> BarsImage(chart, colour, colours, height, width)
        is Chart.Stages -> StripeImage(
            chart.parts.map { (share, stage) -> share to colours.sleepStageArgb(stage, colours.darkNow()) },
            height.value.coerceAtMost(STRIPE_MAX).dp,
            width,
        )
        is Chart.Split -> StripeImage(
            chart.parts.map { (share, each) -> share to colours.tileArgb(each, colours.darkNow()) },
            height.value.coerceAtMost(STRIPE_MAX).dp,
            width,
        )
        is Chart.Scatter -> ScatterImage(chart, colour, colours, height, width)

        is Chart.Arcs -> RingImage(chart.parts, colours, height)

        // Only the thumbnail draws this; the widget lays its own
        // dials out, because it has room for their labels.
        is Chart.Dials -> RingImage(
            chart.parts.map { it.fraction to it.colour }, colours, height,
        )

        // The Paper widget lays its own rail out, with room for hairlines
        // the thumbnail has no space for. This is the fallback shape.
        is Chart.Rail -> Row(modifier = GlanceModifier.fillMaxWidth()) {
            chart.parts.forEach { (name, value, each) ->
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Sub(name, colours.muted, size = 9)
                    Gap(3.dp)
                    Sub(value, colours.tile(each), size = 12)
                }
            }
        }

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

/**
 * What a tile says, as the tile itself says it.
 *
 * The head's name and the full title both come from the kind here rather
 * than from each branch, so the rule the tab uses -- short where the card
 * is narrow, full where it is wide -- cannot drift one kind at a time.
 */
internal fun faceFor(kind: TileKind, snapshot: WidgetSnapshot): Face? =
    bodyFor(kind, snapshot)?.copy(kicker = kind.short, longKicker = kind.title)

private fun bodyFor(kind: TileKind, snapshot: WidgetSnapshot): Face? {
    val health = snapshot.health
    val data = health.snapshot
    return when (kind) {
        TileKind.Heart -> health.restingTrend?.let { trend ->
            Face(
                kicker = "",
                window = "30 days",
                figure = trend.latest.roundToInt().toString(),
                unit = "bpm",
                sub = usualBand(trend, 0),
                // The whole month, as the tile plots it.
                chart = scatterOf(trend, 30),
                chip = chipOf(trend, "bpm"),
            )
        }

        TileKind.Zones -> data.heartToday.takeIf { it.isNotEmpty() }?.let { samples ->
            val latest = samples.maxByOrNull { it.atMillis }?.bpm ?: return@let null
            val low = samples.minOf { it.bpm }
            val high = samples.maxOf { it.bpm }
            Face(
                kicker = "",
                window = "today",
                figure = latest.toString(),
                unit = "bpm",
                sub = "$low–$high today",
                chart = Chart.Bars(samples.byHour(), blankZeros = true),
            )
        }

        TileKind.Sleep -> health.lastNight?.let { night ->
            val score = health.sleepScore?.total
            Face(
                kicker = "",
                window = "${health.nights.size} nights",
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
            val percent = if (goal > 0) (health.stepProgress(goal) * 100).roundToInt() else 0
            Face(
                kicker = "",
                window = "7 days",
                figure = "%,d".format(health.stepsToday),
                // The tab's own line: a share of the goal, not the remainder.
                sub = if (goal > 0) "$percent%% of %,d".format(goal) else "today",
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
                kicker = "",
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
            val sessions = data.sessions
            // The tile totals the sessions themselves rather than a daily
            // series, so the two agreed on the week and disagreed on today.
            val minutes = sessions.sumOf {
                java.time.Duration.between(it.start, it.end).toMinutes()
            }
            Face(
                kicker = "",
                window = "7 days",
                figure = minutes.toString(),
                unit = "min",
                sub = sessionNote(sessions.size),
                chart = Chart.Bars(
                    sessions.map {
                        java.time.Duration.between(it.start, it.end).toMinutes().toFloat()
                    }.takeLast(7),
                    highlightLast = false,
                ),
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
            val inRange = health.vitals.count { it.latestIsUsual }
            Face(
                kicker = "",
                // The tile's own sentence: how many of them are where they
                // usually are, which is the only figure Vitals has.
                figure = inRange.toString(),
                unit = "of ${health.vitals.size}",
                sub = "in range",
                chart = Chart.Rows(rows),
            )
        }

        TileKind.Hrv -> health.hrvTrend?.let { trend ->
            Face(
                kicker = "",
                window = "14 nights",
                figure = trend.latest.roundToInt().toString(),
                unit = "ms",
                sub = usualBand(trend, 0),
                chart = scatterOf(trend, 14),
                chip = chipOf(trend, "ms"),
            )
        }

        TileKind.Nutrition -> {
            val eaten = data.eatenToday ?: return null
            val protein = data.proteinToday ?: 0.0
            val carbs = data.carbsToday ?: 0.0
            val fat = data.fatToday ?: 0.0
            Face(
                kicker = "",
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
                kicker = "",
                window = "30 days",
                figure = "%.1f".format(trend.latest),
                unit = "kg",
                sub = usualBand(trend, 1),
                chart = scatterOf(trend, 30),
                chip = chipOf(trend, "kg", decimals = 1),
            )
        }

        TileKind.Breathing -> health.breathingTrend?.let { trend ->
            Face(
                kicker = "",
                window = "30 nights",
                figure = "%.1f".format(trend.latest),
                unit = "br/min",
                sub = usualBand(trend, 1),
                chart = scatterOf(trend, 14),
                chip = chipOf(trend, "", decimals = 1),
            )
        }

        TileKind.Skin -> health.skinTrend?.let { trend ->
            Face(
                kicker = "",
                window = "30 nights",
                figure = signedDecimal(trend.latest),
                unit = "°C",
                // A band either side of zero: a dash between two signed
                // numbers reads as a subtraction, so it is spelled out.
                sub = usualBand(trend, 1, signed = true),
                chart = scatterOf(trend, 14),
                chip = chipOf(trend, "", decimals = 1),
            )
        }

        TileKind.Oxygen -> health.oxygenTrend?.let { trend ->
            Face(
                kicker = "",
                window = "30 nights",
                figure = trend.latest.roundToInt().toString(),
                unit = "%",
                sub = usualBand(trend, 0),
                chart = scatterOf(trend, 14),
                chip = chipOf(trend, "", decimals = 0),
            )
        }
    }
}

/** The tile's own scatter: the newest [count] readings, and the usual band. */
private fun scatterOf(trend: Trend, count: Int): Chart.Scatter = Chart.Scatter(
    values = trend.readings.takeLast(count).map { it.value },
    low = trend.usual.low,
    high = trend.usual.high,
)

/**
 * "usual 54-59", at the decimals that metric is worth.
 *
 * Where a bound is negative the dash is spelled out, because a hyphen
 * between two signed numbers reads as a subtraction: "-0.2--0.4".
 */
private fun usualBand(trend: Trend, decimals: Int, signed: Boolean = false): String {
    val from = bound(trend.usual.low, decimals, signed)
    val to = bound(trend.usual.high, decimals, signed)
    val join = if (trend.usual.low < 0 || trend.usual.high < 0) " to " else "–"
    return "usual $from$join$to"
}

private fun bound(value: Double, decimals: Int, signed: Boolean): String {
    val text = if (decimals == 0) {
        value.roundToInt().toString()
    } else {
        "%.${decimals}f".format(value)
    }
    val typographic = text.replace("-", "−")
    return if (signed && value > 0) "+$typographic" else typographic
}

// ------------------------------------------------------------------ pieces

private const val STRIPE_MAX = 14f

/** The kicker, the figure and its two lines, above a square's chart. */
private const val SQUARE_ABOVE_CHART = 78f

/** The kicker and its gap: the rest of a wide card is the chart's row. */
private const val WIDE_ABOVE_CHART = 26f

/** The head, the figure and its two lines, above a tall card's chart. */
private const val TALL_ABOVE_CHART = 118f
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

/**
 * The tile's scatter, drawn to a bitmap and shown as an Image.
 *
 * Sized from the cell rather than a constant, and keyed on everything that
 * changes a pixel so scrolling past a widget does not redraw a Canvas that
 * would come out identical.
 */
@Composable
private fun ScatterImage(
    chart: Chart.Scatter,
    colour: TileColour,
    colours: WidgetColours,
    height: Dp,
    width: Dp,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val argb = colours.tileArgb(colour, colours.isDark(context))
    val widthPx = (width.value * density).toInt()
    val heightPx = (height.value * density).toInt()
    val bitmap = remember(widthPx, heightPx, chart.values, chart.low, chart.high, argb) {
        ScatterBitmap.draw(widthPx, heightPx, chart.values, chart.low, chart.high, argb, density)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(height),
    )
}

/** Which half of the palette a bitmap should be painted from. */
@Composable
internal fun WidgetColours.darkNow(): Boolean = isDark(LocalContext.current)

/**
 * A bar chart as one Image.
 *
 * Not as a Row of Boxes: Glance containers hold ten children and quietly
 * drop the rest, so a week came out as five days and a day as five hours.
 */
@Composable
private fun BarsImage(
    chart: Chart.Bars,
    colour: TileColour,
    colours: WidgetColours,
    height: Dp,
    width: Dp,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val dark = colours.isDark(context)
    val ink = colours.tileArgb(colour, dark)
    val dim = colours.tileDimArgb(colour, dark)
    val widthPx = (width.value * density).toInt()
    val heightPx = (height.value * density).toInt()
    val bitmap = remember(widthPx, heightPx, chart.values, chart.ceiling, ink, dim) {
        ChartBitmap.bars(
            widthPx, heightPx, chart.values, chart.ceiling, ink, dim,
            chart.highlightLast, chart.blankZeros, density,
        )
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(height),
    )
}

/** Proportional segments as one Image, for the same reason. */
@Composable
private fun StripeImage(parts: List<Pair<Float, Int>>, height: Dp, width: Dp) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val widthPx = (width.value * density).toInt()
    val heightPx = (height.value * density).toInt()
    val bitmap = remember(widthPx, heightPx, parts) {
        ChartBitmap.stripe(widthPx, heightPx, parts, density)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(height),
    )
}
