package com.rork.thinksortandroid.services

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rork.thinksortandroid.data.AppSettings
import com.rork.thinksortandroid.data.TaskItem
import com.rork.thinksortandroid.domain.Dates

/**
 * Schedules the app's local reminders: due reminders, preparation nudges, the
 * morning briefing and evening reset, plus two trial heads-ups. It clears and
 * rebuilds the whole schedule so the user never accumulates duplicate nudges.
 */
object NotificationService {
    const val CHANNEL_ID = "thinksort_reminders"
    const val EXTRA_KEY = "key"
    const val EXTRA_TITLE = "title"
    const val EXTRA_BODY = "body"
    private const val ACTION = "com.rork.thinksortandroid.REMINDER"
    private const val PREFS = "thinksort_notifications"
    private const val PREF_KEYS = "scheduled_keys"
    private val trialKeys = listOf("trial-midpoint", "trial-final")

    private data class Request(val key: String, val title: String, val body: String, val at: Long)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Task reminders, preparation nudges and daily routines"
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun rebuildSchedule(context: Context, tasks: List<TaskItem>, settings: AppSettings) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(PREF_KEYS, emptySet())?.forEach { cancel(context, it) }
        prefs.edit().putStringSet(PREF_KEYS, emptySet()).apply()

        if (!settings.notificationsEnabled || !hasPermission(context)) return
        ensureChannel(context)

        val now = System.currentTimeMillis()
        val requests = mutableListOf<Request>()

        for (task in tasks.filter { it.isOpen }) {
            val due = task.dueDate ?: continue
            if (task.hasTime && due > now) {
                requests.add(Request("due-${task.id}", task.title, timeBody(task, due), shifted(due, settings)))
                if (settings.preparationRemindersEnabled && (task.openPreparationItems.isNotEmpty() || task.reminderLeadMinutes != null)) {
                    val lead = task.reminderLeadMinutes ?: settings.defaultReminderLeadMinutes
                    val prepAt = Dates.addMinutes(due, -lead.toLong())
                    if (prepAt > now) {
                        val items = task.openPreparationItems
                        val body = if (items.isEmpty()) "Starts at ${Dates.time(due)}."
                        else "Bring: ${items.joinToString(", ") { it.title }}"
                        requests.add(Request("prep-${task.id}", "Get ready: ${task.title}", body, shifted(prepAt, settings)))
                    }
                }
            } else if (!task.hasTime) {
                val morning = Dates.atTime(9, 0, due)
                if (morning > now) {
                    val items = task.openPreparationItems
                    val body = if (items.isEmpty()) "You said you wanted to do this today."
                    else "You'll need: ${items.joinToString(", ") { it.title }}"
                    requests.add(Request("due-${task.id}", "Today: ${task.title}", body, shifted(morning, settings)))
                }
            }
        }

        requests.add(Request("routine-morning", "Good morning", "Here's your day, one thing at a time.", nextDaily(8, 0, now)))
        requests.add(Request("routine-evening", "Evening reset", "Two minutes to clear tomorrow's runway.", nextDaily(20, 30, now)))

        val scheduled = requests.sortedBy { it.at }.take(settings.reminderFrequency.maxScheduled)
        scheduled.forEach { schedule(context, it) }
        prefs.edit().putStringSet(PREF_KEYS, scheduled.map { it.key }.toSet()).apply()
    }

    /** Two quiet heads-ups during the free trial: halfway through and the day before it ends. */
    fun scheduleTrialReminders(context: Context, trialStartedAt: Long?, isUnlocked: Boolean, trialDays: Int) {
        trialKeys.forEach { cancel(context, it) }
        if (isUnlocked || trialStartedAt == null || !hasPermission(context)) return
        ensureChannel(context)
        val now = System.currentTimeMillis()
        val schedule = listOf(
            Request(
                "trial-midpoint",
                "You're halfway through your free week",
                "Still ${trialDays - trialDays / 2} days of ThinkSort left. No payment yet.",
                Dates.atTime(10, 0, Dates.addDays(trialStartedAt, (trialDays / 2).toLong())),
            ),
            Request(
                "trial-final",
                "Your free week ends tomorrow",
                "Unlock ThinkSort once for lifetime access. Your tasks stay on this device either way.",
                Dates.atTime(10, 0, Dates.addDays(trialStartedAt, (trialDays - 1).toLong())),
            ),
        )
        schedule.filter { it.at > now }.forEach { schedule(context, it) }
    }

    /** Re-arms a daily routine reminder for the following day after it fires. */
    fun rescheduleRoutine(context: Context, key: String, title: String, body: String) {
        val now = System.currentTimeMillis()
        val at = when (key) {
            "routine-morning" -> nextDaily(8, 0, now + 60_000L)
            "routine-evening" -> nextDaily(20, 30, now + 60_000L)
            else -> return
        }
        schedule(context, Request(key, title, body, at))
    }

    fun cancelAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(PREF_KEYS, emptySet())?.forEach { cancel(context, it) }
        prefs.edit().putStringSet(PREF_KEYS, emptySet()).apply()
    }

    private fun timeBody(task: TaskItem, due: Long): String {
        val time = Dates.time(due)
        val place = task.locationName?.let { " · $it" } ?: ""
        return "It's $time$place."
    }

    private fun nextDaily(hour: Int, minute: Int, now: Long): Long {
        var at = Dates.atTime(hour, minute, now)
        if (at <= now) at = Dates.addDays(at, 1)
        return at
    }

    /** Pushes a reminder out of quiet hours so the user isn't woken up. */
    private fun shifted(at: Long, settings: AppSettings): Long {
        if (!settings.quietHours.contains(at)) return at
        val end = settings.quietHours.end
        var candidate = Dates.atTime(end / 60, end % 60, at)
        if (candidate < at) candidate = Dates.addDays(candidate, 1)
        return candidate
    }

    private fun intentFor(context: Context, key: String): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION
            data = Uri.parse("thinksort://reminder/$key")
        }

    private fun schedule(context: Context, request: Request) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = intentFor(context, request.key).apply {
            putExtra(EXTRA_KEY, request.key)
            putExtra(EXTRA_TITLE, request.title)
            putExtra(EXTRA_BODY, request.body)
        }
        val pending = PendingIntent.getBroadcast(
            context, request.key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, request.at, pending)
    }

    private fun cancel(context: Context, key: String) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, key.hashCode(), intentFor(context, key),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarm.cancel(pending)
        pending.cancel()
    }
}
