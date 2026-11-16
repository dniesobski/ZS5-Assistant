package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.repository.PolishCalendarCalculator
import com.example.model.HolidayEvent
import com.example.model.ParsedPlan
import java.util.Calendar

object LessonEndScheduler {

    private const val MAX_LESSON_SLOTS = 16

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (slot in 0 until MAX_LESSON_SLOTS) {
            val intent = Intent(context, LessonEndAlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                4000 + slot,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
    }

    fun rescheduleForToday(
        context: Context,
        enabled: Boolean,
        minutesBefore: Int,
        pinnedPlan: ParsedPlan?,
        directorDays: List<HolidayEvent>
    ) {
        cancelAll(context)
        if (!enabled) return

        val today = PolishCalendarCalculator.today()
        val dow = PolishCalendarCalculator.dayOfWeekIso(today)
        if (dow !in 1..5) return

        // Skip scheduling if today is a Polish holiday or director free day
        val countdown = PolishCalendarCalculator.calculateCountdown(today, directorDays)
        if (countdown.todayFreeEvent != null || countdown.isCurrentlySummerBreak) return

        val lessonsToday = pinnedPlan?.lessons
            ?.filter { it.dayOfWeek == dow && it.endMinutes > 0 }
            ?.sortedBy { it.lessonNumber }
            .orEmpty()

        if (lessonsToday.isEmpty()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nowMillis = System.currentTimeMillis()

        for (cell in lessonsToday) {
            val triggerMinutes = (cell.endMinutes - minutesBefore).coerceAtLeast(0)
            val triggerCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, triggerMinutes / 60)
                set(Calendar.MINUTE, triggerMinutes % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (triggerCal.timeInMillis <= nowMillis) continue

            val firstSg = cell.subgroups.firstOrNull()
            val endHour = (cell.endMinutes / 60).toString().padStart(2, '0')
            val endMin = (cell.endMinutes % 60).toString().padStart(2, '0')

            val intent = Intent(context, LessonEndAlarmReceiver::class.java).apply {
                putExtra(LessonEndAlarmReceiver.EXTRA_LESSON_NUMBER, cell.lessonNumber)
                putExtra(LessonEndAlarmReceiver.EXTRA_SUBJECT, firstSg?.subject ?: "Lekcja ${cell.lessonNumber}")
                putExtra(LessonEndAlarmReceiver.EXTRA_ROOM, firstSg?.roomName)
                putExtra(LessonEndAlarmReceiver.EXTRA_END_TIME, "$endHour:$endMin")
                putExtra(LessonEndAlarmReceiver.EXTRA_MINUTES_BEFORE, minutesBefore)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                4000 + (cell.lessonNumber % MAX_LESSON_SLOTS),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerCal.timeInMillis,
                pi
            )
        }
    }
}
