package com.rork.thinksortandroid.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.thinksortandroid.BuildConfig
import com.rork.thinksortandroid.data.AccessState
import com.rork.thinksortandroid.data.AppearanceMode
import com.rork.thinksortandroid.data.ReminderFrequency
import com.rork.thinksortandroid.domain.Dates
import com.rork.thinksortandroid.services.CalendarService
import com.rork.thinksortandroid.services.NotificationService
import com.rork.thinksortandroid.services.PurchaseService
import com.rork.thinksortandroid.ui.BrainViewModel
import com.rork.thinksortandroid.ui.components.CardTextField
import com.rork.thinksortandroid.ui.components.ChipPicker
import com.rork.thinksortandroid.ui.components.DetailTopBar
import com.rork.thinksortandroid.ui.components.RowDivider
import com.rork.thinksortandroid.ui.components.ScreenColumn
import com.rork.thinksortandroid.ui.components.SectionCard
import com.rork.thinksortandroid.ui.components.SwitchRow
import com.rork.thinksortandroid.ui.components.ValueRow
import com.rork.thinksortandroid.ui.components.card
import com.rork.thinksortandroid.ui.navigation.LocalNav
import com.rork.thinksortandroid.ui.navigation.Routes
import com.rork.thinksortandroid.ui.navigation.Sheet
import com.rork.thinksortandroid.ui.theme.Think
import kotlinx.coroutines.launch

private val leadOptions = listOf(10, 15, 30, 45, 60, 120)

private fun openAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}

private fun pickMinutes(context: Context, minutes: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, minutes / 60, minutes % 60, DateFormat.is24HourFormat(context)).show()
}

@Composable
fun SettingsScreen(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val unlocked by PurchaseService.isUnlocked.collectAsStateWithLifecycle()
    val restoring by PurchaseService.isRestoring.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = data.settings
    var refresh by remember { mutableIntStateOf(0) }
    var notifDenied by remember { mutableStateOf(false) }
    var calendarDenied by remember { mutableStateOf(false) }
    var micDenied by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val notifGranted = remember(refresh) { NotificationService.hasPermission(context) }
    val calendarGranted = remember(refresh) { CalendarService.hasPermission(context) }
    val micGranted = remember(refresh) {
        androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifDenied = !granted
        refresh++
        vm.refreshNotifications()
        vm.scheduleTrialReminders()
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calendarDenied = !granted
        vm.updateSettings { it.copy(calendarEnabled = granted) }
        refresh++
        if (granted) scope.launch { CalendarService.reload(context) }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        refresh++
    }

    LaunchedEffect(Unit) { refresh++ }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar("Settings", onBack = nav::back)
        ScreenColumn(spacing = 24.dp) {
            val access = PurchaseService.accessState(settings.trialStartedAt, unlocked)
            Group("Access", footer = if (unlocked) "Thanks for buying ThinkSort. It's yours for good — no subscription, no renewals."
            else "ThinkSort is a one-time purchase. There is no subscription and nothing renews.") {
                when (access) {
                    AccessState.Unlocked -> ValueRow("ThinkSort", "Lifetime access")
                    is AccessState.Trial -> {
                        ValueRow("Free trial", "${access.daysRemaining} day${if (access.daysRemaining == 1) "" else "s"} left")
                        RowDivider()
                        LinkRow("Unlock lifetime access") { nav.show(Sheet.Paywall) }
                    }
                    else -> LinkRow("Unlock lifetime access") { nav.show(Sheet.Paywall) }
                }
                if (!unlocked) {
                    RowDivider()
                    LinkRow(if (restoring) "Restoring…" else "Restore purchase") { if (!restoring) PurchaseService.restore() }
                }
            }

            Group("Account", footer = "ThinkSort works entirely on this device. There's no account and nothing is uploaded.") {
                CardTextField(settings.displayName, { name -> vm.updateSettings { it.copy(displayName = name) } }, "What should ThinkSort call you?", modifier = Modifier.padding(horizontal = 4.dp))
            }

            Group("Notifications", footer = "ThinkSort keeps the number of alerts low on purpose. Nothing fires during quiet hours — it waits until they end.") {
                SwitchRow("Reminders", settings.notificationsEnabled, { v -> vm.updateSettings { it.copy(notificationsEnabled = v) } })
                RowDivider()
                SwitchRow("Preparation nudges", settings.preparationRemindersEnabled, { v -> vm.updateSettings { it.copy(preparationRemindersEnabled = v) } })
                RowDivider()
                Label("How early to warn me")
                ChipPicker(leadOptions, settings.defaultReminderLeadMinutes, { if (it >= 60) "${it / 60}h before" else "$it min" }, { v -> vm.updateSettings { it.copy(defaultReminderLeadMinutes = v) } }, Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp))
                RowDivider()
                Label("How often")
                ChipPicker(ReminderFrequency.entries, settings.reminderFrequency, { it.label }, { v -> vm.updateSettings { it.copy(reminderFrequency = v) } }, Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp))
                RowDivider()
                if (notifGranted) {
                    ValueRow("System permission", "On")
                } else if (notifDenied) {
                    ValueRow("System permission", "Off")
                    RowDivider()
                    LinkRow("Change in Android Settings") { openAppSettings(context) }
                } else {
                    LinkRow("Set Up Notifications") {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openAppSettings(context)
                    }
                }
            }

            Group("Quiet hours") {
                SwitchRow("Hold reminders overnight", settings.quietHours.isEnabled, { v -> vm.updateSettings { it.copy(quietHours = it.quietHours.copy(isEnabled = v)) } })
                if (settings.quietHours.isEnabled) {
                    RowDivider()
                    ValueRow("From", Dates.minutesLabel(settings.quietHours.start), valueColor = Think.colors.accent, onClick = {
                        pickMinutes(context, settings.quietHours.start) { m -> vm.updateSettings { it.copy(quietHours = it.quietHours.copy(start = m)) } }
                    })
                    RowDivider()
                    ValueRow("Until", Dates.minutesLabel(settings.quietHours.end), valueColor = Think.colors.accent, onClick = {
                        pickMinutes(context, settings.quietHours.end) { m -> vm.updateSettings { it.copy(quietHours = it.quietHours.copy(end = m)) } }
                    })
                }
            }

            Group("Calendar", footer = "ThinkSort reads your events to know how much free time you have. It never creates or changes an event.") {
                SwitchRow("Use my calendar", settings.calendarEnabled && calendarGranted, { v ->
                    if (v) {
                        if (calendarGranted) {
                            vm.updateSettings { it.copy(calendarEnabled = true) }
                            scope.launch { CalendarService.reload(context) }
                        } else {
                            calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                    } else {
                        vm.updateSettings { it.copy(calendarEnabled = false) }
                        CalendarService.clear()
                    }
                })
                RowDivider()
                ValueRow("Access", if (calendarGranted) "On" else "Off")
                if (calendarDenied && !calendarGranted) {
                    RowDivider()
                    LinkRow("Change in Android Settings") { openAppSettings(context) }
                }
            }

            Group("Voice", footer = "Speech is transcribed by your device's speech service. You can always type instead.") {
                SwitchRow("Voice input", settings.voiceInputEnabled, { v -> vm.updateSettings { it.copy(voiceInputEnabled = v) } })
                RowDivider()
                ValueRow("Microphone", if (micGranted) "On" else "Off")
                if (!micGranted) {
                    RowDivider()
                    if (micDenied) LinkRow("Change in Android Settings") { openAppSettings(context) }
                    else LinkRow("Set Up Microphone") { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                }
            }

            Group("Appearance & feedback") {
                Label("Theme")
                ChipPicker(AppearanceMode.entries, settings.appearance, { it.label }, { v -> vm.updateSettings { it.copy(appearance = v) } }, Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp))
                RowDivider()
                SwitchRow("Haptics", settings.hapticsEnabled, { v -> vm.updateSettings { it.copy(hapticsEnabled = v) } })
                RowDivider()
                SwitchRow("Reduce animation", settings.reduceVisualMotion, { v -> vm.updateSettings { it.copy(reduceVisualMotion = v) } })
            }

            Group("Focus") {
                Label("Default session")
                ChipPicker(listOf(5, 15, 25, 45), settings.defaultFocusMinutes, { "$it min" }, { v -> vm.updateSettings { it.copy(defaultFocusMinutes = v) } }, Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp))
            }

            Group("Privacy") {
                LinkRow("How your data is handled") { nav.push(Routes.PRIVACY) }
            }

            Group("Your data") {
                LinkRow("Export everything") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, "ThinkSort export")
                        putExtra(Intent.EXTRA_TEXT, vm.exportJSON())
                    }
                    context.startActivity(Intent.createChooser(send, "Export ThinkSort data"))
                }
                RowDivider()
                LinkRow("Reload sample list") {
                    vm.loadSampleData()
                    nav.flash("Sample list restored.")
                }
                RowDivider()
                LinkRow("Delete all data", destructive = true) { confirmDelete = true }
            }

            Group(null, footer = "ThinkSort is a productivity and organization app. It doesn't diagnose, treat, or provide medical advice for any condition.") {
                ValueRow("Version", BuildConfig.VERSION_NAME)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Think.colors.surface,
            title = { Text("Delete everything?", color = Think.colors.ink) },
            text = { Text("Every task, note, list and brain dump is removed from this device. This can't be undone.", color = Think.colors.inkSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteAllData()
                    nav.back()
                    nav.flash("All data deleted.")
                }) { Text("Delete all data", color = Think.colors.coral) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Think.colors.accent) } },
        )
    }
}

@Composable
private fun Group(title: String?, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionCard(title = title, content = content)
        if (footer != null) {
            Text(footer, style = MaterialTheme.typography.bodySmall, color = Think.colors.inkTertiary, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp))
}

@Composable
private fun LinkRow(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (destructive) Think.colors.coral else Think.colors.accent, modifier = Modifier.weight(1f))
        if (!destructive) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Think.colors.inkTertiary)
    }
}

@Composable
fun PrivacyScreen() {
    val nav = LocalNav.current
    val blocks = listOf(
        "Everything stays on your phone" to "Your tasks, notes, brain dumps and history are stored locally on this device. ThinkSort has no account, no server, and nothing is sold or shared.",
        "Microphone & speech" to "Used only while you're recording a brain dump. If you decline, you can type instead and everything still works.",
        "Calendar" to "Read only, and only if you allow it. ThinkSort uses your events to understand how much free time you have. It never creates, edits or deletes an event.",
        "Notifications" to "Used for reminders you set. You control how often, and quiet hours hold everything back until morning.",
        "Purchases" to "Your one-time unlock is handled by Google Play. ThinkSort only learns whether it's unlocked — never your payment details.",
        "Not a medical app" to "ThinkSort is an organization and productivity tool. It does not diagnose, treat or cure any condition, and it doesn't provide medical advice.",
    )
    Column(Modifier.fillMaxSize()) {
        DetailTopBar("Privacy", onBack = nav::back)
        ScreenColumn(spacing = 18.dp) {
            blocks.forEach { (title, body) ->
                Column(Modifier.fillMaxWidth().card().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Think.colors.ink)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
                }
            }
        }
    }
}
