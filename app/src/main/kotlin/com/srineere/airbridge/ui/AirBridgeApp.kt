package com.srineere.airbridge.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.srineere.airbridge.ui.theme.extras

/**
 * The tabs. Health is designed but not built, so the bar carries two for now
 * and the app opens on Alerts.
 */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Alerts("alerts", "Alerts", Icons.Outlined.Vibration),
    Device("device", "Device", Icons.Outlined.Watch),
}

@Composable
fun AirBridgeApp() {
    val state = rememberRelayState(LocalContext.current)
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination

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
        CompositionLocalProvider(LocalRelayState provides state) {
            NavHost(
                navController = nav,
                startDestination = Tab.Alerts.route,
                modifier = Modifier.fillMaxSize().padding(insets),
                // Tabs cross-fade; pushed screens slide, set per route below.
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(180)) },
            ) {
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
) { entry -> Box(Modifier.fillMaxSize()) { content(entry) } }
