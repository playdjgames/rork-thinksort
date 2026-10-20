import SwiftUI

struct ContentView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router

    var body: some View {
        @Bindable var router = router

        TabView(selection: $router.selectedTab) {
            Tab(AppTab.today.title, systemImage: AppTab.today.symbol, value: AppTab.today) {
                NavigationStack(path: $router.todayPath) {
                    TodayView()
                        .navigationDestination(for: Route.self, destination: destination)
                }
            }

            Tab(AppTab.upcoming.title, systemImage: AppTab.upcoming.symbol, value: AppTab.upcoming) {
                NavigationStack(path: $router.upcomingPath) {
                    UpcomingView()
                        .navigationDestination(for: Route.self, destination: destination)
                }
            }

            Tab(AppTab.brainDump.title, systemImage: AppTab.brainDump.symbol, value: AppTab.brainDump) {
                NavigationStack(path: $router.brainDumpPath) {
                    BrainDumpView()
                        .navigationDestination(for: Route.self, destination: destination)
                }
            }

            Tab(AppTab.focus.title, systemImage: AppTab.focus.symbol, value: AppTab.focus) {
                NavigationStack {
                    FocusView()
                }
            }

            Tab(AppTab.shopping.title, systemImage: AppTab.shopping.symbol, value: AppTab.shopping) {
                NavigationStack(path: $router.shoppingPath) {
                    ShoppingView()
                        .navigationDestination(for: Route.self, destination: destination)
                }
            }
        }
        .tint(Theme.accent)
        .overlay(alignment: .top) { toast }
        .sheet(item: $router.sheet) { modal in
            modalView(modal)
        }
        .fullScreenCover(item: $router.fullScreen) { modal in
            modalView(modal)
        }
        .animation(.snappy(duration: 0.25), value: router.toast)
    }

    @ViewBuilder
    private func destination(for route: Route) -> some View {
        switch route {
        case .task(let id):
            TaskDetailView(taskID: id)
        case .note(let id):
            NoteDetailView(noteID: id)
        case .notes:
            NotesView()
        case .search:
            SearchView()
        case .history:
            HistoryView()
        }
    }

    @ViewBuilder
    private func modalView(_ modal: Modal) -> some View {
        switch modal {
        case .leftOff:
            LeftOffView()
        case .oneThing(let id):
            OneThingView(taskID: id)
        case .stuck(let id):
            StuckView(taskID: id)
        case .morningBriefing:
            MorningBriefingView()
        case .eveningReset:
            EveningResetView()
        case .settings:
            SettingsView()
        case .timeAvailable:
            TimeAvailableView()
        case .runningLate:
            RunningLateView()
        case .forgetting:
            ForgettingView()
        case .beforeYouGo:
            BeforeYouGoView()
        case .quickAdd:
            QuickAddView()
        case .onboarding:
            OnboardingView()
        }
    }

    @ViewBuilder
    private var toast: some View {
        if let message = router.toast {
            Text(message)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(Theme.canvas)
                .padding(.horizontal, 18)
                .padding(.vertical, 12)
                .background(Theme.ink.opacity(0.94), in: .capsule)
                .padding(.top, 8)
                .transition(.move(edge: .top).combined(with: .opacity))
                .accessibilityAddTraits(.isStaticText)
        }
    }
}

#Preview {
    ContentView()
        .environment(BrainStore())
        .environment(AppRouter())
}
