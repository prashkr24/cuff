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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.srineere.airbridge.ui.theme.extras

/**
 * The tabs. Health is designed but not built, so the bar carries two for now
 * and the app opens on Notifications.
 */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Notifications("notifications", "Notifications", Icons.Outlined.Vibration),
    Device("device", "Device", Icons.Outlined.Watch),
}

@Composable
fun AirBridgeApp() {
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
        NavHost(
            navController = nav,
            startDestination = Tab.Notifications.route,
            modifier = Modifier.fillMaxSize().padding(insets),
            // Tabs cross-fade; pushed screens slide, which is set per route.
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(180)) },
        ) {
            composable(Tab.Notifications.route) { NotificationsScreen(onOpen = nav::navigate) }
            composable(Tab.Device.route) { DeviceScreen(onOpen = nav::navigate) }

            pushed("apps") { AppsScreen(onBack = nav::popBackStack) }
            pushed("people") { PeopleScreen(onBack = nav::popBackStack) }
            pushed("widgets") { WidgetsScreen(onBack = nav::popBackStack) }
        }
    }
}

/** A screen pushed over a tab: slides in from the trailing edge, back reverses it. */
private fun androidx.navigation.NavGraphBuilder.pushed(
    route: String,
    content: @Composable () -> Unit,
) = composable(
    route = route,
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
) { Box(Modifier.fillMaxSize()) { content() } }
