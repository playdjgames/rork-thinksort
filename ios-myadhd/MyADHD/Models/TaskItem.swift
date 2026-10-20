import Foundation

nonisolated struct Subtask: Identifiable, Codable, Hashable, Sendable {
    var id: UUID = UUID()
    var title: String
    var isDone: Bool = false
    var completedAt: Date?
}

nonisolated struct PreparationItem: Identifiable, Codable, Hashable, Sendable {
    var id: UUID = UUID()
    var title: String
    var isDone: Bool = false
}

nonisolated struct LocationTrigger: Codable, Hashable, Sendable {
    var kind: LocationTriggerKind
    var placeName: String
    var latitude: Double?
    var longitude: Double?

    var isResolved: Bool { latitude != nil && longitude != nil }
}

/// The core unit of ThinkSort. A task, reminder, appointment or errand the user has to act on.
nonisolated struct TaskItem: Identifiable, Codable, Hashable, Sendable {
    var id: UUID = UUID()
    var title: String
    var details: String = ""
    var status: TaskStatus = .notStarted
    var priority: Priority = .soon
    var category: ItemCategory = .task
    /// Day the task is due. Time-of-day is meaningful only when `hasTime` is true.
    var dueDate: Date?
    var hasTime: Bool = false
    /// Hard deadline ("before Friday"), distinct from a scheduled day.
    var deadline: Date?
    var estimatedMinutes: Int?
    var locationName: String?
    var locationTrigger: LocationTrigger?
    var dependencyIDs: [UUID] = []
    var preparationItems: [PreparationItem] = []
    var subtasks: [Subtask] = []
    var recurrence: RecurrenceRule?
    /// Minutes before the due time to fire a preparation reminder.
    var reminderLeadMinutes: Int?
    var repeatUntilDone: Bool = false
    var notes: String = ""
    var createdAt: Date = Date()
    var updatedAt: Date = Date()
    var completedAt: Date?
    var lastStartedAt: Date?
    var lastStoppedAt: Date?
    var postponeCount: Int = 0
    var sourceBrainDumpID: UUID?

    var isOpen: Bool { status.isOpen }

    var nextSubtask: Subtask? {
        subtasks.first { !$0.isDone }
    }

    var completedSubtaskCount: Int {
        subtasks.filter(\.isDone).count
    }

    var openPreparationItems: [PreparationItem] {
        preparationItems.filter { !$0.isDone }
    }

    /// The moment this task should happen, combining day and time-of-day.
    var scheduledDate: Date? {
        dueDate
    }

    func isOverdue(now: Date = Date()) -> Bool {
        guard isOpen else { return false }
        if let deadline, deadline < now { return true }
        guard let dueDate else { return false }
        if hasTime { return dueDate < now }
        return Calendar.current.startOfDay(for: dueDate) < Calendar.current.startOfDay(for: now)
    }

    func isDue(on day: Date) -> Bool {
        guard let dueDate else { return false }
        return Calendar.current.isDate(dueDate, inSameDayAs: day)
    }
}
