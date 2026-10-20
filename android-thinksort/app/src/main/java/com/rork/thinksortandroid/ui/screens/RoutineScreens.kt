package com.rork.thinksortandroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.thinksortandroid.data.nowMillis
import com.rork.thinksortandroid.domain.Dates
import com.rork.thinksortandroid.services.CalendarService
import com.rork.thinksortandroid.ui.BrainViewModel
import com.rork.thinksortandroid.ui.components.AccentCard
import com.rork.thinksortandroid.ui.components.CardRow
import com.rork.thinksortandroid.ui.components.CheckCircle
import com.rork.thinksortandroid.ui.components.DetailTopBar
import com.rork.thinksortandroid.ui.components.Eyebrow
import com.rork.thinksortandroid.ui.components.InfoCard
import com.rork.thinksortandroid.ui.components.PrimaryButton
import com.rork.thinksortandroid.ui.components.RowDivider
import com.rork.thinksortandroid.ui.components.ScreenColumn
import com.rork.thinksortandroid.ui.components.SectionCard
import com.rork.thinksortandroid.ui.components.TimelineRow
import com.rork.thinksortandroid.ui.components.card
import com.rork.thinksortandroid.ui.components.timeLabel
import com.rork.thinksortandroid.ui.navigation.LocalNav
import com.rork.thinksortandroid.ui.theme.Think

@Composable
fun MorningBriefingScreen(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val events by CalendarService.events.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val context = LocalContext.current
    val now = nowMillis()
    LaunchedEffect(Unit) { if (data.settings.calendarEnabled) CalendarService.reload(context) }

    val today = vm.todayTasks(data)
    val todayEvents = events.filter { Dates.isToday(it.start, now) }
    val prepItems = vm.preparationBeforeLeaving(data).flatMap { e -> e.items.map { it.title } }.toSortedSet().toList()
    val first = vm.nextAction(data)?.task

    Column(Modifier.fillMaxSize()) {
        DetailTopBar("", onBack = nav::back, backLabel = "Close")
        ScreenColumn(Modifier.weight(1f), spacing = 22.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Good morning", style = MaterialTheme.typography.displaySmall, color = Think.colors.ink)
                Text(Dates.weekdayMonthDay(now), style = MaterialTheme.typography.titleLarge, color = Think.colors.inkSecondary)
            }
            if (todayEvents.isNotEmpty()) {
                SectionCard(title = "On your calendar") {
                    todayEvents.forEachIndexed { index, event ->
                        TimelineRow(if (event.isAllDay) "All day" else Dates.time(event.start), event.title, Modifier.padding(horizontal = 18.dp), showChevron = false)
                        if (index < todayEvents.size - 1) RowDivider()
                    }
                }
            }
            if (today.isEmpty() && todayEvents.isEmpty()) {
                InfoCard("Clear morning", "Nothing is scheduled. If something's on your mind, dump it and ThinkSort will sort it out.")
            } else if (today.isNotEmpty()) {
                val shown = today.take(6)
                SectionCard(title = "Today") {
                    shown.forEachIndexed { index, task ->
                        val overdue = task.isOverdue()
                        TimelineRow(if (overdue) "Overdue" else task.timeLabel(), task.title, Modifier.padding(horizontal = 18.dp), isOverdue = overdue, showChevron = false)
                        if (index < shown.size - 1) RowDivider()
                    }
                }
            }
            if (prepItems.isNotEmpty()) {
                SectionCard(title = "Before you leave") {
                    prepItems.forEachIndexed { index, item ->
                        CardRow(onClick = null, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                            Icon(Icons.Rounded.ShoppingBag, null, tint = Think.colors.accent, modifier = Modifier.size(18.dp))
                            Text(item, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink)
                        }
                        if (index < prepItems.size - 1) RowDivider()
                    }
                }
            }
            first?.let {
                AccentCard {
                    Eyebrow("First thing", color = Think.colors.accent)
                    Text(it.title, style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
                }
            }
            PrimaryButton("Start My Day", onClick = {
                vm.updateSettings { s -> s.copy(lastMorningBriefing = nowMillis()) }
                nav.back()
                first?.let { task ->
                    vm.start(task.id)
                    nav.oneThing(task.id)
                }
            }, icon = Icons.Rounded.WbSunny)
        }
    }
}

@Composable
fun EveningResetScreen(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val completed = vm.completedToday(data)
    val unfinished = vm.todayTasks(data)
    val tomorrowPrep = vm.preparationBeforeLeaving(data).filter { e -> e.task.dueDate?.let { Dates.isTomorrow(it) } ?: false }

    var moveIds by remember { mutableStateOf(unfinished.map { it.id }.toSet()) }
    var prepIds by remember { mutableStateOf(tomorrowPrep.map { it.task.id }.toSet()) }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar("", onBack = nav::back, backLabel = "Close")
        ScreenColumn(Modifier.weight(1f), spacing = 22.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Evening reset", style = MaterialTheme.typography.displaySmall, color = Think.colors.ink)
                Text("Two minutes to clear tomorrow's runway.", style = MaterialTheme.typography.titleLarge, color = Think.colors.inkSecondary)
            }
            SectionCard(title = "What you got done") {
                if (completed.isEmpty()) {
                    Text(
                        "Nothing checked off today — that happens. Tomorrow's a clean start.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Think.colors.inkSecondary,
                        modifier = Modifier.padding(18.dp),
                    )
                } else {
                    completed.forEachIndexed { index, task ->
                        CardRow(onClick = null, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                            CheckCircle(true)
                            Text(task.title, style = MaterialTheme.typography.bodyLarge, color = Think.colors.inkSecondary)
                        }
                        if (index < completed.size - 1) RowDivider()
                    }
                }
            }
            if (unfinished.isNotEmpty()) {
                SectionCard(title = "What didn't get done") {
                    unfinished.forEachIndexed { index, task ->
                        val moving = task.id in moveIds
                        CardRow(onClick = { moveIds = if (moving) moveIds - task.id else moveIds + task.id }, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                            CheckCircle(moving)
                            Column(Modifier.weight(1f)) {
                                Text(task.title, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink)
                                Text(if (moving) "Moving to tomorrow" else "Leaving it where it is", style = MaterialTheme.typography.bodySmall, color = Think.colors.inkTertiary)
                            }
                        }
                        if (index < unfinished.size - 1) RowDivider()
                    }
                }
            }
            if (tomorrowPrep.isNotEmpty()) {
                SectionCard(title = "Prepare tonight") {
                    tomorrowPrep.forEachIndexed { index, entry ->
                        val on = entry.task.id in prepIds
                        CardRow(onClick = { prepIds = if (on) prepIds - entry.task.id else prepIds + entry.task.id }, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                            CheckCircle(on)
                            Column(Modifier.weight(1f)) {
                                Text(entry.task.title, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink)
                                Text("Grab: ${entry.items.joinToString(", ") { it.title }}", style = MaterialTheme.typography.bodySmall, color = Think.colors.inkSecondary)
                            }
                        }
                        if (index < tomorrowPrep.size - 1) RowDivider()
                    }
                }
            }
            Row(Modifier.fillMaxWidth().card().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                SummaryColumn(completed.size, "Done", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(42.dp).background(Think.colors.hairline))
                SummaryColumn(moveIds.size, "To tomorrow", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(42.dp).background(Think.colors.hairline))
                SummaryColumn(prepIds.size, "Prep tonight", Modifier.weight(1f))
            }
            PrimaryButton("Confirm and Reset", onClick = {
                vm.eveningReset(moveIds.toList(), tomorrowPrep.filter { it.task.id in prepIds })
                nav.back()
                nav.flash("Tomorrow's set up.")
            }, icon = Icons.Rounded.Check)
        }
    }
}

@Composable
private fun SummaryColumn(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$count", style = MaterialTheme.typography.headlineMedium, color = Think.colors.accent)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Think.colors.inkSecondary)
    }
}
