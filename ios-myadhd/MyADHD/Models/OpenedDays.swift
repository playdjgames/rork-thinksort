import Foundation

/// A quiet, local record of which calendar days the app was opened.
/// Stored only in UserDefaults on this device. No streaks, nothing is ever reset.
nonisolated struct OpenedDaysStorage: Sendable {
    private static let key = "openedDays"

    static func load(defaults: UserDefaults = .standard) -> Set<String> {
        Set(defaults.stringArray(forKey: key) ?? [])
    }

    /// Adds the given day (local calendar) and returns the updated set.
    @discardableResult
    static func record(_ date: Date = Date(), defaults: UserDefaults = .standard) -> Set<String> {
        var days = load(defaults: defaults)
        let stamp = CapacityStorage.dayStamp(for: date)
        guard !days.contains(stamp) else { return days }
        days.insert(stamp)
        defaults.set(days.sorted(), forKey: key)
        return days
    }

    /// Unique days opened in the current calendar week, honouring the device's first weekday.
    static func countThisWeek(_ days: Set<String>, now: Date = Date(), calendar: Calendar = .current) -> Int {
        guard let week = calendar.dateInterval(of: .weekOfYear, for: now) else { return 0 }
        return (0..<7).reduce(0) { count, offset in
            guard let day = calendar.date(byAdding: .day, value: offset, to: week.start) else { return count }
            return days.contains(CapacityStorage.dayStamp(for: day)) ? count + 1 : count
        }
    }
}
