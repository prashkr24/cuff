package com.srineere.airbridge.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.srineere.airbridge.health.Goals
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.ui.HealthData
import com.srineere.airbridge.ui.asHoursMinutes
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the Rings family can offer.
 *
 * Apple's grammar rather than Apple's design: three arcs that close, a list
 * that reads at arm's length, and a row of arrows against your own history.
 * Only the first needs a bitmap; the other two are the same Text and Row the
 * tiles already use, which is why all three arrive together.
 */
enum class RingFace(
    val title: String,
    val summary: String,
    val sizes: Set<WidgetSize>,
) {
    Arcs(
        "Three rings",
        "Steps, sleep and burn, as arcs that close",
        setOf(WidgetSize.Square, WidgetSize.Wide, WidgetSize.Tall),
    ),
    Summary(
        "Summary",
        "Four readings, one line each",
        setOf(WidgetSize.Wide, WidgetSize.Tall),
    ),
    Trends(
        "Trends",
        "Each reading against your own fortnight",
        setOf(WidgetSize.Wide, WidgetSize.Tall),
    ),
}

/** One arc: how far round it has gone, and what it is. */
data class Ring(
    val label: String,
    val fraction: Float,
    val colour: TileColour,
    val figure: String,
    val of: String,
)

/**
 * The three rings, and why they are these three.
 *
 * Apple closes Move, Exercise and Stand, all of them daily and all of them
 * against a target the owner set. Ours has to be built from what is actually
 * stored, which rules out the obvious copy: `Goals.activeMinutes` is a
 * *weekly* figure -- the WHO's lower bound -- and a weekly ring beside two
 * daily ones is a chart that lies about what a full circle means. Dividing
 * it by seven would invent a target nobody chose.
 *
 * So: steps and sleep, which have daily goals someone set, and burn, which
 * has no goal at all and is drawn against the fortnight behind it. A ring
 * with no target is a ring against your usual day, and the widget says so
 * rather than implying somebody recommended it.
 *
 * A ring with nothing behind it is absent, never a closed circle and never a
 * zero: both of those are claims.
 */
internal fun ringsOf(health: HealthData, goals: Goals): List<Ring> {
    val rings = mutableListOf<Ring>()

    if (goals.steps > 0) {
        val steps = health.stepsToday
        rings += Ring(
            label = "Steps",
            fraction = health.stepProgress(goals.steps),
            colour = TileColour.Steps,
            figure = "%,d".format(steps),
            of = "of %,d".format(goals.steps),
        )
    }

    val asleep = health.lastNight?.asleep
    if (asleep != null && goals.sleepMinutes > 0) {
        val minutes = asleep.toMinutes()
        rings += Ring(
            label = "Sleep",
            fraction = (minutes.toFloat() / goals.sleepMinutes).coerceIn(0f, 1f),
            colour = TileColour.Sleep,
            figure = asleep.asHoursMinutes(),
            of = "of ${goals.sleepMinutes / 60}h",
        )
    }

    val burned = health.snapshot.burnedToday
    val usual = usualBurn(health)
    if (burned != null && usual != null && usual > 0) {
        rings += Ring(
            label = "Burn",
            fraction = (burned / usual).toFloat().coerceIn(0f, 1f),
            colour = TileColour.Energy,
            figure = "%,d".format(burned.roundToInt()),
            of = "usual %,d".format(usual.roundToInt()),
        )
    }

    return rings
}

/**
 * A usual day's burn, from the days before this one.
 *
 * Today is excluded deliberately: at nine in the morning it is a fifth of a
 * day, and letting it into its own average drags the thing it is measured
 * against down towards itself.
 */
internal fun usualBurn(health: HealthData): Double? {
    val daily = health.snapshot.burnedDaily
    val before = daily.dropLast(1).takeLast(14)
    if (before.size < 3) return null
    return before.map { it.value }.average().takeIf { it > 0.0 }
}

/** The four readings the Summary face lists, in the order it lists them. */
internal fun summaryRows(health: HealthData): List<Triple<String, String, TileColour>> =
    listOfNotNull(
        health.snapshot.stepsToday?.let { Triple("Steps", "%,d".format(it), TileColour.Steps) },
        health.lastNight?.let { Triple("Sleep", it.asleep.asHoursMinutes(), TileColour.Sleep) },
        health.restingTrend?.let {
            Triple("Resting", "${it.latest.roundToInt()} bpm", TileColour.Heart)
        },
        health.snapshot.burnedToday?.let {
            Triple("Burn", "%,d kcal".format(it.roundToInt()), TileColour.Energy)
        },
    )

/**
 * The same four, as a direction and a size rather than a value.
 *
 * Each against its own window, in its own units, the way the tab's chips
 * read. A reading whose change rounds away says Steady, because an arrow
 * pointing at nothing is worse than no arrow.
 */
internal fun trendRows(health: HealthData): List<Triple<String, String, TileColour>> =
    listOfNotNull(
        health.stepsTrend?.let { Triple("Steps", arrowed(it.change, 0, ""), TileColour.Steps) },
        health.sleepTrend?.let { Triple("Sleep", arrowedMinutes(it.change), TileColour.Sleep) },
        health.restingTrend?.let { Triple("Resting", arrowed(it.change, 0, "bpm"), TileColour.Heart) },
        health.hrvTrend?.let { Triple("HRV", arrowed(it.change, 0, "ms"), TileColour.Hrv) },
    )

private fun arrowed(change: Double, decimals: Int, unit: String): String {
    val shown = if (decimals == 0) {
        abs(change).roundToInt().toString()
    } else {
        "%.${decimals}f".format(abs(change))
    }
    if ((shown.toDoubleOrNull() ?: 0.0) <= 0.0) return "Steady"
    val arrow = if (change > 0) "↑" else "↓"
    return if (unit.isEmpty()) "$arrow $shown" else "$arrow $shown $unit"
}

private fun arrowedMinutes(change: Double): String {
    val minutes = change.roundToInt()
    if (abs(minutes) < 5) return "Steady"
    val arrow = if (minutes > 0) "↑" else "↓"
    val size = abs(minutes)
    return if (size < 60) "$arrow ${size}m" else "$arrow ${size / 60}h ${size % 60}m"
}

// ------------------------------------------------------------- the drawing

/**
 * The Rings family, drawn.
 *
 * The arcs are a bitmap and everything else is ordinary Glance. That split
 * is the whole cost of this family: three rings are one Canvas per
 * composition, and the two list faces cost nothing at all -- which is why
 * all three shipped together rather than the arcs alone.
 */
@Composable
internal fun RingsWidget(
    face: RingFace,
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    if (!snapshot.granted) {
        Empty(colours, face.title, "Open the app to connect Health Connect", onClick)
        return
    }
    when (face) {
        RingFace.Arcs -> ArcsFace(size, snapshot, colours, onClick)
        RingFace.Summary -> ListFace(
            kicker = "Summary",
            rows = summaryRows(snapshot.health),
            colours = colours,
            onClick = onClick,
            empty = "Nothing has been read yet",
        )
        RingFace.Trends -> ListFace(
            kicker = "Trends · your fortnight",
            rows = trendRows(snapshot.health),
            colours = colours,
            onClick = onClick,
            empty = "A fortnight of nights gives a trend",
        )
    }
}

@Composable
private fun ArcsFace(
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    val rings = ringsOf(snapshot.health, snapshot.goals)
    if (rings.isEmpty()) {
        Empty(colours, "Today", "No goal has a reading behind it yet", onClick)
        return
    }
    val padding = 11.dp
    val inner = innerHeight(padding)
    // Square spends everything on the arcs; the wider faces give them the
    // card's height and keep the rest for the figures, which is the only
    // place the three readings can actually be named.
    val arcSide = if (size == WidgetSize.Square) {
        minOf(innerWidth(padding).value, inner.value).dp
    } else {
        inner.value.coerceAtMost(ARC_MAX.value).dp
    }

    WidgetCard(colours, onClick, padding) {
        if (size == WidgetSize.Square) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Arcs(rings, colours, arcSide)
            }
        } else {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Arcs(rings, colours, arcSide)
                Spacer(GlanceModifier.width(14.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    rings.forEachIndexed { index, ring ->
                        if (index > 0) Gap(if (size == WidgetSize.Tall) 12.dp else 7.dp)
                        Kicker(ring.label, colours.tile(ring.colour), size = 9)
                        Gap(2.dp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Figure(ring.figure, if (size == WidgetSize.Tall) 20 else 16, colours.onBackground)
                            Spacer(GlanceModifier.width(5.dp))
                            Sub(ring.of, colours.faint, size = 10)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Arcs(rings: List<Ring>, colours: WidgetColours, side: Dp) =
    RingImage(rings.map { it.fraction to it.colour }, colours, side)

/** The bitmap, and the only one in this app so far. */
@Composable
internal fun RingImage(
    parts: List<Pair<Float, TileColour>>,
    colours: WidgetColours,
    side: Dp,
    stroke: Float = RingBitmap.STROKE,
    /**
     * Forces the light half, for a family that paints its own pale card.
     * Null resolves the usual way, from the theme or the launcher.
     */
    light: Boolean? = null,
) {
    if (parts.isEmpty()) return
    val context = LocalContext.current
    val dark = if (light == true) false else colours.isDark(context)
    val density = context.resources.displayMetrics.density
    val sidePx = (side.value * density).toInt()
    val arcs = parts.map { (fraction, colour) -> fraction to colours.tileArgb(colour, dark) }
    val track = colours.trackArgb(dark)
    // Keyed on everything that changes a pixel, so scrolling past a widget
    // does not redraw a Canvas that would come out identical.
    val bitmap = remember(sidePx, arcs, track, stroke) {
        RingBitmap.draw(sidePx, arcs, track, stroke = stroke)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = GlanceModifier.size(side),
    )
}

/**
 * Four readings, one line each.
 *
 * No chart and no arc: the quietest thing this app can put on a home screen
 * and the easiest to read at arm's length.
 */
@Composable
private fun ListFace(
    kicker: String,
    rows: List<Triple<String, String, TileColour>>,
    colours: WidgetColours,
    onClick: Action?,
    empty: String,
) {
    if (rows.isEmpty()) {
        Empty(colours, kicker, empty, onClick)
        return
    }
    WidgetCard(colours, onClick) {
        Kicker(kicker, colours.muted)
        Gap(8.dp)
        rows.forEachIndexed { index, (name, value, colour) ->
            if (index > 0) Gap(7.dp)
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(colours.tile(colour), size = 7.dp)
                Spacer(GlanceModifier.width(8.dp))
                Sub(name, colours.muted, size = 11)
                Spacer(GlanceModifier.defaultWeight())
                Figure(value, 14, colours.onBackground)
            }
        }
    }
}

/** An arc larger than this stops looking like a widget and starts looking like a clock. */
private val ARC_MAX = 132.dp
