package com.rork.thinksortandroid.data

import kotlinx.serialization.Serializable

@Serializable
enum class Priority(val label: String, val weight: Double) {
    NOW("Now", 40.0),
    SOON("Soon", 22.0),
    LATER("Later", 8.0),
    OPTIONAL("Optional", 0.0),
}

@Serializable
enum class TaskStatus(val label: String) {
    NOT_STARTED("Not Started"),
    IN_PROGRESS("In Progress"),
    WAITING("Waiting"),
    COMPLETED("Completed"),
    POSTPONED("Postponed"),
    CANCELLED("Cancelled");

    val isOpen: Boolean get() = this != COMPLETED && this != CANCELLED
}

@Serializable
enum class ItemCategory(val label: String) {
    TASK("Task"),
    REMINDER("Reminder"),
    APPOINTMENT("Appointment"),
    IDEA("Idea"),
    NOTE("Note"),
    SHOPPING("Shopping"),
    ERRAND("Errand"),
}

@Serializable
enum class RecurrenceRule(val label: String) {
    DAILY("Every day"),
    WEEKDAYS("Every weekday"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
}

@Serializable
enum class LocationTriggerKind(val label: String) {
    ARRIVING("When I arrive at"),
    LEAVING("When I leave"),
}

@Serializable
enum class StuckReason(val label: String) {
    DONT_KNOW_WHERE_TO_START("I don't know where to start"),
    TOO_BIG("It's too big"),
    NEED_SOMETHING("I need something"),
    NOT_ENOUGH_TIME("I don't have enough time"),
    DISTRACTED("I'm distracted"),
    DONT_WANT_TO("I don't want to do it"),
    OTHER("Something else"),
}

@Serializable
enum class HistoryKind(val label: String) {
    CREATED("Added"),
    STARTED("Started"),
    STOPPED("Stopped"),
    COMPLETED("Completed"),
    POSTPONED("Postponed"),
    CANCELLED("Cancelled"),
    BRAIN_DUMP("Brain dump"),
    NOTE_ADDED("Note"),
    FOCUS_SESSION("Focus session"),
}

@Serializable
enum class AppearanceMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

@Serializable
enum class ReminderFrequency(val label: String, val maxScheduled: Int) {
    MINIMAL("Only what matters", 12),
    BALANCED("Balanced", 30),
    PERSISTENT("Keep nudging me", 56),
}

/** Whether the user can use ThinkSort right now: a 7-day trial, then a lifetime unlock. */
sealed interface AccessState {
    data object TrialNotStarted : AccessState
    data class Trial(val daysRemaining: Int) : AccessState
    data object Expired : AccessState
    data object Unlocked : AccessState

    val isUsable: Boolean get() = this is Trial || this is Unlocked
}
