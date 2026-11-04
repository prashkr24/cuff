package com.srineere.airbridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.Session
import com.srineere.airbridge.health.SessionReader
import com.srineere.airbridge.ui.theme.extras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Every workout, as far back as you ask.
 *
 * Deliberately plainer than the seven-day list behind it. That one carries
 * four figures a row because seven days is a handful of sessions worth
 * studying; this is a year of them, where the job is finding one. The
 * figures are a tap away, on the session's own screen.
 */
@Composable
internal fun HealthSessionsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val colour = LocalTileColour.current ?: MaterialTheme.extras.activity
    var days by remember { mutableIntStateOf(90) }
    var sessions by remember { mutableStateOf<List<Session>?>(null) }

    LaunchedEffect(days) {
        sessions = null
        sessions = withContext(Dispatchers.IO) { SessionReader().sessions(context, days) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("All activities", onBack = onBack)

        RangePills(
            options = listOf(30, 90, 365),
            selected = days,
            onSelect = { days = it },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            label = { if (it >= 365) "A year" else "$it days" },
        )

        val found = sessions
        when {
            found == null -> Box(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }

            found.isEmpty() -> Text(
                "No workouts in this stretch.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )

            else -> {
                val zone = remember { ZoneId.systemDefault() }
                val locale = Locale.getDefault()
                // Grouped by month, because that is how anyone looking for a
                // run from the spring actually looks for it.
                var month = ""
                found.forEach { session ->
                    val at = session.start.atZone(zone).toLocalDate()
                    val label = at.month.getDisplayName(TextStyle.FULL, locale) + " " + at.year
                    if (label != month) {
                        month = label
                        SectionHeader(label)
                    }
                    HistoryRow(session, colour) {
                        onOpen("healthsession/${session.start.toEpochMilli()}")
                    }
                }
                Text(
                    "${found.size} workouts, newest first. Reading further back than " +
                        "thirty days needs the history permission, which Health " +
                        "Connect asks for separately.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.extras.disabled,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
        Box(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun HistoryRow(
    session: Session,
    colour: androidx.compose.ui.graphics.Color,
    onOpen: () -> Unit,
) {
    val clock = remember {
        DateTimeFormatter
            .ofPattern(
                android.text.format.DateFormat
                    .getBestDateTimePattern(Locale.getDefault(), "EEEdMMM Hm"),
            )
            .withZone(ZoneId.systemDefault())
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Text(
                session.exerciseName().take(1),
                style = MaterialTheme.typography.titleSmall,
                color = colour,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(session.exerciseName(), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                clock.format(session.start),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            session.length.asHoursMinutes(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.extras.disabled,
        )
    }
}
