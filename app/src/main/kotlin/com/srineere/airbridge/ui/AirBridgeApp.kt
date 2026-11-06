package com.srineere.airbridge.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.srineere.airbridge.health.MorningMoment
import com.srineere.airbridge.ui.theme.extras

/**
 * The tabs. Health is first in the bar, as it was always drawn, but the app
 * opens on Health: the relay runs without being looked at, and the readings
 * are the half of this app anyone opens it to see.
 */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Health("health", "Health", Icons.Outlined.MonitorHeart),
    Alerts("alerts", "Alerts", Icons.Outlined.Vibration),
    Device("device", "Device", Icons.Outlined.Watch),
}

@Composable
fun AirBridgeApp(openRoute: String? = null) {
    val context = LocalContext.current
    WarmInstalledApps(context)
    val state = rememberRelayState(context)
    val health = rememberHealthState()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination

    // Where a widget sent us.
    //
    // Navigated once per value rather than once per composition: the same
    // widget tapped twice should land on its screen twice, and a route that
    // stayed in a remembered flag would only work the first time. Anything
    // this build does not know is ignored, because a widget placed by an
    // older version can name a screen that has since been renamed, and
    // crashing on it is a worse answer than opening the Health tab.
    androidx.compose.runtime.LaunchedEffect(openRoute) {
        val route = openRoute ?: return@LaunchedEffect
        if (route == Tab.Health.route) return@LaunchedEffect
        runCatching { nav.navigate(route) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            // Only shown on the tabs themselves; a pushed screen takes the
            // whole height and comes back with its own arrow.
            val onTab = Tab.entries.any { tab ->
                current?.hierarchy?.any { it.route == tab.route } == true
            }
            if (onTab) {
                NavigationBar(containerColor = MaterialTheme.extras.navSurface) {
                    Tab.entries.forEach { tab ->
                        val selected = current?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (selected) return@NavigationBarItem
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { insets ->
        CompositionLocalProvider(
            LocalRelayState provides state,
            LocalHealthState provides health,
        ) {
            NavHost(
                navController = nav,
                startDestination = Tab.Health.route,
                modifier = Modifier.fillMaxSize().padding(insets),
                // Tabs cross-fade; pushed screens slide, set per route below.
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(180)) },
            ) {
                composable(Tab.Health.route) { HealthScreen(onOpen = nav::navigate) }
                composable(Tab.Alerts.route) { AlertsScreen(onOpen = nav::navigate) }
                composable(Tab.Device.route) { DeviceScreen(onOpen = nav::navigate) }

                pushed("apps") { AppsScreen(onBack = nav::popBackStack, onOpen = nav::navigate) }
                pushed("calls") { CallsScreen(onBack = nav::popBackStack, onOpen = nav::navigate) }
                pushed("people") { PeopleScreen(onBack = nav::popBackStack, onOpen = nav::navigate) }
                pushed("activity") { ActivityScreen(onBack = nav::popBackStack) }
                pushed("schedule") { ScheduleScreen(onBack = nav::popBackStack) }
                pushed(
                    route = "person/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    PersonScreen(
                        id = entry.arguments?.getString("id").orEmpty(),
                        onBack = nav::popBackStack,
                        onOpen = nav::navigate,
                    )
                }
                pushed("contacts") { ContactsScreen(onBack = nav::popBackStack, onOpen = nav::navigate) }
                pushed(
                    route = "contacts/{source}",
                    arguments = listOf(navArgument("source") { type = NavType.StringType }),
                ) { entry ->
                    ContactsScreen(
                        onBack = nav::popBackStack,
                        onOpen = nav::navigate,
                        initialSource = entry.arguments?.getString("source"),
                    )
                }
                pushed(
                    route = "callers/{group}",
                    arguments = listOf(navArgument("group") { type = NavType.StringType }),
                ) { entry ->
                    CallerListScreen(
                        group = CallerGroup.of(entry.arguments?.getString("group")),
                        onBack = nav::popBackStack,
                        onOpen = nav::navigate,
                    )
                }
                pushed(
                    route = "callers/{group}/{scope}",
                    arguments = listOf(
                        navArgument("group") { type = NavType.StringType },
                        navArgument("scope") { type = NavType.StringType },
                    ),
                ) { entry ->
                    CallerListScreen(
                        group = CallerGroup.of(entry.arguments?.getString("group")),
                        onBack = nav::popBackStack,
                        onOpen = nav::navigate,
                        packageName = entry.arguments?.getString("scope"),
                    )
                }
                pushed("healthheart") { HealthHeartScreen(onBack = nav::popBackStack) }
                pushed("healthenergy") {
                    HealthEnergyScreen(onBack = nav::popBackStack, onOpen = nav::navigate)
                }
                pushed("healthsleep") {
                    HealthSleepScreen(onBack = nav::popBackStack, onOpen = nav::navigate)
                }
                pushed("export") { HealthExportScreen(onBack = nav::popBackStack) }
                pushed("journal") { JournalScreen(onBack = nav::popBackStack) }
                pushed("readiness") { ReadinessScreen(onBack = nav::popBackStack) }
                pushed("fitnessage") {
                    FitnessAgeScreen(onBack = nav::popBackStack, onOpen = nav::navigate)
                }
                pushed(MorningMoment.ROUTE) {
                    MorningScreen(onBack = nav::popBackStack, onOpen = nav::navigate)
                }
                pushed("healthsleepbank") {
                    HealthSleepBankScreen(onBack = nav::popBackStack)
                }
                pushed("healthsteps") {
                    HealthStepsScreen(onBack = nav::popBackStack, onOpen = nav::navigate)
                }
                pushed("healthvitals") {
                    HealthVitalsScreen(onBack = nav::popBackStack, onOpen = nav::navigate)
                }
                pushed("healthactivity") {
                    HealthActivityScreen(onBack = nav::popBackStack, onOpen = nav::navigate)
                }
                pushed("healthsessions") {
                    HealthSessionsScreen(onBack = nav::popBackStack, onOpen = nav::navigate)
                }
                pushed(
                    route = "healthsession/{start}",
                    arguments = listOf(navArgument("start") { type = NavType.LongType }),
                ) { entry ->
                    HealthSessionScreen(
                        startMillis = entry.arguments?.getLong("start") ?: 0L,
                        onBack = nav::popBackStack,
                    )
                }
                pushed("healthtiles") { HealthTilesScreen(onBack = nav::popBackStack) }
                pushed("healthgoals") { HealthGoalsScreen(onBack = nav::popBackStack) }
                pushed("healthhrv") { HealthHrvScreen(onBack = nav::popBackStack) }
                pushed("healthnutrition") {
                    HealthNutritionScreen(onBack = nav::popBackStack, onOpen = nav::navigate)
                }
                pushed("appearance") { AppearanceScreen(onBack = nav::popBackStack) }
                pushed("healthsavedfoods") {
                    HealthSavedFoodsScreen(onBack = nav::popBackStack)
                }
                pushed("healthfoodlog") {
                    HealthFoodLogScreen(onBack = nav::popBackStack, onOpen = nav::navigate)
                }
                pushed("healthlogfood") { HealthLogFoodScreen(onBack = nav::popBackStack, onOpen = nav::navigate) }
                pushed("healthbalance") { HealthBalanceDayScreen(onBack = nav::popBackStack) }
                pushed("healthtrend") {
                    HealthTrendScreen(
                        metric = TrendMetric.Resting,
                        onBack = nav::popBackStack,
                        onOpen = nav::navigate,
                    )
                }
                pushed("healthweight") {
                    HealthTrendScreen(TrendMetric.Weight, onBack = nav::popBackStack)
                }
                pushed("healthbreathing") {
                    HealthTrendScreen(TrendMetric.Breathing, onBack = nav::popBackStack)
                }
                pushed("healthskin") {
                    HealthTrendScreen(TrendMetric.Skin, onBack = nav::popBackStack)
                }
                pushed("widgets") { WidgetsScreen(onBack = nav::popBackStack) }
                pushed("permissions") { PermissionsScreen(onBack = nav::popBackStack) }
                pushed("about") { AboutScreen(onBack = nav::popBackStack) }
                pushed(
                    route = "appcallers/{packageName}",
                    arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
                ) { entry ->
                    AppCallersScreen(
                        packageName = entry.arguments?.getString("packageName").orEmpty(),
                        onBack = nav::popBackStack,
                        onOpen = nav::navigate,
                    )
                }
                pushed(
                    route = "vibration/{target}",
                    arguments = listOf(navArgument("target") { type = NavType.StringType }),
                ) { entry ->
                    VibrationScreen(
                        target = VibrationTarget.decode(entry.arguments?.getString("target").orEmpty()),
                        onBack = nav::popBackStack,
                    )
                }
            }
        }
    }
}

/**
 * Reads the installed apps off the main thread, and again when something is
 * installed or removed.
 *
 * Without this the first screen that wants an app's name or icon pays for the
 * whole list on the frame it is drawn, which on a phone with a hundred apps is
 * several dropped frames before anything appears.
 */
@Composable
private fun WarmInstalledApps(context: Context) {
    val installed by InstalledApps.current.collectAsState()
    LaunchedEffect(installed) {
        if (installed == null) withContext(Dispatchers.IO) { InstalledApps.load(context) }
    }
    DisposableEffect(context) {
        // Only while we are on screen. Installing something is the one thing
        // that makes the list wrong, and it cannot happen while this is up
        // without us being told.
        val changes = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                InstalledApps.invalidate()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(changes, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(changes) } }
    }
}

/** A screen pushed over a tab: slides in from the trailing edge, back reverses it. */
private fun NavGraphBuilder.pushed(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    arguments = arguments,
    enterTransition = {
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, spring(stiffness = 420f))
    },
    exitTransition = {
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, spring(stiffness = 420f))
    },
    popEnterTransition = {
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, spring(stiffness = 420f))
    },
    popExitTransition = {
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, spring(stiffness = 420f))
    },
) { entry ->
    // The tile's colour is provided to the route that tile opened, not to
    // whichever route is current. Keying it on the current back stack entry
    // meant that on the way back the entry flipped to the tab while this
    // screen was still sliding out, so it spent its last frames repainting
    // itself in the default colour.
    CompositionLocalProvider(
        LocalTileColour provides LocalHealthState.current.openedWith
            ?.takeIf { it.first == route }
            ?.second,
    ) {
        Box(Modifier.fillMaxSize()) { content(entry) }
    }
}
