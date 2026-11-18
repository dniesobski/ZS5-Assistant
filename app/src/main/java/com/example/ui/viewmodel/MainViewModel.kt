package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.ScheduleChangeEntity
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.data.network.GitHubReleaseChecker
import com.example.data.network.TimetableRemoteDataSource
import com.example.data.network.UpdateCheckResult
import com.example.data.repository.PolishCalendarCalculator
import com.example.data.repository.TimetableRepository
import com.example.model.HolidayCountdownInfo
import com.example.model.HolidayEvent
import com.example.model.ParsedPlan
import com.example.model.PlanCategory
import com.example.model.PlanEntry
import com.example.model.SimpleDate
import com.example.model.TeacherLocationStatus
import com.example.model.ThemeMode
import com.example.notifications.LessonEndScheduler
import com.example.notifications.NotificationHelper
import com.example.notifications.ScheduleSyncWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class TeacherLocatorFilter(
    val query: String = "",
    val useCurrentTime: Boolean = true,
    val selectedDayOfWeek: Int = 1,
    val selectedLessonNumber: Int = 1,
    val onlyTeachingNow: Boolean = false
)

data class MainUiState(
    val preferences: UserPreferences = UserPreferences(),
    val indexEntries: List<PlanEntry> = emptyList(),
    val selectedPlanEntry: PlanEntry? = null,
    val viewedPlan: ParsedPlan? = null,
    val pinnedPlan: ParsedPlan? = null,
    val teacherLocations: List<TeacherLocationStatus> = emptyList(),
    val teacherLocatorFilter: TeacherLocatorFilter = TeacherLocatorFilter(),
    val cachedTeacherPlansCount: Int = 0,
    val totalTeachersCount: Int = 0,
    val scheduleChanges: List<ScheduleChangeEntity> = emptyList(),
    val unreadChangesCount: Int = 0,
    val directorFreeDays: List<HolidayEvent> = emptyList(),
    val holidayCountdown: HolidayCountdownInfo = PolishCalendarCalculator.calculateCountdown(
        PolishCalendarCalculator.today(),
        emptyList()
    ),
    val currentMinuteOfDay: Int = 8 * 60,
    val currentSecondOfDay: Int = 8 * 60 * 60,
    val todayDayOfWeek: Int = 1,
    val isSyncingIndex: Boolean = false,
    val isSyncingPlan: Boolean = false,
    val isSyncingTeachers: Boolean = false,
    val syncError: String? = null,
    val snackbarMessage: String? = null,
    val appVersionName: String = BuildConfig.VERSION_NAME,
    val appVersionCode: Int = BuildConfig.VERSION_CODE,
    val isCheckingUpdates: Boolean = false,
    val updateCheckResult: UpdateCheckResult? = null,
    val startupUpdatePrompt: UpdateCheckResult.UpdateAvailable? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.timetableDao()
    private val repository = TimetableRepository(dao, TimetableRemoteDataSource())
    private val prefsRepository = UserPreferencesRepository(application)
    private val githubChecker = GitHubReleaseChecker()

    private val selectedPlanEntryFlow = MutableStateFlow<PlanEntry?>(null)
    private val locatorFilterFlow = MutableStateFlow(
        TeacherLocatorFilter(
            selectedDayOfWeek = currentIsoDay().coerceIn(1, 5)
        )
    )
    private val clockTickFlow = MutableStateFlow(currentTimeSnapshot())
    private val syncFlagsFlow = MutableStateFlow(SyncFlags())

    private data class SyncFlags(
        val isSyncingIndex: Boolean = false,
        val isSyncingPlan: Boolean = false,
        val isSyncingTeachers: Boolean = false,
        val syncError: String? = null,
        val snackbarMessage: String? = null,
        val isCheckingUpdates: Boolean = false,
        val updateCheckResult: UpdateCheckResult? = null,
        val startupUpdatePrompt: UpdateCheckResult.UpdateAvailable? = null
    )

    private val pinnedPlanFlow = prefsRepository.preferencesFlow.flatMapLatest { prefs ->
        val href = prefs.pinnedPlanHref
        if (href != null) repository.observePlan(href) else flowOf(null)
    }

    private val viewedPlanFlow = selectedPlanEntryFlow.flatMapLatest { entry ->
        if (entry != null) repository.observePlan(entry.href) else flowOf(null)
    }

    private val coreDataFlow = combine(
        prefsRepository.preferencesFlow,
        repository.indexEntries,
        selectedPlanEntryFlow,
        viewedPlanFlow,
        pinnedPlanFlow
    ) { prefs, index, selectedEntry, viewedPlan, pinnedPlan ->
        CoreBundle(prefs, index, selectedEntry, viewedPlan, pinnedPlan)
    }

    private val secondaryDataFlow = combine(
        repository.cachedTeacherPlans,
        locatorFilterFlow,
        repository.scheduleChanges,
        repository.unreadChangesCount,
        repository.directorFreeDays
    ) { teacherPlans, locatorFilter, changes, unreadCount, directorDays ->
        SecondaryBundle(teacherPlans, locatorFilter, changes, unreadCount, directorDays)
    }

    val uiState: StateFlow<MainUiState> = combine(
        coreDataFlow,
        secondaryDataFlow,
        clockTickFlow,
        syncFlagsFlow
    ) { core, secondary, clock, flags ->
        val (todayDate, minuteOfDay, secondOfDay, isoDow) = clock
        val teachers = core.index.filter { it.category == PlanCategory.TEACHER }
        val effectiveDay = if (secondary.locatorFilter.useCurrentTime) {
            isoDow.coerceIn(1, 5)
        } else {
            secondary.locatorFilter.selectedDayOfWeek
        }
        val targetMinutes = if (secondary.locatorFilter.useCurrentTime) minuteOfDay else null
        val targetLessonNr = if (secondary.locatorFilter.useCurrentTime) null else secondary.locatorFilter.selectedLessonNumber

        val rawLocations = TimetableRepository.buildTeacherLocations(
            teachers = teachers,
            teacherPlans = secondary.teacherPlans,
            dayOfWeek = effectiveDay,
            targetMinutes = targetMinutes,
            targetLessonNumber = targetLessonNr
        )
        val query = secondary.locatorFilter.query.trim()
        val filteredLocations = rawLocations.filter { loc ->
            val matchesQuery = query.isBlank() ||
                loc.teacher.name.contains(query, ignoreCase = true) ||
                loc.currentLesson?.subject?.contains(query, ignoreCase = true) == true ||
                loc.currentLesson?.roomName?.contains(query, ignoreCase = true) == true ||
                loc.currentLesson?.className?.contains(query, ignoreCase = true) == true
            val matchesTeaching = !secondary.locatorFilter.onlyTeachingNow || loc.isCurrentlyTeaching
            matchesQuery && matchesTeaching
        }.sortedWith(
            compareByDescending<TeacherLocationStatus> { it.isCurrentlyTeaching }
                .thenByDescending { it.hasCachedPlan }
                .thenBy { it.teacher.name }
        )

        val countdown = PolishCalendarCalculator.calculateCountdown(todayDate, secondary.directorDays)

        MainUiState(
            preferences = core.prefs,
            indexEntries = core.index,
            selectedPlanEntry = core.selectedEntry ?: core.prefs.pinnedPlanEntry,
            viewedPlan = core.viewedPlan ?: core.pinnedPlan,
            pinnedPlan = core.pinnedPlan,
            teacherLocations = filteredLocations,
            teacherLocatorFilter = secondary.locatorFilter,
            cachedTeacherPlansCount = secondary.teacherPlans.size,
            totalTeachersCount = teachers.size,
            scheduleChanges = secondary.changes,
            unreadChangesCount = secondary.unreadCount,
            directorFreeDays = secondary.directorDays,
            holidayCountdown = countdown,
            currentMinuteOfDay = minuteOfDay,
            currentSecondOfDay = secondOfDay,
            todayDayOfWeek = isoDow,
            isSyncingIndex = flags.isSyncingIndex,
            isSyncingPlan = flags.isSyncingPlan,
            isSyncingTeachers = flags.isSyncingTeachers,
            syncError = flags.syncError,
            snackbarMessage = flags.snackbarMessage,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            isCheckingUpdates = flags.isCheckingUpdates,
            updateCheckResult = flags.updateCheckResult,
            startupUpdatePrompt = flags.startupUpdatePrompt
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    private data class CoreBundle(
        val prefs: UserPreferences,
        val index: List<PlanEntry>,
        val selectedEntry: PlanEntry?,
        val viewedPlan: ParsedPlan?,
        val pinnedPlan: ParsedPlan?
    )

    private data class SecondaryBundle(
        val teacherPlans: Map<String, ParsedPlan>,
        val locatorFilter: TeacherLocatorFilter,
        val changes: List<ScheduleChangeEntity>,
        val unreadCount: Int,
        val directorDays: List<HolidayEvent>
    )

    init {
        NotificationHelper.ensureChannels(getApplication())
        ScheduleSyncWorker.schedulePeriodic(getApplication())

        // Live clock ticker every second for accurate lesson & break countdowns
        viewModelScope.launch {
            while (true) {
                clockTickFlow.value = currentTimeSnapshot()
                delay(1_000L)
            }
        }

        // Initial automatic sync on startup + optional GitHub release check
        viewModelScope.launch {
            val prefs = prefsRepository.getOnce()
            refreshIndexAndPinnedPlan(silentIfCached = false)
            if (prefs.pinnedPlanEntry != null && selectedPlanEntryFlow.value == null) {
                selectedPlanEntryFlow.value = prefs.pinnedPlanEntry
            }
            if (prefs.autoCheckUpdatesOnStartup) {
                checkForAppUpdates(isAutomatic = true)
            }
        }
    }

    fun refreshIndexAndPinnedPlan(silentIfCached: Boolean = false) {
        viewModelScope.launch {
            syncFlagsFlow.value = syncFlagsFlow.value.copy(isSyncingIndex = true, syncError = null)
            val idxRes = repository.syncIndex()
            idxRes.onFailure { err ->
                syncFlagsFlow.value = syncFlagsFlow.value.copy(
                    isSyncingIndex = false,
                    syncError = "Nie można połączyć z zs5rybnik.pl/_plan: ${err.localizedMessage ?: "brak połączenia"}"
                )
            }.onSuccess { entries ->
                syncFlagsFlow.value = syncFlagsFlow.value.copy(isSyncingIndex = false)
                val prefs = prefsRepository.getOnce()
                val target = prefs.pinnedPlanEntry ?: entries.firstOrNull { it.category == PlanCategory.CLASS }
                if (target != null) {
                    if (prefs.pinnedPlanEntry == null) {
                        prefsRepository.setPinnedPlan(target)
                    }
                    if (selectedPlanEntryFlow.value == null) {
                        selectedPlanEntryFlow.value = target
                    }
                    syncPlan(target, showSnackbarOnChange = true)
                }
            }
        }
    }

    fun openPlan(entry: PlanEntry, recordChanges: Boolean = false) {
        selectedPlanEntryFlow.value = entry
        viewModelScope.launch {
            syncPlan(entry, showSnackbarOnChange = recordChanges)
        }
    }

    fun openPlanByHref(href: String, fallbackName: String, category: PlanCategory) {
        val existing = uiState.value.indexEntries.firstOrNull { it.href == href }
            ?: PlanEntry(href = href, name = fallbackName, category = category)
        openPlan(existing, recordChanges = false)
    }

    fun pinPlan(entry: PlanEntry) {
        viewModelScope.launch {
            prefsRepository.setPinnedPlan(entry)
            selectedPlanEntryFlow.value = entry
            syncPlan(entry, showSnackbarOnChange = false)
            syncFlagsFlow.value = syncFlagsFlow.value.copy(
                snackbarMessage = "Ustawiono „${entry.name}” jako Twój główny plan"
            )
            rescheduleLessonAlarms()
        }
    }

    fun syncPinnedPlanNow() {
        val pinned = uiState.value.preferences.pinnedPlanEntry ?: run {
            refreshIndexAndPinnedPlan()
            return
        }
        viewModelScope.launch {
            syncPlan(pinned, showSnackbarOnChange = true)
        }
    }

    private suspend fun syncPlan(entry: PlanEntry, showSnackbarOnChange: Boolean) {
        syncFlagsFlow.value = syncFlagsFlow.value.copy(isSyncingPlan = true, syncError = null)
        val res = repository.syncPlanAndDetectChanges(entry, recordChanges = true)
        res.onSuccess { (plan, changes) ->
            prefsRepository.setLastSyncEpochMs(System.currentTimeMillis())
            syncFlagsFlow.value = syncFlagsFlow.value.copy(isSyncingPlan = false)

            val prefs = prefsRepository.getOnce()
            if (changes.isNotEmpty() && prefs.notifyScheduleChanges) {
                NotificationHelper.showScheduleChangeNotification(
                    context = getApplication(),
                    planTitle = plan.title,
                    summaries = changes.map { it.summary }
                )
                if (showSnackbarOnChange) {
                    syncFlagsFlow.value = syncFlagsFlow.value.copy(
                        snackbarMessage = "Wykryto ${changes.size} zmian w planie ${plan.title}"
                    )
                }
            }
            if (entry.href == prefs.pinnedPlanHref) {
                rescheduleLessonAlarms()
            }
        }.onFailure { err ->
            syncFlagsFlow.value = syncFlagsFlow.value.copy(
                isSyncingPlan = false,
                syncError = "Błąd synchronizacji planu ${entry.name}: ${err.localizedMessage ?: "błąd sieci"}"
            )
        }
    }

    fun syncAllTeachersForLocator() {
        viewModelScope.launch {
            syncFlagsFlow.value = syncFlagsFlow.value.copy(isSyncingTeachers = true, syncError = null)
            val res = repository.syncAllTeachers()
            res.onSuccess { count ->
                syncFlagsFlow.value = syncFlagsFlow.value.copy(
                    isSyncingTeachers = false,
                    snackbarMessage = "Zaktualizowano plany $count nauczycieli"
                )
            }.onFailure { err ->
                syncFlagsFlow.value = syncFlagsFlow.value.copy(
                    isSyncingTeachers = false,
                    syncError = err.localizedMessage ?: "Nie udało się pobrać planów nauczycieli"
                )
            }
        }
    }

    fun updateTeacherQuery(query: String) {
        locatorFilterFlow.value = locatorFilterFlow.value.copy(query = query)
    }

    fun updateLocatorTimeMode(useCurrentTime: Boolean) {
        locatorFilterFlow.value = locatorFilterFlow.value.copy(useCurrentTime = useCurrentTime)
    }

    fun updateLocatorDay(dayOfWeek: Int) {
        locatorFilterFlow.value = locatorFilterFlow.value.copy(
            useCurrentTime = false,
            selectedDayOfWeek = dayOfWeek.coerceIn(1, 5)
        )
    }

    fun updateLocatorLessonNumber(lessonNumber: Int) {
        locatorFilterFlow.value = locatorFilterFlow.value.copy(
            useCurrentTime = false,
            selectedLessonNumber = lessonNumber.coerceIn(0, 12)
        )
    }

    fun toggleOnlyTeachingNow(onlyTeaching: Boolean) {
        locatorFilterFlow.value = locatorFilterFlow.value.copy(onlyTeachingNow = onlyTeaching)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefsRepository.setThemeMode(mode)
        }
    }

    fun setNotifyScheduleChanges(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setNotifyScheduleChanges(enabled)
        }
    }

    fun setNotifyLessonEnd(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setNotifyLessonEnd(enabled)
            rescheduleLessonAlarms()
        }
    }

    fun setLessonEndMinutesBefore(minutes: Int) {
        viewModelScope.launch {
            prefsRepository.setLessonEndMinutesBefore(minutes)
            rescheduleLessonAlarms()
        }
    }

    fun sendTestNotification() {
        val mins = uiState.value.preferences.lessonEndMinutesBefore
        NotificationHelper.showTestNotification(getApplication(), mins)
        syncFlagsFlow.value = syncFlagsFlow.value.copy(
            snackbarMessage = "Wysłano testowe powiadomienie systemowe"
        )
    }

    fun markChangesRead() {
        viewModelScope.launch { repository.markAllChangesRead() }
    }

    fun clearChangesLog() {
        viewModelScope.launch { repository.clearScheduleChanges() }
    }

    fun addDirectorFreeDay(date: SimpleDate, name: String) {
        viewModelScope.launch {
            repository.addDirectorFreeDay(date, name)
            rescheduleLessonAlarms()
            syncFlagsFlow.value = syncFlagsFlow.value.copy(
                snackbarMessage = "Dodano dzień wolny: ${PolishCalendarCalculator.formatShortDate(date)}"
            )
        }
    }

    fun removeDirectorFreeDay(id: Long) {
        viewModelScope.launch {
            repository.removeDirectorFreeDay(id)
            rescheduleLessonAlarms()
        }
    }

    fun clearPlanCache() {
        viewModelScope.launch {
            repository.clearCachedPlans()
            syncFlagsFlow.value = syncFlagsFlow.value.copy(
                snackbarMessage = "Wyczyszczono lokalną pamięć podręczną planów"
            )
        }
    }

    fun consumeSnackbar() {
        syncFlagsFlow.value = syncFlagsFlow.value.copy(snackbarMessage = null)
    }

    fun clearError() {
        syncFlagsFlow.value = syncFlagsFlow.value.copy(syncError = null)
    }

    fun setAutoCheckUpdatesOnStartup(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setAutoCheckUpdatesOnStartup(enabled)
        }
    }

    fun dismissStartupUpdatePrompt() {
        syncFlagsFlow.value = syncFlagsFlow.value.copy(startupUpdatePrompt = null)
    }

    fun checkForAppUpdates(isAutomatic: Boolean = false) {
        viewModelScope.launch {
            syncFlagsFlow.value = syncFlagsFlow.value.copy(isCheckingUpdates = true)
            val result = githubChecker.checkLatestRelease(BuildConfig.VERSION_NAME)
            val now = System.currentTimeMillis()

            when (result) {
                is UpdateCheckResult.UpToDate -> {
                    prefsRepository.saveGithubSyncInfo(
                        version = result.release.normalizedVersion,
                        url = result.release.apkDownloadUrl ?: result.release.htmlUrl,
                        checkedAtMs = now
                    )
                    syncFlagsFlow.value = syncFlagsFlow.value.copy(
                        isCheckingUpdates = false,
                        updateCheckResult = result,
                        snackbarMessage = if (isAutomatic) null else {
                            "Aplikacja jest aktualna (v${result.localVersion} • GitHub v${result.release.normalizedVersion})"
                        }
                    )
                }
                is UpdateCheckResult.UpdateAvailable -> {
                    prefsRepository.saveGithubSyncInfo(
                        version = result.release.normalizedVersion,
                        url = result.release.apkDownloadUrl ?: result.release.htmlUrl,
                        checkedAtMs = now
                    )
                    syncFlagsFlow.value = syncFlagsFlow.value.copy(
                        isCheckingUpdates = false,
                        updateCheckResult = result,
                        startupUpdatePrompt = if (isAutomatic) result else syncFlagsFlow.value.startupUpdatePrompt,
                        snackbarMessage = if (isAutomatic) null else {
                            "Dostępna nowa wersja na GitHub: v${result.release.normalizedVersion}"
                        }
                    )
                }
                is UpdateCheckResult.NoReleasesPublished -> {
                    prefsRepository.saveGithubSyncInfo(
                        version = null,
                        url = result.repoUrl,
                        checkedAtMs = now
                    )
                    syncFlagsFlow.value = syncFlagsFlow.value.copy(
                        isCheckingUpdates = false,
                        updateCheckResult = result,
                        snackbarMessage = if (isAutomatic) null else {
                            "Brak opublikowanych wydań na GitHubie (wersja lokalna: v${result.localVersion})"
                        }
                    )
                }
                is UpdateCheckResult.Error -> {
                    syncFlagsFlow.value = syncFlagsFlow.value.copy(
                        isCheckingUpdates = false,
                        updateCheckResult = result,
                        snackbarMessage = if (isAutomatic) null else {
                            "Nie udało się sprawdzić aktualizacji: ${result.message}"
                        }
                    )
                }
            }
        }
    }

    private suspend fun rescheduleLessonAlarms() {
        val prefs = prefsRepository.getOnce()
        val pinned = prefs.pinnedPlanHref?.let { repository.getCachedPlanOnce(it) }
        val directorDays = dao.getDirectorFreeDaysOnce().map {
            HolidayEvent(
                date = PolishCalendarCalculator.fromEpochDay(it.epochDay),
                name = it.name,
                isDirectorDay = true,
                directorDayId = it.id
            )
        }
        LessonEndScheduler.rescheduleForToday(
            context = getApplication(),
            enabled = prefs.notifyLessonEnd,
            minutesBefore = prefs.lessonEndMinutesBefore,
            pinnedPlan = pinned,
            directorDays = directorDays
        )
    }

    private fun currentIsoDay(): Int {
        return PolishCalendarCalculator.dayOfWeekIso(PolishCalendarCalculator.today())
    }

    private data class ClockSnapshot(
        val date: SimpleDate,
        val minuteOfDay: Int,
        val secondOfDay: Int,
        val isoDayOfWeek: Int
    )

    private fun currentTimeSnapshot(): ClockSnapshot {
        val cal = Calendar.getInstance()
        val date = SimpleDate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH)
        )
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)
        val minuteOfDay = hour * 60 + minute
        val secondOfDay = minuteOfDay * 60 + second
        val dow = PolishCalendarCalculator.dayOfWeekIso(date)
        return ClockSnapshot(date, minuteOfDay, secondOfDay, dow)
    }
}
