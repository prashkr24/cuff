package com.srineere.airbridge.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * One vocabulary of movement, so the app moves in one way.
 *
 * Before this there were four springs in four files, each written from
 * memory at the moment it was needed, and they did not match: a press
 * settled at one rate, a card at another, a chip at a third. Nobody could
 * have named the difference, which is exactly the problem -- motion that
 * varies without meaning reads as the app being slightly unreliable rather
 * than as anything expressive.
 *
 * Three springs, and the choice between them is about what is moving:
 *
 *  - [press] is for something under a finger. Quick, and it overshoots a
 *    little on the way back, which is what makes a tap feel answered.
 *  - [spatial] is for something changing position or size. Softer, and
 *    barely bouncy: a row that springs past its resting place and comes
 *    back is a row you cannot read for a moment.
 *  - [enter] is for something arriving. Bouncy enough to notice once.
 *
 * On cost: a spring animation is a few floats advanced per frame on the
 * animation clock, and the modifiers here are all draw-layer only -- scale
 * and alpha, never size -- so they never trigger a measure or layout pass
 * on the tree underneath them. The expensive kind of animation is the one
 * that changes a layout bound every frame, and none of these do.
 */
object Motion {
    /** Under a finger: quick, with a little overshoot coming back. */
    val press: SpringSpec<Float> = spring(
        dampingRatio = 0.45f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Moving or resizing: soft, and almost without overshoot. */
    fun <T> spatial(): SpringSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Arriving: bouncy, and seen once. */
    val enter: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )

    /** How far a pressed card shrinks. */
    const val PRESS_SCALE = 0.965f

    /** And a row, which is wider, so the same scale would travel further. */
    const val PRESS_SCALE_ROW = 0.985f
}

/**
 * Shrinks slightly while held, and springs back when let go.
 *
 * Drawn rather than laid out: [graphicsLayer] scales the finished picture,
 * so a pressed row costs one float per frame and nothing around it moves.
 * Using `Modifier.size` or padding for the same effect would re-measure
 * the row, and in a list of ninety apps that is ninety measures a frame.
 *
 * Pass the same [interaction] to whatever makes the row clickable, or the
 * press will never be seen.
 */
@Composable
fun Modifier.pressBounce(
    interaction: InteractionSource,
    scale: Float = Motion.PRESS_SCALE,
): Modifier {
    // Pressed only. Collecting drags as well would double the number of
    // collectors in a list of ninety apps to buy a state nothing in this
    // app enters through an interaction source.
    val pressed by interaction.collectIsPressedAsState()
    val factor by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = Motion.press,
        label = "press",
    )
    // The common case, and the one worth not paying for: at rest this adds
    // no layer at all.
    if (factor == 1f) return this
    return this.graphicsLayer {
        scaleX = factor
        scaleY = factor
    }
}

/** The same, for a caller that has no interaction source of its own to share. */
@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/**
 * A figure that changes by moving, so you can see that it changed.
 *
 * The direction carries the meaning: a number that has gone up rises into
 * place and the old one leaves upward. That is the whole reason this is
 * not a cross-fade -- a fade says "this is different now", and a direction
 * says which way.
 *
 * It only ever runs when the text actually changes, which on this tab is
 * once a sync rather than once a frame. [key] exists for the figures whose
 * text can stay the same while the reading behind it moves.
 */
@Composable
fun AnimatedFigure(
    value: String,
    modifier: Modifier = Modifier,
    rising: Boolean? = null,
    key: Any = value,
    content: @Composable (String) -> Unit,
) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            val now: Double = targetState.figureOrNull() ?: 0.0
            val before: Double = initialState.figureOrNull() ?: 0.0
            val up = rising ?: (now >= before)
            val sign = if (up) 1 else -1
            (
                slideInVertically(Motion.spatial()) { height -> sign * height / 2 } +
                    fadeIn(Motion.spatial())
                ) togetherWith (
                slideOutVertically(Motion.spatial()) { height -> -sign * height / 2 } +
                    fadeOut(Motion.spatial())
                )
        },
        label = "figure$key",
    ) { shown -> content(shown) }
}

/**
 * The number inside a figure, where there is one.
 *
 * Figures here are written for reading rather than parsing -- "8,240",
 * "7h 32m", "62" -- so this takes the digits and is not fussy about the
 * rest. It decides which way a figure slides and nothing else, so being
 * wrong costs a direction, not a reading.
 */
private fun String.figureOrNull(): Double? =
    filter { it.isDigit() || it == '.' }.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

/**
 * Springs in once, the first time it is composed.
 *
 * The stagger is by position, so a grid fills in reading order rather than
 * all at once. [index] beyond [STAGGER_MAX] gets no delay: a tab with
 * twenty rows should not take two seconds to finish arriving, and nobody
 * is watching the twentieth one anyway.
 *
 * Held in a `remember` keyed on nothing, so scrolling away and back does
 * not replay it. An entrance that repeats stops being an entrance.
 */
@Composable
fun Modifier.springIn(index: Int = 0): Modifier {
    var arrived by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = Motion.enter,
        label = "springIn",
    )
    LaunchedEffect(Unit) {
        if (index in 1..STAGGER_MAX) delay(index * STAGGER_MS)
        arrived = true
    }
    if (progress == 1f) return this
    return this.graphicsLayer {
        // From 92%, not from nothing. A card that grows from a point reads
        // as a card being built; one that is already nearly there reads as
        // a card settling, which is the thing being described.
        val eased = 0.92f + 0.08f * progress
        scaleX = eased
        scaleY = eased
        alpha = progress.coerceIn(0f, 1f)
    }
}

/** How many things stagger before the rest simply appear together. */
private const val STAGGER_MAX = 8

/** And how far apart, in milliseconds. */
private const val STAGGER_MS = 28L

/**
 * How a chart arrives: lifted slightly, and faded.
 *
 * Deliberately not a bar growing out of its baseline, which is the usual
 * answer and was the first one tried here. Every chart in this app draws
 * its axis labels into the same canvas as its data, so scaling the canvas
 * vertically squashes the text for the length of the animation -- and on
 * the springs this app uses, a scale that settles also overshoots, which
 * means a bar drawn past the reading it represents. Briefly, and it
 * settles, but a chart that shows a number the data does not support is
 * not a chart with a nice entrance.
 *
 * Alpha and offset distort nothing. The figure is right in every frame.
 */
@Composable
fun Modifier.chartEntrance(): Modifier {
    var shown by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = Motion.spatial(),
        label = "chartEntrance",
    )
    LaunchedEffect(Unit) { shown = true }
    if (progress == 1f) return this
    return this.graphicsLayer {
        alpha = progress.coerceIn(0f, 1f)
        translationY = (1f - progress) * CHART_LIFT_PX
    }
}

/** How far a chart rises into place, in pixels at mdpi-ish scale. */
private const val CHART_LIFT_PX = 18f

/**
 * A value that animates to wherever it is told, from zero the first time.
 *
 * For a ring or a bar whose fill is a fraction: the sweep runs up to the
 * reading rather than appearing at it. [Motion.spatial] is not quite
 * critically damped, so clamp before drawing -- a ring that overshoots
 * past full and comes back has drawn a goal that was not met.
 */
@Composable
fun animatedFraction(target: Float): Float {
    val value by animateFloatAsState(
        targetValue = target,
        animationSpec = Motion.spatial(),
        label = "fraction",
    )
    return value.coerceIn(0f, target.coerceAtLeast(0f))
}

/**
 * Clickable, and answers the press by moving.
 *
 * The three lines this replaces -- hold a source, scale on it, hand it to
 * `clickable` with the ripple off -- were right, and were going to be
 * copied into forty places, which is how three right lines become one
 * wrong one. Written once instead.
 *
 * The ripple is off deliberately and not as an oversight: this app answers
 * a press with movement everywhere else, and a card that both ripples and
 * shrinks is two answers to one tap.
 */
@Composable
fun Modifier.pressFeedback(
    scale: Float = Motion.PRESS_SCALE,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .pressBounce(interaction, scale)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}
