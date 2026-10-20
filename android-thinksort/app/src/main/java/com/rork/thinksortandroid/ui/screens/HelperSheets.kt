package com.rork.thinksortandroid.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.thinksortandroid.data.ItemCategory
import com.rork.thinksortandroid.data.StuckReason
import com.rork.thinksortandroid.data.TaskItem
import com.rork.thinksortandroid.domain.Dates
import com.rork.thinksortandroid.domain.NaturalLanguageOrganizer
import com.rork.thinksortandroid.domain.TaskBreakdown
import com.rork.thinksortandroid.domain.lowercasedFirst
import com.rork.thinksortandroid.services.SpeechState
import com.rork.thinksortandroid.ui.BrainViewModel
import com.rork.thinksortandroid.ui.components.AccentCard
import com.rork.thinksortandroid.ui.components.CardRow
import com.rork.thinksortandroid.ui.components.CardTextField
import com.rork.thinksortandroid.ui.components.CategoryTag
import com.rork.thinksortandroid.ui.components.CheckCircle
import com.rork.thinksortandroid.ui.components.Chevron
import com.rork.thinksortandroid.ui.components.ChoiceChip
import com.rork.thinksortandroid.ui.components.Eyebrow
import com.rork.thinksortandroid.ui.components.IconWell
import com.rork.thinksortandroid.ui.components.InfoCard
import com.rork.thinksortandroid.ui.components.PrimaryButton
import com.rork.thinksortandroid.ui.components.RowDivider
import com.rork.thinksortandroid.ui.components.SectionCard
import com.rork.thinksortandroid.ui.components.SoftButton
import com.rork.thinksortandroid.ui.components.TaskGroupCard
import com.rork.thinksortandroid.ui.components.TextLink
import com.rork.thinksortandroid.ui.components.ThinkSheet
import com.rork.thinksortandroid.ui.components.card
import com.rork.thinksortandroid.ui.components.rememberSpeech
import com.rork.thinksortandroid.ui.components.timeLabel
import com.rork.thinksortandroid.ui.components.whenDescription
import com.rork.thinksortandroid.ui.navigation.LocalNav
import com.rork.thinksortandroid.ui.navigation.Sheet
import com.rork.thinksortandroid.ui.theme.Think

private val StuckReason.icon: ImageVector
    get() = when (this) {
        StuckReason.DONT_KNOW_WHERE_TO_START -> Icons.AutoMirrored.Rounded.HelpOutline
        StuckReason.TOO_BIG -> Icons.Rounded.Layers
        StuckReason.NEED_SOMETHING -> Icons.Rounded.Inventory2
        StuckReason.NOT_ENOUGH_TIME -> Icons.Rounded.MoreTime
        StuckReason.DISTRACTED -> Icons.Rounded.Air
        StuckReason.DONT_WANT_TO -> Icons.Rounded.ThumbDown
        StuckReason.OTHER -> Icons.Rounded.MoreHoriz
    }

/** "I'm stuck" flow: name the friction, then get one tiny first step. */
@Composable
fun StuckSheet(vm: BrainViewModel, taskID: String) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val task = vm.task(taskID, data)
    var reason by remember { mutableStateOf<StuckReason?>(null) }

    ThinkSheet(title = if (reason == null) "What's making this hard?" else "Let's make it smaller", onDismiss = nav::dismissSheet) {
        AnimatedContent(targetState = reason, label = "stuck") { selected ->
            if (selected == null) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    task?.let { Text(it.title, style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink) }
                    Column(Modifier.fillMaxWidth().card()) {
                        StuckReason.entries.forEachIndexed { index, option ->
                            CardRow(onClick = { reason = option }, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                                IconWell(option.icon, size = 38.dp)
                                Text(option.label, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink, modifier = Modifier.weight(1f))
                                Chevron()
                            }
                            if (index < StuckReason.entries.size - 1) RowDivider(16.dp)
                        }
                    }
                }
            } else {
                val step = TaskBreakdown.tinyFirstStep(task?.title ?: "this", selected)
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    AccentCard {
                        Eyebrow("Start with just this", color = Think.colors.accent)
                        Text(step, style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
                        Text("That's the whole ask. You can stop after it.", style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
                    }
                    if ((selected == StuckReason.TOO_BIG || selected == StuckReason.DONT_KNOW_WHERE_TO_START) && task != null && task.subtasks.isEmpty()) {
                        SoftButton("Break the whole thing into steps", {
                            vm.breakDown(task.id)
                            nav.flash("Broke it into steps.")
                        }, icon = Icons.AutoMirrored.Rounded.FormatListBulleted, tint = Think.colors.accent)
                    }
                    if (selected == StuckReason.NOT_ENOUGH_TIME) {
                        SoftButton("Show me what fits in my time", { nav.show(Sheet.TimeAvailable) }, icon = Icons.Rounded.Timer, tint = Think.colors.accent)
                    }
                    if (selected == StuckReason.NEED_SOMETHING && task != null) {
                        SoftButton("Add what I need to the task", {
                            nav.dismissSheet()
                            nav.openTask(task.id)
                        }, icon = Icons.Rounded.AddCircleOutline, tint = Think.colors.accent)
                    }
                    PrimaryButton("Start", onClick = {
                        if (task == null) {
                            nav.dismissSheet()
                            return@PrimaryButton
                        }
                        if (task.subtasks.isEmpty() || (task.subtasks.none { it.title == step } && task.nextSubtask?.title != step)) {
                            vm.addSubtask(step, task.id)
                        }
                        vm.start(task.id)
                        nav.dismissSheet()
                        nav.oneThing(task.id)
                    }, icon = Icons.Rounded.PlayArrow)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SoftButton("Already Done", {
                            task?.subtasks?.firstOrNull { it.title == step && !it.isDone }?.let { vm.toggleSubtask(task.id, it.id) }
                            nav.dismissSheet()
                            nav.flash("Good — that's momentum.")
                        }, Modifier.weight(1f), icon = Icons.Rounded.Check, compact = true)
                        SoftButton("Skip", { reason = null }, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowForward, compact = true)
                    }
                }
            }
        }
    }
}

/** "I only have 10 minutes" — surfaces tasks that actually fit. */
@Composable
fun TimeAvailableSheet(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var minutes by remember { mutableIntStateOf(15) }
    val matches = remember(data, minutes) { vm.tasksFitting(minutes, data).take(6) }

    ThinkSheet(title = "Time available", onDismiss = nav::dismissSheet) {
        Text("How much time do you have?", style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30, 60).forEach { option ->
                ChoiceChip(if (option >= 60) "1h" else "${option}m", minutes == option, { minutes = option }, Modifier.weight(1f))
            }
        }
        val best = matches.firstOrNull()
        if (best != null) {
            AccentCard {
                Eyebrow("$minutes-minute task", color = Think.colors.accent)
                Text(best.title, style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
                Text(best.whenDescription(), style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
            }
            PrimaryButton("Start This One", onClick = {
                vm.start(best.id)
                nav.dismissSheet()
                nav.oneThing(best.id)
            }, icon = Icons.Rounded.PlayArrow)
            if (matches.size > 1) {
                TaskGroupCard("Other things that fit", matches.drop(1), onSelect = {
                    nav.dismissSheet()
                    nav.openTask(it.id)
                }, onToggle = { vm.complete(it.id) })
            }
        } else {
            InfoCard("Nothing that short right now", "Try a longer window, or open a big task and break it into steps.")
        }
    }
}

/** "I'm running late" — triage what happens next without touching appointments. */
@Composable
fun RunningLateSheet(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val today = vm.todayTasks(data)
    val next = today.firstOrNull()
    val rest = today.drop(1)

    ThinkSheet(title = "Running late", onDismiss = nav::dismissSheet) {
        if (next == null) {
            InfoCard("Nothing is waiting on you", "Your day is clear — take the pressure off.")
            return@ThinkSheet
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val due = next.dueDate
            Text(
                if (next.hasTime && due != null) "You're running late for ${Dates.time(due)} — ${next.title.lowercasedFirst}." else "Let's get you moving.",
                style = MaterialTheme.typography.headlineSmall,
                color = Think.colors.ink,
            )
            Text("Nothing gets cancelled unless you say so.", style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
        }
        AccentCard {
            Eyebrow("Next", color = Think.colors.accent)
            Text(next.title, style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
            if (next.openPreparationItems.isNotEmpty()) {
                Text("Grab: ${next.openPreparationItems.joinToString(", ") { it.title }}", style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
            }
        }
        if (rest.isNotEmpty()) {
            SectionCard(title = "Push to later") {
                rest.forEachIndexed { index, task ->
                    CardRow(onClick = null, padding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink)
                            Text(task.whenDescription(), style = MaterialTheme.typography.bodySmall, color = Think.colors.inkTertiary)
                        }
                        TextLink("Tomorrow", { vm.postpone(task.id) }, color = Think.colors.accent)
                    }
                    if (index < rest.size - 1) RowDivider()
                }
            }
            SoftButton("Push everything else to tomorrow", {
                rest.filter { it.category != ItemCategory.APPOINTMENT }.forEach { vm.postpone(it.id) }
                nav.dismissSheet()
                nav.flash("Cleared the rest of today.")
            }, icon = Icons.Rounded.Redo, tint = Think.colors.accent)
        }
        PrimaryButton("Go Do This", onClick = {
            vm.start(next.id)
            nav.dismissSheet()
            nav.oneThing(next.id)
        }, icon = Icons.AutoMirrored.Rounded.DirectionsWalk)
    }
}

/** "What am I forgetting?" */
@Composable
fun ForgettingSheet(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val items = vm.forgottenItems(data)

    fun reason(task: TaskItem): String {
        if (task.isOverdue()) return "Overdue — was due ${task.timeLabel()}"
        if (task.postponeCount >= 2) return "You've moved this ${task.postponeCount} times"
        vm.dependents(task, data).firstOrNull { it.isOpen }?.let { return "${it.title} is waiting on this" }
        if (task.openPreparationItems.isNotEmpty()) return "Still need: ${task.openPreparationItems.joinToString(", ") { it.title }}"
        return task.whenDescription()
    }

    ThinkSheet(title = "What am I forgetting?", onDismiss = nav::dismissSheet) {
        if (items.isEmpty()) {
            InfoCard("Nothing slipping", "Nothing is overdue, stalled, or blocking something else.")
        } else {
            Text("These are the ones quietly slipping:", style = MaterialTheme.typography.titleLarge, color = Think.colors.inkSecondary)
            items.forEach { task ->
                Column(
                    Modifier.fillMaxWidth().card().clickable {
                        nav.dismissSheet()
                        nav.openTask(task.id)
                    }.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, color = Think.colors.ink)
                    Text(reason(task), style = MaterialTheme.typography.bodyMedium, color = if (task.isOverdue()) Think.colors.coral else Think.colors.inkSecondary)
                }
            }
        }
    }
}

/** "What do I need before I go?" */
@Composable
fun BeforeYouGoSheet(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val entries = vm.preparationBeforeLeaving(data)

    ThinkSheet(title = "Before you go", onDismiss = nav::dismissSheet) {
        if (entries.isEmpty()) {
            InfoCard("Nothing to grab", "None of today's or tomorrow's tasks have things you need to bring. You can add them from any task.")
        } else {
            Text("Before you leave, grab:", style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
            entries.forEach { entry ->
                SectionCard(title = "${entry.task.title} · ${entry.task.timeLabel()}") {
                    entry.items.forEachIndexed { index, item ->
                        CardRow(onClick = { vm.togglePreparationItem(entry.task.id, item.id) }) {
                            CheckCircle(item.isDone)
                            Text(item.title, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink, modifier = Modifier.weight(1f))
                        }
                        if (index < entry.items.size - 1) RowDivider()
                    }
                }
            }
            PrimaryButton("I've Got Everything", onClick = {
                vm.markPreparationDone(entries)
                nav.dismissSheet()
                nav.flash("All packed.")
            }, icon = Icons.Rounded.Check)
        }
    }
}

private val quickExamples = listOf(
    "Remind me to call John tomorrow at 9",
    "Next time I'm at Walmart, remind me to buy batteries",
    "I need to finish the taxes before Friday",
    "Tomorrow clean the garage, but I need to buy trash bags first",
)

/** "Ask ThinkSort" — single-line capture with a live preview of what will be created. */
@Composable
fun QuickAddSheet(vm: BrainViewModel) {
    val nav = LocalNav.current
    val speech = rememberSpeech()
    val speechState by speech.service.state.collectAsStateWithLifecycle()
    val transcript by speech.service.transcript.collectAsStateWithLifecycle()
    val isRecording = speechState == SpeechState.Recording
    var text by remember { mutableStateOf("") }
    val current = if (isRecording) transcript else text
    val preview = remember(current) {
        if (current.trim().length > 3) NaturalLanguageOrganizer().organize(current).items else emptyList()
    }

    fun save() {
        if (isRecording) {
            text = transcript
            speech.service.stop()
        }
        val input = (if (isRecording) transcript else text).trim()
        if (input.length < 3) return
        val message = vm.quickCapture(input)
        nav.dismissSheet()
        nav.flash(message)
    }

    ThinkSheet(title = "Ask ThinkSort", onDismiss = nav::dismissSheet, dismissLabel = "Cancel") {
        Text("Say it however it comes out.", style = MaterialTheme.typography.titleLarge, color = Think.colors.inkSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f).card().padding(4.dp)) {
                CardTextField(current, { if (!isRecording) text = it }, "Type or dictate…", singleLine = false, minLines = 2, maxLines = 6)
            }
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) Think.colors.coralSoft else Think.colors.accentSoft)
                    .clickable {
                        if (isRecording) {
                            text = transcript
                            speech.service.stop()
                        } else {
                            speech.start(text)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    if (isRecording) "Stop dictation" else "Dictate",
                    tint = if (isRecording) Think.colors.coral else Think.colors.accent,
                )
            }
        }

        if (preview.isNotEmpty()) {
            SectionCard(title = "I'll create") {
                preview.forEachIndexed { index, item ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryTag(item.category)
                            item.dueDate?.let { due ->
                                Text(
                                    if (item.hasTime) Dates.abbreviatedWithTime(due) else Dates.abbreviated(due),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Think.colors.inkSecondary,
                                )
                            }
                        }
                    }
                    if (index < preview.size - 1) RowDivider()
                }
            }
        }

        if (current.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("Try saying", Modifier.padding(start = 4.dp))
                quickExamples.forEach { example ->
                    Text(
                        "“$example”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Think.colors.inkSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Think.colors.surfaceMuted)
                            .clickable { text = example }
                            .padding(14.dp),
                    )
                }
            }
        }

        PrimaryButton("Add It", ::save, icon = Icons.Rounded.Add, enabled = current.trim().length >= 3)
    }
}
