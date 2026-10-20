import SwiftUI

nonisolated enum AppTab: String, Hashable, CaseIterable, Sendable {
    case today, upcoming, brainDump, focus, shopping

    var title: String {
        switch self {
        case .today: "Today"
        case .upcoming: "Upcoming"
        case .brainDump: "Brain Dump"
        case .focus: "Focus"
        case .shopping: "Shopping"
        }
    }

    var symbol: String {
        switch self {
        case .today: "house"
        case .upcoming: "calendar"
        case .brainDump: "mic"
        case .focus: "target"
        case .shopping: "cart"
        }
    }
}

nonisolated enum Route: Hashable, Sendable {
    case task(UUID)
    case notes
    case search
    case history
}

nonisolated enum Modal: Identifiable, Hashable, Sendable {
    case leftOff
    case oneThing(UUID)
    case stuck(UUID)
    case morningBriefing
    case eveningReset
    case settings
    case timeAvailable
    case runningLate
    case forgetting
    case beforeYouGo
    case quickAdd
    case onboarding

    var id: String {
        switch self {
        case .leftOff: "leftOff"
        case .oneThing(let id): "oneThing-\(id)"
        case .stuck(let id): "stuck-\(id)"
        case .morningBriefing: "morning"
        case .eveningReset: "evening"
        case .settings: "settings"
        case .timeAvailable: "time"
        case .runningLate: "late"
        case .forgetting: "forgetting"
        case .beforeYouGo: "beforeYouGo"
        case .quickAdd: "quickAdd"
        case .onboarding: "onboarding"
        }
    }
}

/// Owns tab selection, the per-tab navigation paths and presented modals so
/// Siri intents and in-app buttons can drive the same destinations.
@Observable
final class AppRouter {
    var selectedTab: AppTab = .today
    var todayPath = NavigationPath()
    var upcomingPath = NavigationPath()
    var shoppingPath = NavigationPath()
    var brainDumpPath = NavigationPath()

    var sheet: Modal?
    var fullScreen: Modal?

    /// Transient confirmation banner, e.g. after a Siri capture.
    var toast: String?

    /// Set when the "Dump" widget opens the app; Brain Dump focuses its text field.
    var focusBrainDump = false

    func openTask(_ id: UUID) {
        switch selectedTab {
        case .upcoming: upcomingPath.append(Route.task(id))
        case .shopping: shoppingPath.append(Route.task(id))
        case .brainDump: brainDumpPath.append(Route.task(id))
        default:
            selectedTab = .today
            todayPath.append(Route.task(id))
        }
    }

    func push(_ route: Route) {
        switch selectedTab {
        case .upcoming: upcomingPath.append(route)
        case .shopping: shoppingPath.append(route)
        case .brainDump: brainDumpPath.append(route)
        default:
            selectedTab = .today
            todayPath.append(route)
        }
    }

    func show(sheet modal: Modal) {
        fullScreen = nil
        sheet = modal
    }

    func show(fullScreen modal: Modal) {
        sheet = nil
        fullScreen = modal
    }

    func dismissModals() {
        sheet = nil
        fullScreen = nil
    }

    func flash(_ message: String) {
        toast = message
        Task {
            try? await Task.sleep(for: .seconds(3))
            if toast == message { toast = nil }
        }
    }
}
