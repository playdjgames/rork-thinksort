package com.rork.thinksortandroid.data

import com.rork.thinksortandroid.domain.Dates
import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

fun nowMillis(): Long = System.currentTimeMillis()

@Serializable
data class Subtask(
    val id: String = newId(),
    val title: String,
    val isDone: Boolean = false,
    val completedAt: Long? = null,
)

@Serializable
data class PreparationItem(
    val id: String = newId(),
    val title: String,
    val isDone: Boolean = false,
)

@Serializable
data class LocationTrigger(
    val kind: LocationTriggerKind,
    val placeName: String,
)

/** The core unit of ThinkSort: a task, reminder, appointment or errand. */
@Serializable
data class TaskItem(
    val id: String = newId(),
    val title: String,
    val details: String = "",
    val status: TaskStatus = TaskStatus.NOT_STARTED,
    val priority: Priority = Priority.SOON,
    val category: ItemCategory = ItemCategory.TASK,
    val dueDate: Long? = null,
    val hasTime: Boolean = false,
    val deadline: Long? = null,
    val estimatedMinutes: Int? = null,
    val locationName: String? = null,
    val locationTrigger: LocationTrigger? = null,
    val dependencyIDs: List<String> = emptyList(),
    val preparationItems: List<PreparationItem> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val recurrence: RecurrenceRule? = null,
    val reminderLeadMinutes: Int? = null,
    val notes: String = "",
    val createdAt: Long = nowMillis(),
    val updatedAt: Long = nowMillis(),
    val completedAt: Long? = null,
    val lastStartedAt: Long? = null,
    val lastStoppedAt: Long? = null,
    val postponeCount: Int = 0,
    val sourceBrainDumpID: String? = null,
) {
    val isOpen: Boolean get() = status.isOpen
    val nextSubtask: Subtask? get() = subtasks.firstOrNull { !it.isDone }
    val completedSubtaskCount: Int get() = subtasks.count { it.isDone }
    val openPreparationItems: List<PreparationItem> get() = preparationItems.filter { !it.isDone }

    fun isOverdue(now: Long = nowMillis()): Boolean {
        if (!isOpen) return false
        if (deadline != null && deadline < now) return true
        val due = dueDate ?: return false
        if (hasTime) return due < now
        return Dates.startOfDay(due) < Dates.startOfDay(now)
    }
}

@Serializable
data class NoteItem(
    val id: String = newId(),
    val text: String,
    val isIdea: Boolean = false,
    val createdAt: Long = nowMillis(),
    val sourceBrainDumpID: String? = null,
)

@Serializable
data class ShoppingItem(
    val id: String = newId(),
    val name: String,
    val store: String? = null,
    val isPurchased: Boolean = false,
    val createdAt: Long = nowMillis(),
    val purchasedAt: Long? = null,
) {
    val storeLabel: String get() = if (store.isNullOrBlank()) "Anywhere" else store
}

@Serializable
data class BrainDumpRecord(
    val id: String = newId(),
    val transcript: String,
    val createdAt: Long = nowMillis(),
    val savedItemCount: Int = 0,
    val wasSpoken: Boolean = false,
)

@Serializable
data class FocusSessionRecord(
    val id: String = newId(),
    val taskID: String? = null,
    val taskTitle: String,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val startedAt: Long = nowMillis(),
    val endedAt: Long? = null,
    val finished: Boolean = false,
)

/** Where the user stopped working — powers "Where did I leave off?". */
@Serializable
data class Checkpoint(
    val taskID: String,
    val subtaskID: String? = null,
    val savedAt: Long = nowMillis(),
    val note: String = "",
)

@Serializable
data class HistoryEntry(
    val id: String = newId(),
    val kind: HistoryKind,
    val title: String,
    val detail: String = "",
    val taskID: String? = null,
    val date: Long = nowMillis(),
)

@Serializable
data class QuietHours(
    val isEnabled: Boolean = true,
    /** Minutes from midnight. */
    val start: Int = 22 * 60,
    val end: Int = 7 * 60,
) {
    fun contains(millis: Long): Boolean {
        if (!isEnabled) return false
        val minutes = Dates.minutesOfDay(millis)
        return if (start <= end) minutes in start until end else minutes >= start || minutes < end
    }
}

@Serializable
data class AppSettings(
    val displayName: String = "",
    val notificationsEnabled: Boolean = true,
    val preparationRemindersEnabled: Boolean = true,
    val defaultReminderLeadMinutes: Int = 45,
    val reminderFrequency: ReminderFrequency = ReminderFrequency.BALANCED,
    val quietHours: QuietHours = QuietHours(),
    val calendarEnabled: Boolean = false,
    val voiceInputEnabled: Boolean = true,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val hapticsEnabled: Boolean = true,
    val reduceVisualMotion: Boolean = false,
    val defaultFocusMinutes: Int = 25,
    val hasCompletedOnboarding: Boolean = false,
    val lastMorningBriefing: Long? = null,
    val lastEveningReset: Long? = null,
    val trialStartedAt: Long? = null,
    val lifetimeUnlocked: Boolean? = null,
)

@Serializable
data class AppData(
    val tasks: List<TaskItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val shopping: List<ShoppingItem> = emptyList(),
    val brainDumps: List<BrainDumpRecord> = emptyList(),
    val focusSessions: List<FocusSessionRecord> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val checkpoint: Checkpoint? = null,
    val settings: AppSettings = AppSettings(),
    val schemaVersion: Int = 1,
)
