package com.rork.thinksortandroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.thinksortandroid.data.HistoryEntry
import com.rork.thinksortandroid.data.NoteItem
import com.rork.thinksortandroid.domain.Dates
import com.rork.thinksortandroid.ui.BrainViewModel
import com.rork.thinksortandroid.ui.components.CardTextField
import com.rork.thinksortandroid.ui.components.ChoiceChip
import com.rork.thinksortandroid.ui.components.DetailTopBar
import com.rork.thinksortandroid.ui.components.EmptyCard
import com.rork.thinksortandroid.ui.components.Eyebrow
import com.rork.thinksortandroid.ui.components.InfoCard
import com.rork.thinksortandroid.ui.components.RowDivider
import com.rork.thinksortandroid.ui.components.ScreenColumn
import com.rork.thinksortandroid.ui.components.SectionCard
import com.rork.thinksortandroid.ui.components.TaskGroupCard
import com.rork.thinksortandroid.ui.components.TextLink
import com.rork.thinksortandroid.ui.components.card
import com.rork.thinksortandroid.ui.navigation.LocalNav
import com.rork.thinksortandroid.ui.theme.Think

@Composable
fun NotesScreen(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var text by rememberSaveable { mutableStateOf("") }
    var isIdea by rememberSaveable { mutableStateOf(true) }
    val ideas = data.notes.filter { it.isIdea }
    val notes = data.notes.filter { !it.isIdea }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar("Notes & ideas", onBack = nav::back)
        ScreenColumn {
            Column(Modifier.fillMaxWidth().card().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardTextField(text, { text = it }, "Something you want to keep, not do", singleLine = false, maxLines = 4)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Idea", isIdea, { isIdea = true })
                    ChoiceChip("Note", !isIdea, { isIdea = false })
                    Spacer(Modifier.weight(1f))
                    TextLink("Save", {
                        vm.addNote(text, isIdea)
                        text = ""
                    }, color = Think.colors.accent, enabled = text.isNotBlank())
                }
            }
            if (ideas.isEmpty() && notes.isEmpty()) {
                EmptyCard(
                    Icons.Rounded.Lightbulb,
                    "Nothing saved yet",
                    "Not everything is a task. Say “I have an idea for my game” in a brain dump and it lands here instead of your to-do list.",
                )
            } else {
                if (ideas.isNotEmpty()) NoteGroup("Ideas", ideas, vm)
                if (notes.isNotEmpty()) NoteGroup("Notes", notes, vm)
            }
        }
    }
}

@Composable
private fun NoteGroup(title: String, notes: List<NoteItem>, vm: BrainViewModel) {
    SectionCard(title = title) {
        notes.forEachIndexed { index, note ->
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
                Text(note.text, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Dates.abbreviated(note.createdAt), style = MaterialTheme.typography.bodySmall, color = Think.colors.inkTertiary, modifier = Modifier.weight(1f))
                    TextLink("Make it a task", { vm.convertNoteToTask(note.id) }, color = Think.colors.accent)
                    TextLink("Delete", { vm.deleteNote(note.id) }, color = Think.colors.coral)
                }
            }
            if (index < notes.size - 1) RowDivider()
        }
    }
}

private val searchExamples = listOf(
    "When did I say I'd call Mike?",
    "What did I need for the vet?",
    "What did I postpone?",
    "What was I working on?",
)

@Composable
fun SearchScreen(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, data) { vm.search(query, data) }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar("Search", onBack = nav::back)
        Row(
            Modifier.padding(horizontal = Think.gutter).fillMaxWidth().card(radius = 18.dp).padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Think.colors.inkTertiary, modifier = Modifier.size(20.dp))
            CardTextField(query, { query = it }, "Ask about anything you said")
        }
        ScreenColumn(spacing = 22.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Think.gutter, end = Think.gutter, top = 18.dp, bottom = 28.dp)) {
            when {
                query.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Eyebrow("Try asking", Modifier.padding(start = 4.dp))
                    searchExamples.forEach { example ->
                        Text(
                            "“$example”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Think.colors.inkSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Think.colors.surfaceMuted)
                                .clickable { query = example }
                                .padding(14.dp),
                        )
                    }
                }
                results.isEmpty -> InfoCard("Nothing matched", "Try a single word — a name, a place, or the thing itself.")
                else -> {
                    if (results.tasks.isNotEmpty()) {
                        TaskGroupCard("Tasks", results.tasks, onSelect = { nav.openTask(it.id) }, showCategory = true, onToggle = { vm.complete(it.id) })
                    }
                    if (results.notes.isNotEmpty()) {
                        SectionCard(title = "Notes & ideas") {
                            results.notes.forEachIndexed { index, note ->
                                Text(note.text, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink, modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp))
                                if (index < results.notes.size - 1) RowDivider()
                            }
                        }
                    }
                    if (results.shopping.isNotEmpty()) {
                        SectionCard(title = "Shopping") {
                            results.shopping.forEachIndexed { index, item ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink, modifier = Modifier.weight(1f))
                                    Text(item.storeLabel, style = MaterialTheme.typography.bodySmall, color = Think.colors.inkSecondary)
                                }
                                if (index < results.shopping.size - 1) RowDivider()
                            }
                        }
                    }
                    if (results.history.isNotEmpty()) {
                        SectionCard(title = "History") {
                            results.history.forEachIndexed { index, entry ->
                                HistoryRow(entry)
                                if (index < results.history.size - 1) RowDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val grouped = remember(data) {
        data.history.groupBy { Dates.startOfDay(it.date) }.toSortedMap(compareByDescending { it }).toList()
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar("History", onBack = nav::back)
        ScreenColumn(spacing = 22.dp) {
            if (data.history.isEmpty()) {
                InfoCard("No history yet", "Everything you start, finish, move or dump shows up here.")
            } else {
                grouped.forEach { (day, entries) ->
                    val label = when {
                        Dates.isToday(day) -> "Today"
                        Dates.isYesterday(day) -> "Yesterday"
                        else -> Dates.weekdayLongMonthShortDay(day)
                    }
                    SectionCard(title = label) {
                        entries.forEachIndexed { index, entry ->
                            val taskID = entry.taskID
                            val exists = taskID != null && vm.task(taskID, data) != null
                            HistoryRow(entry, onClick = if (exists && taskID != null) ({ nav.openTask(taskID) }) else null)
                            if (index < entries.size - 1) RowDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.kind.label, style = MaterialTheme.typography.labelSmall, color = Think.colors.accent, modifier = Modifier.width(78.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyMedium, color = Think.colors.ink)
            if (entry.detail.isNotEmpty()) Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = Think.colors.inkTertiary)
        }
        Text(Dates.time(entry.date), style = MaterialTheme.typography.bodySmall, color = Think.colors.inkTertiary)
    }
}
