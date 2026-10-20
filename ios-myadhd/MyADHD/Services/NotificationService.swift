import Foundation
@preconcurrency import UserNotifications
import OSLog

/// Schedules the app's local notifications: due reminders, preparation nudges,
/// morning briefing and evening reset. Deliberately conservative — it clears and
/// rebuilds the schedule so the user never accumulates duplicate nudges.
nonisolated final class NotificationService: Sendable {
    static let shared = NotificationService()

    private let center = UNUserNotificationCenter.current()

    private init() {}

    func authorizationStatus() async -> UNAuthorizationStatus {
        await center.notificationSettings().authorizationStatus
    }

    @discardableResult
    func requestAuthorization() async -> Bool {
        do {
            return try await center.requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            appLogger.error("Notification permission request failed: \(error.localizedDescription, privacy: .public)")
            return false
        }
    }

    func rebuildSchedule(tasks: [TaskItem], settings: AppSettings) async {
        center.removeAllPendingNotificationRequests()
        guard settings.notificationsEnabled else { return }
        let status = await authorizationStatus()
        guard status == .authorized || status == .provisional else { return }

        var requests: [UNNotificationRequest] = []
        let now = Date()
        let limit = settings.reminderFrequency.maxScheduled

        for task in tasks where task.isOpen {
            guard let due = task.dueDate else { continue }

            if task.hasTime, due > now {
                requests.append(contentsOf: makeRequests(for: task, due: due, settings: settings))
            } else if !task.hasTime, let morning = Calendar.current.date(bySettingHour: 9, minute: 0, second: 0, of: due), morning > now {
                let content = makeContent(
                    title: "Today: \(task.title)",
                    body: task.openPreparationItems.isEmpty
                        ? "You said you wanted to do this today."
                        : "You'll need: \(task.openPreparationItems.map(\.title).joined(separator: ", "))"
                )
                requests.append(request(id: "due-\(task.id.uuidString)", content: content, date: shifted(morning, settings: settings)))
            }
        }

        if settings.notificationsEnabled {
            requests.append(contentsOf: dailyRoutineRequests(settings: settings))
        }

        for request in requests.prefix(limit) {
            do {
                try await center.add(request)
            } catch {
                appLogger.error("Could not schedule reminder: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    private func makeRequests(for task: TaskItem, due: Date, settings: AppSettings) -> [UNNotificationRequest] {
        var result: [UNNotificationRequest] = []
        let now = Date()

        let content = makeContent(
            title: task.title,
            body: timeBody(for: task, due: due)
        )
        result.append(request(id: "due-\(task.id.uuidString)", content: content, date: shifted(due, settings: settings)))

        if settings.preparationRemindersEnabled, !task.openPreparationItems.isEmpty || task.reminderLeadMinutes != nil {
            let lead = task.reminderLeadMinutes ?? settings.defaultReminderLeadMinutes
            if let prepDate = Calendar.current.date(byAdding: .minute, value: -lead, to: due), prepDate > now {
                let items = task.openPreparationItems.map(\.title)
                let body = items.isEmpty
                    ? "Coming up in \(lead) minutes."
                    : "Before you go, grab: \(items.joined(separator: ", "))"
                let prepContent = makeContent(title: "Get ready: \(task.title)", body: body)
                result.append(request(id: "prep-\(task.id.uuidString)", content: prepContent, date: shifted(prepDate, settings: settings)))
            }
        }

        if task.repeatUntilDone, let nudge = Calendar.current.date(byAdding: .minute, value: 30, to: due), nudge > now {
            let nudgeContent = makeContent(title: "Still open: \(task.title)", body: "Mark it done when you've finished.")
            result.append(request(id: "nudge-\(task.id.uuidString)", content: nudgeContent, date: shifted(nudge, settings: settings)))
        }

        return result
    }

    private func dailyRoutineRequests(settings: AppSettings) -> [UNNotificationRequest] {
        var result: [UNNotificationRequest] = []

        var morning = DateComponents()
        morning.hour = 8
        morning.minute = 0
        let morningContent = makeContent(title: "Good morning", body: "Here's your day, one thing at a time.")
        result.append(UNNotificationRequest(
            identifier: "routine-morning",
            content: morningContent,
            trigger: UNCalendarNotificationTrigger(dateMatching: morning, repeats: true)
        ))

        var evening = DateComponents()
        evening.hour = 20
        evening.minute = 30
        let eveningContent = makeContent(title: "Evening reset", body: "Two minutes to clear tomorrow's runway.")
        result.append(UNNotificationRequest(
            identifier: "routine-evening",
            content: eveningContent,
            trigger: UNCalendarNotificationTrigger(dateMatching: evening, repeats: true)
        ))

        return result
    }

    private func timeBody(for task: TaskItem, due: Date) -> String {
        let time = due.formatted(date: .omitted, time: .shortened)
        if let place = task.locationName {
            return "\(time) · \(place)"
        }
        return "Scheduled for \(time)."
    }

    private func makeContent(title: String, body: String) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        return content
    }

    private func request(id: String, content: UNNotificationContent, date: Date) -> UNNotificationRequest {
        let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        return UNNotificationRequest(identifier: id, content: content, trigger: trigger)
    }

    /// Pushes a notification out of quiet hours so the user isn't woken up.
    private func shifted(_ date: Date, settings: AppSettings) -> Date {
        guard settings.quietHours.contains(date) else { return date }
        let calendar = Calendar.current
        let endHour = settings.quietHours.end / 60
        let endMinute = settings.quietHours.end % 60
        var candidate = calendar.date(bySettingHour: endHour, minute: endMinute, second: 0, of: date) ?? date
        if candidate < date {
            candidate = calendar.date(byAdding: .day, value: 1, to: candidate) ?? candidate
        }
        return candidate
    }

    /// Two quiet heads-ups during the free trial: halfway through and on the
    /// last day. Cleared once the lifetime unlock is owned.
    func scheduleTrialReminders(trialStartedAt: Date?, isUnlocked: Bool, trialDays: Int) async {
        let ids = ["trial-midpoint", "trial-final"]
        center.removePendingNotificationRequests(withIdentifiers: ids)
        guard !isUnlocked, let trialStartedAt else { return }

        let status = await authorizationStatus()
        guard status == .authorized || status == .provisional else { return }

        let calendar = Calendar.current
        let now = Date()
        let schedule: [(String, Int, String, String)] = [
            (
                "trial-midpoint",
                trialDays / 2,
                "You're halfway through your free week",
                "Still \(trialDays - trialDays / 2) days of ThinkSort left. No payment yet."
            ),
            (
                "trial-final",
                trialDays - 1,
                "Your free week ends tomorrow",
                "Unlock ThinkSort once for lifetime access. Your tasks stay on this device either way."
            ),
        ]

        for (id, dayOffset, title, body) in schedule {
            guard
                let day = calendar.date(byAdding: .day, value: dayOffset, to: trialStartedAt),
                let fireDate = calendar.date(bySettingHour: 10, minute: 0, second: 0, of: day),
                fireDate > now
            else { continue }

            let request = request(id: id, content: makeContent(title: title, body: body), date: fireDate)
            do {
                try await center.add(request)
            } catch {
                appLogger.error("Could not schedule trial reminder: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    func cancelAll() {
        center.removeAllPendingNotificationRequests()
    }
}
