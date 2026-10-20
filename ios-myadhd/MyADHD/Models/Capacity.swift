import Foundation

/// How much energy the user says they have right now. Chosen once a day on Today.
nonisolated enum Capacity: String, CaseIterable, Identifiable, Sendable {
    case low, medium, high

    var id: String { rawValue }

    var label: String {
        switch self {
        case .low: return "Low"
        case .medium: return "Medium"
        case .high: return "High"
        }
    }

    var symbol: String {
        switch self {
        case .low: return "gauge.with.dots.needle.0percent"
        case .medium: return "gauge.with.dots.needle.50percent"
        case .high: return "gauge.with.dots.needle.100percent"
        }
    }
}

/// Stores today's capacity choice in UserDefaults only (never synced; included only in
/// the user's own local backup file).
/// The choice is tagged with the local calendar day it was made on, so it simply stops
/// applying after midnight local time.
nonisolated struct CapacityStorage: Sendable {
    private static let levelKey = "capacity.level"
    private static let dayKey = "capacity.day"

    static func dayStamp(for date: Date) -> String {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    static func load(on date: Date = Date(), defaults: UserDefaults = .standard) -> Capacity? {
        guard defaults.string(forKey: dayKey) == dayStamp(for: date),
              let raw = defaults.string(forKey: levelKey) else { return nil }
        return Capacity(rawValue: raw)
    }

    /// The stored choice and the day it was made on, regardless of whether it is still today.
    static func loadRaw(defaults: UserDefaults = .standard) -> (level: Capacity, day: String)? {
        guard let raw = defaults.string(forKey: levelKey),
              let level = Capacity(rawValue: raw),
              let day = defaults.string(forKey: dayKey) else { return nil }
        return (level, day)
    }

    /// Writes a choice tagged with an explicit day stamp (used when restoring a backup).
    /// Passing nil clears the stored choice.
    static func restore(level: Capacity?, day: String?, defaults: UserDefaults = .standard) {
        if let level, let day {
            defaults.set(level.rawValue, forKey: levelKey)
            defaults.set(day, forKey: dayKey)
        } else {
            defaults.removeObject(forKey: levelKey)
            defaults.removeObject(forKey: dayKey)
        }
    }

    static func save(_ capacity: Capacity, on date: Date = Date(), defaults: UserDefaults = .standard) {
        defaults.set(capacity.rawValue, forKey: levelKey)
        defaults.set(dayStamp(for: date), forKey: dayKey)
    }
}
