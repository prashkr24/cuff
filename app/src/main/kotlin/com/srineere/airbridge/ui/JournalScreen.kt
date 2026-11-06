package com.srineere.airbridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.HealthSettings
import com.srineere.airbridge.health.Journal
import com.srineere.airbridge.health.JournalAnswer
import com.srineere.airbridge.health.JournalReminder
import com.srineere.airbridge.health.JournalCounting
import com.srineere.airbridge.health.JournalFinding
import com.srineere.airbridge.health.JournalTag
import com.srineere.airbridge.health.analyseJournal
import com.srineere.airbridge.health.sleepScoreOf
import com.srineere.airbridge.ui.theme.extras
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What you did before a night, and what it has turned out to mean.
 *
 * Yes and no, never a blank. An unanswered tag and a "no" look identical in
 * a correlation and mean opposite things, which is how every journal that
 * records only the yeses ends up claiming caffeine helps.
 *
 * Nothing is said about a tag until there are five nights with it and five
 * without, the gap is wider than the night-to-night swing of the scores
 * themselves, and the lag that showed it is named. What is still being
 * counted is shown rather than hidden, so the silence on a tag has a
 * reason somebody can read.
 */
@Composable
internal fun JournalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val extras = MaterialTheme.extras
    val worse = extras.heart
    val better = extras.positive

    // The night these answers are about, which the clock decides: in the
    // evening it is the night ahead, in the morning the one just ended.
    // Both are the same key, which is what makes answering at either end
    // of the day land in the same place.
    val night = remember(resumeTicks()) { Journal.nightFor() }
    val tonight = night > java.time.LocalDate.now().toEpochDay()
    var answers by remember(night) { mutableStateOf(Journal.read(context, night)) }
    var history by remember { mutableStateOf(Journal.history(context)) }
    var sending by remember { mutableStateOf(HealthSettings.journal(context)) }

    val scores = remember(data.nights) {
        data.nights.mapNotNull { night ->
            sleepScoreOf(night, data.nights)?.total?.let { night.day to it }
        }.toMap()
    }
    val (findings, counting) = remember(history, scores) { analyseJournal(history, scores) }

    fun set(tag: JournalTag, answer: JournalAnswer?) {
        Journal.answer(context, night, tag, answer)
        answers = Journal.read(context, night)
        history = Journal.history(context)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(
            "Journal",
            subtitle = if (tonight) "tonight" else "last night",
            onBack = onBack,
        )

        SectionHeader(
            if (tonight) "Has any of this happened today?" else "Did any of this happen?",
            action = "${answers.size} of ${JournalTag.entries.size}",
        )
        DetailCard {
            JournalTag.entries.forEachIndexed { index, tag ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                TagRow(tag, answers[tag], better, worse) { wanted ->
                    // Tapping the answer it already has clears it, which is
                    // the only way back to "not asked" -- and that state has
                    // to stay reachable, because a wrong answer left in
                    // place is worse than no answer at all.
                    set(tag, if (answers[tag] == wanted) null else wanted)
                }
            }
        }

        SectionHeader("What has shown up", action = "${Journal.WINDOW_DAYS} days")
        if (findings.isEmpty()) {
            DetailCard {
                Text(
                    "Nothing yet. A tag needs ${Journal.NEEDED} nights with it and " +
                        "${Journal.NEEDED} without before anything is said about it, and " +
                        "the difference has to be wider than your usual night-to-night " +
                        "swing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        findings.forEach { finding ->
            FindingCard(finding, better, worse)
        }

        if (counting.isNotEmpty()) {
            SectionHeader("Still counting")
            DetailCard {
                counting.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    CountingRow(row)
                }
                Text(
                    "Nothing is said about a tag until there are ${Journal.NEEDED} nights " +
                        "with it and ${Journal.NEEDED} without.",
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.disabled,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        SectionHeader("Asking")
        SettingsRow(
            title = "Ask me each evening",
            subtitle = if (sending.on) "At ${sending.clock}, before bed"
            else "Off — open this when you remember",
            trailing = {
                RowSwitch(
                    checked = sending.on,
                    onCheckedChange = { wanted ->
                        sending = sending.copy(on = wanted)
                        HealthSettings.writeJournal(context, sending)
                        JournalReminder.schedule(context)
                    },
                )
            },
        )
        if (sending.on) {
            SettingsRow(
                indent = Indent.UnderTitle,
                title = "At",
                subtitle = "Late enough to be the whole day, early enough to answer",
                onClick = {
                    android.app.TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            sending = sending.copy(minute = hour * 60 + minute)
                            HealthSettings.writeJournal(context, sending)
                            JournalReminder.schedule(context)
                        },
                        sending.hour,
                        sending.past,
                        android.text.format.DateFormat.is24HourFormat(context),
                    ).show()
                },
                trailing = {
                    Text(sending.clock, style = MaterialTheme.typography.bodyLarge)
                },
            )
        }

        Text(
            "Your tags live in this app, because Health Connect has no record for " +
                "them. They are never sent anywhere, and clearing a tag clears what " +
                "it told you.",
            style = MaterialTheme.typography.labelMedium,
            color = extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

/** One tag, and the two answers it can have. */
@Composable
private fun TagRow(
    tag: JournalTag,
    answer: JournalAnswer?,
    better: Color,
    worse: Color,
    onAnswer: (JournalAnswer) -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
) {
    Text(
        tag.label,
        style = MaterialTheme.typography.bodyMedium,
        // An unanswered tag is drawn quieter than an answered one, so the
        // ones still waiting are findable without counting them.
        color = if (answer == null) MaterialTheme.extras.disabled
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AnswerPill("Yes", answer == JournalAnswer.Yes, better) { onAnswer(JournalAnswer.Yes) }
        AnswerPill("No", answer == JournalAnswer.No, worse) { onAnswer(JournalAnswer.No) }
    }
}

@Composable
private fun AnswerPill(label: String, on: Boolean, tint: Color, onPick: () -> Unit) = Surface(
    color = if (on) tint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(50),
    modifier = Modifier.clickable(onClick = onPick),
) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        color = if (on) tint else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.width(54.dp).padding(vertical = 8.dp),
    )
}

/**
 * One finding, with its working under it.
 *
 * The two means are drawn as bars against each other rather than as a
 * figure, because the question is which is bigger and by how much, and the
 * counts sit under them because a mean of five nights and a mean of fifty
 * are different claims written the same way.
 */
@Composable
private fun FindingCard(finding: JournalFinding, better: Color, worse: Color) = DetailCard {
    val down = finding.gap < 0
    val tint = if (down) worse else better
    Text(
        buildString {
            append("On nights ")
            append(if (finding.lag == 0) "after " else "${finding.lag} after ")
            append(finding.tag.label.lowercase())
            append(", your sleep score is ")
            append("${abs(finding.gap).roundToInt()} ")
            append(if (down) "lower" else "higher")
            append(".")
        },
        style = MaterialTheme.typography.bodyLarge,
    )
    MeanBar("Without", finding.withoutMean, better, finding.withoutMean, finding.withMean)
    MeanBar("With", finding.withMean, tint, finding.withoutMean, finding.withMean)
    Text(
        "${finding.withoutCount} nights without · ${finding.withCount} with",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.extras.disabled,
        modifier = Modifier.padding(top = 6.dp),
    )
    Text(
        // "Not a cause" in the finding itself, not in a footnote nobody
        // reaches. Two readings that move together is all this can see.
        "A pattern, not a cause. The gap is wider than your usual " +
            "night-to-night swing, which is the only reason it is here. Checked " +
            "the same night and up to ${Journal.MAX_LAG} nights after; this one " +
            (if (finding.lag == 0) "showed on the same night." else "showed ${finding.lag} nights after."),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.extras.disabled,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun MeanBar(label: String, value: Double, tint: Color, low: Double, high: Double) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(58.dp),
    )
    // Both bars share a scale that starts at zero, so the taller one is
    // genuinely taller rather than merely further along a cropped axis.
    val top = maxOf(high, low, 1.0)
    Box(
        Modifier.weight(1f).height(13.dp).clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier.fillMaxWidth((value / top).toFloat().coerceIn(0f, 1f))
                .height(13.dp).clip(RoundedCornerShape(3.dp)).background(tint),
        )
    }
    Text(
        "${value.roundToInt()}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.End,
        modifier = Modifier.width(34.dp).padding(start = 8.dp),
    )
}

/** A tag that has not earned a finding, and how far it still has to go. */
@Composable
private fun CountingRow(row: JournalCounting) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
) {
    Text(
        row.tag.label,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
    )
    Box(
        Modifier.width(92.dp).height(6.dp).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier.fillMaxWidth((row.have.toFloat() / Journal.NEEDED).coerceIn(0f, 1f))
                .height(6.dp).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
    Text(
        "${row.yes} yes · ${row.no} no",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier.width(76.dp).padding(start = 8.dp),
    )
}
