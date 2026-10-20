package com.rork.thinksortandroid.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.thinksortandroid.data.FocusSessionRecord
import com.rork.thinksortandroid.domain.Dates
import com.rork.thinksortandroid.ui.BrainViewModel
import com.rork.thinksortandroid.ui.FocusViewModel
import com.rork.thinksortandroid.ui.components.ChoiceChip
import com.rork.thinksortandroid.ui.components.Eyebrow
import com.rork.thinksortandroid.ui.components.IconWell
import com.rork.thinksortandroid.ui.components.LargeTitle
import com.rork.thinksortandroid.ui.components.PrimaryButton
import com.rork.thinksortandroid.ui.components.RowDivider
import com.rork.thinksortandroid.ui.components.ScreenColumn
import com.rork.thinksortandroid.ui.components.SectionCard
import com.rork.thinksortandroid.ui.components.SoftButton
import com.rork.thinksortandroid.ui.components.card
import com.rork.thinksortandroid.ui.theme.Think

private val presets = listOf(5, 15, 25)
private val customOptions = listOf(10, 20, 30, 40, 45, 50, 60, 75, 90)

@Composable
fun FocusScreen(vm: BrainViewModel, focus: FocusViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val state by focus.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var pickerOpen by remember { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focus.applyDefaultMinutes(data.settings.defaultFocusMinutes) }
    LaunchedEffect(state.showKeepGoing) {
        if (state.showKeepGoing) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val task = state.selectedTaskID?.let { vm.task(it, data) }?.takeIf { it.isOpen } ?: vm.nextAction(data)?.task

    fun finish(completed: Boolean) {
        val (elapsed, startedAt) = focus.finish()
        if (task != null && startedAt != null) {
            vm.recordFocusSession(
                FocusSessionRecord(
                    taskID = task.id,
                    taskTitle = task.title,
                    plannedMinutes = state.minutes,
                    completedMinutes = elapsed,
                    startedAt = startedAt,
                    endedAt = System.currentTimeMillis(),
                    finished = completed,
                )
            )
            if (!completed) vm.saveCheckpoint(task.id, "Paused a focus session")
        }
    }

    Column(Modifier.fillMaxSize()) {
        LargeTitle("Focus")
        ScreenColumn(spacing = 24.dp) {
            if (task != null) {
                Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Eyebrow("Focusing on")
                    Text(task.title, style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
                    task.nextSubtask?.let {
                        Text("Next step: ${it.title}", style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
                    }
                    if (!state.isRunning) {
                        Box {
                            Row(
                                Modifier.clickable { pickerOpen = true }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Rounded.SwapHoriz, null, tint = Think.colors.accent, modifier = Modifier.size(18.dp))
                                Text("Pick a different task", style = MaterialTheme.typography.bodyMedium, color = Think.colors.accent)
                            }
                            DropdownMenu(pickerOpen, { pickerOpen = false }, containerColor = Think.colors.surface, shape = RoundedCornerShape(18.dp)) {
                                vm.openTasks(data).forEach { candidate ->
                                    DropdownMenuItem(
                                        text = { Text(candidate.title, color = Think.colors.ink) },
                                        onClick = {
                                            focus.select(candidate.id)
                                            pickerOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nothing to focus on yet", style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
                    Text("Add something first and it'll show up here.", style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
                }
            }

            TimerRing(progress = state.progress, remaining = state.remaining, isRunning = state.isRunning)

            if (!state.isRunning) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    presets.forEach { preset ->
                        ChoiceChip("${preset}m", state.minutes == preset, { focus.setMinutes(preset) }, Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f)) {
                        ChoiceChip(
                            if (state.minutes in presets) "Custom" else "${state.minutes}m",
                            state.minutes !in presets,
                            { customOpen = true },
                            Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(customOpen, { customOpen = false }, containerColor = Think.colors.surface, shape = RoundedCornerShape(18.dp)) {
                            customOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("$option minutes", color = Think.colors.ink) },
                                    onClick = {
                                        focus.setMinutes(option)
                                        customOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    if (state.isRunning) "Pause" else "Start Focus",
                    onClick = {
                        if (state.isRunning) {
                            focus.pause()
                        } else if (task != null) {
                            vm.start(task.id)
                            focus.select(task.id)
                            focus.start()
                        }
                    },
                    icon = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    enabled = task != null,
                )
                if (state.hasProgress) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SoftButton("Stop", { finish(false) }, Modifier.weight(1f), icon = Icons.Rounded.Stop, compact = true)
                        SoftButton("Task Done", {
                            if (task != null) {
                                vm.complete(task.id)
                                finish(true)
                            }
                        }, Modifier.weight(1f), icon = Icons.Rounded.Check, compact = true)
                    }
                }
            }

            if (data.focusSessions.isNotEmpty()) {
                val sessions = data.focusSessions.take(4)
                SectionCard(title = "Recent focus") {
                    sessions.forEachIndexed { index, session ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconWell(if (session.finished) Icons.Rounded.Check else Icons.Rounded.Pause, size = 34.dp)
                            Column(Modifier.weight(1f)) {
                                Text(session.taskTitle, style = MaterialTheme.typography.bodyMedium, color = Think.colors.ink, maxLines = 1)
                                Text(
                                    "${session.completedMinutes} min · ${Dates.abbreviatedWithTime(session.startedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Think.colors.inkTertiary,
                                )
                            }
                        }
                        if (index < sessions.size - 1) RowDivider(16.dp)
                    }
                }
            }
        }
    }

    if (state.showKeepGoing) {
        AlertDialog(
            onDismissRequest = { focus.dismissKeepGoing() },
            containerColor = Think.colors.surface,
            title = { Text("Time's up", color = Think.colors.ink) },
            text = { Text("Do you want to keep going?", color = Think.colors.inkSecondary) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { focus.extend(10) }) { Text("Keep going 10 more", color = Think.colors.accent) }
                    TextButton(onClick = { finish(true) }) { Text("I'm done", color = Think.colors.accent) }
                    TextButton(onClick = {
                        task?.let { vm.complete(it.id) }
                        finish(true)
                    }) { Text("Mark task done", color = Think.colors.accent) }
                }
            },
        )
    }
}

@Composable
private fun TimerRing(progress: Float, remaining: Int, isRunning: Boolean) {
    val animated by animateFloatAsState(progress, tween(900), label = "ring")
    val track = Think.colors.hairline
    val accent = Think.colors.accent
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { contentDescription = "Focus timer, ${remaining / 60} minutes ${remaining % 60} seconds remaining" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(240.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(accent, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "%d:%02d".format(remaining / 60, remaining % 60),
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = Think.colors.ink,
            )
            Text(if (isRunning) "Focusing" else "Ready", style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
        }
    }
}
