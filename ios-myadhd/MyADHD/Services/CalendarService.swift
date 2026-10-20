import Foundation
import EventKit
import OSLog

nonisolated struct CalendarEventSummary: Identifiable, Hashable, Sendable {
    var id: String
    var title: String
    var startDate: Date
    var endDate: Date
    var location: String?
    var isAllDay: Bool
}

/// Read-only calendar access. ThinkSort never modifies calendar events.
@Observable
final class CalendarService {
    static let shared = CalendarService()

    private let store = EKEventStore()

    private(set) var authorizationStatus: EKAuthorizationStatus = EKEventStore.authorizationStatus(for: .event)
    private(set) var events: [CalendarEventSummary] = []

    private init() {}

    var isAuthorized: Bool {
        authorizationStatus == .fullAccess || authorizationStatus == .authorized
    }

    func refreshStatus() {
        authorizationStatus = EKEventStore.authorizationStatus(for: .event)
    }

    @discardableResult
    func requestAccess() async -> Bool {
        do {
            let granted = try await store.requestFullAccessToEvents()
            refreshStatus()
            if granted { await reload() }
            return granted
        } catch {
            appLogger.error("Calendar access request failed: \(error.localizedDescription, privacy: .public)")
            refreshStatus()
            return false
        }
    }

    func reload(daysAhead: Int = 7) async {
        refreshStatus()
        guard isAuthorized else {
            events = []
            return
        }
        let calendar = Calendar.current
        let start = calendar.startOfDay(for: Date())
        guard let end = calendar.date(byAdding: .day, value: daysAhead, to: start) else { return }
        let predicate = store.predicateForEvents(withStart: start, end: end, calendars: nil)
        let found = store.events(matching: predicate)
        events = found
            .sorted { $0.startDate < $1.startDate }
            .map {
                CalendarEventSummary(
                    id: $0.eventIdentifier ?? UUID().uuidString,
                    title: $0.title ?? "Event",
                    startDate: $0.startDate,
                    endDate: $0.endDate,
                    location: $0.location?.isEmpty == false ? $0.location : nil,
                    isAllDay: $0.isAllDay
                )
            }
    }

    func events(on day: Date) -> [CalendarEventSummary] {
        events.filter { Calendar.current.isDate($0.startDate, inSameDayAs: day) }
    }

    /// Free minutes between now and the next event — used by "what fits in my time?".
    func minutesUntilNextEvent(from date: Date = Date()) -> Int? {
        guard let next = events.first(where: { $0.startDate > date && !$0.isAllDay }) else { return nil }
        return Calendar.current.dateComponents([.minute], from: date, to: next.startDate).minute
    }
}
