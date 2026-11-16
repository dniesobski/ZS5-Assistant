package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    const val CHANNEL_CHANGES = "zs5_schedule_changes"
    const val CHANNEL_LESSON_END = "zs5_lesson_end"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val changesChannel = NotificationChannel(
                CHANNEL_CHANGES,
                context.getString(R.string.channel_changes_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_changes_desc)
            }

            val lessonEndChannel = NotificationChannel(
                CHANNEL_LESSON_END,
                context.getString(R.string.channel_lesson_end_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_lesson_end_desc)
            }

            manager.createNotificationChannel(changesChannel)
            manager.createNotificationChannel(lessonEndChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showScheduleChangeNotification(
        context: Context,
        planTitle: String,
        summaries: List<String>
    ) {
        if (!hasNotificationPermission(context) || summaries.isEmpty()) return
        ensureChannels(context)

        val preview = if (summaries.size == 1) {
            summaries.first()
        } else {
            "Wykryto ${summaries.size} zmian w planie ($planTitle)"
        }
        val bigText = summaries.take(5).joinToString("\n• ", prefix = "• ")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CHANGES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Zmiana w planie: $planTitle")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(
            2000 + (planTitle.hashCode() and 0x0FFF),
            notification
        )
    }

    fun showLessonEndNotification(
        context: Context,
        lessonNumber: Int,
        subject: String,
        room: String?,
        endTime: String,
        minutesBefore: Int
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val title = if (minutesBefore <= 0) {
            "Dzwonek! Koniec $lessonNumber. lekcji"
        } else {
            "Koniec $lessonNumber. lekcji za $minutesBefore min"
        }
        val roomSuffix = room?.let { " • sala $it" } ?: ""
        val body = "$subject$roomSuffix (koniec o $endTime)"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            3000 + lessonNumber,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_LESSON_END)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(3000 + lessonNumber, notification)
    }

    fun showTestNotification(context: Context, minutesBefore: Int) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_LESSON_END)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("ZS5 Rybnik • Test powiadomień")
            .setContentText("Powiadomienia działają poprawnie (alert końca lekcji: $minutesBefore min przed dzwonkiem).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(9999, notification)
    }
}
