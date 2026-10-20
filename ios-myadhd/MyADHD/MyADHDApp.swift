import SwiftUI

@main
struct MyADHDApp: App {
    @State private var store = BrainStore.shared
    @State private var router = AppRouter()
    @Environment(\.scenePhase) private var scenePhase

    init() {
        StoreService.shared.configure()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
                .environment(router)
                .preferredColorScheme(colorScheme)
                .tint(Theme.accent)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background || phase == .inactive {
                store.saveNow()
            }
            if phase == .active {
                Task { await CalendarService.shared.reload() }
                Task { await StoreService.shared.refresh() }
            }
        }
    }

    private var colorScheme: ColorScheme? {
        switch store.settings.appearance {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}

/// Decides between onboarding, the trial gate and the main tab shell, and wires
/// Siri intents to the router through `IntentBridge`.
struct RootView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router

    private var purchases: StoreService { StoreService.shared }

    private var access: AccessState {
        purchases.accessState(trialStartedAt: store.settings.trialStartedAt)
    }

    var body: some View {
        Group {
            if !store.settings.hasCompletedOnboarding {
                OnboardingView()
            } else {
                switch access {
                case .trialNotStarted:
                    TrialStartView()
                case .expired:
                    NavigationStack {
                        PaywallView(isHardGate: true)
                    }
                case .trial, .unlocked:
                    ContentView()
                }
            }
        }
        .animation(.snappy(duration: 0.3), value: access)
        .task {
            IntentBridge.shared.attach(store: store, router: router)
            purchases.attach(cachedUnlock: store.settings.lifetimeUnlocked == true) { isUnlocked in
                store.setLifetimeUnlocked(isUnlocked)
            }
            if store.settings.calendarEnabled {
                await CalendarService.shared.reload()
            }
        }
        .task(id: trialReminderKey) {
            await NotificationService.shared.scheduleTrialReminders(
                trialStartedAt: store.settings.trialStartedAt,
                isUnlocked: purchases.isUnlocked,
                trialDays: StoreService.trialDays
            )
        }
        .onReceive(NotificationCenter.default.publisher(for: IntentBridge.routeNotification)) { note in
            guard let request = note.object as? IntentRouteRequest else { return }
            IntentBridge.shared.handle(request, router: router)
        }
    }

    /// Reschedules trial nudges when the trial starts or the app is unlocked.
    private var trialReminderKey: String {
        "\(store.settings.trialStartedAt?.timeIntervalSince1970 ?? 0)-\(purchases.isUnlocked)"
    }
}
