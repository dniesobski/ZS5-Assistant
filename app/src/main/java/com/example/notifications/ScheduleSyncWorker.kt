package com.example.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.network.TimetableRemoteDataSource
import com.example.data.repository.PolishCalendarCalculator
import com.example.data.repository.TimetableRepository
import com.example.model.HolidayEvent
import java.util.concurrent.TimeUnit

class ScheduleSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.timetableDao()
        val repo = TimetableRepository(dao, TimetableRemoteDataSource())
        val prefsRepo = UserPreferencesRepository(applicationContext)
        val prefs = prefsRepo.getOnce()

        val pinned = prefs.pinnedPlanEntry ?: return Result.success()

        val syncResult = repo.syncPlanAndDetectChanges(pinned, recordChanges = true)
        syncResult.onSuccess { (freshPlan, changes) ->
            prefsRepo.setLastSyncEpochMs(System.currentTimeMillis())

            if (prefs.notifyScheduleChanges && changes.isNotEmpty()) {
                NotificationHelper.showScheduleChangeNotification(
                    context = applicationContext,
                    planTitle = freshPlan.title,
                    summaries = changes.map { it.summary }
                )
            }

            val directorDays = dao.getDirectorFreeDaysOnce().map {
                HolidayEvent(
                    date = PolishCalendarCalculator.fromEpochDay(it.epochDay),
                    name = it.name,
                    isDirectorDay = true,
                    directorDayId = it.id
                )
            }

            LessonEndScheduler.rescheduleForToday(
                context = applicationContext,
                enabled = prefs.notifyLessonEnd,
                minutesBefore = prefs.lessonEndMinutesBefore,
                pinnedPlan = freshPlan,
                directorDays = directorDays
            )
        }

        return if (syncResult.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "zs5_schedule_periodic_sync"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(
                2, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
