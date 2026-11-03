package com.srineere.airbridge.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.ui.theme.extras

/**
 * A gallery, not a configurator: Android's own long-press picker does the
 * placing, so this screen only has to say what exists and what each looks like.
 */
@Composable
internal fun WidgetsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Widgets", onBack = onBack)

        Text(
            "Long-press your home screen, pick Widgets, then Air Bridge. They follow " +
                "your accent colour and refresh in the background.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        SectionHeader("Ready now")
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            WidgetTile("Band battery", "2×1", Modifier.weight(1f)) { BatteryPreview() }
            WidgetTile("Last buzz", "2×1", Modifier.weight(1f)) { LastBuzzPreview() }
        }

        SectionHeader("Arrives with Health")
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            WidgetTile("Activity rings", "2×2", Modifier.weight(1f), dimmed = true) { ComingPreview("Steps") }
            WidgetTile("Heart trend", "2×2", Modifier.weight(1f), dimmed = true) { ComingPreview("Resting HR") }
        }

        Text(
            "Band battery reads straight off the Air. The health ones read Health " +
                "Connect, so they show whatever Google Health last wrote there.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        )
    }
}

@Composable
private fun WidgetTile(
    name: String,
    size: String,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    preview: @Composable () -> Unit,
) {
    Column(modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.padding(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) {
                    Box(Modifier.padding(10.dp), contentAlignment = Alignment.CenterStart) { preview() }
                }
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (dimmed) MaterialTheme.extras.disabled else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(size, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BatteryPreview() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Air", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("72%", style = MaterialTheme.typography.titleMedium)
        }
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            Box(
                Modifier.fillMaxWidth(0.72f).height(5.dp).clip(CircleShape)
                    .background(MaterialTheme.extras.positive)
            )
        }
    }
}

@Composable
private fun LastBuzzPreview() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.extras.positive))
            Text(
                "  Relaying",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("Priya", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
        Text(
            "Phone · 2 min ago",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ComingPreview(label: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("—", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.extras.disabled)
    }
}
