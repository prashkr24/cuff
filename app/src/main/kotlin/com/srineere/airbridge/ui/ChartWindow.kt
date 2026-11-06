package com.srineere.airbridge.ui

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.ui.theme.extras
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Which stretch of days a screen is looking at.
 *
 * Two numbers rather than one: [length] is how much of a run you see at a
 * time, [offset] is how far back that run sits. A chart that only ever ends
 * today can answer "how am I doing" and nothing else — "what did last week
 * look like" needs the window to move, and moving it is a different gesture
 * from resizing it.
 */
@Stable
internal class ChartWindow(initialLength: Int) {
    var length by mutableIntStateOf(initialLength)
        private set

    /** Days between the end of this window and today. Zero is the present. */
    var offset by mutableIntStateOf(0)
        private set

    /** How far back the reader has to go to cover this window. */
    val daysNeeded: Int get() = offset + length

    val isNow: Boolean get() = offset == 0

    /** The newest day this window shows, as an epoch day. */
    val until: Long get() = LocalDate.now().toEpochDay() - offset

    /** The oldest day this window shows. */
    val from: Long get() = until - length + 1

    fun resize(days: Int) {
        // Keep the end of the window where it is when the length changes,
        // so widening does not silently jump you back to today.
        length = days
    }

    fun back() {
        offset += length
    }

    /** Forward, never past today. */
    fun forward() {
        offset = (offset - length).coerceAtLeast(0)
    }

    fun select(day: Long) {
        offset = (LocalDate.now().toEpochDay() - day).toInt().coerceAtLeast(0)
    }

    /** The readings inside this window, oldest first. */
    fun <T> slice(rows: List<T>, dayOf: (T) -> Long): List<T> =
        rows.filter { dayOf(it) in from..until }.sortedBy(dayOf)
}

@Composable
internal fun rememberChartWindow(length: Int): ChartWindow =
    remember { ChartWindow(length) }

/**
 * The window's controls: how long, how far back, and which days that is.
 *
 * The arrows step by a whole window, because stepping by a day turns
 * "last week" into seven taps. Forward is disabled at today rather than
 * hidden, so the control does not change shape as you use it.
 */
@Composable
internal fun WindowBar(
    window: ChartWindow,
    options: List<Int>,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
    label: (Int) -> String = { "$it days" },
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        options.forEach { option ->
            val on = option == window.length
            val interaction = rememberPressSource()
            val fill by animateColorAsState(
                if (on) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                Motion.spatial(),
                label = "windowFill",
            )
            val ink by animateColorAsState(
                if (on) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                Motion.spatial(),
                label = "windowInk",
            )
            Surface(
                color = fill,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    .pressBounce(interaction)
                    .clickable(interactionSource = interaction, indication = null) {
                        window.resize(option)
                        onChange()
                    },
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                )
            }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    ) {
        IconButton(
            onClick = {
                window.back()
                onChange()
            },
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "Earlier",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            window.rangeLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                window.forward()
                onChange()
            },
            enabled = !window.isNow,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Later",
                tint = if (window.isNow) MaterialTheme.extras.disabled
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Last 7 days", or "6–12 Oct" once you have stepped back off today. */
@Composable
internal fun ChartWindow.rangeLabel(): String {
    if (isNow) return "Last $length days"
    val locale = Locale.getDefault()
    val pattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "dMMM") }
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern, locale) }
    val start = LocalDate.ofEpochDay(from)
    val end = LocalDate.ofEpochDay(until)
    return "${formatter.format(start)} – ${formatter.format(end)}"
}

/**
 * Lets a finger pick a point out of a chart.
 *
 * [count] evenly spaced points sit inside the plot, which starts [gutter]
 * from the left edge when the chart has a scale. Dragging keeps the
 * selection following the finger; lifting leaves it where it was, because
 * a value that vanishes the moment you can read it is not a readout.
 */
internal fun Modifier.scrubbable(
    count: Int,
    gutter: Dp,
    onSelect: (Int?) -> Unit,
): Modifier = if (count <= 0) this else composed {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gutterPx = with(density) { gutter.toPx() }
    pointerInput(count, gutterPx) {
        fun pick(x: Float) {
            val usable = (size.width - gutterPx).coerceAtLeast(1f)
            val at = ((x - gutterPx) / usable * count).toInt().coerceIn(0, count - 1)
            onSelect(at)
        }
        detectTapGestures(onTap = { pick(it.x) })
    }.pointerInput(count, gutterPx) {
        detectHorizontalDragGestures(
            onDragStart = { down ->
                val usable = (size.width - gutterPx).coerceAtLeast(1f)
                onSelect(((down.x - gutterPx) / usable * count).toInt().coerceIn(0, count - 1))
            },
        ) { change, _ ->
            val usable = (size.width - gutterPx).coerceAtLeast(1f)
            onSelect(
                ((change.position.x - gutterPx) / usable * count).toInt().coerceIn(0, count - 1),
            )
        }
    }
}


/**
 * The same gesture, reported as a fraction across the plot.
 *
 * A chart whose points sit at their own times cannot be picked by index:
 * the gap between two beats is not the gap between two days. These charts
 * take the fraction and find the nearest reading themselves.
 */
internal fun Modifier.scrubbableAcross(
    gutter: Dp,
    onSelect: (Float?) -> Unit,
): Modifier = composed {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gutterPx = with(density) { gutter.toPx() }
    pointerInput(gutterPx) {
        fun pick(x: Float) {
            val usable = (size.width - gutterPx).coerceAtLeast(1f)
            onSelect(((x - gutterPx) / usable).coerceIn(0f, 1f))
        }
        detectTapGestures(onTap = { pick(it.x) })
    }.pointerInput(gutterPx) {
        val usable = { (size.width - gutterPx).coerceAtLeast(1f) }
        detectHorizontalDragGestures(
            onDragStart = { onSelect(((it.x - gutterPx) / usable()).coerceIn(0f, 1f)) },
        ) { change, _ ->
            onSelect(((change.position.x - gutterPx) / usable()).coerceIn(0f, 1f))
        }
    }
}

/**
 * What the finger is currently on, under the chart it belongs to.
 *
 * Under rather than over: a tooltip at the touch point is a tooltip under a
 * thumb. The row keeps its height when nothing is picked so the card does
 * not jump as you scrub, and it says how to use the chart until you have.
 */
@Composable
internal fun ChartReadout(
    day: Long?,
    value: String?,
    modifier: Modifier = Modifier,
    detail: String? = null,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.fillMaxWidth()
        // Reserved whether or not anything is picked, so the card does not
        // jump as a finger moves across the chart.
        .height(26.dp)
        .padding(top = 8.dp),
) {
    // Nothing until something is picked. The instruction was on every card
    // on the tab at once, which is a lot of screen spent teaching a gesture
    // people find by touching the chart.
    if (day == null || value == null) return@Row
    Text(
        dayLabel(day),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
    Text(
        "  $value",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
    if (detail != null) {
        Text(
            "  ·  $detail",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The gutter a chart with a scale reserves, for mapping a touch to a point. */
internal val CHART_GUTTER = 28.dp
