package com.rork.thinksortandroid.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowCircleRight
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.thinksortandroid.data.ItemCategory
import com.rork.thinksortandroid.data.Priority
import com.rork.thinksortandroid.data.TaskItem
import com.rork.thinksortandroid.data.nowMillis
import com.rork.thinksortandroid.domain.NaturalLanguageOrganizer
import com.rork.thinksortandroid.domain.capitalizedFirst
import com.rork.thinksortandroid.services.CalendarService
import com.rork.thinksortandroid.services.SpeechState
import com.rork.thinksortandroid.ui.BrainViewModel
import com.rork.thinksortandroid.ui.components.AccentCard
import com.rork.thinksortandroid.ui.components.CardTextField
import com.rork.thinksortandroid.ui.components.Eyebrow
import com.rork.thinksortandroid.ui.components.IconWell
import com.rork.thinksortandroid.ui.components.PrimaryButton
import com.rork.thinksortandroid.ui.components.TextLink
import com.rork.thinksortandroid.ui.components.card
import com.rork.thinksortandroid.ui.components.icon
import com.rork.thinksortandroid.ui.components.rememberSpeech
import com.rork.thinksortandroid.ui.theme.Think
import kotlinx.coroutines.launch

private enum class Stage { INTRO, PERMISSIONS, FIRST_TASK, FIRST_STEP }

private data class Page(val icon: ImageVector, val title: String, val body: String)

private val pages = listOf(
    Page(Icons.Rounded.Psychology, "Welcome to ThinkSort", "Add a little structure to your daily chaos."),
    Page(Icons.Rounded.Mic, "Dump Your Brain", "Tell us what's on your mind. We'll help organize it."),
    Page(Icons.Rounded.ArrowCircleRight, "Know What's Next", "ThinkSort helps you focus on one thing at a time."),
    Page(Icons.Rounded.Bookmark, "Never Lose Your Place", "Ask where you left off and pick up where you stopped."),
    Page(Icons.Rounded.Widgets, "Talk, Don't Type", "Say it however it comes out — ThinkSort sorts the tasks, times and lists."),
)

/**
 * First-run flow: five calm pages, permissions explained one at a time (a single
 * neutral "Continue" that always proceeds to the system prompt), then first-task capture.
 */
@Composable
fun OnboardingScreen(vm: BrainViewModel, onStartTask: (String) -> Unit) {
    var stage by remember { mutableStateOf(Stage.INTRO) }
    var createdTask by remember { mutableStateOf<TaskItem?>(null) }

    fun complete() = vm.updateSettings { it.copy(hasCompletedOnboarding = true) }

    Box(Modifier.fillMaxSize().background(Think.colors.canvas).statusBarsPadding().navigationBarsPadding()) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = { (fadeIn() + slideInHorizontally { it / 6 }) togetherWith fadeOut() },
            label = "onboarding",
        ) { current ->
            when (current) {
                Stage.INTRO -> IntroFlow { stage = Stage.PERMISSIONS }
                Stage.PERMISSIONS -> PermissionsFlow(vm) { stage = Stage.FIRST_TASK }
                Stage.FIRST_TASK -> FirstTaskFlow(
                    onCreate = { task ->
                        vm.add(task)
                        createdTask = task
                        stage = Stage.FIRST_STEP
                    },
                    onSkip = ::complete,
                )
                Stage.FIRST_STEP -> FirstStepFlow(
                    task = createdTask,
                    onStart = {
                        val task = createdTask
                        complete()
                        if (task != null) {
                            vm.start(task.id)
                            onStartTask(task.id)
                        }
                    },
                    onSkip = ::complete,
                )
            }
        }
    }
}

@Composable
private fun IntroFlow(onDone: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
            TextLink("Skip", onDone)
        }
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize().padding(horizontal = Think.gutter),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                IconWell(page.icon, size = 92.dp)
                Text(page.title, style = MaterialTheme.typography.displaySmall, color = Think.colors.ink)
                Text(page.body, style = MaterialTheme.typography.titleLarge, color = Think.colors.inkSecondary)
                Spacer(Modifier.height(60.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { index ->
                val width by animateDpAsState(if (pager.currentPage == index) 22.dp else 8.dp, label = "dot")
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(if (pager.currentPage == index) Think.colors.accent else Think.colors.hairline)
                )
            }
        }
        PrimaryButton(
            if (pager.currentPage < pages.size - 1) "Next" else "Let's Go",
            onClick = {
                if (pager.currentPage < pages.size - 1) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else onDone()
            },
            modifier = Modifier.padding(horizontal = Think.gutter).padding(bottom = 16.dp),
        )
    }
}

private data class Ask(val icon: ImageVector, val title: String, val body: String, val permission: String)

@Composable
private fun PermissionsFlow(vm: BrainViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val asks = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Ask(Icons.Rounded.NotificationsActive, "Reminders at the right moment",
                    "ThinkSort can nudge you before something starts and tell you what to bring. It keeps alerts deliberately few, and holds them during quiet hours.",
                    Manifest.permission.POST_NOTIFICATIONS))
            }
            add(Ask(Icons.Rounded.GraphicEq, "Talk instead of typing",
                "Speak your brain dump and ThinkSort turns it into tasks. This uses the microphone and your phone's speech service.",
                Manifest.permission.RECORD_AUDIO))
            add(Ask(Icons.Rounded.CalendarMonth, "Know how much time you have",
                "Reading your calendar lets ThinkSort suggest things that actually fit between your events. It never creates or changes an event.",
                Manifest.permission.READ_CALENDAR))
        }
    }
    var index by remember { mutableIntStateOf(0) }
    var requesting by remember { mutableStateOf(false) }

    fun next() {
        requesting = false
        if (index < asks.size - 1) index += 1 else onDone()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when (asks[index].permission) {
            Manifest.permission.READ_CALENDAR -> {
                vm.updateSettings { it.copy(calendarEnabled = granted) }
                if (granted) scope.launch { CalendarService.reload(context) }
            }
            Manifest.permission.POST_NOTIFICATIONS -> vm.refreshNotifications()
        }
        next()
    }

    val ask = asks[index]
    AnimatedContent(targetState = ask, label = "ask") { current ->
        Column(Modifier.fillMaxSize().padding(horizontal = Think.gutter), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.weight(1f))
            IconWell(current.icon, size = 84.dp)
            Text(current.title, style = MaterialTheme.typography.displaySmall, color = Think.colors.ink)
            Text(current.body, style = MaterialTheme.typography.titleLarge, color = Think.colors.inkSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                "${index + 1} of ${asks.size} · You can change this anytime in Android Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = Think.colors.inkTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton("Continue", onClick = {
                if (!requesting) {
                    requesting = true
                    launcher.launch(current.permission)
                }
            }, enabled = !requesting, modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun FirstTaskFlow(onCreate: (TaskItem) -> Unit, onSkip: () -> Unit) {
    val speech = rememberSpeech()
    val state by speech.service.state.collectAsStateWithLifecycle()
    val transcript by speech.service.transcript.collectAsStateWithLifecycle()
    val isRecording = state == SpeechState.Recording
    var text by remember { mutableStateOf("") }
    val current = if (isRecording) transcript else text

    fun create() {
        if (isRecording) {
            text = transcript
            speech.service.stop()
        }
        val input = (if (isRecording) transcript else text).trim()
        if (input.isEmpty()) return
        val item = NaturalLanguageOrganizer().parseSingle(input)
        val task = TaskItem(
            title = item?.title ?: input.capitalizedFirst,
            category = item?.category?.takeIf { it != ItemCategory.IDEA && it != ItemCategory.NOTE && it != ItemCategory.SHOPPING } ?: ItemCategory.TASK,
            dueDate = item?.dueDate ?: nowMillis(),
            hasTime = item?.hasTime ?: false,
            priority = Priority.NOW,
            estimatedMinutes = item?.estimatedMinutes,
            locationName = item?.locationName,
        )
        onCreate(task)
    }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = Think.gutter), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.weight(1f))
        Text("What's something you need to get done today?", style = MaterialTheme.typography.displaySmall, color = Think.colors.ink)
        Text("Say it however it comes out — ThinkSort will sort the details.", style = MaterialTheme.typography.titleLarge, color = Think.colors.inkSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f).card().padding(4.dp)) {
                CardTextField(current, { if (!isRecording) text = it }, "Call the electric company…", singleLine = false, maxLines = 4, onDone = ::create)
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
                    }
                    .semantics { contentDescription = if (isRecording) "Stop dictation" else "Dictate" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic, null, tint = if (isRecording) Think.colors.coral else Think.colors.accent)
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Add It", ::create, icon = Icons.AutoMirrored.Rounded.ArrowForward, enabled = current.trim().length >= 3)
        TextLink("I'll do this later", onSkip, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
    }
}

@Composable
private fun FirstStepFlow(task: TaskItem?, onStart: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = Think.gutter), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.weight(1f))
        Eyebrow("Your first next step", color = Think.colors.accent)
        if (task != null) {
            AccentCard {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconWell(task.category.icon, size = 62.dp, background = Think.colors.surface)
                    Text(task.title, style = MaterialTheme.typography.headlineMedium, color = Think.colors.ink)
                }
                Text("That's the whole idea: ThinkSort always shows you one thing, and why it matters.", style = MaterialTheme.typography.bodyMedium, color = Think.colors.inkSecondary)
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Start", onStart, icon = Icons.Rounded.PlayArrow)
        TextLink("Take me to my day", onSkip, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
    }
}
