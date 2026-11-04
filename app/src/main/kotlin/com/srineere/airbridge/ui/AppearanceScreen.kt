package com.srineere.airbridge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.srineere.airbridge.ui.theme.Accent
import com.srineere.airbridge.ui.theme.Edges
import com.srineere.airbridge.ui.theme.Ground
import com.srineere.airbridge.ui.theme.ThemeMode
import com.srineere.airbridge.ui.theme.ThemePreset
import com.srineere.airbridge.ui.theme.accentSwatches
import com.srineere.airbridge.ui.theme.clampToGround
import com.srineere.airbridge.ui.theme.contrast
import com.srineere.airbridge.ui.theme.extras
import com.srineere.airbridge.ui.theme.isAvailable
import com.srineere.airbridge.ui.theme.passes
import kotlin.math.roundToInt

/**
 * How the app looks.
 *
 * A theme here is a ground and an accent chosen together, in both modes, and
 * the two controls that make it up stay live underneath. The preset is not a
 * third state above them — that would raise a "which one wins" question with
 * no honest answer. Picking one simply writes the fields; changing a field
 * afterwards means no preset matches, which the screen calls Custom.
 */
@Composable
internal fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appearance by AppSettings.appearance.collectAsState()
    val dark = when (appearance.mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    var picking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Appearance", onBack = onBack)

        SectionHeader("Light or dark")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                Segment(
                    label = mode.name,
                    selected = appearance.mode == mode,
                    modifier = Modifier.weight(1f),
                ) { AppSettings.setMode(context, mode) }
            }
        }

        // The presets and the pieces they are made of, kept plainly apart.
        // Run together, the list read as five more controls rather than as
        // five answers that save you from the controls underneath.
        SectionHeader("Themes")
        ThemePreset.entries.forEach { preset ->
            PresetRow(preset, appearance.preset == preset, dark) {
                AppSettings.setPreset(context, preset)
            }
        }

        SectionHeader(
            text = "Make your own",
            action = if (appearance.preset == null) "Custom" else null,
            onAction = if (appearance.preset == null) ({}) else null,
        )

        Label("Ground")
        val grounds = if (dark) Ground.darks else Ground.lights
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            grounds.forEach { ground ->
                GroundChip(ground, appearance.ground(dark) == ground, Modifier.weight(1f)) {
                    AppSettings.setGround(context, ground)
                }
            }
        }
        Note(appearance.ground(dark).note)

        Label("Accent")
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            accentSwatches.forEach { accent ->
                Dot(
                    fill = accent.colour(dark),
                    selected = appearance.custom == null && appearance.accent == accent,
                    label = accent.label,
                ) { AppSettings.setAccent(context, accent) }
            }
            Dot(
                fill = appearance.custom?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
                selected = appearance.custom != null,
                label = "Custom colour",
                wheel = appearance.custom == null,
            ) { picking = true }
        }
        Note("Hues the charts do not use. The last one is yours.")
        if (Accent.Dynamic.isAvailable) {
            SettingsRow(
                title = "Match my wallpaper",
                subtitle = "Its hue only. Your ground stays yours.",
                onClick = { AppSettings.setAccent(context, Accent.Dynamic) },
                trailing = {
                    if (appearance.custom == null && appearance.accent == Accent.Dynamic) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }

        Label("Edges")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Edges.entries.forEach { edge ->
                Segment(edge.label, appearance.edges == edge, Modifier.weight(1f)) {
                    AppSettings.setEdges(context, edge)
                }
            }
        }
        Note(
            if (!appearance.ground(dark).liftsTiles) {
                "${appearance.edges.note} Never softer than Defined on this ground."
            } else {
                appearance.edges.note
            },
        )

        Preview(dark)

        Box(Modifier.padding(bottom = 28.dp))
    }

    if (picking) {
        ColourPicker(
            ground = appearance.ground(dark).base,
            start = appearance.custom?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
            onDismiss = { picking = false },
            onPick = {
                AppSettings.setCustom(context, it)
                picking = false
            },
        )
    }
}

/** A heading inside a section, for one control. */
@Composable
private fun Label(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
)

/** One short line under a control. Never two. */
@Composable
private fun Note(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.extras.disabled,
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
)

@Composable
private fun PresetRow(preset: ThemePreset, selected: Boolean, dark: Boolean, onPick: () -> Unit) {
    val ground = if (dark) preset.darkGround else preset.lightGround
    SettingsRow(
        title = preset.label,
        subtitle = preset.note,
        leading = {
            // The theme as a two-colour chip: its ground, with its accent on
            // it. A list of names tells you nothing about how any of them look.
            Box(
                Modifier.size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ground.base)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(clampToGround(preset.accent.colour(dark), ground.base)),
                )
            }
        },
        onClick = onPick,
        trailing = {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun GroundChip(ground: Ground, selected: Boolean, modifier: Modifier, onPick: () -> Unit) =
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            Modifier.fillMaxWidth().height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ground.base)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            // A sample of the card that will sit on it, so the chip shows the
            // lift as well as the ground -- which is the whole difference on
            // true black, where there is none.
            Box(
                Modifier.size(width = 26.dp, height = 16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (ground.liftsTiles) {
                            androidx.compose.ui.graphics.lerp(ground.base, ground.ink, 0.07f)
                        } else {
                            ground.base
                        },
                    )
                    .border(1.dp, androidx.compose.ui.graphics.lerp(ground.base, ground.ink, 0.26f), RoundedCornerShape(4.dp)),
            )
        }
        Text(
            ground.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

@Composable
private fun Dot(
    fill: Color,
    selected: Boolean,
    label: String,
    wheel: Boolean = false,
    onClick: () -> Unit,
) = Box(
    Modifier.size(34.dp)
        .border(
            2.dp,
            if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            CircleShape,
        )
        .padding(4.dp)
        .clip(CircleShape)
        .then(
            if (wheel) {
                Modifier.background(
                    Brush.sweepGradient(
                        listOf(
                            Color(0xFFE05B5B), Color(0xFFE0B85B), Color(0xFF7FD06A),
                            Color(0xFF5BC7E0), Color(0xFF7B6FE0), Color(0xFFE05BC0),
                            Color(0xFFE05B5B),
                        ),
                    ),
                )
            } else {
                Modifier.background(fill)
            },
        )
        .clickable(onClick = onClick)
        .semanticsLabel(label),
)

/**
 * Picking a colour that the app can actually draw with.
 *
 * The accent draws 1px chart strokes and 11px axis labels, so one that cannot
 * clear 4.5:1 on the ground breaks a promise the rest of the app keeps. The
 * unusable part of the field is dimmed and crossed by a line, and a pick
 * inside it keeps its hue and is lifted to the nearest value that passes —
 * then the sheet says so, with the colour it actually landed on.
 *
 * Rejecting the pick outright would be rude, and a "use it anyway" escape
 * would be dishonest.
 */
@Composable
private fun ColourPicker(ground: Color, start: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    var hue by remember { mutableFloatStateOf(com.srineere.airbridge.ui.theme.hueOf(start)) }
    var level by remember { mutableFloatStateOf(0.6f) }

    val raw = hsl(hue, 0.62f, level)
    val safe = clampToGround(raw, ground)
    val ok = passes(raw, ground)

    // A dialog, not a card in the page. Inline it floated over the content
    // it was meant to interrupt, with the screen still scrolling behind it.
    Dialog(onDismissRequest = onDismiss) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Custom accent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            // Hue first: it is the part a person actually has an opinion
            // about. Every hue is available, because every hue has a
            // lightness that passes.
            Canvas(
                Modifier.fillMaxWidth().height(40.dp).padding(top = 14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { hue = (it.x / size.width * 360f).coerceIn(0f, 360f) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            hue = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                        }
                    },
            ) {
                val steps = 60
                repeat(steps) { i ->
                    drawRect(
                        color = hsl(i * 360f / steps, 0.62f, 0.6f),
                        topLeft = Offset(size.width * i / steps, 0f),
                        size = androidx.compose.ui.geometry.Size(size.width / steps + 1f, size.height),
                    )
                }
                val x = size.width * (hue / 360f)
                drawCircle(Color.White, radius = 9f, center = Offset(x.coerceIn(10f, size.width - 10f), size.height / 2))
            }

            // Lightness, with the part that cannot pass dimmed out.
            Canvas(
                Modifier.fillMaxWidth().height(40.dp).padding(top = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { level = (it.x / size.width).coerceIn(0.05f, 0.95f) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            level = (change.position.x / size.width).coerceIn(0.05f, 0.95f)
                        }
                    },
            ) {
                val steps = 60
                repeat(steps) { i ->
                    val at = (i + 0.5f) / steps
                    val colour = hsl(hue, 0.62f, at)
                    drawRect(
                        color = if (passes(colour, ground)) colour else colour.copy(alpha = 0.22f),
                        topLeft = Offset(size.width * i / steps, 0f),
                        size = androidx.compose.ui.geometry.Size(size.width / steps + 1f, size.height),
                    )
                }
                val x = size.width * level
                drawCircle(Color.White, radius = 9f, center = Offset(x.coerceIn(10f, size.width - 10f), size.height / 2))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Swatch("Picked", raw, ground)
                if (!ok) Swatch("Used", safe, ground)
            }

            Text(
                if (ok) {
                    "Passes at %.1f:1 against this ground.".format(contrast(raw, ground))
                } else {
                    "That colour is %.1f:1 on this ground, under the 4.5:1 an 11px label needs. "
                        .format(contrast(raw, ground)) +
                        "Its hue is kept and the lightness raised to %.1f:1."
                            .format(contrast(safe, ground))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.extras.hrv,
                modifier = Modifier.padding(top = 12.dp),
            )

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Box(Modifier.weight(1f))
                TextButton(onClick = { onPick(safe) }) {
                    Text("Use this", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    }
}

@Composable
private fun Swatch(label: String, colour: Color, ground: Color) =
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ground)
                // On true black the ground and the sheet are the same colour,
                // and without this the sample is an "Aa" floating in nothing.
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Aa",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colour,
            )
        }
        Text(
            "$label ${"%.1f".format(contrast(colour, ground))}:1",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.extras.disabled,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

/** The theme as it will actually look, rather than as a row of swatches. */
@Composable
private fun Preview(dark: Boolean) {
    SectionHeader("How it looks")
    DetailCard {
        Row(verticalAlignment = Alignment.Bottom) {
            BigNumber("7h 32m")
            Text(
                " last night",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 5.dp),
            )
            Box(Modifier.weight(1f))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    "Steady",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        // The six that never move, so the effect of a ground on them is
        // visible rather than claimed.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            val extras = MaterialTheme.extras
            listOf(
                extras.heart, extras.steps, extras.activity,
                extras.hrv, extras.energy, extras.sleepRem, extras.sleepAwake,
            ).forEach {
                Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(6.dp)).background(it))
            }
        }
        Note2("The health colours never move. The last block does — it is mixed from the ground.")
    }
}

/** The one line inside the preview card. */
@Composable
private fun Note2(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.extras.disabled,
    modifier = Modifier.padding(top = 8.dp),
)

/** HSL to RGB, because a picker thinks in hue and lightness, not channels. */
internal fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1f - kotlin.math.abs(2 * lightness - 1f)) * saturation
    val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val m = lightness - c / 2
    val (r, g, b) = when (((hue % 360f) / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        ((r + m).coerceIn(0f, 1f) * 255).roundToInt(),
        ((g + m).coerceIn(0f, 1f) * 255).roundToInt(),
        ((b + m).coerceIn(0f, 1f) * 255).roundToInt(),
    )
}

private fun Modifier.semanticsLabel(label: String): Modifier =
    this.semantics { contentDescription = label }
