package com.srineere.airbridge.ui

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.srineere.airbridge.health.ActiveZoneStore
import com.srineere.airbridge.health.BehaviorTarget
import com.srineere.airbridge.health.ChaDomain
import com.srineere.airbridge.health.ChaDomainResult
import com.srineere.airbridge.health.ChaEstimate
import com.srineere.airbridge.health.CoachableHealthAge
import com.srineere.airbridge.health.CounterfactualCoach
import com.srineere.airbridge.health.CounterfactualOption
import com.srineere.airbridge.health.CounterfactualPlan
import com.srineere.airbridge.health.DailyZoneSummary
import com.srineere.airbridge.health.FitnessProfile
import com.srineere.airbridge.health.FitnessProfileStore
import com.srineere.airbridge.health.GAgePrefs
import com.srineere.airbridge.health.GAgeSettings
import com.srineere.airbridge.health.HealthAvailability
import com.srineere.airbridge.health.HealthConnect
import com.srineere.airbridge.health.HealthEnabled
import com.srineere.airbridge.health.ProjectedWeek
import com.srineere.airbridge.health.Sex
import com.srineere.airbridge.health.SimulationMode
import com.srineere.airbridge.health.WeeklyChaPoint
import com.srineere.airbridge.ui.theme.extras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The G-Age tab: Coachable Health Age (CHA), 12-week feasibility-constrained
 * counterfactual pathways, interactive What-If simulator, and offline
 * Appendix C.3 coaching prompt builder from arXiv:2611.08568v1.
 *
 * Runs 100% locally on Fitbit Air readings synced via Health Connect — no
 * network connection or INTERNET permission required.
 */
@Composable
internal fun GAgeScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val healthOn by HealthEnabled.enabled.collectAsState()
    val state = LocalHealthState.current

    val askPermissions = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        if (granted.intersect(HealthConnect.readPermissions).isNotEmpty()) {
            HealthEnabled.set(context, true)
            state.refresh(context, force = true)
        }
    }

    if (!healthOn) {
        GAgeConnectGate(
            availability = remember { HealthConnect.availability(context) },
            onConnect = { runCatching { askPermissions.launch(HealthConnect.allPermissions) } },
            onOpenPermissions = { onOpen("permissions") },
        )
        return
    }

    val data by healthData(context)
    when (data.stage) {
        HealthStage.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
        HealthStage.NotConnected, HealthStage.NeedsUpdate -> GAgeConnectGate(
            availability = if (data.stage == HealthStage.NeedsUpdate) {
                HealthAvailability.NeedsUpdate
            } else {
                HealthAvailability.Ready
            },
            onConnect = { runCatching { askPermissions.launch(HealthConnect.allPermissions) } },
            onOpenPermissions = { onOpen("permissions") },
        )
        HealthStage.Granted -> GAgeBody(data = data, onOpen = onOpen)
    }
}

@Composable
private fun GAgeConnectGate(
    availability: HealthAvailability,
    onConnect: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val extras = MaterialTheme.extras
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("G-Age", subtitle = "Coachable Health Age · arXiv:2611.08568")
        DetailCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Icon(
                        Icons.Outlined.HourglassEmpty,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Connect Health Connect",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Reads your Fitbit Air history locally",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "G-Age implements Google Research's Coachable Health Age (resting heart rate, " +
                    "daily steps, main sleep, Active Zone Minutes, plus optional VO₂ max and " +
                    "sleep regularity) and 12-week counterfactual coaching plans completely " +
                    "offline on your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            when (availability) {
                HealthAvailability.Ready -> {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    ) {
                        Text("Enable Health Connect")
                    }
                }
                HealthAvailability.NeedsUpdate -> {
                    Text(
                        "Health Connect needs an update before readings can be accessed.",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.heart,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    OutlinedButton(
                        onClick = onOpenPermissions,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    ) {
                        Text("Open Permissions")
                    }
                }
                HealthAvailability.NotInstalled -> {
                    Text(
                        "Health Connect is not installed on this phone.",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.disabled,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GAgeBody(data: HealthData, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val state = LocalHealthState.current
    val extras = MaterialTheme.extras
    val scheme = MaterialTheme.colorScheme

    // Widen to 24 weeks (168 days -> 180-day window) to support the paper's
    // 90-day CHA window and 24-week habitual distributions/peaks.
    LaunchedEffect(Unit) {
        state.widen(context, CoachableHealthAge.HABITUAL_WINDOW_DAYS)
    }

    var profile by remember { mutableStateOf(FitnessProfileStore.read(context)) }
    var prefs by remember { mutableStateOf(GAgeSettings.read(context)) }
    var zoneSyncKey by remember { mutableIntStateOf(0) }
    var zoneProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    var dailyZones by remember(profile.age) {
        mutableStateOf(
            ActiveZoneStore.overlayRecent(
                ActiveZoneStore.read(context, profile.age),
                data.snapshot,
                profile.age,
            ),
        )
    }

    // Incrementally index missing past days of HeartRateRecord into local AZM cache.
    LaunchedEffect(data.snapshot.writtenAt, state.loadedDays, profile.age, zoneSyncKey) {
        val currentAge = profile.age
        val base = withContext(Dispatchers.IO) {
            ActiveZoneStore.overlayRecent(
                ActiveZoneStore.read(context, currentAge),
                data.snapshot,
                currentAge,
            )
        }
        dailyZones = base
        val targetDays = min(max(state.loadedDays, CoachableHealthAge.WINDOW_DAYS), CoachableHealthAge.HABITUAL_WINDOW_DAYS)
        withContext(Dispatchers.IO) {
            ActiveZoneStore.syncMissing(
                context = context,
                snapshot = data.snapshot,
                days = targetDays,
                age = currentAge,
            ) { updated, done, total ->
                dailyZones = updated
                zoneProgress = if (total > 0 && done < total) done to total else null
            }
        }
        zoneProgress = null
    }

    val chaConfig = remember(prefs) { prefs.toChaConfig() }
    val summary = remember(data.snapshot, dailyZones, profile, chaConfig, prefs.insulinResistant) {
        CoachableHealthAge.summarize(
            snapshot = data.snapshot,
            dailyZones = dailyZones,
            profile = profile,
            config = chaConfig,
            insulinResistant = prefs.insulinResistant,
        )
    }
    val plan = remember(summary, dailyZones, data.snapshot, chaConfig, prefs.simulationMode) {
        CounterfactualCoach.plan(
            summary = summary,
            dailyZones = dailyZones,
            snapshot = data.snapshot,
            config = chaConfig,
            mode = prefs.simulationMode,
        )
    }

    // Selected pathway index (null = custom What-If slider values).
    var selectedOptionIdx by remember(plan?.effectiveMode, plan?.options?.size) {
        mutableStateOf(if (plan != null && plan.options.isNotEmpty()) 0 else null)
    }
    var customTarget by remember(
        plan?.baseline,
        plan?.effectiveMode,
        plan?.options?.firstOrNull()?.target,
    ) {
        mutableStateOf(
            plan?.options?.firstOrNull()?.target
                ?: plan?.baseline
                ?: BehaviorTarget(8_000.0, 7.0, 15.0),
        )
    }

    // Keep simulator sliders synced when a pathway card is tapped.
    val activeSimulation = remember(summary, plan, customTarget, chaConfig) {
        plan?.let { CounterfactualCoach.simulateCustom(summary, it, customTarget, chaConfig) }
    }

    var promptExpanded by remember { mutableStateOf(false) }
    var copiedBanner by remember { mutableStateOf(false) }
    var showHeightDialog by remember { mutableStateOf(false) }

    LaunchedEffect(copiedBanner) {
        if (copiedBanner) {
            delay(2_000)
            copiedBanner = false
        }
    }

    val readingLong = state.wideningTo != null
    val est = summary.estimate

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(
            title = "G-Age",
            subtitle = "Coachable Health Age · arXiv:2611.08568",
            action = {
                IconButton(
                    onClick = {
                        state.refresh(context, force = true)
                        zoneSyncKey++
                    },
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh readings")
                }
            },
        )

        // ----------------------------------------------------- 1. Hero card
        DetailCard {
            if (profile.birthYear == null) {
                Text(
                    "Set your birth year to compute G-Age",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Coachable Health Age is expressed as your chronological age plus your " +
                        "wearable risk delta (in years). Your birth year stays on this phone " +
                        "and is shared with Fitness Age.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Button(
                    onClick = {
                        openBirthYearPicker(context, profile) { updated ->
                            profile = updated
                            FitnessProfileStore.write(context, updated)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text("Set year of birth")
                }
            } else {
                val delta = est.deltaYears
                val accentColor = when {
                    delta == null -> extras.disabled
                    delta <= -0.2 -> extras.positive
                    delta >= 0.2 -> extras.heart
                    else -> scheme.primary
                }
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        est.healthAge?.let { "%.1f".format(it) } ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "You are ${profile.age}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                            )
                            if (delta != null) {
                                Spacer(Modifier.width(8.dp))
                                DeltaChip(deltaYears = delta)
                            }
                        }
                        Text(
                            when {
                                est.healthAge == null && readingLong ->
                                    "Reading your last ${state.wideningTo} days…"
                                est.healthAge == null ->
                                    "Waiting for Fitbit Air readings in Health Connect"
                                summary.calibrated ->
                                    "90-day window · ${summary.validWeeksIn90Days} valid weeks · " +
                                        "${est.corePresent}/4 core signals"
                                else ->
                                    "Early estimate · ${summary.validWeeksIn90Days} of " +
                                        "${CoachableHealthAge.MIN_VALID_WEEKS} valid weeks · " +
                                        "${est.corePresent}/4 core"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = extras.disabled,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                if (delta != null) {
                    ChaDeltaRail(
                        deltaYears = delta,
                        coreFloor = est.coreFloorDelta,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }

                // Quick core metrics strip
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                ) {
                    Stat(
                        label = "RHR",
                        unit = "bpm",
                        value = summary.inputs.restingHeartRateBpm?.roundToInt()?.toString(),
                        tint = extras.heart,
                        modifier = Modifier.weight(1f),
                    )
                    Stat(
                        label = "Steps",
                        unit = "/day",
                        value = summary.inputs.dailySteps?.let { formatWhole(it) },
                        tint = extras.steps,
                        modifier = Modifier.weight(1f),
                    )
                    Stat(
                        label = "Sleep",
                        unit = "h",
                        value = summary.inputs.sleepHours?.let { "%.1f".format(it) },
                        tint = extras.sleepRem,
                        modifier = Modifier.weight(1f),
                    )
                    Stat(
                        label = "AZM",
                        unit = "/wk",
                        value = summary.inputs.weeklyAzm?.roundToInt()?.toString(),
                        tint = extras.activity,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (zoneProgress != null) {
                    val (done, total) = zoneProgress!!
                    val frac = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(
                            "Indexing Fitbit Air heart zones ($done / $total days)…",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                    }
                } else if (readingLong) {
                    Text(
                        "Widening Health Connect history to ${state.wideningTo} days…",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.disabled,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        // --------------------------------------------- 2. Domain breakdown
        SectionHeader("What drives your G-Age")
        DetailCard {
            val shownDomains = est.domains.filter { d ->
                !d.domain.optional || d.included ||
                    (d.domain == ChaDomain.Vo2Max && prefs.includeVo2Max) ||
                    (d.domain == ChaDomain.SleepRegularity && prefs.includeSleepRegularity)
            }
            shownDomains.forEachIndexed { idx, item ->
                if (idx > 0) {
                    HorizontalDivider(
                        color = scheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                DomainBreakdownRow(item = item)
            }
            Text(
                buildString {
                    append(
                        if (prefs.collinearityAdjusted) {
                            "WEAR-ME collinearity-adjusted weights (core rejuvenation floor " +
                                "${"%.1f".format(est.coreFloorDelta)} yr)."
                        } else {
                            "Unadjusted epidemiological weights (core rejuvenation floor " +
                                "${"%.1f".format(est.coreFloorDelta)} yr)."
                        },
                    )
                    append(" Active Zone Minutes are reconstructed from your Fitbit Air heart-rate stream via Heart Rate Reserve (1× moderate, 2× cardio/peak).")
                },
                style = MaterialTheme.typography.labelMedium,
                color = extras.disabled,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // ------------------------------------------ 3. Trajectory chart
        if (profile.age != null && (summary.weeklyHistory.isNotEmpty() || plan != null)) {
            SectionHeader("History & 12-week projection")
            DetailCard {
                GAgeTrajectoryChart(
                    history = summary.weeklyHistory,
                    chronologicalAge = profile.age!!.toDouble(),
                    statusQuo = plan?.statusQuo?.trajectory.orEmpty(),
                    counterfactual = activeSimulation?.rollout?.trajectory.orEmpty(),
                    modifier = Modifier.fillMaxWidth().height(170.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    LegendDot("Observed", scheme.primary)
                    LegendDot("Age ${profile.age}", extras.disabled)
                    if (plan != null) {
                        LegendDot("Future A", extras.hrv)
                        LegendDot("Future B", extras.positive)
                    }
                }
            }
        }

        // ---------------------------------- 4. 12-week counterfactual paths
        if (plan != null) {
            SectionHeader("12-week counterfactual paths")

            // Simulation engine toggle pills (Analytical vs Experimental N=1)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            ) {
                SimulationMode.entries.forEach { mode ->
                    val on = prefs.simulationMode == mode
                    ChoicePill(
                        label = mode.label,
                        selected = on,
                        onClick = {
                            prefs = prefs.copy(simulationMode = mode)
                            GAgeSettings.write(context, prefs)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                plan.diagnostics.note + (
                    plan.diagnostics.rhrR2?.let { " (in-sample RHR R² = ${"%.2f".format(it)})" } ?: ""
                ),
                style = MaterialTheme.typography.labelMedium,
                color = extras.disabled,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )

            // Persona & Future A card
            DetailCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = scheme.secondaryContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            plan.persona.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${plan.habitual.weeksObserved}-wk history",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.disabled,
                    )
                }
                Text(
                    plan.persona.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                HorizontalDivider(
                    color = scheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Future A · Keep current routine",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${formatWhole(plan.baseline.dailySteps)} steps · " +
                                "${"%.1f".format(plan.baseline.sleepHours)} h sleep · " +
                                "${plan.baseline.cardioMinPerDay.roundToInt()} cardio min/d",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${"%.1f".format(plan.statusQuo.endpointCha)} yr",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                    )
                }
            }

            // Ranked Future B option cards
            if (plan.options.isEmpty()) {
                DetailCard {
                    Text(
                        "You are near the behavioral ceiling",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = extras.positive,
                    )
                    Text(
                        "No feasible step, sleep, or cardio change lowers your projected G-Age " +
                            "by more than 0.1 years beyond your current habits. Use the What-If " +
                            "simulator below to test custom scenarios.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                plan.options.forEachIndexed { idx, option ->
                    PathwayOptionCard(
                        index = idx + 1,
                        option = option,
                        selected = selectedOptionIdx == idx,
                        onSelect = {
                            selectedOptionIdx = idx
                            customTarget = option.target
                        },
                    )
                }
            }

            // ----------------------------------- 5. What-If simulator
            SectionHeader(
                "What-if simulator",
                action = "Reset",
                onAction = {
                    selectedOptionIdx = null
                    customTarget = plan.baseline
                },
            )
            DetailCard {
                if (activeSimulation != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Projected 12-week G-Age",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "${"%.1f".format(activeSimulation.rollout.endpointCha)} yr",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.width(8.dp))
                                DeltaChip(
                                    deltaYears = activeSimulation.chaDeltaVsStatusQuo,
                                    suffix = "vs Future A",
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Effort E = ${"%.2f".format(activeSimulation.effort)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                            activeSimulation.rollout.endpointWeeklyAzm?.let { azm ->
                                Text(
                                    "AZM ~${azm.roundToInt()}/wk",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = extras.activity,
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        color = scheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }

                // Steps slider
                SimulatorSliderRow(
                    label = "Daily steps",
                    valueText = "${formatWhole(customTarget.dailySteps)} /day",
                    hint = "avg ${formatWhole(plan.habitual.steps.mean)} · peak ${formatWhole(plan.habitual.steps.peak)}",
                    value = customTarget.dailySteps.toFloat().coerceIn(1_000f, 15_000f),
                    range = 1_000f..15_000f,
                    accent = extras.steps,
                    onChange = { v ->
                        selectedOptionIdx = null
                        val snapped = (v / 250f).roundToInt() * 250.0
                        customTarget = customTarget.copy(dailySteps = snapped)
                    },
                )

                // Sleep slider
                SimulatorSliderRow(
                    label = "Main sleep",
                    valueText = "${"%.2f".format(customTarget.sleepHours)} h/night",
                    hint = "avg ${"%.1f".format(plan.habitual.sleepHours.mean)} h · peak ${"%.1f".format(plan.habitual.sleepHours.peak)} h",
                    value = customTarget.sleepHours.toFloat().coerceIn(4.5f, 10.0f),
                    range = 4.5f..10.0f,
                    accent = extras.sleepRem,
                    onChange = { v ->
                        selectedOptionIdx = null
                        val snapped = (v * 4f).roundToInt() / 4.0
                        customTarget = customTarget.copy(sleepHours = snapped)
                    },
                )

                // Cardio slider
                SimulatorSliderRow(
                    label = "Cardio intensity (≥60% HRR)",
                    valueText = "${customTarget.cardioMinPerDay.roundToInt()} min/day",
                    hint = "avg ${plan.habitual.cardioMinPerDay.mean.roundToInt()} · peak ${plan.habitual.cardioMinPerDay.peak.roundToInt()} min/d",
                    value = customTarget.cardioMinPerDay.toFloat().coerceIn(0f, 60f),
                    range = 0f..60f,
                    accent = extras.activity,
                    onChange = { v ->
                        selectedOptionIdx = null
                        customTarget = customTarget.copy(cardioMinPerDay = v.roundToInt().toDouble())
                    },
                )
            }

            // -------------------------------- 6. Offline AI Coach Prompt
            SectionHeader("12-week AI coaching prompt")
            val promptText = remember(summary, plan, profile, prefs.heightCm) {
                CounterfactualCoach.buildCounterfactualCoachPrompt(
                    summary = summary,
                    plan = plan,
                    profile = profile,
                    heightCm = prefs.heightCm,
                )
            }
            DetailCard {
                Text(
                    "Generates the paper's verbatim Appendix C.3 coaching prompt pre-filled with " +
                        "your Fitbit Air baseline, 24-week peaks, and ranked Future A / B pathways. " +
                        "Cuff stays 100% offline — copy or share directly into Gemini, Claude, or ChatGPT.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Button(
                        onClick = {
                            copyToClipboard(context, promptText)
                            copiedBanner = true
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (copiedBanner) "Copied!" else "Copy prompt")
                    }
                    OutlinedButton(
                        onClick = { sharePrompt(context, promptText) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Share to AI")
                    }
                }
                TextButton(
                    onClick = { promptExpanded = !promptExpanded },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(if (promptExpanded) "Hide prompt preview" else "Preview prompt")
                }
                if (promptExpanded) {
                    Surface(
                        color = scheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Text(
                            promptText,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }

        // -------------------------------------- 7. Profile & model settings
        SectionHeader("Profile & model")
        SettingsRow(
            title = "Year of birth",
            subtitle = "Shared with Fitness Age · stays on this phone",
            onClick = {
                openBirthYearPicker(context, profile) { updated ->
                    profile = updated
                    FitnessProfileStore.write(context, updated)
                }
            },
            trailing = {
                Text(
                    profile.birthYear?.toString() ?: "Not set",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (profile.birthYear == null) extras.disabled else scheme.onSurface,
                )
            },
        )
        SettingsRow(
            title = "Sex reference (FRIEND VO₂ max)",
            subtitle = "Optional · used only when VO₂ max is included",
            trailing = {},
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Sex.entries.forEach { option ->
                ChoicePill(
                    label = option.label,
                    selected = profile.sex == option,
                    onClick = {
                        profile = profile.copy(sex = option)
                        FitnessProfileStore.write(context, profile)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SettingsRow(
            title = "Height (for BMI in coach prompt)",
            subtitle = "Optional · never required for G-Age",
            onClick = { showHeightDialog = true },
            trailing = {
                Text(
                    prefs.heightCm?.let { "$it cm" } ?: "Not set",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (prefs.heightCm == null) extras.disabled else scheme.onSurface,
                )
            },
        )
        SettingsRow(
            title = "Collinearity-adjusted weights",
            subtitle = "WEAR-ME adjusted (Eq. 8); off uses unadjusted literature HRs",
            trailing = {
                RowSwitch(
                    checked = prefs.collinearityAdjusted,
                    onCheckedChange = { on ->
                        prefs = prefs.copy(collinearityAdjusted = on)
                        GAgeSettings.write(context, prefs)
                    },
                )
            },
        )
        SettingsRow(
            title = "Include VO₂ max (FRIEND)",
            subtitle = "Optional domain · needs VO₂ max, sex and weight",
            trailing = {
                RowSwitch(
                    checked = prefs.includeVo2Max,
                    onCheckedChange = { on ->
                        prefs = prefs.copy(includeVo2Max = on)
                        GAgeSettings.write(context, prefs)
                    },
                )
            },
        )
        SettingsRow(
            title = "Include sleep regularity",
            subtitle = "Optional domain · circular SD of sleep midpoint",
            trailing = {
                RowSwitch(
                    checked = prefs.includeSleepRegularity,
                    onCheckedChange = { on ->
                        prefs = prefs.copy(includeSleepRegularity = on)
                        GAgeSettings.write(context, prefs)
                    },
                )
            },
        )
        SettingsRow(
            title = "Lab insulin resistance (optional)",
            subtitle = "Omitted by default · set only if you have a lab HOMA-IR test",
            trailing = {},
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            listOf(
                Triple("Omit", null, prefs.insulinResistant == null),
                Triple("Normal", false, prefs.insulinResistant == false),
                Triple("IR (≥2.9)", true, prefs.insulinResistant == true),
            ).forEach { (label, value, on) ->
                ChoicePill(
                    label = label,
                    selected = on,
                    onClick = {
                        prefs = prefs.copy(insulinResistant = value)
                        GAgeSettings.write(context, prefs)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SettingsRow(
            title = "Re-index Fitbit Air heart zones",
            subtitle = "${summary.zoneDaysIn90Days} of ${summary.observedDaysIn90Days} recent days cached",
            onClick = {
                ActiveZoneStore.clear(context)
                dailyZones = emptyMap()
                zoneSyncKey++
            },
        )
        SettingsRow(
            title = "Health Connect permissions",
            subtitle = "Grant History permission for full 90–168 day windows",
            onClick = { onOpen("permissions") },
        )

        Text(
            "Research implementation for personal testing based on Lee et al. (2026), " +
                "arXiv:2611.08568v1. Not medical advice or a diagnostic tool.",
            style = MaterialTheme.typography.labelMedium,
            color = extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showHeightDialog) {
        HeightPickerDialog(
            currentCm = prefs.heightCm,
            onDismiss = { showHeightDialog = false },
            onSave = { cm ->
                prefs = prefs.copy(heightCm = cm)
                GAgeSettings.write(context, prefs)
                showHeightDialog = false
            },
        )
    }
}

// ------------------------------------------------------------------ components

@Composable
private fun DeltaChip(deltaYears: Double, suffix: String? = null) {
    val extras = MaterialTheme.extras
    val scheme = MaterialTheme.colorScheme
    val (bg, fg, label) = when {
        deltaYears <= -0.05 -> Triple(
            extras.positive.copy(alpha = 0.16f),
            extras.positive,
            "${"%.1f".format(deltaYears)} yr" + (suffix?.let { " $it" } ?: " younger"),
        )
        deltaYears >= 0.05 -> Triple(
            extras.heart.copy(alpha = 0.16f),
            extras.heart,
            "+${"%.1f".format(deltaYears)} yr" + (suffix?.let { " $it" } ?: " older"),
        )
        else -> Triple(
            scheme.surfaceContainerHigh,
            scheme.onSurfaceVariant,
            "0.0 yr" + (suffix?.let { " $it" } ?: ""),
        )
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ChaDeltaRail(
    deltaYears: Double,
    coreFloor: Double,
    modifier: Modifier = Modifier,
) {
    val extras = MaterialTheme.extras
    val scheme = MaterialTheme.colorScheme
    val minRange = min(coreFloor - 1.0, deltaYears - 0.5)
    val maxRange = max(6.0, deltaYears + 0.5)

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val w = size.width
            val cy = size.height / 2f
            val trackH = 6.dp.toPx()
            val span = (maxRange - minRange).coerceAtLeast(1.0)
            fun xOf(v: Double): Float = (((v - minRange) / span).coerceIn(0.0, 1.0) * w).toFloat()

            // Base track
            drawRoundRect(
                color = scheme.surfaceContainerHigh,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(w, trackH),
                cornerRadius = CornerRadius(trackH / 2f, trackH / 2f),
            )
            // Younger zone [coreFloor..0]
            val xFloor = xOf(coreFloor)
            val xZero = xOf(0.0)
            drawRoundRect(
                color = extras.positive.copy(alpha = 0.28f),
                topLeft = Offset(xFloor, cy - trackH / 2f),
                size = Size((xZero - xFloor).coerceAtLeast(2f), trackH),
                cornerRadius = CornerRadius(trackH / 2f, trackH / 2f),
            )
            // Zero chronological anchor tick
            drawLine(
                color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                start = Offset(xZero, cy - 8.dp.toPx()),
                end = Offset(xZero, cy + 8.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
            )
            // Current marker
            val xVal = xOf(deltaYears)
            val markerColor = when {
                deltaYears <= -0.1 -> extras.positive
                deltaYears >= 0.1 -> extras.heart
                else -> scheme.primary
            }
            drawCircle(color = scheme.surface, radius = 8.dp.toPx(), center = Offset(xVal, cy))
            drawCircle(color = markerColor, radius = 5.5.dp.toPx(), center = Offset(xVal, cy))
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        ) {
            Text(
                "${"%.1f".format(coreFloor)} yr floor",
                style = MaterialTheme.typography.labelSmall,
                color = extras.disabled,
            )
            Text(
                "0 yr (age)",
                style = MaterialTheme.typography.labelSmall,
                color = extras.disabled,
            )
            Text(
                "+${"%.0f".format(maxRange)} yr",
                style = MaterialTheme.typography.labelSmall,
                color = extras.disabled,
            )
        }
    }
}

@Composable
private fun DomainBreakdownRow(item: ChaDomainResult) {
    val extras = MaterialTheme.extras
    val scheme = MaterialTheme.colorScheme
    val dotColor = when (item.domain) {
        ChaDomain.RestingHeartRate -> extras.heart
        ChaDomain.DailySteps -> extras.steps
        ChaDomain.SleepDuration -> extras.sleepRem
        ChaDomain.WeeklyAzm -> extras.activity
        ChaDomain.Vo2Max -> extras.hrv
        ChaDomain.SleepRegularity -> extras.sleepLight
        ChaDomain.InsulinResistance -> extras.energy
    }
    val valueStr = when {
        !item.included && item.measuredValue == null -> "—"
        item.domain == ChaDomain.DailySteps -> formatWhole(item.measuredValue!!)
        item.domain == ChaDomain.RestingHeartRate -> "${item.measuredValue!!.roundToInt()} bpm"
        item.domain == ChaDomain.SleepDuration -> "${"%.1f".format(item.measuredValue)} h"
        item.domain == ChaDomain.WeeklyAzm -> "${item.measuredValue!!.roundToInt()} min/wk"
        item.domain == ChaDomain.Vo2Max -> "${"%.1f".format(item.measuredValue)} ml/kg"
        item.domain == ChaDomain.SleepRegularity -> "${item.measuredValue!!.roundToInt()} min SD"
        item.domain == ChaDomain.InsulinResistance ->
            if ((item.measuredValue ?: 0.0) > 0.5) "Elevated" else "Normal"
        else -> "%.1f".format(item.measuredValue)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (item.included) dotColor else extras.disabled))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.domain.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (item.included) scheme.onSurface else extras.disabled,
                )
                if (item.domain.optional) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "optional",
                        style = MaterialTheme.typography.labelSmall,
                        color = extras.disabled,
                    )
                }
            }
            Text(
                buildString {
                    append(valueStr)
                    append(" · ")
                    append(item.reference)
                    if (item.included) {
                        append(" · w=")
                        append("%.2f".format(item.weight))
                    }
                    if (item.saturated) append(" · capped")
                },
                style = MaterialTheme.typography.labelMedium,
                color = extras.disabled,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        val deltaText = when {
            !item.included -> "—"
            abs(item.yearsDelta) < 0.005 -> "0.00 yr"
            else -> "%+.2f yr".format(item.yearsDelta)
        }
        val deltaColor = when {
            !item.included -> extras.disabled
            item.yearsDelta <= -0.05 -> extras.positive
            item.yearsDelta >= 0.05 -> extras.heart
            else -> scheme.onSurfaceVariant
        }
        Text(
            deltaText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = deltaColor,
        )
    }
}

@Composable
private fun GAgeTrajectoryChart(
    history: List<WeeklyChaPoint>,
    chronologicalAge: Double,
    statusQuo: List<ProjectedWeek>,
    counterfactual: List<ProjectedWeek>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = MaterialTheme.extras
    val allY = buildList {
        add(chronologicalAge)
        history.forEach { add(it.healthAge) }
        statusQuo.forEach { add(it.healthAge) }
        counterfactual.forEach { add(it.healthAge) }
    }
    val minY = (allY.minOrNull() ?: chronologicalAge) - 1.0
    val maxY = (allY.maxOrNull() ?: chronologicalAge) + 1.0
    val spanY = (maxY - minY).coerceAtLeast(2.0)

    val histCount = history.size.coerceAtLeast(1)
    val projCount = max(statusQuo.size, counterfactual.size)
    val totalSteps = max(histCount - 1 + projCount, 1)

    Canvas(modifier) {
        val padL = 8.dp.toPx()
        val padR = 8.dp.toPx()
        val padT = 10.dp.toPx()
        val padB = 10.dp.toPx()
        val plotW = (size.width - padL - padR).coerceAtLeast(1f)
        val plotH = (size.height - padT - padB).coerceAtLeast(1f)

        fun xOf(step: Int): Float = padL + (step.toFloat() / totalSteps) * plotW
        fun yOf(age: Double): Float = padT + ((maxY - age) / spanY).toFloat().coerceIn(0f, 1f) * plotH

        // Chronological age horizontal dashed reference
        val yAge = yOf(chronologicalAge)
        drawLine(
            color = extras.disabled.copy(alpha = 0.55f),
            start = Offset(padL, yAge),
            end = Offset(padL + plotW, yAge),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )

        // Vertical "Now" divider when projections are present
        val nowStep = histCount - 1
        if (projCount > 0) {
            val xNow = xOf(nowStep)
            drawLine(
                color = scheme.outlineVariant,
                start = Offset(xNow, padT),
                end = Offset(xNow, padT + plotH),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // Observed weekly CHA curve
        if (history.size >= 2) {
            val path = Path()
            history.forEachIndexed { idx, pt ->
                val x = xOf(idx)
                val y = yOf(pt.healthAge)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = scheme.primary,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        history.forEachIndexed { idx, pt ->
            drawCircle(
                color = scheme.primary,
                radius = if (pt.validWeek) 3.dp.toPx() else 2.dp.toPx(),
                center = Offset(xOf(idx), yOf(pt.healthAge)),
            )
        }

        val anchorAge = history.lastOrNull()?.healthAge
            ?: statusQuo.firstOrNull()?.healthAge
            ?: chronologicalAge

        // Future A (status quo) dashed curve
        if (statusQuo.isNotEmpty()) {
            val path = Path().apply {
                moveTo(xOf(nowStep), yOf(anchorAge))
                statusQuo.forEachIndexed { idx, pt ->
                    lineTo(xOf(nowStep + idx + 1), yOf(pt.healthAge))
                }
            }
            drawPath(
                path = path,
                color = extras.hrv,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                ),
            )
        }

        // Future B (selected counterfactual) solid curve
        if (counterfactual.isNotEmpty()) {
            val path = Path().apply {
                moveTo(xOf(nowStep), yOf(anchorAge))
                counterfactual.forEachIndexed { idx, pt ->
                    lineTo(xOf(nowStep + idx + 1), yOf(pt.healthAge))
                }
            }
            drawPath(
                path = path,
                color = extras.positive,
                style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            val endPt = counterfactual.last()
            drawCircle(
                color = extras.positive,
                radius = 4.dp.toPx(),
                center = Offset(xOf(nowStep + counterfactual.size), yOf(endPt.healthAge)),
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, colour: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(colour))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun PathwayOptionCard(
    index: Int,
    option: CounterfactualOption,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = MaterialTheme.extras
    val border = if (selected) BorderStroke(1.5.dp, scheme.primary) else BorderStroke(1.dp, scheme.outlineVariant)
    val fill = if (selected) scheme.primaryContainer.copy(alpha = 0.25f) else scheme.surfaceContainer

    Surface(
        color = fill,
        border = border,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clickable(onClick = onSelect),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Option $index · ${option.kind.label}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                DeltaChip(deltaYears = option.chaDeltaVsStatusQuo, suffix = "in 12 wk")
            }
            Text(
                option.describeDeltas(),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    "→ ${"%.1f".format(option.projectedCha)} yr",
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.positive,
                    fontWeight = FontWeight.SemiBold,
                )
                option.projectedRhr?.let { rhr ->
                    Text(
                        "RHR ${"%.1f".format(rhr)} bpm",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Effort ${"%.2f".format(option.effort)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.disabled,
                )
                if (option.withinHistoricalPeak) {
                    Text(
                        "· within peak",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.positive,
                    )
                }
            }
        }
    }
}

@Composable
private fun SimulatorSliderRow(
    label: String,
    valueText: String,
    hint: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                valueText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.extras.disabled,
        )
    }
}

@Composable
private fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(50),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
        )
    }
}

@Composable
private fun HeightPickerDialog(
    currentCm: Int?,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit,
) {
    var text by remember { mutableStateOf(currentCm?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Height (cm)") },
        text = {
            Column {
                Text(
                    "Optional — used only to calculate BMI inside the exported coaching prompt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { s -> text = s.filter { it.isDigit() }.take(3) },
                    label = { Text("Centimetres (100–250)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = text.toIntOrNull()?.takeIf { it in 100..250 }
                    onSave(parsed)
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ------------------------------------------------------------------ helpers

private fun openBirthYearPicker(
    context: Context,
    profile: FitnessProfile,
    onPicked: (FitnessProfile) -> Unit,
) {
    val now = LocalDate.now()
    DatePickerDialog(
        context,
        { _, year, _, _ -> onPicked(profile.copy(birthYear = year)) },
        profile.birthYear ?: (now.year - 30),
        0,
        1,
    ).show()
}

private fun formatWhole(value: Double): String =
    NumberFormat.getIntegerInstance().format(value.roundToInt())

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("G-Age Coaching Prompt", text))
}

private fun sharePrompt(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "12-Week Counterfactual Health Coaching Plan (G-Age)")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Send coaching prompt to…"))
    }
}
