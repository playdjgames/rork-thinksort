package com.rork.thinksortandroid.domain

import com.rork.thinksortandroid.data.Checkpoint
import com.rork.thinksortandroid.data.ItemCategory
import com.rork.thinksortandroid.data.LocationTriggerKind
import com.rork.thinksortandroid.data.TaskItem
import com.rork.thinksortandroid.data.TaskStatus
import com.rork.thinksortandroid.data.nowMillis

data class NextAction(
    val task: TaskItem,
    val reason: String,
    val score: Double,
)

/**
 * Chooses the single most sensible thing to do right now. Considers time of day,
 * overdue state, priority, duration, dependencies, preparation needs, calendar
 * pressure and what the user already started.
 */
class NextActionEngine(
    private val now: Long = nowMillis(),
    private val availableMinutes: Int? = null,
    private val minutesUntilNextEvent: Int? = null,
) {
    fun recommend(tasks: List<TaskItem>, checkpoint: Checkpoint?): NextAction? {
        val candidates = tasks.filter { it.isOpen && it.status != TaskStatus.WAITING }
        if (candidates.isEmpty()) return null

        val blockedIDs = candidates.filter { task ->
            task.dependencyIDs.any { depId -> tasks.firstOrNull { it.id == depId }?.isOpen ?: false }
        }.map { it.id }.toSet()

        val scored = mutableListOf<NextAction>()
        for (task in candidates) {
            if (task.id in blockedIDs) continue
            val estimate = task.estimatedMinutes
            if (availableMinutes != null && estimate != null && estimate > availableMinutes) continue
            if (minutesUntilNextEvent != null && minutesUntilNextEvent < 25 && estimate != null && estimate > minutesUntilNextEvent) continue
            val (score, reason) = evaluate(task, tasks, checkpoint)
            scored.add(NextAction(task, reason, score))
        }

        if (scored.isEmpty()) {
            val prerequisite = candidates.firstOrNull { task -> candidates.any { it.dependencyIDs.contains(task.id) } }
            if (prerequisite != null) return NextAction(prerequisite, "Something else is waiting on this one.", 1.0)
            val fallback = candidates.firstOrNull() ?: return null
            return NextAction(fallback, reasonText(fallback), 1.0)
        }
        return scored.maxByOrNull { it.score }
    }

    private fun evaluate(task: TaskItem, allTasks: List<TaskItem>, checkpoint: Checkpoint?): Pair<Double, String> {
        var score = task.priority.weight
        val reasons = mutableListOf<String>()

        if (task.isOverdue(now)) {
            score += 45
            reasons.add("it's overdue")
        }
        if (task.status == TaskStatus.IN_PROGRESS || checkpoint?.taskID == task.id) {
            score += 35
            reasons.add("you already started it")
        }
        task.dueDate?.let { due ->
            if (Dates.isToday(due, now)) {
                score += 25
                if (task.hasTime) {
                    val minutes = ((due - now) / 60_000L).toInt()
                    if (minutes in 1..120) {
                        score += (120 - minutes) / 4.0
                        reasons.add("it's at ${Dates.time(due)}")
                    }
                } else {
                    reasons.add("it's on today's list")
                }
            } else if (Dates.isTomorrow(due, now)) {
                score += 6
            }
        }

        val parent = allTasks.firstOrNull { it.dependencyIDs.contains(task.id) && it.isOpen }
        if (parent != null) {
            score += 30
            val parentDue = parent.dueDate
            if (parentDue != null) {
                val whenText = if (Dates.isTomorrow(parentDue, now)) "tomorrow" else Dates.weekday(parentDue)
                reasons.add("you need it for ${parent.title.lowercasedFirst} $whenText")
            } else {
                reasons.add("${parent.title} depends on it")
            }
        }

        task.estimatedMinutes?.let { estimate ->
            if (estimate <= 15) {
                score += 12
                reasons.add("it should only take about $estimate minutes")
            }
            if (minutesUntilNextEvent != null && estimate <= minutesUntilNextEvent) score += 6
        }

        if (task.postponeCount >= 2) {
            score += 10
            reasons.add("you've moved it ${task.postponeCount} times already")
        }

        task.locationTrigger?.let { trigger ->
            score -= 15
            val phrase = if (trigger.kind == LocationTriggerKind.ARRIVING) "when you get to" else "when you leave"
            reasons.add("it's set for $phrase ${trigger.placeName}")
        }

        if (task.category == ItemCategory.IDEA || task.category == ItemCategory.NOTE) score -= 60
        if (Dates.hour(now) >= 21 && (task.estimatedMinutes ?: 30) > 45) score -= 12

        val text = if (reasons.isEmpty()) reasonText(task) else reasons.take(2).joinToString(", and ").capitalizedFirst + "."
        return score to text
    }

    private fun reasonText(task: TaskItem): String {
        val due = task.dueDate
        if (due != null && task.hasTime) return "It's scheduled for ${Dates.time(due)}."
        task.estimatedMinutes?.let { return "It's a short one — about $it minutes." }
        return "It's the clearest thing you can move forward right now."
    }

    /** Tasks that fit into a stated pocket of time. */
    fun tasksFitting(minutes: Int, tasks: List<TaskItem>): List<TaskItem> =
        tasks
            .filter { it.isOpen && it.category != ItemCategory.IDEA && it.category != ItemCategory.NOTE }
            .filter { (it.estimatedMinutes ?: defaultEstimate(it)) <= minutes }
            .sortedWith(
                compareByDescending<TaskItem> { it.priority.weight }
                    .thenBy { it.estimatedMinutes ?: defaultEstimate(it) }
            )

    fun defaultEstimate(task: TaskItem): Int {
        if (task.subtasks.isNotEmpty()) return task.subtasks.size * 10
        return when (task.category) {
            ItemCategory.SHOPPING, ItemCategory.ERRAND -> 30
            ItemCategory.APPOINTMENT -> 60
            ItemCategory.REMINDER -> 5
            else -> 20
        }
    }
}
