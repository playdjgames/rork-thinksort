package com.rork.thinksortandroid.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rork.thinksortandroid.data.PersistenceService

/** Alarms don't survive a reboot, so rebuild the reminder schedule from saved data. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val data = PersistenceService(context).load() ?: return
        NotificationService.rebuildSchedule(context, data.tasks, data.settings)
        NotificationService.scheduleTrialReminders(
            context,
            data.settings.trialStartedAt,
            data.settings.lifetimeUnlocked == true,
            PurchaseService.TRIAL_DAYS,
        )
    }
}
