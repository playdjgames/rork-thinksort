package com.rork.thinksortandroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rork.thinksortandroid.data.ItemCategory
import com.rork.thinksortandroid.data.Priority
import com.rork.thinksortandroid.data.TaskItem
import com.rork.thinksortandroid.data.nowMillis
import com.rork.thinksortandroid.domain.ExtractedItem
import com.rork.thinksortandroid.ui.components.ChipPicker
import com.rork.thinksortandroid.ui.components.Eyebrow
import com.rork.thinksortandroid.ui.components.FormField
import com.rork.thinksortandroid.ui.components.PrimaryButton
import com.rork.thinksortandroid.ui.components.SectionCard
import com.rork.thinksortandroid.ui.components.SoftButton
import com.rork.thinksortandroid.ui.components.SwitchRow
import com.rork.thinksortandroid.ui.components.TextLink
import com.rork.thinksortandroid.ui.components.ThinkSheet
import com.rork.thinksortandroid.ui.components.WhenEditor
import com.rork.thinksortandroid.ui.theme.Think

/** Lightweight editor for a single extracted item before it's saved. */
@Composable
fun ExtractedItemEditor(
    item: ExtractedItem,
    onDismiss: () -> Unit,
    onSave: (ExtractedItem) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(item.title) }
    var category by remember { mutableStateOf(item.category) }
    var priority by remember { mutableStateOf(item.priority) }
    var hasDate by remember { mutableStateOf(item.dueDate != null) }
    var date by remember { mutableStateOf(item.dueDate ?: nowMillis()) }
    var hasTime by remember { mutableStateOf(item.hasTime) }

    ThinkSheet(
        title = "Edit item",
        onDismiss = onDismiss,
        dismissLabel = "Cancel",
        actions = {
            TextLink("Save", {
                onSave(
                    item.copy(
                        title = title.trim(),
                        category = category,
                        priority = priority,
                        dueDate = if (hasDate) date else null,
                        hasTime = hasDate && hasTime,
                        needsClarification = false,
                    )
                )
            }, color = Think.colors.accent, enabled = title.isNotBlank())
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("What", Modifier.padding(start = 4.dp))
            FormField(title, { title = it }, "Title", singleLine = false)
            ChipPicker(ItemCategory.entries, category, { it.label }, { category = it })
        }
        SectionCard(title = "When") {
            WhenEditor(hasDate, date, hasTime, { hasDate = it }, { date = it }, { hasTime = it })
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("How urgent", Modifier.padding(start = 4.dp))
            ChipPicker(Priority.entries, priority, { it.label }, { priority = it })
        }
        SoftButton("Remove this item", onDelete, tint = Think.colors.coral)
    }
}

private val estimateOptions = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
private val leadOptions = listOf(10, 15, 30, 45, 60, 120)

/** Full editor for an existing task. */
@Composable
fun TaskEditor(task: TaskItem, onDismiss: () -> Unit, onSave: (TaskItem) -> Unit) {
    var title by remember { mutableStateOf(task.title) }
    var notes by remember { mutableStateOf(task.notes) }
    var category by remember { mutableStateOf(task.category) }
    var priority by remember { mutableStateOf(task.priority) }
    var hasDate by remember { mutableStateOf(task.dueDate != null) }
    var date by remember { mutableStateOf(task.dueDate ?: nowMillis()) }
    var hasTime by remember { mutableStateOf(task.hasTime) }
    var location by remember { mutableStateOf(task.locationName.orEmpty()) }
    var hasEstimate by remember { mutableStateOf(task.estimatedMinutes != null) }
    var estimate by remember { mutableStateOf(task.estimatedMinutes ?: 20) }
    var hasLead by remember { mutableStateOf(task.reminderLeadMinutes != null) }
    var lead by remember { mutableStateOf(task.reminderLeadMinutes ?: 45) }
    var recurrence by remember { mutableStateOf(task.recurrence) }

    fun save() {
        onSave(
            task.copy(
                title = title.trim(),
                notes = notes.trim(),
                category = category,
                priority = priority,
                dueDate = if (hasDate) date else null,
                hasTime = hasDate && hasTime,
                locationName = location.trim().ifEmpty { null },
                estimatedMinutes = if (hasEstimate) estimate else null,
                reminderLeadMinutes = if (hasLead) lead else null,
                recurrence = if (hasDate) recurrence else null,
            )
        )
    }

    ThinkSheet(
        title = "Edit task",
        onDismiss = onDismiss,
        dismissLabel = "Cancel",
        actions = { TextLink("Save", ::save, color = Think.colors.accent, enabled = title.isNotBlank()) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("What", Modifier.padding(start = 4.dp))
            FormField(title, { title = it }, "Title", singleLine = false)
            FormField(notes, { notes = it }, "Notes", singleLine = false)
            FormField(location, { location = it }, "Where (optional)")
            ChipPicker(ItemCategory.entries, category, { it.label }, { category = it })
        }
        SectionCard(title = "When") {
            WhenEditor(hasDate, date, hasTime, { hasDate = it }, { date = it }, { hasTime = it })
        }
        if (hasDate) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("Repeat", Modifier.padding(start = 4.dp))
                ChipPicker(
                    listOf(null) + com.rork.thinksortandroid.data.RecurrenceRule.entries,
                    recurrence,
                    { it?.label ?: "Never" },
                    { recurrence = it },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("How urgent", Modifier.padding(start = 4.dp))
            ChipPicker(Priority.entries, priority, { it.label }, { priority = it })
        }
        SectionCard(title = "Time & reminders") {
            SwitchRow("Estimate how long", hasEstimate, { hasEstimate = it })
            if (hasEstimate) {
                ChipPicker(estimateOptions, estimate, { "$it min" }, { estimate = it }, Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
            SwitchRow("Heads-up before it starts", hasLead, { hasLead = it })
            if (hasLead) {
                ChipPicker(leadOptions, lead, { if (it >= 60) "${it / 60}h before" else "$it min" }, { lead = it }, Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }
        PrimaryButton("Save Changes", ::save, enabled = title.isNotBlank())
        Text("", modifier = Modifier.fillMaxWidth())
    }
}
