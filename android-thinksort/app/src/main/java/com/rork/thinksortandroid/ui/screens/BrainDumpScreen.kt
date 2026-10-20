package com.rork.thinksortandroid.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.thinksortandroid.data.ItemCategory
import com.rork.thinksortandroid.domain.Dates
import com.rork.thinksortandroid.domain.ExtractedItem
import com.rork.thinksortandroid.domain.NaturalLanguageOrganizer
import com.rork.thinksortandroid.domain.OrganizerResult
import com.rork.thinksortandroid.domain.lowercasedFirst
import com.rork.thinksortandroid.services.SpeechState
import com.rork.thinksortandroid.ui.BrainViewModel
import com.rork.thinksortandroid.ui.components.CardTextField
import com.rork.thinksortandroid.ui.components.CategoryTag
import com.rork.thinksortandroid.ui.components.CheckCircle
import com.rork.thinksortandroid.ui.components.Chevron
import com.rork.thinksortandroid.ui.components.Eyebrow
import com.rork.thinksortandroid.ui.components.LargeTitle
import com.rork.thinksortandroid.ui.components.PrimaryButton
import com.rork.thinksortandroid.ui.components.RowDivider
import com.rork.thinksortandroid.ui.components.ScreenColumn
import com.rork.thinksortandroid.ui.components.SectionCard
import com.rork.thinksortandroid.ui.components.SoftButton
import com.rork.thinksortandroid.ui.components.card
import com.rork.thinksortandroid.ui.components.rememberSpeech
import com.rork.thinksortandroid.ui.navigation.AppTab
import com.rork.thinksortandroid.ui.navigation.LocalNav
import com.rork.thinksortandroid.ui.theme.Think

@Composable
fun BrainDumpScreen(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val speech = rememberSpeech()
    val speechState by speech.service.state.collectAsStateWithLifecycle()
    val transcript by speech.service.transcript.collectAsStateWithLifecycle()
    val level by speech.service.audioLevel.collectAsStateWithLifecycle()
    val isRecording = speechState == SpeechState.Recording

    var typed by rememberSaveable { mutableStateOf("") }
    var wasSpoken by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<OrganizerResult?>(null) }
    val current = if (isRecording) transcript else typed

    fun stopIfRecording() {
        if (isRecording) {
            typed = transcript
            speech.service.stop()
            wasSpoken = true
        }
    }

    fun organize() {
        stopIfRecording()
        val text = (if (isRecording) transcript else typed).trim()
        if (text.isEmpty()) return
        val organized = NaturalLanguageOrganizer().organize(text)
        if (organized.isEmpty) {
            nav.flash("I couldn't pull anything out of that — try adding a bit more.")
            return
        }
        result = organized
    }

    AnimatedContent(
        targetState = result,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
        label = "dump",
    ) { organized ->
        if (organized != null) {
            BrainDumpConfirm(
                result = organized,
                onSave = { items ->
                    val count = vm.saveBrainDump(organized.transcript, items, wasSpoken)
                    typed = ""
                    wasSpoken = false
                    speech.service.reset()
                    result = null
                    nav.flash("Saved $count thing${if (count == 1) "" else "s"}.")
                    nav.selectTab(AppTab.TODAY)
                },
                onEdit = { result = null },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                LargeTitle("Brain Dump")
                ScreenColumn(spacing = 22.dp) {
                    Text(
                        "Just talk. Don't organize it — that's the whole point.",
                        style = MaterialTheme.typography.titleLarge,
                        color = Think.colors.inkSecondary,
                    )

                    MicCard(isRecording = isRecording, level = level) {
                        if (isRecording) stopIfRecording() else speech.start(typed)
                    }

                    when (val state = speechState) {
                        is SpeechState.Denied -> PermissionNotice(state.message, showSettings = true)
                        is SpeechState.Unavailable -> PermissionNotice(state.message, showSettings = false)
                        else -> Unit
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Eyebrow(if (isRecording) "Listening" else "What's on your mind", Modifier.padding(start = 4.dp))
                        Box(Modifier.fillMaxWidth().heightIn(min = 180.dp).card().padding(4.dp)) {
                            CardTextField(
                                value = current,
                                onValueChange = { if (!isRecording) typed = it },
                                placeholder = "Tap the mic, or type it all out here — one long ramble is fine.",
                                singleLine = false,
                                minLines = 6,
                                maxLines = 14,
                            )
                        }
                    }

                    PrimaryButton(
                        "Organize This",
                        onClick = ::organize,
                        icon = Icons.Rounded.AutoAwesome,
                        enabled = current.trim().length >= 3,
                    )

                    if (data.brainDumps.isNotEmpty()) {
                        val recent = data.brainDumps.take(3)
                        SectionCard(title = "Recent dumps") {
                            recent.forEachIndexed { index, dump ->
                                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(dump.transcript, style = MaterialTheme.typography.bodyMedium, color = Think.colors.ink, maxLines = 2)
                                    Text(
                                        "${Dates.abbreviatedWithTime(dump.createdAt)} · ${dump.savedItemCount} item${if (dump.savedItemCount == 1) "" else "s"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Think.colors.inkTertiary,
                                    )
                                }
                                if (index < recent.size - 1) RowDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MicCard(isRecording: Boolean, level: Float, onToggle: () -> Unit) {
    val ring by animateDpAsState(118.dp + (level * 46f).dp, tween(180), label = "ring")
    Column(
        Modifier.fillMaxWidth().card().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
            if (isRecording) {
                Box(Modifier.size(ring).border(3.dp, Think.colors.coral.copy(alpha = 0.35f), CircleShape))
            }
            Box(
                Modifier
                    .size(118.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) Think.colors.coral.copy(alpha = 0.14f) else Think.colors.accentSoft)
                    .clickable(onClick = onToggle)
                    .semantics { contentDescription = if (isRecording) "Stop recording" else "Start recording" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = if (isRecording) Think.colors.coral else Think.colors.accent,
                    modifier = Modifier.size(46.dp),
                )
            }
        }
        Text(
            if (isRecording) "Listening — take as long as you need" else "Tap to start talking",
            style = MaterialTheme.typography.bodyMedium,
            color = Think.colors.inkSecondary,
        )
    }
}

@Composable
private fun PermissionNotice(message: String, showSettings: Boolean) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Think.colors.coralSoft).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Info, null, tint = Think.colors.coral)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = Think.colors.ink)
            if (showSettings) {
                Row(
                    Modifier.clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Settings, null, tint = Think.colors.accent, modifier = Modifier.size(16.dp))
                    Text("Open Android Settings", style = MaterialTheme.typography.labelLarge, color = Think.colors.accent)
                }
            }
        }
    }
}

/** Conversational confirmation of what ThinkSort heard and how it organized it. */
@Composable
private fun BrainDumpConfirm(result: OrganizerResult, onSave: (List<ExtractedItem>) -> Unit, onEdit: () -> Unit) {
    val items = remember(result) { mutableStateListOf<ExtractedItem>().apply { addAll(result.items) } }
    var editing by remember { mutableStateOf<ExtractedItem?>(null) }

    fun groupLabel(item: ExtractedItem) = when (item.category) {
        ItemCategory.SHOPPING -> "Shopping"
        ItemCategory.IDEA -> "Ideas"
        ItemCategory.NOTE -> "Notes"
        else -> item.dayBucketLabel()
    }

    val scheduled = items.filter { !it.needsClarification }
    val order = scheduled.map { groupLabel(it) }.distinct()
    val unclear = items.filter { it.needsClarification }

    fun replace(updated: ExtractedItem) {
        val index = items.indexOfFirst { it.id == updated.id }
        if (index >= 0) items[index] = updated
    }

    ScreenColumn(
        spacing = 20.dp,
        modifier = Modifier.statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Think.gutter, end = Think.gutter, top = 16.dp, bottom = 28.dp),
    ) {
        Text(
            "Got it — here's what I heard and how I organized it:",
            style = MaterialTheme.typography.headlineSmall,
            color = Think.colors.ink,
        )

        order.forEach { label ->
            val groupItems = scheduled.filter { groupLabel(it) == label }
            SectionCard(title = label, tint = if (label == "Today") Think.colors.accentSoft else Think.colors.surface) {
                groupItems.forEachIndexed { index, item ->
                    ExtractedRow(item, onToggle = { replace(item.copy(isSelected = !item.isSelected)) }, onClick = { editing = item })
                    if (index < groupItems.size - 1) RowDivider()
                }
            }
        }

        unclear.forEach { item ->
            Column(Modifier.fillMaxWidth().card().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = Think.colors.coral)
                    Text(item.clarificationQuestion ?: "Is “${item.title}” a task or an idea?", style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Task", {
                        replace(item.copy(category = ItemCategory.TASK, needsClarification = false, clarificationQuestion = null))
                    }, Modifier.weight(1f), icon = Icons.Rounded.Check, fill = Think.colors.surfaceMuted, compact = true)
                    SoftButton("Idea", {
                        replace(item.copy(category = ItemCategory.IDEA, needsClarification = false, clarificationQuestion = null))
                    }, Modifier.weight(1f), icon = Icons.Rounded.Lightbulb, fill = Think.colors.surfaceMuted, compact = true)
                }
            }
        }

        PrimaryButton("Looks Good, Save It", onClick = { onSave(items.toList()) }, icon = Icons.Rounded.Check)
        SoftButton("Edit Something", onEdit, icon = Icons.Rounded.Edit, tint = Think.colors.accent)
    }

    editing?.let { item ->
        ExtractedItemEditor(
            item = item,
            onDismiss = { editing = null },
            onSave = {
                replace(it)
                editing = null
            },
            onDelete = {
                items.removeAll { existing -> existing.id == item.id }
                editing = null
            },
        )
    }
}

@Composable
private fun ExtractedRow(item: ExtractedItem, onToggle: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CheckCircle(
            item.isSelected,
            Modifier.clip(CircleShape).clickable(onClick = onToggle).semantics {
                contentDescription = if (item.isSelected) "Don't save ${item.title}" else "Save ${item.title}"
            },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, color = if (item.isSelected) Think.colors.ink else Think.colors.inkTertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryTag(item.category)
                detailLine(item)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Think.colors.inkSecondary) }
            }
        }
        Chevron()
    }
}

private fun detailLine(item: ExtractedItem): String? {
    val parts = mutableListOf<String>()
    val due = item.dueDate
    if (due != null && item.hasTime) parts.add(Dates.time(due))
    item.preparationForTitle?.let { parts.add("Before ${it.lowercasedFirst}") }
    val trigger = item.locationTrigger
    if (trigger != null) parts.add("${trigger.kind.label} ${trigger.placeName}") else item.store?.let { parts.add(it) }
    item.recurrence?.let { parts.add(it.label) }
    item.reminderLeadMinutes?.let { parts.add("$it min before") }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}
