package com.rork.thinksortandroid.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.thinksortandroid.data.TaskItem
import com.rork.thinksortandroid.data.nowMillis
import com.rork.thinksortandroid.domain.Dates
import com.rork.thinksortandroid.domain.NextAction
import com.rork.thinksortandroid.ui.BrainViewModel
import com.rork.thinksortandroid.ui.components.Chevron
import com.rork.thinksortandroid.ui.components.CountTile
import com.rork.thinksortandroid.ui.components.Eyebrow
import com.rork.thinksortandroid.ui.components.IconWell
import com.rork.thinksortandroid.ui.components.PrimaryButton
import com.rork.thinksortandroid.ui.components.QuickAction
import com.rork.thinksortandroid.ui.components.QuickActionRow
import com.rork.thinksortandroid.ui.components.RowDivider
import com.rork.thinksortandroid.ui.components.ScreenColumn
import com.rork.thinksortandroid.ui.components.SoftButton
import com.rork.thinksortandroid.ui.components.TimelineRow
import com.rork.thinksortandroid.ui.components.card
import com.rork.thinksortandroid.ui.components.icon
import com.rork.thinksortandroid.ui.components.timeLabel
import com.rork.thinksortandroid.ui.navigation.AppTab
import com.rork.thinksortandroid.ui.navigation.LocalNav
import com.rork.thinksortandroid.ui.navigation.Routes
import com.rork.thinksortandroid.ui.navigation.Sheet
import com.rork.thinksortandroid.ui.theme.Think
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val haptics = LocalHapticFeedback.current
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = nowMillis()
        }
    }

    val nextAction = remember(data, now) { vm.nextAction(data, now) }
    val todayTasks = remember(data, now) { vm.todayTasks(data, now) }
    val upcomingCount = remember(data, now) { vm.upcomingTasks(data, now).size }
    val checkpointTask = data.checkpoint?.let { vm.task(it.taskID, data) }?.takeIf { it.isOpen }

    fun handle(action: QuickAction, task: TaskItem) {
        when (action) {
            QuickAction.START, QuickAction.RESUME -> {
                vm.start(task.id)
                nav.oneThing(task.id)
            }
            QuickAction.DONE -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.complete(task.id)
                nav.flash("Done — nice.")
            }
            QuickAction.LATER, QuickAction.NOT_NOW -> {
                vm.snooze(task.id, 60)
                nav.flash("Moved an hour out.")
            }
            QuickAction.STUCK -> nav.show(Sheet.Stuck(task.id))
        }
    }

    ScreenColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Think.gutter, end = Think.gutter, top = 8.dp, bottom = 28.dp)) {
        Header(now = now, name = data.settings.displayName)

        if (checkpointTask != null) ResumeBanner(checkpointTask) { nav.push(Routes.LEFT_OFF) }

        AnimatedContent(
            targetState = nextAction,
            contentKey = { it?.task?.id },
            transitionSpec = { (fadeIn(tween(250)) + scaleIn(initialScale = 0.97f)) togetherWith fadeOut(tween(150)) },
            label = "nextUp",
        ) { action ->
            if (action != null) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    NextUpCard(action) { nav.openTask(action.task.id) }
                    QuickActionRow(
                        actions = listOf(QuickAction.START, QuickAction.DONE, QuickAction.LATER, QuickAction.STUCK),
                        onAction = { handle(it, action.task) },
                    )
                }
            } else {
                AllClearCard()
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountTile("Today", todayTasks.size, Icons.Rounded.EventAvailable, onClick = {}, modifier = Modifier.weight(1f))
            CountTile("Upcoming", upcomingCount, Icons.AutoMirrored.Rounded.FormatListBulleted, onClick = { nav.selectTab(AppTab.UPCOMING) }, modifier = Modifier.weight(1f))
        }

        val timeline = todayTasks.take(5)
        if (timeline.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Today", style = MaterialTheme.typography.titleMedium, color = Think.colors.inkSecondary, modifier = Modifier.padding(start = 4.dp))
                Column(Modifier.fillMaxWidth().card()) {
                    timeline.forEachIndexed { index, task ->
                        val overdue = task.isOverdue(now)
                        TimelineRow(
                            timeLabel = if (overdue) "Overdue" else task.timeLabel(now),
                            title = task.title,
                            isOverdue = overdue,
                            modifier = Modifier.clickable { nav.openTask(task.id) }.padding(horizontal = 18.dp),
                        )
                        if (index < timeline.size - 1) RowDivider()
                    }
                }
            }
        }

        PrimaryButton("Dump My Brain", onClick = { nav.selectTab(AppTab.BRAIN_DUMP) }, icon = Icons.Rounded.Mic)
        SoftButton("Ask ThinkSort", onClick = { nav.show(Sheet.QuickAdd) }, icon = Icons.AutoMirrored.Rounded.Chat)
    }
}

@Composable
private fun Header(now: Long, name: String) {
    val nav = LocalNav.current
    var menuOpen by remember { mutableStateOf(false) }
    val greeting = when (Dates.hour(now)) {
        in 0..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (name.isBlank()) greeting else "$greeting, ${name.trim()}",
                style = MaterialTheme.typography.displaySmall,
                color = Think.colors.ink,
            )
            Text(Dates.weekdayMonthDay(now), style = MaterialTheme.typography.titleLarge, color = Think.colors.inkSecondary)
        }
        Box {
            IconWell(
                Icons.Rounded.Person,
                size = 48.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { menuOpen = true }
                    .semantics { contentDescription = "More options" },
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = Think.colors.surface,
                shape = RoundedCornerShape(18.dp),
            ) {
                fun go(action: () -> Unit) {
                    menuOpen = false
                    action()
                }
                MenuRow("Morning briefing", Icons.Rounded.WbSunny) { go { nav.push(Routes.MORNING) } }
                MenuRow("Evening reset", Icons.Rounded.Bedtime) { go { nav.push(Routes.EVENING) } }
                HorizontalDivider(color = Think.colors.hairline)
                MenuRow("I only have a few minutes", Icons.Rounded.Timer) { go { nav.show(Sheet.TimeAvailable) } }
                MenuRow("What do I need before I go?", Icons.Rounded.ShoppingBag) { go { nav.show(Sheet.BeforeYouGo) } }
                MenuRow("What am I forgetting?", Icons.AutoMirrored.Rounded.HelpOutline) { go { nav.show(Sheet.Forgetting) } }
                MenuRow("I'm running late", Icons.AutoMirrored.Rounded.DirectionsRun) { go { nav.show(Sheet.RunningLate) } }
                HorizontalDivider(color = Think.colors.hairline)
                MenuRow("Notes & ideas", Icons.Rounded.Lightbulb) { go { nav.push(Routes.NOTES) } }
                MenuRow("Search", Icons.Rounded.Search) { go { nav.push(Routes.SEARCH) } }
                MenuRow("History", Icons.Rounded.History) { go { nav.push(Routes.HISTORY) } }
                MenuRow("Settings", Icons.Rounded.Settings) { go { nav.push(Routes.SETTINGS) } }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = Think.colors.accent) },
        onClick = onClick,
    )
}

@Composable
private fun NextUpCard(action: NextAction, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().card().clickable(onClick = onClick).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconWell(action.task.category.icon, size = 68.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow("Next up")
            Text(action.task.title, style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
            Text("WHY: ${action.reason}", style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
        }
    }
}

@Composable
private fun AllClearCard() {
    Column(
        Modifier.fillMaxWidth().card().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconWell(Icons.Rounded.Check, size = 56.dp)
        Text("Nothing is waiting on you", style = MaterialTheme.typography.headlineSmall, color = Think.colors.ink)
        Text(
            "When something pops into your head, dump it here and ThinkSort will sort it out.",
            style = MaterialTheme.typography.bodyMedium,
            color = Think.colors.inkSecondary,
        )
    }
}

@Composable
private fun ResumeBanner(task: TaskItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Think.colors.accentSoft)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconWell(Icons.AutoMirrored.Rounded.Undo, size = 40.dp, background = Think.colors.surface)
        Column(Modifier.weight(1f)) {
            Eyebrow("You left off", color = Think.colors.accent)
            Text(task.title, style = MaterialTheme.typography.titleMedium, color = Think.colors.ink, maxLines = 1)
        }
        Chevron()
    }
}
