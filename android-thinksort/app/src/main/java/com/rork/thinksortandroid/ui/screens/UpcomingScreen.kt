package com.rork.thinksortandroid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.thinksortandroid.data.nowMillis
import com.rork.thinksortandroid.domain.Dates
import com.rork.thinksortandroid.ui.BrainViewModel
import com.rork.thinksortandroid.ui.components.CircleIconButton
import com.rork.thinksortandroid.ui.components.EmptyCard
import com.rork.thinksortandroid.ui.components.LargeTitle
import com.rork.thinksortandroid.ui.components.ScreenColumn
import com.rork.thinksortandroid.ui.components.TaskGroupCard
import com.rork.thinksortandroid.ui.navigation.LocalNav
import com.rork.thinksortandroid.ui.navigation.Sheet
import com.rork.thinksortandroid.ui.theme.Think

@Composable
fun UpcomingScreen(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val now = nowMillis()
    val grouped = remember(data) {
        vm.upcomingTasks(data, now)
            .groupBy { Dates.startOfDay(it.dueDate ?: now) }
            .toSortedMap()
            .map { (day, tasks) -> label(day, now) to tasks.sortedWith(BrainViewModel.chronologically) }
    }
    val unscheduled = remember(data) { vm.unscheduledTasks(data) }

    Column(Modifier.fillMaxSize()) {
        LargeTitle("Upcoming") {
            CircleIconButton(Icons.Rounded.Add, "Add something", { nav.show(Sheet.QuickAdd) }, background = Think.colors.accentSoft, tint = Think.colors.accent)
        }
        ScreenColumn {
            if (grouped.isEmpty() && unscheduled.isEmpty()) {
                EmptyCard(Icons.Rounded.CalendarMonth, "Nothing scheduled ahead", "Anything you say with a day or time attached will land here.")
            } else {
                grouped.forEach { (label, tasks) ->
                    TaskGroupCard(label, tasks, onSelect = { nav.openTask(it.id) }, onToggle = { vm.complete(it.id) })
                }
                if (unscheduled.isNotEmpty()) {
                    TaskGroupCard("No date yet", unscheduled, onSelect = { nav.openTask(it.id) }, showCategory = true, onToggle = { vm.complete(it.id) })
                }
            }
        }
    }
}

private fun label(day: Long, now: Long): String {
    if (Dates.isTomorrow(day, now)) return "Tomorrow"
    if (Dates.daysBetween(now, day) < 7) return Dates.weekday(day)
    return Dates.weekdayShortMonthDay(day)
}
