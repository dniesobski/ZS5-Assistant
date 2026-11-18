package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.PlanCategory
import com.example.model.PlanEntry
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zs5_prefs")

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pinnedPlanHref: String? = null,
    val pinnedPlanName: String? = null,
    val pinnedPlanCategory: PlanCategory? = null,
    val notifyScheduleChanges: Boolean = true,
    val notifyLessonEnd: Boolean = false,
    val lessonEndMinutesBefore: Int = 3,
    val lastSyncEpochMs: Long = 0L,
    val autoCheckUpdatesOnStartup: Boolean = true,
    val lastSyncedGithubVersion: String? = null,
    val lastSyncedGithubUrl: String? = null,
    val lastGithubCheckEpochMs: Long = 0L
) {
    val pinnedPlanEntry: PlanEntry?
        get() = if (pinnedPlanHref != null && pinnedPlanName != null && pinnedPlanCategory != null) {
            PlanEntry(href = pinnedPlanHref, name = pinnedPlanName, category = pinnedPlanCategory)
        } else null
}

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PINNED_HREF = stringPreferencesKey("pinned_plan_href")
        val PINNED_NAME = stringPreferencesKey("pinned_plan_name")
        val PINNED_CATEGORY = stringPreferencesKey("pinned_plan_category")
        val NOTIFY_CHANGES = booleanPreferencesKey("notify_schedule_changes")
        val NOTIFY_LESSON_END = booleanPreferencesKey("notify_lesson_end")
        val LESSON_END_MINUTES = intPreferencesKey("lesson_end_minutes_before")
        val LAST_SYNC = longPreferencesKey("last_sync_epoch_ms")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates_startup")
        val LAST_GITHUB_VERSION = stringPreferencesKey("last_synced_github_version")
        val LAST_GITHUB_URL = stringPreferencesKey("last_synced_github_url")
        val LAST_GITHUB_CHECK = longPreferencesKey("last_github_check_epoch_ms")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        val cat = prefs[Keys.PINNED_CATEGORY]?.let { runCatching { PlanCategory.valueOf(it) }.getOrNull() }
        UserPreferences(
            themeMode = theme,
            pinnedPlanHref = prefs[Keys.PINNED_HREF],
            pinnedPlanName = prefs[Keys.PINNED_NAME],
            pinnedPlanCategory = cat,
            notifyScheduleChanges = prefs[Keys.NOTIFY_CHANGES] ?: true,
            notifyLessonEnd = prefs[Keys.NOTIFY_LESSON_END] ?: false,
            lessonEndMinutesBefore = prefs[Keys.LESSON_END_MINUTES] ?: 3,
            lastSyncEpochMs = prefs[Keys.LAST_SYNC] ?: 0L,
            autoCheckUpdatesOnStartup = prefs[Keys.AUTO_CHECK_UPDATES] ?: true,
            lastSyncedGithubVersion = prefs[Keys.LAST_GITHUB_VERSION],
            lastSyncedGithubUrl = prefs[Keys.LAST_GITHUB_URL],
            lastGithubCheckEpochMs = prefs[Keys.LAST_GITHUB_CHECK] ?: 0L
        )
    }

    suspend fun getOnce(): UserPreferences = preferencesFlow.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setPinnedPlan(entry: PlanEntry) {
        context.dataStore.edit {
            it[Keys.PINNED_HREF] = entry.href
            it[Keys.PINNED_NAME] = entry.name
            it[Keys.PINNED_CATEGORY] = entry.category.name
        }
    }

    suspend fun setNotifyScheduleChanges(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_CHANGES] = enabled }
    }

    suspend fun setNotifyLessonEnd(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_LESSON_END] = enabled }
    }

    suspend fun setLessonEndMinutesBefore(minutes: Int) {
        context.dataStore.edit { it[Keys.LESSON_END_MINUTES] = minutes.coerceIn(0, 15) }
    }

    suspend fun setLastSyncEpochMs(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNC] = timestamp }
    }

    suspend fun setAutoCheckUpdatesOnStartup(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CHECK_UPDATES] = enabled }
    }

    suspend fun saveGithubSyncInfo(version: String?, url: String?, checkedAtMs: Long) {
        context.dataStore.edit {
            if (version != null) it[Keys.LAST_GITHUB_VERSION] = version
            if (url != null) it[Keys.LAST_GITHUB_URL] = url
            it[Keys.LAST_GITHUB_CHECK] = checkedAtMs
        }
    }
}
