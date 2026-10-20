package com.rork.thinksortandroid.services

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rork.thinksortandroid.MainActivity
import com.rork.thinksortandroid.R

/** Posts a scheduled reminder. Tapping it opens the app. */
class ReminderReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(NotificationService.EXTRA_KEY) ?: return
        val title = intent.getStringExtra(NotificationService.EXTRA_TITLE) ?: "ThinkSort"
        val body = intent.getStringExtra(NotificationService.EXTRA_BODY) ?: ""

        if (key.startsWith("routine-")) {
            NotificationService.rescheduleRoutine(context, key, title, body)
        }
        if (!NotificationService.hasPermission(context)) return
        NotificationService.ensureChannel(context)

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_thinksort)
            .setColor(0xFF2E9E8B.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
    }
}
