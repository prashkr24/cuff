package com.srineere.airbridge.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.srineere.airbridge.CalendarNotificationListener
import com.srineere.airbridge.CalendarRelayService
import com.srineere.airbridge.ui.theme.extras

/** How much a missing grant costs: the relay, its reliability, or a nicety. */
internal enum class Need { Required, Recommended, Optional }

internal data class Grant(
    val title: String,
    val body: String,
    val granted: Boolean,
    val need: Need,
    val fix: () -> Unit,
)

/**
 * Android hands these out one at a time, each on a screen of its own, and
 * several of them are the difference between a band that buzzes and one that
 * stays quiet. So they get a screen rather than a line of fine print — but
 * only the ones that are still missing ask for anything.
 */
@Composable
internal fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val grants = rememberGrants(context)
    val missing = grants.filter { !it.granted }
    val blocking = missing.count { it.need == Need.Required }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Permissions", onBack = onBack)

        Text(
            when {
                blocking > 0 -> "$blocking thing${if (blocking == 1) "" else "s"} to allow before the relay can reach your band. " +
                    "Each one opens a system screen — come back here after."
                missing.isEmpty() -> "Everything is allowed. Nothing you see here leaves your phone."
                else -> "The relay can run. What is left below makes it more reliable."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )

        // The first thing still missing is the one being asked for; the rest
        // wait their turn rather than shouting at once.
        val next = missing.firstOrNull()
        Box(Modifier.padding(top = 14.dp))
        grants.forEach { grant -> GrantCard(grant, current = grant === next) }

        Text(
            "Air Bridge talks to the band directly over Bluetooth. No account, no server, " +
                "nothing uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun GrantCard(grant: Grant, current: Boolean) {
    val scale by animateFloatAsState(
        if (current) 1f else 0.985f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "grantCard",
    )
    Surface(
        color = if (current) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (current) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (current) MaterialTheme.extras.accentOutline else MaterialTheme.colorScheme.outline,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).scale(scale),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Marker(grant.granted)
                Column(Modifier.weight(1f)) {
                    Text(grant.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        grant.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (current) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            if (!grant.granted) {
                Button(
                    onClick = grant.fix,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                ) {
                    Text(if (grant.need == Need.Optional) "Allow (optional)" else "Allow")
                }
            }
        }
    }
}

@Composable
private fun Marker(granted: Boolean) {
    if (granted) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "Allowed",
                tint = MaterialTheme.extras.positive,
                modifier = Modifier.size(17.dp),
            )
        }
    } else {
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

/**
 * Counts the times this screen has come back. Android changes permissions on
 * screens of its own, so returning is the only moment we can trust to look
 * again.
 */
@Composable
internal fun resumeTicks(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

@Composable
private fun rememberGrants(context: Context): List<Grant> {
    val resumes = resumeTicks()
    // A permission dialog resumes us too, but not on every OEM build, so the
    // launcher's own answer counts as a reason to look again.
    var answered by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { answered++ }
    return remember(resumes, answered) { grantsFor(context) { permission -> ask.launch(permission) } }
}

private fun grantsFor(context: Context, ask: (String) -> Unit): List<Grant> = buildList {
    add(
        Grant(
            title = "Read your notifications",
            body = "So we can tell a call from a group chat. Nothing leaves your phone.",
            granted = CalendarRelayService.hasAccess(context) && CalendarNotificationListener.connected,
            need = Need.Required,
        ) { context.openNotificationAccess() }
    )
    if (Build.VERSION.SDK_INT >= 31) {
        add(
            Grant(
                title = "Reach your band",
                body = "Nearby devices, so we can talk to the Air over Bluetooth. " +
                    "We never scan for anything else.",
                granted = context.has(Manifest.permission.BLUETOOTH_CONNECT),
                need = Need.Required,
            ) { ask(Manifest.permission.BLUETOOTH_CONNECT) }
        )
    }
    if (Build.VERSION.SDK_INT >= 33) {
        add(
            Grant(
                title = "Show the relay's notification",
                body = "The relay runs inside one notification — it is how you see it is on, " +
                    "and how you silence a ring.",
                granted = context.has(Manifest.permission.POST_NOTIFICATIONS),
                need = Need.Required,
            ) { ask(Manifest.permission.POST_NOTIFICATIONS) }
        )
    }
    add(
        Grant(
            title = "Keep running in the background",
            body = "Without this, Android stops us once the screen has been off a while — " +
                "and the band goes quiet.",
            granted = CalendarRelayService.batteryExempt(context),
            need = Need.Recommended,
        ) { context.openBatterySettings() }
    )
    add(
        Grant(
            title = "Your contacts",
            body = "Only to put a name on a number. Skip it and you will see numbers instead.",
            granted = context.has(Manifest.permission.READ_CONTACTS),
            need = Need.Optional,
        ) { ask(Manifest.permission.READ_CONTACTS) }
    )
}

private fun Context.has(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun Context.openNotificationAccess() {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Android 11 and up can open straight onto our own switch.
    if (Build.VERSION.SDK_INT >= 30) {
        intent.putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(this, CalendarNotificationListener::class.java).flattenToString(),
        )
    }
    runCatching { startActivity(intent) }.onFailure {
        runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }
}

private fun Context.openBatterySettings() {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Some OEM builds refuse the direct request; the full list always opens.
    runCatching { startActivity(direct) }.onFailure {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** What the relay still needs before Start can mean anything. */
internal fun blockingGrants(context: Context): Int =
    grantsFor(context) {}.count { !it.granted && it.need == Need.Required }
