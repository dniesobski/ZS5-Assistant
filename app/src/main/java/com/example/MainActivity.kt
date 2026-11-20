package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.network.ApkDownloadState
import com.example.data.network.ApkUpdateInstaller
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
    val context = LocalContext.current
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (ApkUpdateInstaller.canInstallPackages(context)) {
            viewModel.installDownloadedApk()
        }
    }

    uiState.startupUpdatePrompt?.let { available ->
        val downloadState = uiState.apkDownloadState
        val isDownloading = downloadState is ApkDownloadState.Downloading
        AlertDialog(
            onDismissRequest = {
                if (!isDownloading) viewModel.dismissStartupUpdatePrompt()
            },
            title = { Text("Dostępna aktualizacja v${available.release.normalizedVersion}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val notes = available.release.releaseNotes.takeIf { it.isNotBlank() }
                        ?.let { "\n\n${it.take(300)}" } ?: ""
                    Text(
                        "Opublikowano nową wersję aplikacji ZS5 Assistant (obecna: v${available.localVersion}).$notes"
                    )
                    when (downloadState) {
                        is ApkDownloadState.Downloading -> {
                            if (downloadState.progress != null) {
                                LinearProgressIndicator(
                                    progress = { downloadState.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Pobieranie: ${(downloadState.progress * 100).toInt()}% (${ApkUpdateInstaller.formatBytesMb(downloadState.downloadedBytes)} / ${ApkUpdateInstaller.formatBytesMb(downloadState.totalBytes)})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = "Pobieranie pakietu APK...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        is ApkDownloadState.ReadyToInstall -> {
                            Text(
                                text = if (downloadState.needsInstallPermission || !ApkUpdateInstaller.canInstallPackages(context)) {
                                    "Pobrano plik APK. Zezwól na instalację z tego źródła, aby zainstalować aktualizację."
                                } else {
                                    "Pakiet APK gotowy do instalacji."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is ApkDownloadState.Error -> {
                            Text(
                                text = downloadState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        ApkDownloadState.Idle -> Unit
                    }
                }
            },
            confirmButton = {
                when {
                    downloadState is ApkDownloadState.ReadyToInstall -> {
                        Button(
                            onClick = {
                                if (ApkUpdateInstaller.canInstallPackages(context)) {
                                    viewModel.installDownloadedApk()
                                } else {
                                    unknownSourcesLauncher.launch(
                                        ApkUpdateInstaller.buildInstallPermissionIntent(context)
                                    )
                                }
                            },
                            modifier = Modifier.testTag("startup_install_apk_btn")
                        ) {
                            Text(
                                if (downloadState.needsInstallPermission || !ApkUpdateInstaller.canInstallPackages(context)) {
                                    "Zezwól i zainstaluj"
                                } else {
                                    "Zainstaluj teraz"
                                }
                            )
                        }
                    }
                    available.release.apkDownloadUrl != null -> {
                        Button(
                            onClick = {
                                viewModel.downloadAndInstallUpdate(
                                    apkUrl = available.release.apkDownloadUrl,
                                    versionTag = available.release.normalizedVersion
                                )
                            },
                            enabled = !isDownloading,
                            modifier = Modifier.testTag("startup_download_install_btn")
                        ) {
                            Text(if (isDownloading) "Pobieranie..." else "Pobierz i zainstaluj")
                        }
                    }
                    else -> {
                        Button(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(available.release.htmlUrl)))
                            viewModel.dismissStartupUpdatePrompt()
                        }) {
                            Text("Otwórz GitHub")
                        }
                    }
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { viewModel.dismissStartupUpdatePrompt() }) {
                        Text("Później")
                    }
                }
            }
        )
    }

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
                            onAddDirectorDay = { startDate, endDate, name ->
                                viewModel.addDirectorFreeDay(startDate, endDate, name)
                            },
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
                            onAddDirectorDay = { startDate, endDate, name ->
                                viewModel.addDirectorFreeDay(startDate, endDate, name)
                            },
                            onRemoveDirectorDay = { viewModel.removeDirectorFreeDay(it) },
                            onCheckChangesNow = { viewModel.syncPinnedPlanNow() },
                            onSendTestNotification = { viewModel.sendTestNotification() },
                            onPinEntry = { viewModel.pinPlan(it) },
                            onRefreshIndex = { viewModel.refreshIndexAndPinnedPlan() },
                            onSetAutoCheckUpdates = { viewModel.setAutoCheckUpdatesOnStartup(it) },
                            onCheckUpdatesNow = { viewModel.checkForAppUpdates(isAutomatic = false) },
                            onDownloadAndInstallUpdate = { url, tag ->
                                viewModel.downloadAndInstallUpdate(url, tag)
                            },
                            onInstallDownloadedApk = { viewModel.installDownloadedApk() },
                            onClearCache = { viewModel.clearPlanCache() },
                            onClearChangesLog = { viewModel.clearChangesLog() }
                        )
                    }
                }
            }
        }
    }
}
