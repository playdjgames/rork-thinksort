import SwiftUI
import Combine

struct TodayView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var now: Date = Date()
    @State private var showMoreMenu = false

    private let ticker = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    private var nextAction: NextAction? { store.nextAction(now: now) }
    private var todayTasks: [TaskItem] { store.todayTasks(now: now) }
    private var upcomingCount: Int { store.upcomingTasks(now: now).count }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header

                if let checkpoint = store.checkpoint, let task = store.task(id: checkpoint.taskID), task.isOpen {
                    resumeBanner(task: task)
                }

                capacityRow

                if let action = nextAction {
                    nextUpCard(action)
                    QuickActionRow(actions: [.start, .done, .later, .stuck]) { handle($0, task: action.task) }
                } else {
                    allClearCard
                }

                countTiles

                if !timelineTasks.isEmpty {
                    timelineSection
                }

                Button {
                    router.selectedTab = .brainDump
                } label: {
                    Label("Dump My Brain", systemImage: "mic.fill")
                }
                .buttonStyle(PrimaryButtonStyle())

                Button {
                    router.show(sheet: .quickAdd)
                } label: {
                    Label("Ask ThinkSort", systemImage: "text.bubble")
                }
                .buttonStyle(SoftButtonStyle())
            }
            .padding(.horizontal, Theme.gutter)
            .padding(.top, 8)
            .padding(.bottom, 28)
        }
        .canvas()
        .scrollIndicators(.hidden)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .navigationBar)
        .onReceive(ticker) { now = $0 }
        .refreshable {
            now = Date()
            if store.settings.calendarEnabled { await CalendarService.shared.reload() }
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 2) {
                Text(greeting)
                    .font(.largeTitle.bold())
                    .foregroundStyle(Theme.ink)
                Text(now.formatted(.dateTime.weekday(.wide).month(.wide).day()))
                    .font(.title3)
                    .foregroundStyle(Theme.inkSecondary)
                Text(openedLine)
                    .font(.footnote)
                    .foregroundStyle(Theme.inkTertiary)
                    .padding(.top, 2)
            }
            Spacer(minLength: 12)
            Menu {
                Button { router.show(sheet: .morningBriefing) } label: { Label("Morning briefing", systemImage: "sunrise") }
                Button { router.show(sheet: .eveningReset) } label: { Label("Evening reset", systemImage: "moon.stars") }
                Divider()
                Button { router.show(sheet: .timeAvailable) } label: { Label("I only have a few minutes", systemImage: "timer") }
                Button { router.show(sheet: .beforeYouGo) } label: { Label("What do I need before I go?", systemImage: "bag") }
                Button { router.show(sheet: .forgetting) } label: { Label("What am I forgetting?", systemImage: "questionmark.circle") }
                Button { router.show(sheet: .runningLate) } label: { Label("I'm running late", systemImage: "hare") }
                Divider()
                Button { router.push(.notes) } label: { Label("Notes & ideas", systemImage: "lightbulb") }
                Button { router.push(.search) } label: { Label("Search", systemImage: "magnifyingglass") }
                Button { router.push(.history) } label: { Label("History", systemImage: "clock.arrow.circlepath") }
                Button { router.show(sheet: .settings) } label: { Label("Settings", systemImage: "gearshape") }
            } label: {
                IconWell(symbol: "person.fill", size: 48)
            }
            .accessibilityLabel("More options")
        }
    }

    private var openedLine: String {
        let count = max(store.openedDaysThisWeek(now: now), 1)
        return "Opened \(count) \(count == 1 ? "day" : "days") this week"
    }

    // MARK: - Capacity

    private var capacityRow: some View {
        let selected = store.capacity(now: now)
        return VStack(alignment: .leading, spacing: 10) {
            Text("How much gas do you have right now?")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.ink)
            HStack(spacing: 8) {
                ForEach(Capacity.allCases) { level in
                    let isOn = selected == level
                    Button {
                        withAnimation(reduceMotion ? nil : .snappy(duration: 0.25)) {
                            store.setCapacity(level, now: now)
                        }
                    } label: {
                        Label(level.label, systemImage: level.symbol)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                            .frame(maxWidth: .infinity, minHeight: 40)
                            .foregroundStyle(isOn ? Theme.accent : Theme.inkSecondary)
                            .background(isOn ? Theme.accentSoft : Theme.surface, in: .capsule)
                            .overlay(Capsule().strokeBorder(isOn ? Theme.accent.opacity(0.5) : Theme.hairline, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .sensoryFeedback(.selection, trigger: isOn)
                    .accessibilityAddTraits(isOn ? .isSelected : [])
                }
            }
        }
    }

    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: now)
        switch hour {
        case 0..<12: return "Good morning"
        case 12..<18: return "Good afternoon"
        default: return "Good evening"
        }
    }

    // MARK: - Next up

    private func nextUpCard(_ action: NextAction) -> some View {
        Button {
            router.openTask(action.task.id)
        } label: {
            HStack(alignment: .top, spacing: 16) {
                IconWell(symbol: action.task.category.symbol, size: 68)
                VStack(alignment: .leading, spacing: 8) {
                    Eyebrow(text: "Next up")
                    Text(action.task.title)
                        .font(.title2.bold())
                        .foregroundStyle(Theme.ink)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("WHY: \(action.reason)")
                        .font(.subheadline)
                        .foregroundStyle(Theme.inkSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                    if let note = action.note {
                        Label(note, systemImage: "arrow.uturn.forward")
                            .font(.footnote)
                            .foregroundStyle(Theme.inkTertiary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .card()
        }
        .buttonStyle(.plain)
        .transition(reduceMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.97)))
        .id(action.task.id)
        .animation(reduceMotion ? nil : .snappy(duration: 0.3), value: action.task.id)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Opens the task")
    }

    private var allClearCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            IconWell(symbol: "checkmark", size: 56)
            Text("Nothing is waiting on you")
                .font(.title2.bold())
                .foregroundStyle(Theme.ink)
            Text("When something pops into your head, dump it here and ThinkSort will sort it out.")
                .font(.subheadline)
                .foregroundStyle(Theme.inkSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    private func resumeBanner(task: TaskItem) -> some View {
        Button {
            router.show(fullScreen: .leftOff)
        } label: {
            HStack(spacing: 14) {
                IconWell(symbol: "arrow.uturn.left", size: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Eyebrow(text: "You left off", color: Theme.accent)
                    Text(task.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.ink)
                        .lineLimit(1)
                }
                Spacer(minLength: 6)
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Theme.accent)
            }
            .padding(14)
            .frame(maxWidth: .infinity)
            .background(Theme.accentSoft, in: .rect(cornerRadius: 18))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Tiles & timeline

    private var countTiles: some View {
        HStack(spacing: 12) {
            Button {
                router.selectedTab = .today
            } label: {
                CountTile(title: "Today", count: todayTasks.count, symbol: "calendar.badge.clock")
            }
            .buttonStyle(.plain)

            Button {
                router.selectedTab = .upcoming
            } label: {
                CountTile(title: "Upcoming", count: upcomingCount, symbol: "list.bullet")
            }
            .buttonStyle(.plain)
        }
    }

    private var timelineTasks: [TaskItem] {
        Array(todayTasks.prefix(5))
    }

    private var timelineSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Today")
                .font(.headline)
                .foregroundStyle(Theme.inkSecondary)
                .padding(.leading, 4)

            VStack(spacing: 0) {
                ForEach(Array(timelineTasks.enumerated()), id: \.element.id) { index, task in
                    Button {
                        router.openTask(task.id)
                    } label: {
                        TimelineRow(
                            timeLabel: task.isOverdue(now: now) ? "Overdue" : task.timeLabel(now: now),
                            title: task.title,
                            isOverdue: task.isOverdue(now: now)
                        )
                        .padding(.horizontal, 18)
                    }
                    .buttonStyle(.plain)

                    if index < timelineTasks.count - 1 {
                        RowDivider(leading: 18)
                    }
                }
            }
            .card()
        }
    }

    // MARK: - Actions

    private func handle(_ action: QuickActionRow.Action, task: TaskItem) {
        switch action {
        case .start, .resume:
            store.start(taskID: task.id)
            router.show(fullScreen: .oneThing(task.id))
        case .done:
            withAnimation(reduceMotion ? nil : .snappy) {
                store.complete(taskID: task.id)
            }
            router.flash("Done — nice.")
        case .later, .notNow:
            store.snooze(taskID: task.id, minutes: 60)
            router.flash("Moved an hour out.")
        case .stuck:
            router.show(sheet: .stuck(task.id))
        }
    }
}

#Preview {
    NavigationStack { TodayView() }
        .environment(BrainStore())
        .environment(AppRouter())
}
