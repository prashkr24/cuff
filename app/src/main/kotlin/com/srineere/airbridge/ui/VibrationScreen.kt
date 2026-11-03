package com.srineere.airbridge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.RelayPattern
import com.srineere.airbridge.RelayPreset
import com.srineere.airbridge.RelayRingSettings
import com.srineere.airbridge.ui.theme.extras

/**
 * Pattern, then cadence, then strength — three short lists rather than one
 * long list of every combination. Saved on each tap: there is no Save button
 * because there is nothing a half-made choice could mean.
 */
@Composable
internal fun VibrationScreen(target: VibrationTarget, onBack: () -> Unit) {
    val state = LocalRelayState.current
    val current = state.preset(target) ?: state.inherited(target)
    val callsOnly = target is VibrationTarget.DefaultCall ||
        target is VibrationTarget.DefaultAppCall ||
        (target is VibrationTarget.App && target.calls) ||
        target is VibrationTarget.Caller

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(titleFor(target, state), numberFor(target, state), onBack = onBack)

        SectionHeader("Pattern")
        RelayPattern.entries
            // A repeat has nothing to stop it unless a call is still ringing,
            // so it is only offered where a call is what is being described.
            .filter { it != RelayPattern.RING || callsOnly }
            .forEach { pattern ->
                PatternRow(
                    pattern = pattern,
                    selected = current.pattern == pattern,
                    onClick = {
                        state.setPreset(
                            target,
                            if (pattern == RelayPattern.SILENT) RelayPreset.MUTE
                            else RelayPreset(pattern, current.strength),
                        )
                    },
                )
            }

        AnimatedVisibility(visible = current.pattern == RelayPattern.RING) {
            Column {
                SectionHeader("How fast it repeats")
                val ring = state.ring(target)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    CADENCES.forEach { (label, gap) ->
                        Segment(
                            label = label,
                            selected = ring.gapMillis == gap,
                            modifier = Modifier.weight(1f),
                        ) { state.setRing(target, ring.copy(gapMillis = gap)) }
                    }
                }
                Text(
                    "A pulse every ${ring.gapMillis} ms, for up to ${ring.maxSeconds} seconds. " +
                        "Give people you want to recognise different speeds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }

        AnimatedVisibility(visible = !current.silent) {
            Column {
                SectionHeader("Strength")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    Segment(
                        label = "Light",
                        selected = current.strength == RelayPreset.LOW_STRENGTH,
                        modifier = Modifier.weight(1f),
                    ) { state.setPreset(target, current.copy(strength = RelayPreset.LOW_STRENGTH)) }
                    Segment(
                        label = "Strong",
                        selected = current.strength == RelayPreset.FULL_STRENGTH,
                        modifier = Modifier.weight(1f),
                    ) { state.setPreset(target, current.copy(strength = RelayPreset.FULL_STRENGTH)) }
                }
                Text(
                    "The Air has two. Strong is what you want on the wrist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }

        // Picking the default's current shape by hand freezes it onto this
        // target; following the default is a different thing, and the only
        // way back out of a rule.
        val own = state.preset(target) != null &&
            (target is VibrationTarget.Caller || target is VibrationTarget.App)
        AnimatedVisibility(visible = own) {
            TextButton(
                onClick = { state.clearPreset(target); onBack() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text("Use the default instead")
            }
        }

        Box(Modifier.padding(bottom = 28.dp))
    }
}

private val CADENCES = listOf(
    "Urgent" to 350,
    "Steady" to RelayRingSettings.DEFAULT_GAP_MILLIS,
    "Gentle" to 1_200,
)

@Composable
private fun PatternRow(pattern: RelayPattern, selected: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "patternFill",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
    ) {
        // The shape of the buzz, drawn: one dot, two dots, or a run of them.
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.size(width = 56.dp, height = 12.dp),
        ) {
            val dots = when (pattern) {
                RelayPattern.SILENT -> 0
                RelayPattern.ONE -> 1
                RelayPattern.TWO -> 2
                RelayPattern.RING -> 3
            }
            if (dots == 0) {
                Box(
                    Modifier.size(5.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant)
                )
            } else {
                repeat(dots) {
                    Box(
                        Modifier.size(11.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                if (pattern == RelayPattern.RING) {
                    Text("…", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                when (pattern) {
                    RelayPattern.SILENT -> "Silent"
                    RelayPattern.ONE -> "One pulse"
                    RelayPattern.TWO -> "Two pulses"
                    RelayPattern.RING -> "Ring until answered"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (pattern == RelayPattern.RING) {
                Text(
                    "Keeps pulsing while the phone rings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun Segment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        if (selected) scheme.primaryContainer else Color.Transparent,
        label = "segmentFill",
    )
    val scale by animateFloatAsState(
        if (selected) 1f else 0.98f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "segmentScale",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .border(
                1.dp,
                if (selected) MaterialTheme.extras.accentOutline else scheme.outline,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        )
    }
}

private fun titleFor(target: VibrationTarget, state: RelayState): String = when (target) {
    VibrationTarget.Default -> "Everything else"
    VibrationTarget.DefaultCall -> "Phone calls"
    VibrationTarget.DefaultAppCall -> "App calls"
    is VibrationTarget.App -> if (target.calls) "Calls" else "Messages"
    is VibrationTarget.Caller -> state.caller(target.id)?.label ?: "Caller"
}

/**
 * Which number this rule is for, under the name. A contact can hold two, and
 * the rule only ever covers the one that was tapped -- without this the two
 * screens are identical and the wrong one gets edited.
 */
private fun numberFor(target: VibrationTarget, state: RelayState): String? {
    if (target !is VibrationTarget.Caller) return null
    return state.caller(target.id)?.detail?.takeIf { it.isNotBlank() }
}
