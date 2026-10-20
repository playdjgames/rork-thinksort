package com.rork.thinksortandroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.rork.thinksortandroid.data.ItemCategory
import com.rork.thinksortandroid.data.LocationTriggerKind
import com.rork.thinksortandroid.data.TaskItem
import com.rork.thinksortandroid.data.TaskStatus
import com.rork.thinksortandroid.data.nowMillis
import com.rork.thinksortandroid.domain.Dates
import com.rork.thinksortandroid.ui.theme.Think

val ItemCategory.icon: ImageVector
    get() = when (this) {
        ItemCategory.TASK -> Icons.Rounded.CheckBox
        ItemCategory.REMINDER -> Icons.Rounded.Notifications
        ItemCategory.APPOINTMENT -> Icons.Rounded.Event
        ItemCategory.IDEA -> Icons.Rounded.Lightbulb
        ItemCategory.NOTE -> Icons.AutoMirrored.Rounded.StickyNote2
        ItemCategory.SHOPPING -> Icons.Rounded.ShoppingCart
        ItemCategory.ERRAND -> Icons.Rounded.DirectionsCar
    }

/** "3:00 PM", "Tonight", "Tomorrow" — the short label used in timelines. */
fun TaskItem.timeLabel(now: Long = nowMillis()): String {
    val due = dueDate ?: return "Anytime"
    if (hasTime) {
        if (Dates.isToday(due, now) && Dates.hour(due) >= 19) return "Tonight"
        return Dates.time(due)
    }
    return when {
        Dates.isToday(due, now) -> "Today"
        Dates.isTomorrow(due, now) -> "Tomorrow"
        Dates.isYesterday(due, now) -> "Yesterday"
        else -> Dates.monthDay(due)
    }
}

fun TaskItem.whenDescription(now: Long = nowMillis()): String {
    val parts = mutableListOf<String>()
    dueDate?.let { due ->
        parts.add(
            when {
                Dates.isToday(due, now) -> "Today"
                Dates.isTomorrow(due, now) -> "Tomorrow"
                Dates.isYesterday(due, now) -> "Yesterday"
                else -> Dates.weekdayLongMonthShortDay(due)
            }
        )
        if (hasTime) parts.add(Dates.time(due))
    }
    locationName?.let { parts.add(it) }
    if (parts.isEmpty()) estimatedMinutes?.let { parts.add("About $it min") }
    if (parts.isEmpty()) parts.add("No date yet")
    return parts.joinToString(" · ")
}

/** Small pill showing a task's category. */
@Composable
fun CategoryTag(category: ItemCategory, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(CircleShape).background(Think.colors.accentSoft).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(category.icon, contentDescription = null, tint = Think.colors.accent, modifier = Modifier.size(12.dp))
        Text(category.label, style = MaterialTheme.typography.labelSmall, color = Think.colors.accent)
    }
}

/** Standard task row: check circle, title, supporting line, chevron. */
@Composable
fun TaskRow(
    task: TaskItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCategory: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    val overdue = task.isOverdue()
    CardRow(onClick = onClick, modifier = modifier) {
        if (onToggle != null) {
            val done = task.status == TaskStatus.COMPLETED
            CheckCircle(
                isDone = done,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onToggle)
                    .semantics { contentDescription = if (done) "Mark ${task.title} not done" else "Mark ${task.title} done" },
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Think.colors.ink,
                textDecoration = if (task.status == TaskStatus.COMPLETED) TextDecoration.LineThrough else null,
            )
            val parts = supportingParts(task)
            if (showCategory || parts.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showCategory) CategoryTag(task.category)
                    if (parts.isNotEmpty()) {
                        if (overdue) Icon(Icons.Rounded.Error, contentDescription = null, tint = Think.colors.coral, modifier = Modifier.size(13.dp))
                        Text(
                            parts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) Think.colors.coral else Think.colors.inkSecondary,
                        )
                    }
                }
            }
        }
        Chevron()
    }
}

private fun supportingParts(task: TaskItem): List<String> {
    val parts = mutableListOf<String>()
    if (task.isOverdue()) parts.add("Overdue") else if (task.dueDate != null) parts.add(task.timeLabel())
    if (task.status == TaskStatus.IN_PROGRESS) parts.add("In progress")
    if (task.subtasks.isNotEmpty()) parts.add("${task.completedSubtaskCount}/${task.subtasks.size} steps")
    val trigger = task.locationTrigger
    if (trigger != null) {
        parts.add("${if (trigger.kind == LocationTriggerKind.ARRIVING) "At" else "Leaving"} ${trigger.placeName}")
    } else if (task.locationName != null && task.dueDate == null) {
        parts.add(task.locationName)
    }
    if (task.estimatedMinutes != null && parts.size < 2) parts.add("${task.estimatedMinutes} min")
    return parts
}

/** Grouped card of task rows with hairline dividers. */
@Composable
fun TaskGroupCard(
    title: String?,
    tasks: List<TaskItem>,
    onSelect: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
    showCategory: Boolean = false,
    onToggle: ((TaskItem) -> Unit)? = null,
) {
    SectionCard(modifier = modifier, title = title) {
        tasks.forEachIndexed { index, task ->
            TaskRow(
                task = task,
                onClick = { onSelect(task) },
                showCategory = showCategory,
                onToggle = onToggle?.let { handler -> { handler(task) } },
            )
            if (index < tasks.size - 1) RowDivider()
        }
    }
}

@Composable
fun CountTile(title: String, count: Int, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.card().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconWell(icon, size = 38.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Think.colors.ink, maxLines = 1, softWrap = false)
            Text("$count task${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = Think.colors.inkSecondary, maxLines = 1)
        }
    }
}

@Composable
fun EmptyCard(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().card().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconWell(icon, size = 56.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
    }
}
