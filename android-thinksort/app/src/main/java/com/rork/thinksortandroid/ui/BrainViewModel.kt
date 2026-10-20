package com.rork.thinksortandroid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rork.thinksortandroid.data.AppData
import com.rork.thinksortandroid.data.AppSettings
import com.rork.thinksortandroid.data.BrainDumpRecord
import com.rork.thinksortandroid.data.Checkpoint
import com.rork.thinksortandroid.data.FocusSessionRecord
import com.rork.thinksortandroid.data.HistoryEntry
import com.rork.thinksortandroid.data.HistoryKind
import com.rork.thinksortandroid.data.ItemCategory
import com.rork.thinksortandroid.data.NoteItem
import com.rork.thinksortandroid.data.PersistenceService
import com.rork.thinksortandroid.data.PreparationItem
import com.rork.thinksortandroid.data.Priority
import com.rork.thinksortandroid.data.RecurrenceRule
import com.rork.thinksortandroid.data.SampleData
import com.rork.thinksortandroid.data.ShoppingItem
import com.rork.thinksortandroid.data.Subtask
import com.rork.thinksortandroid.data.TaskItem
import com.rork.thinksortandroid.data.TaskStatus
import com.rork.thinksortandroid.data.newId
import com.rork.thinksortandroid.data.nowMillis
import com.rork.thinksortandroid.domain.Dates
import com.rork.thinksortandroid.domain.ExtractedItem
import com.rork.thinksortandroid.domain.NaturalLanguageOrganizer
import com.rork.thinksortandroid.domain.NextAction
import com.rork.thinksortandroid.domain.NextActionEngine
import com.rork.thinksortandroid.domain.TaskBreakdown
import com.rork.thinksortandroid.domain.capitalizedFirst
import com.rork.thinksortandroid.domain.lowercasedFirst
import com.rork.thinksortandroid.domain.naturalList
import com.rork.thinksortandroid.services.CalendarService
import com.rork.thinksortandroid.services.NotificationService
import com.rork.thinksortandroid.services.PurchaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchResults(
    val tasks: List<TaskItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val shopping: List<ShoppingItem> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = tasks.isEmpty() && notes.isEmpty() && shopping.isEmpty() && history.isEmpty()
}

data class PrepEntry(val task: TaskItem, val items: List<PreparationItem>)

/**
 * The single source of truth for ThinkSort. Owns all data, persistence and the derived
 * answers the app is built around ("what's next?", "where did I leave off?").
 */
class BrainViewModel(application: Application) : AndroidViewModel(application) {
    private val persistence = PersistenceService(application)
    private val _data = MutableStateFlow(persistence.load() ?: SampleData.starter())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private var saveJob: Job? = null
    private var notifyJob: Job? = null

    private val current: AppData get() = _data.value
    val settings: AppSettings get() = current.settings

    // region Derived queries

    fun openTasks(data: AppData = current): List<TaskItem> =
        data.tasks.filter { it.isOpen && it.category != ItemCategory.IDEA && it.category != ItemCategory.NOTE }

    fun task(id: String, data: AppData = current): TaskItem? = data.tasks.firstOrNull { it.id == id }

    fun todayTasks(data: AppData = current, now: Long = nowMillis()): List<TaskItem> =
        openTasks(data)
            .filter { task -> task.isOverdue(now) || (task.dueDate?.let { Dates.isToday(it, now) } ?: false) }
            .sortedWith(chronologically)

    fun upcomingTasks(data: AppData = current, now: Long = nowMillis()): List<TaskItem> {
        val startOfTomorrow = Dates.addDays(Dates.startOfDay(now), 1)
        return openTasks(data).filter { (it.dueDate ?: return@filter false) >= startOfTomorrow }.sortedWith(chronologically)
    }

    fun unscheduledTasks(data: AppData = current): List<TaskItem> =
        openTasks(data).filter { it.dueDate == null }.sortedByDescending { it.priority.weight }

    fun nextAction(data: AppData = current, now: Long = nowMillis(), availableMinutes: Int? = null): NextAction? =
        NextActionEngine(now, availableMinutes, CalendarService.minutesUntilNextEvent(now))
            .recommend(data.tasks, data.checkpoint)

    fun tasksFitting(minutes: Int, data: AppData = current): List<TaskItem> =
        NextActionEngine().tasksFitting(minutes, data.tasks)

    fun forgottenItems(data: AppData = current, now: Long = nowMillis()): List<TaskItem> {
        val prerequisiteIDs = data.tasks.filter { it.isOpen }.flatMap { it.dependencyIDs }.toSet()
        return openTasks(data).filter { task ->
            task.isOverdue(now) ||
                task.postponeCount >= 2 ||
                task.id in prerequisiteIDs ||
                (task.openPreparationItems.isNotEmpty() && (task.dueDate?.let { Dates.isToday(it, now) || Dates.isTomorrow(it, now) } ?: false))
        }.sortedByDescending { it.priority.weight }
    }

    fun preparationBeforeLeaving(data: AppData = current, now: Long = nowMillis()): List<PrepEntry> =
        openTasks(data)
            .filter { task -> task.dueDate?.let { Dates.isToday(it, now) || Dates.isTomorrow(it, now) } ?: false }
            .mapNotNull { task -> task.openPreparationItems.takeIf { it.isNotEmpty() }?.let { PrepEntry(task, it) } }

    fun dependencies(task: TaskItem, data: AppData = current): List<TaskItem> =
        task.dependencyIDs.mapNotNull { id -> data.tasks.firstOrNull { it.id == id } }

    fun dependents(task: TaskItem, data: AppData = current): List<TaskItem> =
        data.tasks.filter { it.dependencyIDs.contains(task.id) }

    fun completedToday(data: AppData = current, now: Long = nowMillis()): List<TaskItem> =
        data.tasks.filter { task -> task.completedAt?.let { Dates.isSameDay(it, now) } ?: false }

    fun shoppingByStore(data: AppData = current): List<Pair<String, List<ShoppingItem>>> =
        data.shopping.groupBy { it.storeLabel }
            .map { (store, items) -> store to items.sortedBy { it.isPurchased } }
            .sortedWith { lhs, rhs ->
                when {
                    lhs.first == "Anywhere" -> 1
                    rhs.first == "Anywhere" -> -1
                    else -> lhs.first.compareTo(rhs.first)
                }
            }

    /** Human sentence answering "where did I leave off?". */
    fun leftOffSummary(data: AppData = current): String {
        val checkpoint = data.checkpoint
        val task = checkpoint?.let { task(it.taskID, data) }
        if (task == null) {
            val recent = data.tasks.filter { it.lastStartedAt != null && it.isOpen }.maxByOrNull { it.lastStartedAt ?: 0L }
            return recent?.let { "You were working on ${it.title.lowercasedFirst}. Nothing was checked off yet." }
                ?: "You don't have anything in progress right now."
        }
        val done = task.subtasks.filter { it.isDone }.map { it.title.lowercasedFirst }
        var sentence = "You were ${task.title.lowercasedFirst}."
        if (done.isNotEmpty()) sentence += " You finished ${naturalList(done)}."
        sentence += task.nextSubtask?.let { " Your next step is ${it.title.lowercasedFirst}." } ?: " There's nothing left but wrapping it up."
        return sentence
    }

    fun leftOffTask(data: AppData = current): TaskItem? {
        data.checkpoint?.let { cp -> task(cp.taskID, data)?.takeIf { it.isOpen }?.let { return it } }
        return data.tasks.filter { it.isOpen && it.lastStartedAt != null }.maxByOrNull { it.lastStartedAt ?: 0L }
    }

    fun search(query: String, data: AppData = current): SearchResults {
        val trimmed = query.trim()
        if (trimmed.length <= 1) return SearchResults()
        val keywords = searchKeywords(trimmed)
        if (keywords.isEmpty()) return SearchResults()
        fun matches(text: String) = keywords.any { text.contains(it, ignoreCase = true) }

        var history = data.history.filter { matches(it.title) || matches(it.detail) }.take(20)
        val lower = trimmed.lowercase()
        if (lower.contains("postpone") || lower.contains("moved")) {
            history = data.history.filter { it.kind == HistoryKind.POSTPONED }.take(20)
        }
        if (lower.contains("working on") || lower.contains("started")) {
            history = data.history.filter { it.kind == HistoryKind.STARTED || it.kind == HistoryKind.STOPPED }.take(20)
        }
        return SearchResults(
            tasks = data.tasks.filter { t ->
                matches(t.title) || matches(t.notes) || matches(t.details) ||
                    t.subtasks.any { matches(it.title) } || t.preparationItems.any { matches(it.title) }
            },
            notes = data.notes.filter { matches(it.text) },
            shopping = data.shopping.filter { matches(it.name) },
            history = history,
        )
    }

    // endregion

    // region Task mutations

    fun add(task: TaskItem) = commit { data ->
        data.copy(tasks = data.tasks + task.copy(updatedAt = nowMillis())).withLog(HistoryKind.CREATED, task.title, taskID = task.id)
    }

    fun update(task: TaskItem) = mutateTask(task.id) { task.copy(updatedAt = nowMillis()) }

    fun delete(taskID: String) = commit { data ->
        data.copy(
            tasks = data.tasks.filter { it.id != taskID }.map { it.copy(dependencyIDs = it.dependencyIDs - taskID) },
            checkpoint = data.checkpoint?.takeIf { it.taskID != taskID },
        )
    }

    fun start(taskID: String) = commit { data ->
        val task = task(taskID, data) ?: return@commit data
        val now = nowMillis()
        data.replace(task.copy(status = TaskStatus.IN_PROGRESS, lastStartedAt = now, updatedAt = now))
            .copy(checkpoint = Checkpoint(taskID, task.nextSubtask?.id, note = "Started"))
            .withLog(HistoryKind.STARTED, task.title, taskID = taskID)
    }

    fun complete(taskID: String) = commit { completeIn(it, taskID) }

    private fun completeIn(data: AppData, taskID: String): AppData {
        val task = task(taskID, data) ?: return data
        val now = nowMillis()
        val done = task.copy(
            status = TaskStatus.COMPLETED,
            completedAt = now,
            updatedAt = now,
            subtasks = task.subtasks.map { if (it.isDone) it else it.copy(isDone = true, completedAt = now) },
        )
        var next = data.replace(done).withLog(HistoryKind.COMPLETED, task.title, taskID = taskID)
        val recurrence = task.recurrence
        val due = task.dueDate
        if (recurrence != null && due != null) {
            val repeat = task.copy(
                id = newId(),
                status = TaskStatus.NOT_STARTED,
                completedAt = null,
                createdAt = now,
                dueDate = nextDate(due, recurrence),
                subtasks = task.subtasks.map { Subtask(title = it.title) },
                preparationItems = task.preparationItems.map { PreparationItem(title = it.title) },
            )
            next = next.copy(tasks = next.tasks + repeat)
        }
        if (next.checkpoint?.taskID == taskID) next = next.copy(checkpoint = null)
        return next
    }

    fun reopen(taskID: String) = mutateTask(taskID) { it.copy(status = TaskStatus.NOT_STARTED, completedAt = null, updatedAt = nowMillis()) }

    fun postpone(taskID: String, to: Long? = null) = commit { data -> postponeIn(data, taskID, to) }

    private fun postponeIn(data: AppData, taskID: String, to: Long?): AppData {
        val task = task(taskID, data) ?: return data
        val target = to ?: task.dueDate?.let { Dates.addDays(it, 1) } ?: Dates.addDays(Dates.startOfDay(nowMillis()), 1)
        val updated = task.copy(
            dueDate = target,
            postponeCount = task.postponeCount + 1,
            status = if (task.status == TaskStatus.IN_PROGRESS) TaskStatus.IN_PROGRESS else TaskStatus.POSTPONED,
            updatedAt = nowMillis(),
        )
        return data.replace(updated).withLog(HistoryKind.POSTPONED, task.title, "Moved to ${Dates.abbreviated(target)}", taskID)
    }

    fun snooze(taskID: String, minutes: Int) = commit { data ->
        val task = task(taskID, data) ?: return@commit data
        val base = maxOf(nowMillis(), task.dueDate ?: nowMillis())
        data.replace(
            task.copy(dueDate = Dates.addMinutes(base, minutes.toLong()), hasTime = true, postponeCount = task.postponeCount + 1, updatedAt = nowMillis())
        ).withLog(HistoryKind.POSTPONED, task.title, "Snoozed $minutes min", taskID)
    }

    fun setPriority(priority: Priority, taskID: String) = mutateTask(taskID) { it.copy(priority = priority, updatedAt = nowMillis()) }

    fun setStatus(status: TaskStatus, taskID: String) = mutateTask(taskID) {
        it.copy(status = status, updatedAt = nowMillis(), completedAt = if (status == TaskStatus.COMPLETED) nowMillis() else it.completedAt)
    }

    // endregion

    // region Subtasks, preparation, dependencies

    fun toggleSubtask(taskID: String, subtaskID: String) = commit { data ->
        val task = task(taskID, data) ?: return@commit data
        val now = nowMillis()
        val target = task.subtasks.firstOrNull { it.id == subtaskID } ?: return@commit data
        val nowDone = !target.isDone
        var updated = task.copy(
            subtasks = task.subtasks.map { if (it.id == subtaskID) it.copy(isDone = nowDone, completedAt = if (nowDone) now else null) else it },
            updatedAt = now,
        )
        if (nowDone && updated.status == TaskStatus.NOT_STARTED) updated = updated.copy(status = TaskStatus.IN_PROGRESS, lastStartedAt = now)
        var next = data.replace(updated)
        if (next.checkpoint?.taskID == taskID) {
            next = next.copy(checkpoint = next.checkpoint?.copy(subtaskID = updated.nextSubtask?.id, savedAt = now))
        }
        if (updated.subtasks.isNotEmpty() && updated.subtasks.all { it.isDone }) completeIn(next, taskID) else next
    }

    fun addSubtask(title: String, taskID: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        mutateTask(taskID) { it.copy(subtasks = it.subtasks + Subtask(title = trimmed), updatedAt = nowMillis()) }
    }

    fun removeSubtask(subtaskID: String, taskID: String) = mutateTask(taskID) { it.copy(subtasks = it.subtasks.filter { s -> s.id != subtaskID }) }

    fun breakDown(taskID: String) = mutateTask(taskID) { task ->
        if (task.subtasks.isNotEmpty()) task
        else task.copy(subtasks = TaskBreakdown.steps(task.title).map { Subtask(title = it) }, updatedAt = nowMillis())
    }

    fun togglePreparationItem(taskID: String, itemID: String) = mutateTask(taskID) { task ->
        task.copy(preparationItems = task.preparationItems.map { if (it.id == itemID) it.copy(isDone = !it.isDone) else it }, updatedAt = nowMillis())
    }

    fun markPreparationDone(entries: List<PrepEntry>) = commit { data ->
        entries.fold(data) { acc, entry ->
            val task = task(entry.task.id, acc) ?: return@fold acc
            val ids = entry.items.map { it.id }.toSet()
            acc.replace(task.copy(preparationItems = task.preparationItems.map { if (it.id in ids) it.copy(isDone = true) else it }))
        }
    }

    fun addPreparationItem(title: String, taskID: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        mutateTask(taskID) { it.copy(preparationItems = it.preparationItems + PreparationItem(title = trimmed), updatedAt = nowMillis()) }
    }

    fun addPreparationItems(titles: List<String>, taskID: String) = mutateTask(taskID) { task ->
        task.copy(preparationItems = task.preparationItems + titles.map { PreparationItem(title = it) }, updatedAt = nowMillis())
    }

    fun removePreparationItem(itemID: String, taskID: String) = mutateTask(taskID) {
        it.copy(preparationItems = it.preparationItems.filter { p -> p.id != itemID }, updatedAt = nowMillis())
    }

    fun addDependency(dependencyID: String, taskID: String) {
        if (dependencyID == taskID) return
        mutateTask(taskID) { if (it.dependencyIDs.contains(dependencyID)) it else it.copy(dependencyIDs = it.dependencyIDs + dependencyID) }
    }

    fun addNewPrerequisite(title: String, taskID: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val parent = task(taskID) ?: return
        val prep = TaskItem(
            title = trimmed.capitalizedFirst,
            dueDate = parent.dueDate?.let { maxOf(Dates.addDays(Dates.startOfDay(it), -1), Dates.startOfDay(nowMillis())) },
        )
        add(prep)
        addDependency(prep.id, taskID)
    }

    fun removeDependency(dependencyID: String, taskID: String) = mutateTask(taskID) { it.copy(dependencyIDs = it.dependencyIDs - dependencyID) }

    // endregion

    // region Checkpoints

    fun saveCheckpoint(taskID: String, note: String = "Stopped here") = commit { data ->
        val task = task(taskID, data) ?: return@commit data
        data.replace(task.copy(lastStoppedAt = nowMillis()))
            .copy(checkpoint = Checkpoint(taskID, task.nextSubtask?.id, note = note))
            .withLog(HistoryKind.STOPPED, task.title, note, taskID)
    }

    fun clearCheckpoint() = commit { it.copy(checkpoint = null) }

    // endregion

    // region Notes & shopping

    fun addNote(text: String, isIdea: Boolean) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        commit { data -> data.copy(notes = listOf(NoteItem(text = trimmed, isIdea = isIdea)) + data.notes).withLog(HistoryKind.NOTE_ADDED, trimmed) }
    }

    fun deleteNote(id: String) = commit { data -> data.copy(notes = data.notes.filter { it.id != id }) }

    fun convertNoteToTask(id: String) = commit { data ->
        val note = data.notes.firstOrNull { it.id == id } ?: return@commit data
        val task = TaskItem(title = note.text)
        data.copy(tasks = data.tasks + task, notes = data.notes.filter { it.id != id }).withLog(HistoryKind.CREATED, task.title, taskID = task.id)
    }

    fun addShoppingItem(name: String, store: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        commit { data -> data.copy(shopping = listOf(ShoppingItem(name = trimmed.capitalizedFirst, store = store)) + data.shopping) }
    }

    fun toggleShoppingItem(id: String) = commit { data ->
        data.copy(shopping = data.shopping.map {
            if (it.id == id) it.copy(isPurchased = !it.isPurchased, purchasedAt = if (!it.isPurchased) nowMillis() else null) else it
        })
    }

    fun deleteShoppingItem(id: String) = commit { data -> data.copy(shopping = data.shopping.filter { it.id != id }) }

    fun clearPurchasedShopping() = commit { data -> data.copy(shopping = data.shopping.filter { !it.isPurchased }) }

    // endregion

    // region Brain dump intake

    fun saveBrainDump(transcript: String, items: List<ExtractedItem>, wasSpoken: Boolean): Int {
        var saved = 0
        commit { start ->
            val record = BrainDumpRecord(transcript = transcript, savedItemCount = items.count { it.isSelected }, wasSpoken = wasSpoken)
            var data = start
            val createdTasks = linkedMapOf<String, String>()
            val selected = items.filter { it.isSelected }

            for (item in selected) {
                when (item.category) {
                    ItemCategory.IDEA -> data = data.copy(notes = listOf(NoteItem(text = item.title, isIdea = true, sourceBrainDumpID = record.id)) + data.notes)
                    ItemCategory.NOTE -> data = data.copy(notes = listOf(NoteItem(text = item.title, isIdea = false, sourceBrainDumpID = record.id)) + data.notes)
                    ItemCategory.SHOPPING -> data = data.copy(shopping = listOf(ShoppingItem(name = item.title, store = item.store)) + data.shopping)
                    else -> {
                        val suggestions = TaskBreakdown.preparationSuggestions(item.title)
                        val task = TaskItem(
                            title = item.title,
                            category = item.category,
                            dueDate = item.dueDate,
                            hasTime = item.hasTime,
                            deadline = item.deadline,
                            estimatedMinutes = item.estimatedMinutes,
                            priority = item.priority,
                            locationName = item.locationName,
                            locationTrigger = item.locationTrigger,
                            recurrence = item.recurrence,
                            reminderLeadMinutes = item.reminderLeadMinutes,
                            sourceBrainDumpID = record.id,
                            preparationItems = if (item.category == ItemCategory.APPOINTMENT) suggestions.map { PreparationItem(title = it) } else emptyList(),
                        )
                        data = data.copy(tasks = data.tasks + task).withLog(HistoryKind.CREATED, task.title, taskID = task.id)
                        createdTasks[item.title.lowercase()] = task.id
                    }
                }
                saved += 1
            }

            // Second pass: wire up the "X before Y" relationships.
            for (item in selected) {
                val targetTitle = item.preparationForTitle?.lowercase() ?: continue
                val prepID = createdTasks[item.title.lowercase()] ?: continue
                val parentID = createdTasks.entries.firstOrNull { (key, _) -> key.contains(targetTitle) || targetTitle.contains(key) }?.value
                    ?: data.tasks.firstOrNull { it.isOpen && it.title.lowercase().contains(targetTitle) }?.id
                if (parentID == null || parentID == prepID) continue
                val parent = task(parentID, data) ?: continue
                if (!parent.dependencyIDs.contains(prepID)) data = data.replace(parent.copy(dependencyIDs = parent.dependencyIDs + prepID))
                val parentDue = parent.dueDate
                val prep = task(prepID, data)
                if (parentDue != null && prep != null && (prep.dueDate == null || prep.dueDate >= parentDue)) {
                    val target = maxOf(Dates.addDays(parentDue, -1), Dates.startOfDay(nowMillis()))
                    data = data.replace(prep.copy(dueDate = target))
                }
            }

            data.copy(brainDumps = listOf(record) + data.brainDumps)
                .withLog(HistoryKind.BRAIN_DUMP, "Brain dump", "$saved item${if (saved == 1) "" else "s"} organized")
        }
        return saved
    }

    /** Single-sentence capture used by quick add. Returns a confirmation line. */
    fun quickCapture(text: String): String {
        val result = NaturalLanguageOrganizer().organize(text)
        if (result.isEmpty) return "I didn't catch anything to add."
        val count = saveBrainDump(text, result.items, wasSpoken = false)
        val first = result.items.first()
        if (count != 1) return "Got it — added $count things."
        return when (first.category) {
            ItemCategory.SHOPPING -> "Added ${first.title.lowercasedFirst} to your shopping list."
            ItemCategory.IDEA -> "Saved that as an idea."
            ItemCategory.NOTE -> "Saved that note."
            else -> {
                val due = first.dueDate
                if (due != null) {
                    val whenText = when {
                        Dates.isToday(due) -> "today"
                        Dates.isTomorrow(due) -> "tomorrow"
                        else -> Dates.weekday(due)
                    }
                    val time = if (first.hasTime) " at ${Dates.time(due)}" else ""
                    "Got it — ${first.title.lowercasedFirst} $whenText$time."
                } else {
                    "Got it — added ${first.title.lowercasedFirst}."
                }
            }
        }
    }

    // endregion

    // region Focus, routines, settings

    fun recordFocusSession(session: FocusSessionRecord) = commit { data ->
        data.copy(focusSessions = listOf(session) + data.focusSessions)
            .withLog(HistoryKind.FOCUS_SESSION, session.taskTitle, "${session.completedMinutes} min", session.taskID)
    }

    fun moveAllToTomorrow(ids: List<String>) = commit { data ->
        val tomorrow = Dates.addDays(Dates.startOfDay(nowMillis()), 1)
        ids.fold(data) { acc, id -> postponeIn(acc, id, tomorrow) }
    }

    /** Evening reset: move chosen tasks and turn tonight's prep into a real task. */
    fun eveningReset(moveIds: List<String>, prepEntries: List<PrepEntry>) {
        moveAllToTomorrow(moveIds)
        val tonight = Dates.atTime(20, 0, nowMillis())
        for (entry in prepEntries) {
            val title = "Get ready for ${entry.task.title.lowercasedFirst}"
            if (openTasks().any { it.title == title }) continue
            val prep = TaskItem(
                title = title,
                dueDate = tonight,
                hasTime = true,
                priority = Priority.SOON,
                estimatedMinutes = 10,
                subtasks = entry.items.map { Subtask(title = it.title) },
            )
            add(prep)
            addDependency(prep.id, entry.task.id)
        }
        updateSettings { it.copy(lastEveningReset = nowMillis()) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = commit { it.copy(settings = transform(it.settings)) }

    fun startTrialIfNeeded() {
        if (current.settings.trialStartedAt != null) return
        updateSettings { it.copy(trialStartedAt = nowMillis()) }
        scheduleTrialReminders()
    }

    fun setLifetimeUnlocked(isUnlocked: Boolean) {
        if (current.settings.lifetimeUnlocked == isUnlocked) return
        _data.update { it.copy(settings = it.settings.copy(lifetimeUnlocked = isUnlocked)) }
        scheduleSave()
        scheduleTrialReminders()
    }

    fun scheduleTrialReminders() {
        val app = getApplication<Application>()
        val settings = current.settings
        viewModelScope.launch(Dispatchers.Default) {
            NotificationService.scheduleTrialReminders(app, settings.trialStartedAt, PurchaseService.isUnlocked.value, PurchaseService.TRIAL_DAYS)
        }
    }

    fun refreshNotifications() {
        val app = getApplication<Application>()
        val snapshot = current
        notifyJob?.cancel()
        notifyJob = viewModelScope.launch(Dispatchers.Default) {
            delay(400)
            NotificationService.rebuildSchedule(app, snapshot.tasks, snapshot.settings)
        }
    }

    fun exportJSON(): String = persistence.exportJSON(current)

    fun deleteAllData() {
        val keep = current.settings
        persistence.deleteAll()
        NotificationService.cancelAll(getApplication())
        commit {
            AppData(
                settings = AppSettings(
                    hasCompletedOnboarding = true,
                    trialStartedAt = keep.trialStartedAt,
                    lifetimeUnlocked = keep.lifetimeUnlocked,
                    appearance = keep.appearance,
                )
            )
        }
    }

    fun loadSampleData() = commit { SampleData.starter().copy(settings = it.settings) }

    // endregion

    // region Plumbing

    private fun commit(transform: (AppData) -> AppData) {
        _data.update(transform)
        scheduleSave()
        refreshNotifications()
    }

    private fun mutateTask(id: String, transform: (TaskItem) -> TaskItem) = commit { data ->
        val task = task(id, data) ?: return@commit data
        data.replace(transform(task))
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        val snapshot = current
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(350)
            persistence.save(snapshot)
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        val snapshot = current
        viewModelScope.launch(Dispatchers.IO) { persistence.save(snapshot) }
    }

    override fun onCleared() {
        persistence.save(current)
        super.onCleared()
    }

    private fun AppData.replace(task: TaskItem): AppData = copy(tasks = tasks.map { if (it.id == task.id) task else it })

    private fun AppData.withLog(kind: HistoryKind, title: String, detail: String = "", taskID: String? = null): AppData =
        copy(history = (listOf(HistoryEntry(kind = kind, title = title, detail = detail, taskID = taskID)) + history).take(400))

    // endregion

    companion object {
        val chronologically: Comparator<TaskItem> = Comparator { lhs, rhs ->
            val left = lhs.dueDate
            val right = rhs.dueDate
            when {
                left != null && right != null -> {
                    if (Dates.isSameDay(left, right)) {
                        when {
                            lhs.hasTime != rhs.hasTime -> if (lhs.hasTime) -1 else 1
                            lhs.hasTime && rhs.hasTime -> left.compareTo(right)
                            else -> rhs.priority.weight.compareTo(lhs.priority.weight)
                        }
                    } else {
                        left.compareTo(right)
                    }
                }
                left == null && right != null -> 1
                left != null && right == null -> -1
                else -> rhs.priority.weight.compareTo(lhs.priority.weight)
            }
        }

        private val stopWords = setOf(
            "what", "when", "where", "did", "i", "was", "the", "a", "an", "to", "for", "my",
            "me", "is", "are", "do", "need", "needed", "say", "said", "going", "on", "of",
            "about", "that", "this", "last", "week", "yesterday", "today", "have", "had", "it",
        )

        fun searchKeywords(query: String): List<String> =
            query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 && it !in stopWords }

        fun nextDate(after: Long, rule: RecurrenceRule): Long = when (rule) {
            RecurrenceRule.DAILY -> Dates.addDays(after, 1)
            RecurrenceRule.WEEKDAYS -> {
                var candidate = Dates.addDays(after, 1)
                while (Dates.dayOfWeek(candidate).value >= 6) candidate = Dates.addDays(candidate, 1)
                candidate
            }
            RecurrenceRule.WEEKLY -> Dates.addDays(after, 7)
            RecurrenceRule.MONTHLY -> Dates.addMonths(after, 1)
        }
    }
}
