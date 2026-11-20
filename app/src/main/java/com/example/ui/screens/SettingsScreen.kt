package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.network.ApkDownloadState
import com.example.data.network.ApkUpdateInstaller
import com.example.data.network.GitHubReleaseChecker
import com.example.data.network.UpdateCheckResult
import com.example.data.repository.PolishCalendarCalculator
import com.example.model.PlanCategory
import com.example.model.PlanEntry
import com.example.model.SimpleDate
import com.example.model.ThemeMode
import com.example.notifications.NotificationHelper
import com.example.ui.components.SelectMainPlanDialog
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.MainUiState

@Composable
fun SettingsScreen(
    uiState: MainUiState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetNotifyScheduleChanges: (Boolean) -> Unit,
    onSetNotifyLessonEnd: (Boolean) -> Unit,
    onSetLessonEndMinutesBefore: (Int) -> Unit,
    onAddDirectorDay: (SimpleDate, String) -> Unit,
    onRemoveDirectorDay: (Long) -> Unit,
    onCheckChangesNow: () -> Unit,
    onSendTestNotification: () -> Unit,
    onPinEntry: (PlanEntry) -> Unit,
    onRefreshIndex: () -> Unit,
    onSetAutoCheckUpdates: (Boolean) -> Unit,
    onCheckUpdatesNow: () -> Unit,
    onDownloadAndInstallUpdate: (String, String) -> Unit,
    onInstallDownloadedApk: () -> Unit,
    onClearCache: () -> Unit,
    onClearChangesLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }
    var showAddDirectorDialog by remember { mutableStateOf(false) }
    var showSelectMainPlanDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            onSetNotifyLessonEnd(true)
        }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (ApkUpdateInstaller.canInstallPackages(context)) {
            onInstallDownloadedApk()
        }
    }

    if (showAddDirectorDialog) {
        AddDirectorDayDialog(
            onDismiss = { showAddDirectorDialog = false },
            onConfirm = { date, name ->
                onAddDirectorDay(date, name)
                showAddDirectorDialog = false
            }
        )
    }

    if (showSelectMainPlanDialog) {
        SelectMainPlanDialog(
            indexEntries = uiState.indexEntries,
            currentPinnedHref = uiState.preferences.pinnedPlanHref,
            initialCategory = uiState.preferences.pinnedPlanCategory ?: PlanCategory.CLASS,
            onSelectAndPin = onPinEntry,
            onRefreshIndex = onRefreshIndex,
            onDismiss = { showSelectMainPlanDialog = false }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Ustawienia",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        // Theme Selector (Jasny / Ciemny / Systemowy)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Wygląd aplikacji", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.preferences.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.labelPl) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_chip_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // Pinned Plan Selection (Uczeń / Nauczyciel)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Mój główny plan", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = uiState.preferences.pinnedPlanEntry?.let { entry ->
                                    val rolePrefix = when (entry.category) {
                                        PlanCategory.CLASS -> "Plan klasy (Uczeń)"
                                        PlanCategory.TEACHER -> "Plan nauczyciela"
                                        PlanCategory.ROOM -> "Plan sali"
                                    }
                                    "$rolePrefix: ${entry.name}"
                                } ?: "Nie wybrano (klasa lub nauczyciel)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { showSelectMainPlanDialog = true },
                        modifier = Modifier.testTag("settings_select_main_plan_btn")
                    ) {
                        Text(if (uiState.preferences.pinnedPlanEntry != null) "Zmień" else "Wybierz")
                    }
                }
            }
        }

        // Push Notifications & Lesson-End Alerts
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Powiadomienia push", style = MaterialTheme.typography.titleMedium)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("request_notification_permission_btn")
                        ) {
                            Text("Zezwól na powiadomienia systemowe")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Zmiany w planie lekcji", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Sprawdzaj w tle plan na zs5rybnik.pl/_plan i powiadamiaj o zmianach",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = uiState.preferences.notifyScheduleChanges,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                onSetNotifyScheduleChanges(enabled)
                            },
                            modifier = Modifier.testTag("switch_notify_changes")
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Powiadomienie o końcu lekcji", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Przypomnienie przed lub w momencie dzwonka na przerwę",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = uiState.preferences.notifyLessonEnd,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                onSetNotifyLessonEnd(enabled)
                            },
                            modifier = Modifier.testTag("switch_notify_lesson_end")
                        )
                    }

                    if (uiState.preferences.notifyLessonEnd) {
                        Text(
                            text = "Kiedy wysłać alert?",
                            style = MaterialTheme.typography.labelLarge
                        )
                        val options = listOf(0 to "Na dzwonek", 1 to "1 min przed", 3 to "3 min przed", 5 to "5 min przed")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            options.forEach { (mins, label) ->
                                FilterChip(
                                    selected = uiState.preferences.lessonEndMinutesBefore == mins,
                                    onClick = { onSetLessonEndMinutesBefore(mins) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("lesson_end_lead_$mins")
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCheckChangesNow,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("check_changes_now_btn")
                        ) {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sprawdź plan")
                        }
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onSendTestNotification()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_notification_btn")
                        ) {
                            Text("Test alertu")
                        }
                    }
                }
            }
        }

        // Director's Free Days Management
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dni dyrektorskie (wolne)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Uwzględniane w odliczaniu do wolnego i wakacji",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { showAddDirectorDialog = true },
                            modifier = Modifier.testTag("settings_add_director_day_btn")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Dodaj")
                        }
                    }

                    if (uiState.directorFreeDays.isEmpty()) {
                        Text(
                            text = "Brak dodanych dni dyrektorskich.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.directorFreeDays.forEach { day ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(day.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        PolishCalendarCalculator.formatDatePl(day.date),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (day.directorDayId != null) {
                                    IconButton(onClick = { onRemoveDirectorDay(day.directorDayId) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Usuń dzień dyrektorski",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // GitHub Releases Update & Direct APK Install
        item {
            val updateResult = uiState.updateCheckResult
            val downloadState = uiState.apkDownloadState
            val apkUrl = (updateResult as? UpdateCheckResult.UpdateAvailable)?.release?.apkDownloadUrl
            val targetReleaseWebUrl = when (updateResult) {
                is UpdateCheckResult.UpdateAvailable -> updateResult.release.htmlUrl
                is UpdateCheckResult.UpToDate -> updateResult.release.htmlUrl
                is UpdateCheckResult.NoReleasesPublished -> updateResult.repoUrl
                else -> uiState.preferences.lastSyncedGithubUrl ?: GitHubReleaseChecker.RELEASES_WEB_URL
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Aktualizacje (GitHub)", style = MaterialTheme.typography.titleMedium)
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "v${uiState.appVersionName}",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    val statusText = when (updateResult) {
                        is UpdateCheckResult.UpdateAvailable ->
                            "Dostępna nowa wersja: v${updateResult.release.normalizedVersion} (obecna: v${updateResult.localVersion})"
                        is UpdateCheckResult.UpToDate ->
                            "Wersja zsynchronizowana z GitHubem (v${updateResult.release.normalizedVersion})"
                        is UpdateCheckResult.NoReleasesPublished ->
                            "Brak opublikowanych wydań na GitHubie • wersja lokalna v${updateResult.localVersion}"
                        is UpdateCheckResult.Error ->
                            "Błąd sprawdzania: ${updateResult.message}"
                        null -> uiState.preferences.lastSyncedGithubVersion?.let {
                            "Ostatnio zsynchronizowana wersja GitHub: v${GitHubReleaseChecker.normalizeVersion(it)}"
                        } ?: "Zainstalowana wersja: v${uiState.appVersionName} (kod ${uiState.appVersionCode})"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (updateResult is UpdateCheckResult.UpdateAvailable) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    if (updateResult is UpdateCheckResult.UpdateAvailable && updateResult.release.releaseNotes.isNotBlank()) {
                        Text(
                            text = updateResult.release.releaseNotes.take(220),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Direct APK download progress / permission / install state
                    when (downloadState) {
                        is ApkDownloadState.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (downloadState.progress != null) {
                                    LinearProgressIndicator(
                                        progress = { downloadState.progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    val percent = (downloadState.progress * 100).toInt()
                                    Text(
                                        text = "Pobieranie APK: $percent% (${ApkUpdateInstaller.formatBytesMb(downloadState.downloadedBytes)} / ${ApkUpdateInstaller.formatBytesMb(downloadState.totalBytes)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text(
                                        text = "Pobieranie APK: ${ApkUpdateInstaller.formatBytesMb(downloadState.downloadedBytes)}...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        is ApkDownloadState.ReadyToInstall -> {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val needsPermission = downloadState.needsInstallPermission ||
                                        !ApkUpdateInstaller.canInstallPackages(context)
                                    Text(
                                        text = if (needsPermission) {
                                            "Plik APK v${downloadState.versionTag} został pobrany. Zezwól na instalację aplikacji z tego źródła, aby kontynuować."
                                        } else {
                                            "Pobrano pakiet APK v${downloadState.versionTag}. Możesz uruchomić instalację bezpośrednio."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Button(
                                        onClick = {
                                            if (ApkUpdateInstaller.canInstallPackages(context)) {
                                                onInstallDownloadedApk()
                                            } else {
                                                unknownSourcesLauncher.launch(
                                                    ApkUpdateInstaller.buildInstallPermissionIntent(context)
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("install_downloaded_apk_btn")
                                    ) {
                                        Icon(
                                            imageVector = if (needsPermission) Icons.Rounded.Security else Icons.Rounded.InstallMobile,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (needsPermission) {
                                                "Zezwól na instalację i zainstaluj"
                                            } else {
                                                "Zainstaluj aktualizację"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        is ApkDownloadState.Error -> {
                            Text(
                                text = "Błąd aktualizacji: ${downloadState.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        ApkDownloadState.Idle -> Unit
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatyczne sprawdzanie aktualizacji przy uruchomieniu",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Repozytorium: github.com/dniesobski/ZS5-Assistant",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = uiState.preferences.autoCheckUpdatesOnStartup,
                            onCheckedChange = onSetAutoCheckUpdates,
                            modifier = Modifier.testTag("switch_auto_check_updates")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCheckUpdatesNow,
                            enabled = !uiState.isCheckingUpdates && downloadState !is ApkDownloadState.Downloading,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("check_updates_now_btn")
                        ) {
                            if (uiState.isCheckingUpdates) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sprawdź teraz")
                            }
                        }

                        if (updateResult is UpdateCheckResult.UpdateAvailable && apkUrl != null) {
                            Button(
                                onClick = {
                                    onDownloadAndInstallUpdate(apkUrl, updateResult.release.normalizedVersion)
                                },
                                enabled = downloadState !is ApkDownloadState.Downloading,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("download_install_apk_btn")
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (downloadState is ApkDownloadState.Downloading) {
                                        "Pobieranie..."
                                    } else {
                                        "Aktualizuj v${updateResult.release.normalizedVersion}"
                                    }
                                )
                            }
                        } else {
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetReleaseWebUrl))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_github_release_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("GitHub Release")
                            }
                        }
                    }
                }
            }
        }

        // Schedule Changes Log & Cache Info
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Historia wykrytych zmian", style = MaterialTheme.typography.titleMedium)
                        if (uiState.scheduleChanges.isNotEmpty()) {
                            TextButton(onClick = onClearChangesLog) {
                                Text("Wyczyść")
                            }
                        }
                    }

                    if (uiState.scheduleChanges.isEmpty()) {
                        Text(
                            text = "Nie wykryto jeszcze żadnych zmian pomiędzy kolejnymi synchronizacjami planu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.scheduleChanges.take(8).forEach { change ->
                            Text(
                                text = "• ${change.planTitle}: ${change.summary}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "Źródło danych: http://zs5rybnik.pl/_plan (Optivum VULCAN)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onClearCache,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Wyczyść pamięć podręczną planów")
                    }
                }
            }
        }
    }
}
