import Foundation

nonisolated enum Priority: String, Codable, CaseIterable, Identifiable, Sendable {
    case now, soon, later, optional

    var id: String { rawValue }

    var label: String {
        switch self {
        case .now: "Now"
        case .soon: "Soon"
        case .later: "Later"
        case .optional: "Optional"
        }
    }

    var symbol: String {
        switch self {
        case .now: "exclamationmark.2"
        case .soon: "flag"
        case .later: "clock"
        case .optional: "circle.dotted"
        }
    }

    var weight: Double {
        switch self {
        case .now: 40
        case .soon: 22
        case .later: 8
        case .optional: 0
        }
    }
}

nonisolated enum TaskStatus: String, Codable, CaseIterable, Identifiable, Sendable {
    case notStarted, inProgress, waiting, completed, postponed, cancelled

    var id: String { rawValue }

    var label: String {
        switch self {
        case .notStarted: "Not Started"
        case .inProgress: "In Progress"
        case .waiting: "Waiting"
        case .completed: "Completed"
        case .postponed: "Postponed"
        case .cancelled: "Cancelled"
        }
    }

    var symbol: String {
        switch self {
        case .notStarted: "circle"
        case .inProgress: "play.circle"
        case .waiting: "hourglass"
        case .completed: "checkmark.circle.fill"
        case .postponed: "arrow.uturn.forward.circle"
        case .cancelled: "xmark.circle"
        }
    }

    var isOpen: Bool {
        self != .completed && self != .cancelled
    }
}

nonisolated enum ItemCategory: String, Codable, CaseIterable, Identifiable, Sendable {
    case task, reminder, appointment, idea, note, shopping, errand

    var id: String { rawValue }

    var label: String {
        switch self {
        case .task: "Task"
        case .reminder: "Reminder"
        case .appointment: "Appointment"
        case .idea: "Idea"
        case .note: "Note"
        case .shopping: "Shopping"
        case .errand: "Errand"
        }
    }

    var symbol: String {
        switch self {
        case .task: "checkmark.square"
        case .reminder: "bell"
        case .appointment: "calendar"
        case .idea: "lightbulb"
        case .note: "note.text"
        case .shopping: "cart"
        case .errand: "car"
        }
    }
}

nonisolated enum RecurrenceRule: String, Codable, CaseIterable, Identifiable, Sendable {
    case daily, weekdays, weekly, monthly

    var id: String { rawValue }

    var label: String {
        switch self {
        case .daily: "Every day"
        case .weekdays: "Every weekday"
        case .weekly: "Every week"
        case .monthly: "Every month"
        }
    }
}

nonisolated enum LocationTriggerKind: String, Codable, Sendable {
    case arriving, leaving

    var label: String {
        switch self {
        case .arriving: "When I arrive at"
        case .leaving: "When I leave"
        }
    }
}

nonisolated enum StuckReason: String, Codable, CaseIterable, Identifiable, Sendable {
    case dontKnowWhereToStart
    case tooBig
    case needSomething
    case notEnoughTime
    case distracted
    case dontWantTo
    case other

    var id: String { rawValue }

    var label: String {
        switch self {
        case .dontKnowWhereToStart: "I don't know where to start"
        case .tooBig: "It's too big"
        case .needSomething: "I need something"
        case .notEnoughTime: "I don't have enough time"
        case .distracted: "I'm distracted"
        case .dontWantTo: "I don't want to do it"
        case .other: "Something else"
        }
    }

    var symbol: String {
        switch self {
        case .dontKnowWhereToStart: "questionmark.circle"
        case .tooBig: "square.stack.3d.up"
        case .needSomething: "shippingbox"
        case .notEnoughTime: "clock.badge.exclamationmark"
        case .distracted: "wind"
        case .dontWantTo: "hand.thumbsdown"
        case .other: "ellipsis.circle"
        }
    }
}

nonisolated enum HistoryKind: String, Codable, Sendable {
    case created, started, stopped, completed, postponed, cancelled, brainDump, noteAdded, focusSession

    var label: String {
        switch self {
        case .created: "Added"
        case .started: "Started"
        case .stopped: "Stopped"
        case .completed: "Completed"
        case .postponed: "Postponed"
        case .cancelled: "Cancelled"
        case .brainDump: "Brain dump"
        case .noteAdded: "Note"
        case .focusSession: "Focus session"
        }
    }
}
