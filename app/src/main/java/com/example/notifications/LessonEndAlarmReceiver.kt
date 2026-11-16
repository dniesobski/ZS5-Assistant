package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LessonEndAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val lessonNumber = intent.getIntExtra(EXTRA_LESSON_NUMBER, 1)
        val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: "Lekcja"
        val room = intent.getStringExtra(EXTRA_ROOM)
        val endTime = intent.getStringExtra(EXTRA_END_TIME) ?: ""
        val minutesBefore = intent.getIntExtra(EXTRA_MINUTES_BEFORE, 0)

        NotificationHelper.showLessonEndNotification(
            context = context,
            lessonNumber = lessonNumber,
            subject = subject,
            room = room,
            endTime = endTime,
            minutesBefore = minutesBefore
        )
    }

    companion object {
        const val EXTRA_LESSON_NUMBER = "extra_lesson_number"
        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_ROOM = "extra_room"
        const val EXTRA_END_TIME = "extra_end_time"
        const val EXTRA_MINUTES_BEFORE = "extra_minutes_before"
    }
}
