package com.rork.thinksortandroid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.thinksortandroid.data.Priority
import com.rork.thinksortandroid.data.TaskItem
import com.rork.thinksortandroid.data.TaskStatus
import com.rork.thinksortandroid.domain.TaskBreakdown
import com.rork.thinksortandroid.ui.BrainViewModel
import com.rork.thinksortandroid.ui.components.CardRow
import com.rork.thinksortandroid.ui.components.CardTextField
import com.rork.thinksortandroid.ui.components.CheckCircle
import com.rork.thinksortandroid.ui.components.Chevron
import com.rork.thinksortandroid.ui.components.CircleIconButton
import com.rork.thinksortandroid.ui.components.DetailTopBar
import com.rork.thinksortandroid.ui.components.Eyebrow
import com.rork.thinksortandroid.ui.components.IconWell
import com.rork.thinksortandroid.ui.components.PrimaryButton
import com.rork.thinksortandroid.ui.components.RowDivider
import com.rork.thinksortandroid.ui.components.ScreenColumn
import com.rork.thinksortandroid.ui.components.SectionCard
import com.rork.thinksortandroid.ui.components.SoftButton
import com.rork.thinksortandroid.ui.components.StrikeText
import com.rork.thinksortandroid.ui.components.TextLink
import com.rork.thinksortandroid.ui.components.ThinkSheet
import com.rork.thinksortandroid.ui.components.BottomActions
import com.rork.thinksortandroid.ui.components.card
import com.rork.thinksortandroid.ui.components.icon
import com.rork.thinksortandroid.ui.components.timeLabel
import com.rork.thinksortandroid.ui.components.whenDescription
import com.rork.thinksortandroid.ui.navigation.LocalNav
import com.rork.thinksortandroid.ui.navigation.Sheet
import com.rork.thinksortandroid.ui.theme.Think

@Composable
fun TaskDetailScreen(vm: BrainViewModel, taskID: String) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val task = vm.task(taskID, data)
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var pickingDependency by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = "", onBack = nav::back) {
            if (task != null) {
                Box {
                    CircleIconButton(Icons.Rounded.MoreHoriz, "Task options", { menuOpen = true }, background = Think.colors.accentSoft, tint = Think.colors.accent)
                    DropdownMenu(menuOpen, { menuOpen = false }, containerColor = Think.colors.surface, shape = RoundedCornerShape(18.dp)) {
                        fun go(action: () -> Unit) {
                            menuOpen = false
                            action()
                        }
                        Item("Edit details", Icons.Rounded.Edit) { go { editing = true } }
                        if (task.subtasks.isEmpty()) Item("Break into steps", Icons.AutoMirrored.Rounded.FormatListBulleted) { go { vm.breakDown(task.id) } }
                        Item("Add a prerequisite", Icons.Rounded.AccountTree) { go { pickingDependency = true } }
                        Item("I'm stuck", Icons.Rounded.ErrorOutline) { go { nav.show(Sheet.Stuck(task.id)) } }
                        HorizontalDivider(color = Think.colors.hairline)
                        Item("Move to tomorrow", Icons.Rounded.EventRepeat) { go { vm.postpone(task.id); nav.flash("Moved to tomorrow.") } }
                        Item("Mark as waiting", Icons.Rounded.HourglassEmpty) { go { vm.setStatus(TaskStatus.WAITING, task.id) } }
                        HorizontalDivider(color = Think.colors.hairline)
                        Item("Delete", Icons.Rounded.DeleteOutline, destructive = true) { go { confirmDelete = true } }
                    }
                }
            }
        }

        if (task == null) {
            Column(Modifier.fillMaxSize().padding(Think.gutter), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Task not found", style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
                Text("It may have been deleted.", style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
            }
            return@Column
        }

        ScreenColumn(modifier = Modifier.weight(1f), spacing = 22.dp) {
            Header(task)
            if (task.details.isNotEmpty() || task.notes.isNotEmpty()) {
                SectionCard(title = "Notes") {
                    Text(
                        listOf(task.details, task.notes).filter { it.isNotEmpty() }.joinToString("\n\n"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Think.colors.ink,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
            if (task.subtasks.isNotEmpty()) StepsSection(vm, task) else BreakDownPrompt { vm.breakDown(task.id) }
            if (task.preparationItems.isNotEmpty() || TaskBreakdown.preparationSuggestions(task.title).isNotEmpty()) PrepSection(vm, task)

            val dependencies = vm.dependencies(task, data)
            if (dependencies.isNotEmpty()) {
                SectionCard(title = "Depends on") {
                    dependencies.forEachIndexed { index, dep ->
                        CardRow(onClick = { nav.openTask(dep.id) }) {
                            val done = dep.status == TaskStatus.COMPLETED
                            CheckCircle(done, Modifier.clip(CircleShape).clickable { if (done) vm.reopen(dep.id) else vm.complete(dep.id) })
                            Column(Modifier.weight(1f)) {
                                Text(dep.title, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink)
                                Text(if (done) "Done" else dep.whenDescription(), style = MaterialTheme.typography.bodySmall, color = Think.colors.inkSecondary)
                            }
                            Chevron()
                        }
                        if (index < dependencies.size - 1) RowDivider()
                    }
                }
            }

            val dependents = vm.dependents(task, data)
            if (dependents.isNotEmpty()) {
                SectionCard(title = "Needed for") {
                    dependents.forEachIndexed { index, dep ->
                        CardRow(onClick = { nav.openTask(dep.id) }) {
                            Icon(Icons.Rounded.SubdirectoryArrowRight, null, tint = Think.colors.accent, modifier = Modifier.size(18.dp))
                            Text(dep.title, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink, modifier = Modifier.weight(1f))
                            Text(dep.timeLabel(), style = MaterialTheme.typography.bodySmall, color = Think.colors.inkSecondary)
                        }
                        if (index < dependents.size - 1) RowDivider()
                    }
                }
            }

            AttributesCard(vm, task)
        }

        BottomActions {
            if (task.status == TaskStatus.COMPLETED) {
                SoftButton("Mark as not done", { vm.reopen(task.id) }, icon = Icons.AutoMirrored.Rounded.Undo)
            } else {
                PrimaryButton(
                    if (task.status == TaskStatus.IN_PROGRESS) "Resume This Task" else "Start This Task",
                    onClick = {
                        vm.start(task.id)
                        nav.oneThing(task.id)
                    },
                    icon = Icons.Rounded.PlayArrow,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Done", {
                        vm.complete(task.id)
                        nav.flash("Done — nice.")
                        nav.back()
                    }, Modifier.weight(1f), icon = Icons.Rounded.Check, compact = true)
                    SoftButton("Tomorrow", {
                        vm.postpone(task.id)
                        nav.flash("Moved to tomorrow.")
                    }, Modifier.weight(1f), icon = Icons.Rounded.Schedule, compact = true)
                }
            }
        }
    }

    if (task != null && editing) {
        TaskEditor(task, onDismiss = { editing = false }, onSave = {
            vm.update(it)
            editing = false
        })
    }
    if (task != null && pickingDependency) {
        DependencyPicker(vm, task) { pickingDependency = false }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Think.colors.surface,
            title = { Text("Delete this task?", color = Think.colors.ink) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(taskID)
                    nav.back()
                }) { Text("Delete", color = Think.colors.coral) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it", color = Think.colors.accent) } },
        )
    }
}

@Composable
private fun Item(label: String, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) Think.colors.coral else Think.colors.ink
    DropdownMenuItem(
        text = { Text(label, color = tint) },
        leadingIcon = { Icon(icon, null, tint = if (destructive) Think.colors.coral else Think.colors.accent) },
        onClick = onClick,
    )
}

@Composable
private fun Header(task: TaskItem) {
    val overdue = task.isOverdue()
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        IconWell(task.category.icon, size = 62.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(task.title, style = MaterialTheme.typography.headlineMedium, color = Think.colors.ink)
            Text(task.whenDescription(), style = MaterialTheme.typography.bodyMedium, color = if (overdue) Think.colors.coral else Think.colors.inkSecondary)
            if (overdue) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Error, null, tint = Think.colors.coral, modifier = Modifier.size(14.dp))
                    Text("Overdue", style = MaterialTheme.typography.labelSmall, color = Think.colors.coral)
                }
            }
        }
    }
}

@Composable
private fun StepsSection(vm: BrainViewModel, task: TaskItem) {
    var newStep by remember { mutableStateOf("") }
    SectionCard(
        title = "Steps",
        trailing = { Text("${task.completedSubtaskCount} of ${task.subtasks.size}", style = MaterialTheme.typography.bodySmall, color = Think.colors.inkTertiary) },
    ) {
        task.subtasks.forEach { subtask ->
            CardRow(onClick = { vm.toggleSubtask(task.id, subtask.id) }) {
                CheckCircle(subtask.isDone)
                StrikeText(subtask.title, subtask.isDone, Modifier.weight(1f))
            }
            RowDivider()
        }
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AddCircleOutline, null, tint = Think.colors.accent, modifier = Modifier.size(22.dp))
            CardTextField(newStep, { newStep = it }, "Add a step", onDone = {
                vm.addSubtask(newStep, task.id)
                newStep = ""
            })
        }
    }
}

@Composable
private fun BreakDownPrompt(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().card().clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconWell(Icons.AutoMirrored.Rounded.FormatListBulleted, size = 42.dp)
        Column(Modifier.weight(1f)) {
            Text("Feels too big?", style = MaterialTheme.typography.titleMedium, color = Think.colors.ink)
            Text("Break it into smaller steps", style = MaterialTheme.typography.bodySmall, color = Think.colors.inkSecondary)
        }
        Chevron()
    }
}

@Composable
private fun PrepSection(vm: BrainViewModel, task: TaskItem) {
    var newItem by remember { mutableStateOf("") }
    val suggestions = TaskBreakdown.preparationSuggestions(task.title)
    SectionCard(title = "Before you leave") {
        task.preparationItems.forEach { item ->
            CardRow(onClick = { vm.togglePreparationItem(task.id, item.id) }) {
                CheckCircle(item.isDone)
                StrikeText(item.title, item.isDone, Modifier.weight(1f))
                Icon(
                    Icons.Rounded.DeleteOutline,
                    "Remove ${item.title}",
                    tint = Think.colors.inkTertiary,
                    modifier = Modifier.size(20.dp).clickable { vm.removePreparationItem(item.id, task.id) },
                )
            }
            RowDivider()
        }
        if (task.preparationItems.isEmpty() && suggestions.isNotEmpty()) {
            CardRow(onClick = { vm.addPreparationItems(suggestions, task.id) }) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Think.colors.accent, modifier = Modifier.size(18.dp))
                Text("Add the usual: ${suggestions.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = Think.colors.accent, modifier = Modifier.weight(1f))
            }
            RowDivider()
        }
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AddCircleOutline, null, tint = Think.colors.accent, modifier = Modifier.size(22.dp))
            CardTextField(newItem, { newItem = it }, "Add something to bring", onDone = {
                vm.addPreparationItem(newItem, task.id)
                newItem = ""
            })
        }
    }
}

@Composable
private fun AttributesCard(vm: BrainViewModel, task: TaskItem) {
    var priorityOpen by remember { mutableStateOf(false) }
    var statusOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().card()) {
        AttributeRow(Icons.Rounded.Flag, "Priority", task.priority.label, { priorityOpen = true }) {
            DropdownMenu(priorityOpen, { priorityOpen = false }, containerColor = Think.colors.surface) {
                Priority.entries.forEach { p ->
                    DropdownMenuItem(text = { Text(p.label, color = Think.colors.ink) }, onClick = {
                        vm.setPriority(p, task.id)
                        priorityOpen = false
                    })
                }
            }
        }
        RowDivider(16.dp)
        AttributeRow(Icons.Rounded.RadioButtonUnchecked, "Status", task.status.label, { statusOpen = true }) {
            DropdownMenu(statusOpen, { statusOpen = false }, containerColor = Think.colors.surface) {
                TaskStatus.entries.forEach { s ->
                    DropdownMenuItem(text = { Text(s.label, color = Think.colors.ink) }, onClick = {
                        vm.setStatus(s, task.id)
                        statusOpen = false
                    })
                }
            }
        }
        task.locationTrigger?.let { trigger ->
            RowDivider(16.dp)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconWell(Icons.Rounded.Place, size = 38.dp)
                Column(Modifier.weight(1f)) {
                    Text("${trigger.kind.label} ${trigger.placeName}", style = MaterialTheme.typography.bodyMedium, color = Think.colors.ink)
                    Text(
                        "Place alerts aren't on for Android yet — you'll get a normal reminder instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Think.colors.inkSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttributeRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit, menu: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconWell(icon, size = 38.dp)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Think.colors.inkSecondary, modifier = Modifier.weight(1f))
        Box {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink)
            menu()
        }
    }
}

/** Links an existing open task as a prerequisite, or creates a new one. */
@Composable
private fun DependencyPicker(vm: BrainViewModel, task: TaskItem, onDismiss: () -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    var newTitle by remember { mutableStateOf("") }
    val current = vm.task(task.id, data) ?: task
    val candidates = vm.openTasks(data).filter { it.id != task.id && !current.dependencyIDs.contains(it.id) }

    ThinkSheet(title = "Prerequisites", onDismiss = onDismiss, dismissLabel = "Done") {
        SectionCard(title = "Add a new prerequisite") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 8.dp)) {
                CardTextField(newTitle, { newTitle = it }, "What has to happen first?", modifier = Modifier.weight(1f), onDone = {
                    vm.addNewPrerequisite(newTitle, task.id)
                    newTitle = ""
                })
                TextLink("Add", {
                    vm.addNewPrerequisite(newTitle, task.id)
                    newTitle = ""
                }, color = Think.colors.accent, enabled = newTitle.isNotBlank())
            }
        }
        if (candidates.isNotEmpty()) {
            SectionCard(title = "Or pick something you already have") {
                candidates.forEachIndexed { index, candidate ->
                    CardRow(onClick = {
                        vm.addDependency(candidate.id, task.id)
                        onDismiss()
                    }) {
                        Column(Modifier.weight(1f)) {
                            Text(candidate.title, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink)
                            Text(candidate.whenDescription(), style = MaterialTheme.typography.bodySmall, color = Think.colors.inkSecondary)
                        }
                    }
                    if (index < candidates.size - 1) RowDivider()
                }
            }
        }
        val deps = vm.dependencies(current, data)
        if (deps.isNotEmpty()) {
            SectionCard(title = "Current prerequisites") {
                deps.forEachIndexed { index, dep ->
                    CardRow(onClick = null) {
                        Text(dep.title, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink, modifier = Modifier.weight(1f))
                        TextLink("Remove", { vm.removeDependency(dep.id, task.id) }, color = Think.colors.coral)
                    }
                    if (index < deps.size - 1) RowDivider()
                }
            }
        }
        Eyebrow("ThinkSort surfaces prerequisites first in Next Up.", Modifier.padding(start = 4.dp), color = Think.colors.inkTertiary)
    }
}
