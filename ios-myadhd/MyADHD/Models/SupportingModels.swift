import Foundation

nonisolated struct NoteItem: Identifiable, Codable, Hashable, Sendable {
    var id: UUID = UUID()
    var text: String
    var isIdea: Bool = false
    var createdAt: Date = Date()
    var sourceBrainDumpID: UUID?
}

nonisolated struct ShoppingItem: Identifiable, Codable, Hashable, Sendable {
    var id: UUID = UUID()
    var name: String
    var store: String?
    var isPurchased: Bool = false
    var createdAt: Date = Date()
    var purchasedAt: Date?

    var storeLabel: String { store?.isEmpty == false ? store! : "Anywhere" }
}

nonisolated struct BrainDumpRecord: Identifiable, Codable, Hashable, Sendable {
    var id: UUID = UUID()
    var transcript: String
    var createdAt: Date = Date()
    var savedItemCount: Int = 0
    var wasSpoken: Bool = false
}

nonisolated struct FocusSessionRecord: Identifiable, Codable, Hashable, Sendable {
    var id: UUID = UUID()
    var taskID: UUID?
    var taskTitle: String
    var plannedMinutes: Int
    var completedMinutes: Int
    var startedAt: Date = Date()
    var endedAt: Date?
    var finished: Bool = false
}

/// Where the user stopped working — powers "Where did I leave off?".
nonisolated struct Checkpoint: Codable, Hashable, Sendable {
    var taskID: UUID
    var subtaskID: UUID?
    var savedAt: Date = Date()
    var note: String = ""
}

nonisolated struct HistoryEntry: Identifiable, Codable, Hashable, Sendable {
    var id: UUID = UUID()
    var kind: HistoryKind
    var title: String
    var detail: String = ""
    var taskID: UUID?
    var date: Date = Date()
}

nonisolated struct QuietHours: Codable, Hashable, Sendable {
    var isEnabled: Bool = true
    /// Minutes from midnight.
    var start: Int = 22 * 60
    var end: Int = 7 * 60

    func contains(_ date: Date) -> Bool {
        guard isEnabled else { return false }
        let comps = Calendar.current.dateComponents([.hour, .minute], from: date)
        let minutes = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
        if start <= end { return minutes >= start && minutes < end }
        return minutes >= start || minutes < end
    }
}

nonisolated enum AppearanceMode: String, Codable, CaseIterable, Identifiable, Sendable {
    case system, light, dark

    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        }
    }
}

nonisolated enum ReminderFrequency: String, Codable, CaseIterable, Identifiable, Sendable {
    case minimal, balanced, persistent

    var id: String { rawValue }

    var label: String {
        switch self {
        case .minimal: "Only what matters"
        case .balanced: "Balanced"
        case .persistent: "Keep nudging me"
        }
    }

    var maxScheduled: Int {
        switch self {
        case .minimal: 12
        case .balanced: 30
        case .persistent: 56
        }
    }
}

nonisolated struct AppSettings: Codable, Hashable, Sendable {
    var displayName: String = ""
    var notificationsEnabled: Bool = true
    var preparationRemindersEnabled: Bool = true
    var defaultReminderLeadMinutes: Int = 45
    var reminderFrequency: ReminderFrequency = .balanced
    var quietHours: QuietHours = QuietHours()
    var calendarEnabled: Bool = false
    var locationRemindersEnabled: Bool = false
    var voiceInputEnabled: Bool = true
    var speakResponses: Bool = false
    var appearance: AppearanceMode = .system
    var hapticsEnabled: Bool = true
    var reduceVisualMotion: Bool = false
    var defaultFocusMinutes: Int = 25
    var hasCompletedOnboarding: Bool = false
    var lastMorningBriefing: Date?
    var lastEveningReset: Date?
    /// When the user activated the 7-day free trial. Optional so older saved
    /// files keep decoding.
    var trialStartedAt: Date?
    /// Cached lifetime-unlock flag so launch is instant and works offline.
    var lifetimeUnlocked: Bool?
}

nonisolated struct AppData: Codable, Sendable {
    var tasks: [TaskItem] = []
    var notes: [NoteItem] = []
    var shopping: [ShoppingItem] = []
    var brainDumps: [BrainDumpRecord] = []
    var focusSessions: [FocusSessionRecord] = []
    var history: [HistoryEntry] = []
    var checkpoint: Checkpoint?
    var settings: AppSettings = AppSettings()
    var schemaVersion: Int = 1
}
