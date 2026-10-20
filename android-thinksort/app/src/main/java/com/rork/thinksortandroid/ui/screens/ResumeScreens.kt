package com.rork.thinksortandroid.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.thinksortandroid.data.Subtask
import com.rork.thinksortandroid.domain.lowercasedFirst
import com.rork.thinksortandroid.ui.BrainViewModel
import com.rork.thinksortandroid.ui.components.AccentCard
import com.rork.thinksortandroid.ui.components.CheckCircle
import com.rork.thinksortandroid.ui.components.CircleIconButton
import com.rork.thinksortandroid.ui.components.Eyebrow
import com.rork.thinksortandroid.ui.components.IconWell
import com.rork.thinksortandroid.ui.components.PrimaryButton
import com.rork.thinksortandroid.ui.components.QuickAction
import com.rork.thinksortandroid.ui.components.QuickActionRow
import com.rork.thinksortandroid.ui.components.RowDivider
import com.rork.thinksortandroid.ui.components.ScreenColumn
import com.rork.thinksortandroid.ui.components.SoftButton
import com.rork.thinksortandroid.ui.components.card
import com.rork.thinksortandroid.ui.navigation.AppTab
import com.rork.thinksortandroid.ui.navigation.LocalNav
import com.rork.thinksortandroid.ui.navigation.Sheet
import com.rork.thinksortandroid.ui.theme.Think

/** Signature "Where did I leave off?" screen. */
@Composable
fun LeftOffScreen(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val task = vm.leftOffTask(data)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            CircleIconButton(Icons.Rounded.Close, "Close", nav::back)
        }
        if (task == null) {
            Column(Modifier.weight(1f).padding(horizontal = Think.gutter), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                IconWell(Icons.Rounded.PauseCircle, size = 58.dp)
                Text("Nothing in progress", style = MaterialTheme.typography.headlineMedium, color = Think.colors.ink)
                Text("Start something and ThinkSort will remember exactly where you stopped.", style = MaterialTheme.typography.bodyLarge, color = Think.colors.inkSecondary)
            }
            return@Column
        }
        ScreenColumn(Modifier.weight(1f), spacing = 20.dp) {
            Text("You were ${task.title.lowercasedFirst}.", style = MaterialTheme.typography.displaySmall, color = Think.colors.ink)
            val done = task.subtasks.filter { it.isDone }
            if (done.isNotEmpty()) SubtaskList(done, isDone = true)
            val next = task.nextSubtask
            AccentCard {
                Eyebrow("Your next step", color = Think.colors.accent)
                if (next != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconWell(Icons.Rounded.Inventory2, size = 62.dp, background = Think.colors.surface)
                        Text(next.title, style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
                    }
                } else {
                    Text("Finish it off and mark it done.", style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
                }
            }
            val remaining = task.subtasks.filter { !it.isDone }.drop(1)
            if (remaining.isNotEmpty()) SubtaskList(remaining, isDone = false)
        }
        QuickActionRow(
            actions = listOf(QuickAction.RESUME, QuickAction.NOT_NOW, QuickAction.STUCK),
            onAction = { action ->
                when (action) {
                    QuickAction.RESUME, QuickAction.START -> {
                        vm.start(task.id)
                        nav.back()
                        nav.oneThing(task.id)
                    }
                    QuickAction.NOT_NOW, QuickAction.LATER -> {
                        vm.saveCheckpoint(task.id, "Picking it up later")
                        nav.back()
                    }
                    QuickAction.STUCK -> {
                        nav.back()
                        nav.show(Sheet.Stuck(task.id))
                    }
                    QuickAction.DONE -> {
                        vm.complete(task.id)
                        nav.back()
                    }
                }
            },
            modifier = Modifier.padding(horizontal = Think.gutter, vertical = 12.dp),
        )
    }
}

@Composable
private fun SubtaskList(subtasks: List<Subtask>, isDone: Boolean) {
    Column(Modifier.fillMaxWidth().card()) {
        subtasks.forEachIndexed { index, subtask ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                CheckCircle(isDone)
                Text(subtask.title, style = MaterialTheme.typography.bodyLarge, color = if (isDone) Think.colors.inkTertiary else Think.colors.inkSecondary)
            }
            if (index < subtasks.size - 1) RowDivider()
        }
    }
}

/** Distraction-free mode: only the one thing happening right now. */
@Composable
fun OneThingScreen(vm: BrainViewModel, taskID: String) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val haptics = LocalHapticFeedback.current
    val task = vm.task(taskID, data)
    var pulse by remember { mutableIntStateOf(0) }
    val scale by animateFloatAsState(if (pulse % 2 == 1) 1.03f else 1f, spring(dampingRatio = 0.5f), label = "pulse")

    fun advance() {
        val current = vm.task(taskID) ?: return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        pulse += 1
        val next = current.nextSubtask
        if (next != null) {
            vm.toggleSubtask(current.id, next.id)
            if (vm.task(current.id)?.isOpen != true) {
                nav.back()
                nav.flash("Finished — that's the whole thing.")
            }
        } else {
            vm.complete(current.id)
            nav.back()
            nav.flash("Done. Nice work.")
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Think.gutter - 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clickable {
                    task?.let { vm.saveCheckpoint(it.id, "Stopped here") }
                    nav.back()
                }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Close, null, tint = Think.colors.inkSecondary, modifier = Modifier.size(18.dp))
                Text("I'm stopping here", style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
            }
            Spacer(Modifier.weight(1f))
            if (task != null && task.subtasks.isNotEmpty()) {
                Text("${task.completedSubtaskCount}/${task.subtasks.size}", style = MaterialTheme.typography.labelLarge, color = Think.colors.accent, modifier = Modifier.padding(end = 8.dp))
            }
        }

        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Think.gutter), verticalArrangement = Arrangement.Center) {
            if (task == null) {
                Text("This task is gone.", style = MaterialTheme.typography.headlineSmall, color = Think.colors.inkSecondary)
            } else {
                val step = task.nextSubtask?.title
                Column(Modifier.scale(scale), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Eyebrow("Right now", color = Think.colors.accent)
                    AnimatedContent(
                        targetState = step ?: task.title,
                        transitionSpec = { (fadeIn() + slideInVertically { it / 4 }) togetherWith fadeOut() },
                        label = "step",
                    ) { text ->
                        Text(text, style = MaterialTheme.typography.displaySmall, color = Think.colors.ink)
                    }
                    if (step != null) {
                        Text("Part of ${task.title.lowercasedFirst}", style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
                    }
                    if (task.openPreparationItems.isNotEmpty()) {
                        Text("Bring: ${task.openPreparationItems.joinToString(", ") { it.title }}", style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
                    }
                }
            }
        }

        if (task != null) {
            Column(Modifier.padding(horizontal = Think.gutter, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PrimaryButton(if (task.nextSubtask == null) "Mark It Done" else "Step Done", ::advance, icon = Icons.Rounded.Check)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Later", {
                        vm.snooze(task.id, 30)
                        vm.saveCheckpoint(task.id, "Paused")
                        nav.back()
                    }, Modifier.weight(1f), icon = Icons.Rounded.Schedule, compact = true)
                    SoftButton("Stuck", {
                        nav.back()
                        nav.show(Sheet.Stuck(task.id))
                    }, Modifier.weight(1f), icon = Icons.Rounded.ErrorOutline, tint = Think.colors.coral, compact = true)
                }
                Row(
                    Modifier.clickable {
                        nav.back()
                        nav.selectTab(AppTab.FOCUS)
                    }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Timer, null, tint = Think.colors.accent, modifier = Modifier.size(18.dp))
                    Text("Start a focus timer", style = MaterialTheme.typography.bodyMedium, color = Think.colors.accent)
                }
            }
        }
    }
}
