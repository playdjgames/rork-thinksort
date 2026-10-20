import Foundation
import SwiftUI

/// The single source of truth for ThinkSort. Owns all data, persistence and the
/// derived answers the app is built around ("what's next?", "where did I leave off?").
@Observable
final class BrainStore {
    static let shared = BrainStore()

    private let persistence = PersistenceService()
    private var saveTask: Task<Void, Never>?

    private(set) var data: AppData
    /// Time pocket the user declared ("I have 15 minutes"), cleared when consumed.
    var availableMinutes: Int?
    /// Today's capacity choice, mirrored from UserDefaults so views update when it changes.
    private var capacityChoice: Capacity? = CapacityStorage.load()
    private var capacityDay: String = CapacityStorage.dayStamp(for: Date())
    /// Local calendar days the app has been opened on (device only).
    private(set) var openedDays: Set<String> = OpenedDaysStorage.record()

    init(persistence: PersistenceService = PersistenceService(), seedIfEmpty: Bool = true) {
        if let loaded = persistence.load() {
            data = loaded
        } else {
            data = AppData()
            if seedIfEmpty { data = SampleData.starter() }
        }
    }

    // MARK: - Convenience accessors

    var tasks: [TaskItem] { data.tasks }
    var notes: [NoteItem] { data.notes }
    var shopping: [ShoppingItem] { data.shopping }
    var history: [HistoryEntry] { data.history }
    var brainDumps: [BrainDumpRecord] { data.brainDumps }
    var checkpoint: Checkpoint? { data.checkpoint }

    var settings: AppSettings {
        get { data.settings }
        set {
            data.settings = newValue
            scheduleSave()
            refreshNotifications()
        }
    }

    var openTasks: [TaskItem] {
        data.tasks.filter { $0.isOpen && $0.category != .idea && $0.category != .note }
    }

    func task(id: UUID) -> TaskItem? {
        data.tasks.first { $0.id == id }
    }

    func todayTasks(now: Date = Date()) -> [TaskItem] {
        openTasks
            .filter { task in
                if task.isOverdue(now: now) { return true }
                guard let due = task.dueDate else { return false }
                return Calendar.current.isDateInToday(due)
            }
            .sorted(by: Self.chronologically)
    }

    func upcomingTasks(now: Date = Date()) -> [TaskItem] {
        let startOfTomorrow = Calendar.current.startOfDay(for: now).addingTimeInterval(86_400)
        return openTasks
            .filter { task in
                guard let due = task.dueDate else { return false }
                return due >= startOfTomorrow
            }
            .sorted(by: Self.chronologically)
    }

    var unscheduledTasks: [TaskItem] {
        openTasks.filter { $0.dueDate == nil }.sorted { $0.priority.weight > $1.priority.weight }
    }

    var ideas: [NoteItem] { data.notes.filter(\.isIdea) }
    var plainNotes: [NoteItem] { data.notes.filter { !$0.isIdea } }

    static func chronologically(_ lhs: TaskItem, _ rhs: TaskItem) -> Bool {
        switch (lhs.dueDate, rhs.dueDate) {
        case let (left?, right?):
            if Calendar.current.isDate(left, inSameDayAs: right) {
                if lhs.hasTime != rhs.hasTime { return lhs.hasTime }
                if lhs.hasTime, rhs.hasTime { return left < right }
                return lhs.priority.weight > rhs.priority.weight
            }
            return left < right
        case (nil, _?): return false
        case (_?, nil): return true
        default: return lhs.priority.weight > rhs.priority.weight
        }
    }

    // MARK: - Next action

    /// The capacity the user picked today, or nil once the local calendar day has rolled over.
    func capacity(now: Date = Date()) -> Capacity? {
        capacityDay == CapacityStorage.dayStamp(for: now) ? capacityChoice : nil
    }

    /// Called whenever the app becomes active.
    func recordOpen(now: Date = Date()) {
        let updated = OpenedDaysStorage.record(now)
        if updated != openedDays { openedDays = updated }
        // Pick up a capacity choice made today, or drop yesterday's after midnight.
        let today = CapacityStorage.dayStamp(for: now)
        if capacityDay != today {
            capacityChoice = CapacityStorage.load(on: now)
            capacityDay = today
        }
    }

    func openedDaysThisWeek(now: Date = Date()) -> Int {
        OpenedDaysStorage.countThisWeek(openedDays, now: now)
    }

    func setCapacity(_ capacity: Capacity, now: Date = Date()) {
        CapacityStorage.save(capacity, on: now)
        capacityChoice = capacity
        capacityDay = CapacityStorage.dayStamp(for: now)
    }

    func nextAction(now: Date = Date()) -> NextAction? {
        let engine = NextActionEngine(
            now: now,
            availableMinutes: availableMinutes,
            minutesUntilNextEvent: CalendarService.shared.minutesUntilNextEvent(from: now),
            nextEventTitle: CalendarService.shared.events.first?.title,
            capacity: capacity(now: now)
        )
        return engine.recommend(from: data.tasks, checkpoint: data.checkpoint)
    }

    func tasksFitting(minutes: Int) -> [TaskItem] {
        NextActionEngine(now: Date()).tasksFitting(minutes: minutes, in: data.tasks)
    }

    /// Open items the user is most likely to have forgotten: overdue, repeatedly
    /// postponed, or prerequisites for something happening soon.
    func forgottenItems(now: Date = Date()) -> [TaskItem] {
        let prerequisiteIDs = Set(data.tasks.filter(\.isOpen).flatMap(\.dependencyIDs))
        return openTasks
            .filter { task in
                task.isOverdue(now: now)
                || task.postponeCount >= 2
                || (prerequisiteIDs.contains(task.id) && task.isOpen)
                || (!task.openPreparationItems.isEmpty && (task.dueDate.map { Calendar.current.isDateInTomorrow($0) || Calendar.current.isDateInToday($0) } ?? false))
            }
            .sorted { $0.priority.weight > $1.priority.weight }
    }

    /// Everything the user needs to physically take with them for imminent tasks.
    func preparationItemsBeforeLeaving(now: Date = Date()) -> [(task: TaskItem, items: [PreparationItem])] {
        openTasks
            .filter { task in
                guard let due = task.dueDate else { return false }
                return Calendar.current.isDateInToday(due) || Calendar.current.isDateInTomorrow(due)
            }
            .compactMap { task in
                let open = task.openPreparationItems
                return open.isEmpty ? nil : (task, open)
            }
    }

    // MARK: - Mutations

    func add(_ task: TaskItem) {
        var copy = task
        copy.updatedAt = Date()
        data.tasks.append(copy)
        log(.created, title: copy.title, taskID: copy.id)
        commit()
    }

    func update(_ task: TaskItem) {
        guard let index = data.tasks.firstIndex(where: { $0.id == task.id }) else { return }
        var copy = task
        copy.updatedAt = Date()
        data.tasks[index] = copy
        commit()
    }

    func delete(taskID: UUID) {
        data.tasks.removeAll { $0.id == taskID }
        for index in data.tasks.indices {
            data.tasks[index].dependencyIDs.removeAll { $0 == taskID }
        }
        if data.checkpoint?.taskID == taskID { data.checkpoint = nil }
        commit()
    }

    func start(taskID: UUID) {
        guard let index = data.tasks.firstIndex(where: { $0.id == taskID }) else { return }
        data.tasks[index].status = .inProgress
        data.tasks[index].lastStartedAt = Date()
        data.tasks[index].updatedAt = Date()
        data.checkpoint = Checkpoint(
            taskID: taskID,
            subtaskID: data.tasks[index].nextSubtask?.id,
            note: "Started"
        )
        log(.started, title: data.tasks[index].title, taskID: taskID)
        commit()
    }

    func complete(taskID: UUID) {
        guard let index = data.tasks.firstIndex(where: { $0.id == taskID }) else { return }
        var task = data.tasks[index]
        task.status = .completed
        task.completedAt = Date()
        task.updatedAt = Date()
        for subtaskIndex in task.subtasks.indices where !task.subtasks[subtaskIndex].isDone {
            task.subtasks[subtaskIndex].isDone = true
            task.subtasks[subtaskIndex].completedAt = Date()
        }
        data.tasks[index] = task
        log(.completed, title: task.title, taskID: taskID)

        if let recurrence = task.recurrence, let due = task.dueDate {
            var next = task
            next.id = UUID()
            next.status = .notStarted
            next.completedAt = nil
            next.createdAt = Date()
            next.dueDate = Self.nextDate(after: due, rule: recurrence)
            next.subtasks = task.subtasks.map { Subtask(title: $0.title) }
            next.preparationItems = task.preparationItems.map { PreparationItem(title: $0.title) }
            data.tasks.append(next)
        }

        if data.checkpoint?.taskID == taskID { data.checkpoint = nil }
        commit()
    }

    func reopen(taskID: UUID) {
        guard let index = data.tasks.firstIndex(where: { $0.id == taskID }) else { return }
        data.tasks[index].status = .notStarted
        data.tasks[index].completedAt = nil
        data.tasks[index].updatedAt = Date()
        commit()
    }

    func postpone(taskID: UUID, to date: Date? = nil) {
        guard let index = data.tasks.firstIndex(where: { $0.id == taskID }) else { return }
        var task = data.tasks[index]
        let calendar = Calendar.current
        let target: Date
        if let date {
            target = date
        } else if let due = task.dueDate {
            target = calendar.date(byAdding: .day, value: 1, to: due) ?? due
        } else {
            target = calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: Date())) ?? Date()
        }
        task.dueDate = target
        task.postponeCount += 1
        task.status = task.status == .inProgress ? .inProgress : .postponed
        task.updatedAt = Date()
        data.tasks[index] = task
        log(.postponed, title: task.title, detail: "Moved to \(target.formatted(date: .abbreviated, time: .omitted))", taskID: taskID)
        commit()
    }

    func snooze(taskID: UUID, minutes: Int) {
        guard let index = data.tasks.firstIndex(where: { $0.id == taskID }) else { return }
        let base = data.tasks[index].dueDate ?? Date()
        data.tasks[index].dueDate = max(Date(), base).addingTimeInterval(TimeInterval(minutes * 60))
        data.tasks[index].hasTime = true
        data.tasks[index].postponeCount += 1
        data.tasks[index].updatedAt = Date()
        log(.postponed, title: data.tasks[index].title, detail: "Snoozed \(minutes) min", taskID: taskID)
        commit()
    }

    func setPriority(_ priority: Priority, taskID: UUID) {
        guard let index = data.tasks.firstIndex(where: { $0.id == taskID }) else { return }
        data.tasks[index].priority = priority
        data.tasks[index].updatedAt = Date()
        commit()
    }

    func setStatus(_ status: TaskStatus, taskID: UUID) {
        guard let index = data.tasks.firstIndex(where: { $0.id == taskID }) else { return }
        data.tasks[index].status = status
        data.tasks[index].updatedAt = Date()
        if status == .completed { data.tasks[index].completedAt = Date() }
        commit()
    }

    // MARK: - Subtasks & preparation

    func toggleSubtask(taskID: UUID, subtaskID: UUID) {
        guard let taskIndex = data.tasks.firstIndex(where: { $0.id == taskID }),
              let subtaskIndex = data.tasks[taskIndex].subtasks.firstIndex(where: { $0.id == subtaskID }) else { return }
        let nowDone = !data.tasks[taskIndex].subtasks[subtaskIndex].isDone
        data.tasks[taskIndex].subtasks[subtaskIndex].isDone = nowDone
        data.tasks[taskIndex].subtasks[subtaskIndex].completedAt = nowDone ? Date() : nil
        data.tasks[taskIndex].updatedAt = Date()

        if nowDone, data.tasks[taskIndex].status == .notStarted {
            data.tasks[taskIndex].status = .inProgress
            data.tasks[taskIndex].lastStartedAt = Date()
        }
        if data.checkpoint?.taskID == taskID {
            data.checkpoint?.subtaskID = data.tasks[taskIndex].nextSubtask?.id
            data.checkpoint?.savedAt = Date()
        }
        if data.tasks[taskIndex].subtasks.allSatisfy(\.isDone), !data.tasks[taskIndex].subtasks.isEmpty {
            complete(taskID: taskID)
            return
        }
        commit()
    }

    func addSubtask(_ title: String, taskID: UUID) {
        guard let index = data.tasks.firstIndex(where: { $0.id == taskID }) else { return }
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        data.tasks[index].subtasks.append(Subtask(title: trimmed))
        data.tasks[index].updatedAt = Date()
        commit()
    }

    func removeSubtask(_ subtaskID: UUID, taskID: UUID) {
        guard let index = data.tasks.firstIndex(where: { $0.id == taskID }) else { return }
        data.tasks[index].subtasks.removeAll { $0.id == subtaskID }
        commit()
    }

    func breakDown(taskID: UUID) {
        guard let index = data.tasks.firstIndex(where: { $0.id == taskID }) else { return }
        guard data.tasks[index].subtasks.isEmpty else { return }
        let steps = TaskBreakdown.steps(for: data.tasks[index].title)
        data.tasks[index].subtasks = steps.map { Subtask(title: $0) }
        data.tasks[index].updatedAt = Date()
        commit()
    }

    func togglePreparationItem(taskID: UUID, itemID: UUID) {
        guard let taskIndex = data.tasks.firstIndex(where: { $0.id == taskID }),
              let itemIndex = data.tasks[taskIndex].preparationItems.firstIndex(where: { $0.id == itemID }) else { return }
        data.tasks[taskIndex].preparationItems[itemIndex].isDone.toggle()
        data.tasks[taskIndex].updatedAt = Date()
        commit()
    }

    func addPreparationItem(_ title: String, taskID: UUID) {
        guard let index = data.tasks.firstIndex(where: { $0.id == taskID }) else { return }
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        data.tasks[index].preparationItems.append(PreparationItem(title: trimmed))
        data.tasks[index].updatedAt = Date()
        commit()
    }

    func removePreparationItem(_ itemID: UUID, taskID: UUID) {
        guard let index = data.tasks.firstIndex(where: { $0.id == taskID }) else { return }
        data.tasks[index].preparationItems.removeAll { $0.id == itemID }
        data.tasks[index].updatedAt = Date()
        commit()
    }

    func addDependency(_ dependencyID: UUID, to taskID: UUID) {
        guard let index = data.tasks.firstIndex(where: { $0.id == taskID }), dependencyID != taskID else { return }
        guard !data.tasks[index].dependencyIDs.contains(dependencyID) else { return }
        data.tasks[index].dependencyIDs.append(dependencyID)
        commit()
    }

    func removeDependency(_ dependencyID: UUID, from taskID: UUID) {
        guard let index = data.tasks.firstIndex(where: { $0.id == taskID }) else { return }
        data.tasks[index].dependencyIDs.removeAll { $0 == dependencyID }
        commit()
    }

    func dependencies(of task: TaskItem) -> [TaskItem] {
        task.dependencyIDs.compactMap { id in data.tasks.first { $0.id == id } }
    }

    func dependents(of task: TaskItem) -> [TaskItem] {
        data.tasks.filter { $0.dependencyIDs.contains(task.id) }
    }

    // MARK: - Checkpoints

    func saveCheckpoint(taskID: UUID, note: String = "Stopped here") {
        guard let task = task(id: taskID) else { return }
        data.checkpoint = Checkpoint(taskID: taskID, subtaskID: task.nextSubtask?.id, note: note)
        if let index = data.tasks.firstIndex(where: { $0.id == taskID }) {
            data.tasks[index].lastStoppedAt = Date()
            if data.tasks[index].status == .inProgress {
                data.tasks[index].status = .inProgress
            }
        }
        log(.stopped, title: task.title, detail: note, taskID: taskID)
        commit()
    }

    func clearCheckpoint() {
        data.checkpoint = nil
        commit()
    }

    /// Human sentence answering "where did I leave off?".
    func leftOffSummary() -> String {
        guard let checkpoint = data.checkpoint, let task = task(id: checkpoint.taskID) else {
            if let recent = data.tasks.filter({ $0.lastStartedAt != nil && $0.isOpen })
                .max(by: { ($0.lastStartedAt ?? .distantPast) < ($1.lastStartedAt ?? .distantPast) }) {
                return "You were working on \(recent.title.lowercasedFirst). Nothing was checked off yet."
            }
            return "You don't have anything in progress right now."
        }

        let done = task.subtasks.filter(\.isDone).map(\.title)
        var sentence = "You were \(task.title.lowercasedFirst)."
        if !done.isEmpty {
            sentence += " You finished \(Self.list(done.map { $0.lowercasedFirst }))."
        }
        if let next = task.nextSubtask {
            sentence += " Your next step is \(next.title.lowercasedFirst)."
        } else {
            sentence += " There's nothing left but wrapping it up."
        }
        return sentence
    }

    static func list(_ items: [String]) -> String {
        switch items.count {
        case 0: return ""
        case 1: return items[0]
        case 2: return "\(items[0]) and \(items[1])"
        default: return items.dropLast().joined(separator: ", ") + ", and " + (items.last ?? "")
        }
    }

    // MARK: - Notes, ideas, shopping

    func addNote(_ text: String, isIdea: Bool, brainDumpID: UUID? = nil) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        data.notes.insert(NoteItem(text: trimmed, isIdea: isIdea, sourceBrainDumpID: brainDumpID), at: 0)
        log(.noteAdded, title: trimmed)
        commit()
    }

    func deleteNote(_ id: UUID) {
        data.notes.removeAll { $0.id == id }
        commit()
    }

    func convertNoteToTask(_ id: UUID) {
        guard let note = data.notes.first(where: { $0.id == id }) else { return }
        add(TaskItem(title: note.text, category: .task))
        data.notes.removeAll { $0.id == id }
        commit()
    }

    func addShoppingItem(_ name: String, store: String? = nil) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        data.shopping.insert(ShoppingItem(name: trimmed.capitalizedFirst, store: store), at: 0)
        commit()
    }

    func toggleShoppingItem(_ id: UUID) {
        guard let index = data.shopping.firstIndex(where: { $0.id == id }) else { return }
        data.shopping[index].isPurchased.toggle()
        data.shopping[index].purchasedAt = data.shopping[index].isPurchased ? Date() : nil
        commit()
    }

    func deleteShoppingItem(_ id: UUID) {
        data.shopping.removeAll { $0.id == id }
        commit()
    }

    func clearPurchasedShopping() {
        data.shopping.removeAll(where: \.isPurchased)
        commit()
    }

    var shoppingByStore: [(store: String, items: [ShoppingItem])] {
        let grouped = Dictionary(grouping: data.shopping) { $0.storeLabel }
        return grouped
            .map { (store: $0.key, items: $0.value.sorted { !$0.isPurchased && $1.isPurchased }) }
            .sorted { lhs, rhs in
                if lhs.store == "Anywhere" { return false }
                if rhs.store == "Anywhere" { return true }
                return lhs.store < rhs.store
            }
    }

    // MARK: - Brain dump intake

    @discardableResult
    func saveBrainDump(transcript: String, items: [ExtractedItem], wasSpoken: Bool) -> Int {
        let record = BrainDumpRecord(transcript: transcript, savedItemCount: items.count, wasSpoken: wasSpoken)
        var createdTasks: [String: UUID] = [:]
        var saved = 0

        // First pass: create the concrete records.
        for item in items where item.isSelected {
            switch item.category {
            case .idea:
                addNoteSilently(item.title, isIdea: true, brainDumpID: record.id)
            case .note:
                addNoteSilently(item.title, isIdea: false, brainDumpID: record.id)
            case .shopping:
                data.shopping.insert(ShoppingItem(name: item.title, store: item.store), at: 0)
            default:
                var task = TaskItem(title: item.title, category: item.category)
                task.dueDate = item.dueDate
                task.hasTime = item.hasTime
                task.deadline = item.deadline
                task.estimatedMinutes = item.estimatedMinutes
                task.priority = item.priority
                task.locationName = item.locationName
                task.locationTrigger = item.locationTrigger
                task.recurrence = item.recurrence
                task.reminderLeadMinutes = item.reminderLeadMinutes
                task.sourceBrainDumpID = record.id
                if let suggestions = Optional(TaskBreakdown.preparationSuggestions(for: item.title)), !suggestions.isEmpty, item.category == .appointment {
                    task.preparationItems = suggestions.map { PreparationItem(title: $0) }
                }
                data.tasks.append(task)
                createdTasks[item.title.lowercased()] = task.id
                log(.created, title: task.title, taskID: task.id)
            }
            saved += 1
        }

        // Second pass: wire up the "X before Y" relationships.
        for item in items where item.isSelected {
            guard let targetTitle = item.preparationForTitle?.lowercased(),
                  let prepID = createdTasks[item.title.lowercased()] else { continue }
            let parentID = createdTasks.first { key, _ in
                key.contains(targetTitle) || targetTitle.contains(key)
            }?.value ?? data.tasks.first { $0.isOpen && $0.title.lowercased().contains(targetTitle) }?.id

            if let parentID, parentID != prepID,
               let parentIndex = data.tasks.firstIndex(where: { $0.id == parentID }) {
                if !data.tasks[parentIndex].dependencyIDs.contains(prepID) {
                    data.tasks[parentIndex].dependencyIDs.append(prepID)
                }
                // A prep task should land before its parent.
                if let parentDue = data.tasks[parentIndex].dueDate,
                   let prepIndex = data.tasks.firstIndex(where: { $0.id == prepID }) {
                    if data.tasks[prepIndex].dueDate == nil || (data.tasks[prepIndex].dueDate ?? .distantFuture) >= parentDue {
                        data.tasks[prepIndex].dueDate = Calendar.current.date(byAdding: .day, value: -1, to: parentDue).map {
                            max($0, Calendar.current.startOfDay(for: Date()))
                        }
                    }
                }
            }
        }

        data.brainDumps.insert(record, at: 0)
        log(.brainDump, title: "Brain dump", detail: "\(saved) item\(saved == 1 ? "" : "s") organized")
        commit()
        return saved
    }

    private func addNoteSilently(_ text: String, isIdea: Bool, brainDumpID: UUID?) {
        data.notes.insert(NoteItem(text: text, isIdea: isIdea, sourceBrainDumpID: brainDumpID), at: 0)
    }

    /// Single-sentence capture used by quick add and Siri.
    @discardableResult
    func quickCapture(_ text: String) -> String {
        let organizer = NaturalLanguageOrganizer()
        let result = organizer.organize(text)
        guard !result.isEmpty else { return "I didn't catch anything to add." }
        let count = saveBrainDump(transcript: text, items: result.items, wasSpoken: false)
        if count == 1, let first = result.items.first {
            return confirmation(for: first)
        }
        return "Got it — added \(count) things."
    }

    private func confirmation(for item: ExtractedItem) -> String {
        switch item.category {
        case .shopping:
            return "Added \(item.title.lowercasedFirst) to your shopping list."
        case .idea:
            return "Saved that as an idea."
        case .note:
            return "Saved that note."
        default:
            if let due = item.dueDate {
                let when = Calendar.current.isDateInToday(due) ? "today"
                    : Calendar.current.isDateInTomorrow(due) ? "tomorrow"
                    : due.formatted(.dateTime.weekday(.wide))
                let time = item.hasTime ? " at \(due.formatted(date: .omitted, time: .shortened))" : ""
                return "Got it — \(item.title.lowercasedFirst) \(when)\(time)."
            }
            return "Got it — added \(item.title.lowercasedFirst)."
        }
    }

    // MARK: - Focus sessions

    func recordFocusSession(_ session: FocusSessionRecord) {
        data.focusSessions.insert(session, at: 0)
        log(.focusSession, title: session.taskTitle, detail: "\(session.completedMinutes) min", taskID: session.taskID)
        commit()
    }

    // MARK: - Evening reset

    func completedToday(now: Date = Date()) -> [TaskItem] {
        data.tasks.filter { task in
            guard let completedAt = task.completedAt else { return false }
            return Calendar.current.isDate(completedAt, inSameDayAs: now)
        }
    }

    func unfinishedToday(now: Date = Date()) -> [TaskItem] {
        todayTasks(now: now)
    }

    func moveAllToTomorrow(_ ids: [UUID]) {
        let tomorrow = Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: Date())) ?? Date()
        for id in ids { postpone(taskID: id, to: tomorrow) }
    }

    // MARK: - Search

    nonisolated struct SearchResults: Sendable {
        var tasks: [TaskItem] = []
        var notes: [NoteItem] = []
        var shopping: [ShoppingItem] = []
        var history: [HistoryEntry] = []

        var isEmpty: Bool { tasks.isEmpty && notes.isEmpty && shopping.isEmpty && history.isEmpty }
    }

    func search(_ query: String) -> SearchResults {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count > 1 else { return SearchResults() }
        let keywords = Self.searchKeywords(from: trimmed)
        guard !keywords.isEmpty else { return SearchResults() }

        func matches(_ text: String) -> Bool {
            keywords.contains { text.localizedStandardContains($0) }
        }

        var results = SearchResults()
        results.tasks = data.tasks.filter {
            matches($0.title) || matches($0.notes) || matches($0.details)
            || $0.subtasks.contains { matches($0.title) }
            || $0.preparationItems.contains { matches($0.title) }
        }
        results.notes = data.notes.filter { matches($0.text) }
        results.shopping = data.shopping.filter { matches($0.name) }
        results.history = data.history.filter { matches($0.title) || matches($0.detail) }.prefix(20).map { $0 }

        // Intent shortcuts for natural questions.
        let lower = trimmed.lowercased()
        if lower.contains("postpone") || lower.contains("moved") {
            results.history = data.history.filter { $0.kind == .postponed }.prefix(20).map { $0 }
        }
        if lower.contains("working on") || lower.contains("started") {
            results.history = data.history.filter { $0.kind == .started || $0.kind == .stopped }.prefix(20).map { $0 }
        }
        return results
    }

    static func searchKeywords(from query: String) -> [String] {
        let stopWords: Set<String> = [
            "what", "when", "where", "did", "i", "was", "the", "a", "an", "to", "for", "my",
            "me", "is", "are", "do", "need", "needed", "say", "said", "going", "on", "of",
            "about", "that", "this", "last", "week", "yesterday", "today", "have", "had", "it",
        ]
        return query
            .lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { $0.count > 2 && !stopWords.contains($0) }
    }

    // MARK: - History

    func log(_ kind: HistoryKind, title: String, detail: String = "", taskID: UUID? = nil) {
        data.history.insert(HistoryEntry(kind: kind, title: title, detail: detail, taskID: taskID), at: 0)
        if data.history.count > 400 { data.history.removeLast(data.history.count - 400) }
    }

    // MARK: - Access & trial

    /// Starts the 7-day free trial once. Calling it again is a no-op so the
    /// clock can never be reset by reopening onboarding.
    func startTrialIfNeeded(now: Date = Date()) {
        guard data.settings.trialStartedAt == nil else { return }
        data.settings.trialStartedAt = now
        commit()
    }

    /// Caches the lifetime unlock so launch is instant and works offline.
    func setLifetimeUnlocked(_ isUnlocked: Bool) {
        guard data.settings.lifetimeUnlocked != isUnlocked else { return }
        data.settings.lifetimeUnlocked = isUnlocked
        scheduleSave()
    }

    // MARK: - Data management

    func exportJSON() -> String {
        persistence.exportJSON(data)
    }

    func deleteAllData() {
        let trialStartedAt = data.settings.trialStartedAt
        let lifetimeUnlocked = data.settings.lifetimeUnlocked
        data = AppData()
        data.settings.hasCompletedOnboarding = true
        // Purchases and the trial clock survive a data wipe — they belong to
        // the Apple Account, not the content.
        data.settings.trialStartedAt = trialStartedAt
        data.settings.lifetimeUnlocked = lifetimeUnlocked
        persistence.deleteAll()
        NotificationService.shared.cancelAll()
        LocationService.shared.stopMonitoringAll()
        commit()
    }

    func loadSampleData() {
        data = SampleData.starter()
        commit()
    }

    // MARK: - Persistence plumbing

    private func commit() {
        scheduleSave()
        refreshNotifications()
    }

    private func scheduleSave() {
        saveTask?.cancel()
        let snapshot = data
        let persistence = persistence
        saveTask = Task.detached(priority: .utility) {
            try? await Task.sleep(for: .milliseconds(350))
            guard !Task.isCancelled else { return }
            persistence.save(snapshot)
        }
    }

    func saveNow() {
        saveTask?.cancel()
        persistence.save(data)
    }

    private func refreshNotifications() {
        let snapshot = data.tasks
        let settings = data.settings
        Task {
            await NotificationService.shared.rebuildSchedule(tasks: snapshot, settings: settings)
        }
    }

    static func nextDate(after date: Date, rule: RecurrenceRule) -> Date {
        let calendar = Calendar.current
        switch rule {
        case .daily:
            return calendar.date(byAdding: .day, value: 1, to: date) ?? date
        case .weekdays:
            var candidate = calendar.date(byAdding: .day, value: 1, to: date) ?? date
            while !(2...6).contains(calendar.component(.weekday, from: candidate)) {
                candidate = calendar.date(byAdding: .day, value: 1, to: candidate) ?? candidate
            }
            return candidate
        case .weekly:
            return calendar.date(byAdding: .day, value: 7, to: date) ?? date
        case .monthly:
            return calendar.date(byAdding: .month, value: 1, to: date) ?? date
        }
    }
}
