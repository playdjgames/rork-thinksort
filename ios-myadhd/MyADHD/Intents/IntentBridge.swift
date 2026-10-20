import Foundation
import SwiftUI

/// What an App Intent wants the app to show after Siri finishes speaking.
nonisolated enum IntentRoute: Hashable, Sendable {
    case leftOff
    case oneThing(UUID)
    case stuck(UUID)
    case morningBriefing
    case eveningReset
    case forgetting
    case beforeYouGo
    case brainDump
    case upcoming
    case shopping
    case task(UUID)
    case toast(String)
}

/// Payload posted through `NotificationCenter` so intents can drive navigation
/// even when the app was not running at the time Siri was invoked.
nonisolated final class IntentRouteRequest: NSObject, @unchecked Sendable {
    let route: IntentRoute

    init(route: IntentRoute) {
        self.route = route
    }
}

/// Single connection point between App Intents (which run outside the view tree)
/// and the live store + router. Intents talk to the store directly for answers and
/// post a route request when the user should also see the matching screen.
@Observable
final class IntentBridge {
    static let shared = IntentBridge()

    private(set) weak var store: BrainStore?
    private(set) weak var router: AppRouter?

    static let routeNotification = Notification.Name("app.rork.myadhd.intentRoute")

    private init() {}

    func attach(store: BrainStore, router: AppRouter) {
        self.store = store
        self.router = router
    }

    /// The store an intent should mutate. Falls back to the shared instance when
    /// the app is cold and no view has attached yet.
    var activeStore: BrainStore { store ?? BrainStore.shared }

    /// Queues a route; applied immediately if a router is attached, otherwise
    /// broadcast so `RootView` can pick it up once it appears.
    func requestRoute(_ route: IntentRoute) {
        if let router {
            apply(route, router: router)
        } else {
            NotificationCenter.default.post(
                name: Self.routeNotification,
                object: IntentRouteRequest(route: route)
            )
        }
    }

    func handle(_ request: IntentRouteRequest, router: AppRouter) {
        apply(request.route, router: router)
    }

    private func apply(_ route: IntentRoute, router: AppRouter) {
        guard activeStore.settings.hasCompletedOnboarding else { return }
        switch route {
        case .leftOff:
            router.show(fullScreen: .leftOff)
        case .oneThing(let id):
            router.show(fullScreen: .oneThing(id))
        case .stuck(let id):
            router.show(sheet: .stuck(id))
        case .morningBriefing:
            router.show(sheet: .morningBriefing)
        case .eveningReset:
            router.show(sheet: .eveningReset)
        case .forgetting:
            router.show(sheet: .forgetting)
        case .beforeYouGo:
            router.show(sheet: .beforeYouGo)
        case .brainDump:
            router.dismissModals()
            router.selectedTab = .brainDump
        case .upcoming:
            router.dismissModals()
            router.selectedTab = .upcoming
        case .shopping:
            router.dismissModals()
            router.selectedTab = .shopping
        case .task(let id):
            router.dismissModals()
            router.openTask(id)
        case .toast(let message):
            router.flash(message)
        }
    }
}
