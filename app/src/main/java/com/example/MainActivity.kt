package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.PlanCategory
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TeacherLocatorScreen
import com.example.ui.screens.TimetableScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.Zs5RybnikTheme
import com.example.ui.viewmodel.MainViewModel

private enum class AppDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TODAY("today", "Dzisiaj", Icons.Rounded.Today, Icons.Outlined.Today),
    PLANS("plans", "Plany", Icons.Rounded.ViewWeek, Icons.Outlined.ViewWeek),
    LOCATOR("locator", "Nauczyciele", Icons.Rounded.PersonSearch, Icons.Outlined.PersonSearch),
    CALENDAR("calendar", "Wolne", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    SETTINGS("settings", "Ustawienia", Icons.Rounded.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            Zs5RybnikTheme(themeMode = uiState.preferences.themeMode) {
                Zs5AppShell(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun Zs5AppShell(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppDestination.TODAY.route
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    val navigateToTab: (AppDestination) -> Unit = { dest ->
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        AppDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val badgeCount = if (dest == AppDestination.TODAY) uiState.unreadChangesCount else 0
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge { Text(badgeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    }
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail {
                        AppDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val badgeCount = if (dest == AppDestination.TODAY) uiState.unreadChangesCount else 0
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(dest) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge { Text(badgeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    }
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppDestination.TODAY.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(AppDestination.TODAY.route) {
                        TodayScreen(
                            uiState = uiState,
                            onRefresh = { viewModel.refreshIndexAndPinnedPlan() },
                            onToggleTheme = { viewModel.setThemeMode(it) },
                            onOpenPlansTab = { navigateToTab(AppDestination.PLANS) },
                            onOpenLocatorTab = { navigateToTab(AppDestination.LOCATOR) },
                            onOpenCalendarTab = { navigateToTab(AppDestination.CALENDAR) },
                            onOpenPlanHref = { href, name, cat ->
                                viewModel.openPlanByHref(href, name, cat)
                                navigateToTab(AppDestination.PLANS)
                            },
                            onPinEntry = { viewModel.pinPlan(it) },
                            onMarkChangesRead = { viewModel.markChangesRead() }
                        )
                    }

                    composable(AppDestination.PLANS.route) {
                        TimetableScreen(
                            uiState = uiState,
                            onSelectEntry = { viewModel.openPlan(it) },
                            onPinEntry = { viewModel.pinPlan(it) },
                            onOpenPlanHref = { href, name, cat ->
                                viewModel.openPlanByHref(href, name, cat)
                            },
                            onRefreshIndex = { viewModel.refreshIndexAndPinnedPlan() }
                        )
                    }

                    composable(AppDestination.LOCATOR.route) {
                        TeacherLocatorScreen(
                            uiState = uiState,
                            onQueryChange = { viewModel.updateTeacherQuery(it) },
                            onTimeModeChange = { viewModel.updateLocatorTimeMode(it) },
                            onSelectDay = { viewModel.updateLocatorDay(it) },
                            onSelectLessonNumber = { viewModel.updateLocatorLessonNumber(it) },
                            onToggleOnlyTeaching = { viewModel.toggleOnlyTeachingNow(it) },
                            onSyncAllTeachers = { viewModel.syncAllTeachersForLocator() },
                            onOpenTeacherPlan = { teacher ->
                                viewModel.openPlan(teacher)
                                navigateToTab(AppDestination.PLANS)
                            },
                            onPinTeacherPlan = { teacher -> viewModel.pinPlan(teacher) }
                        )
                    }

                    composable(AppDestination.CALENDAR.route) {
                        CalendarScreen(
                            uiState = uiState,
                            onAddDirectorDay = { date, name -> viewModel.addDirectorFreeDay(date, name) },
                            onRemoveDirectorDay = { viewModel.removeDirectorFreeDay(it) }
                        )
                    }

                    composable(AppDestination.SETTINGS.route) {
                        SettingsScreen(
                            uiState = uiState,
                            onSetThemeMode = { viewModel.setThemeMode(it) },
                            onSetNotifyScheduleChanges = { viewModel.setNotifyScheduleChanges(it) },
                            onSetNotifyLessonEnd = { viewModel.setNotifyLessonEnd(it) },
                            onSetLessonEndMinutesBefore = { viewModel.setLessonEndMinutesBefore(it) },
                            onAddDirectorDay = { date, name -> viewModel.addDirectorFreeDay(date, name) },
                            onRemoveDirectorDay = { viewModel.removeDirectorFreeDay(it) },
                            onCheckChangesNow = { viewModel.syncPinnedPlanNow() },
                            onSendTestNotification = { viewModel.sendTestNotification() },
                            onPinEntry = { viewModel.pinPlan(it) },
                            onRefreshIndex = { viewModel.refreshIndexAndPinnedPlan() },
                            onClearCache = { viewModel.clearPlanCache() },
                            onClearChangesLog = { viewModel.clearChangesLog() }
                        )
                    }
                }
            }
        }
    }
}
