import Foundation

/// The plain JSON file a user can export and later restore. Everything lives in one
/// object so the file is readable and portable. It never leaves the device unless the
/// user shares it themselves.
nonisolated struct ThinkSortBackup: Codable, Sendable {
    static let currentVersion = 1

    var thinksortBackupVersion: Int
    var exportedAt: Date
    var tasks: [TaskItem]
    var notes: [NoteItem]
    var shopping: [ShoppingItem]
    var brainDumps: [BrainDumpRecord]
    var focusSessions: [FocusSessionRecord]
    var history: [HistoryEntry]
    var checkpoint: Checkpoint?
    var settings: AppSettings
    var capacity: BackupCapacity?
    var openedDays: [String]
}

nonisolated struct BackupCapacity: Codable, Sendable {
    var level: String
    /// Local calendar day (yyyy-MM-dd) the choice was made on.
    var day: String
}

nonisolated enum BackupError: LocalizedError, Sendable {
    case missingVersion
    case unsupportedVersion(Int)
    case unreadable

    var errorDescription: String? { "That file is not a ThinkSort backup" }
}

/// Used to check the version key before decoding anything else.
nonisolated struct BackupVersionProbe: Decodable, Sendable {
    var thinksortBackupVersion: Int?
}
