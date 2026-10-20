import SwiftUI

struct MorningBriefingView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss

    private var todayTasks: [TaskItem] { store.todayTasks() }
    private var events: [CalendarEventSummary] { CalendarService.shared.events(on: Date()) }

    private var prepItems: [String] {
        let fromTasks = store.preparationItemsBeforeLeaving().flatMap { $0.items.map(\.title) }
        return Array(Set(fromTasks)).sorted()
    }

    private var firstThing: TaskItem? { store.nextAction()?.task }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Text("Good morning")
                        .font(.largeTitle.bold())
                        .foregroundStyle(Theme.ink)
                    Text(Date().formatted(.dateTime.weekday(.wide).month(.wide).day()))
                        .font(.title3)
                        .foregroundStyle(Theme.inkSecondary)

                    if !events.isEmpty {
                        calendarCard
                    }

                    if todayTasks.isEmpty && events.isEmpty {
                        emptyCard
                    } else if !todayTasks.isEmpty {
                        todayCard
                    }

                    if !prepItems.isEmpty {
                        prepCard
                    }

                    if let firstThing {
                        firstThingCard(firstThing)
                    }

                    Button {
                        startDay()
                    } label: {
                        Label("Start My Day", systemImage: "sun.max.fill")
                    }
                    .buttonStyle(PrimaryButtonStyle())
                }
                .padding(.horizontal, Theme.gutter)
                .padding(.bottom, 24)
            }
            .canvas()
            .scrollIndicators(.hidden)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .presentationDetents([.large])
        .presentationContentInteraction(.scrolls)
    }

    private var calendarCard: some View {
        SectionCard(title: "On your calendar") {
            ForEach(Array(events.enumerated()), id: \.element.id) { index, event in
                TimelineRow(
                    timeLabel: event.isAllDay ? "All day" : event.startDate.formatted(date: .omitted, time: .shortened),
                    title: event.title,
                    trailingSymbol: nil
                )
                .padding(.horizontal, 18)

                if index < events.count - 1 { RowDivider(leading: 18) }
            }
        }
    }

    private var todayCard: some View {
        SectionCard(title: "Today") {
            ForEach(Array(todayTasks.prefix(6).enumerated()), id: \.element.id) { index, task in
                TimelineRow(
                    timeLabel: task.isOverdue() ? "Overdue" : task.timeLabel(),
                    title: task.title,
                    isOverdue: task.isOverdue(),
                    trailingSymbol: nil
                )
                .padding(.horizontal, 18)

                if index < min(6, todayTasks.count) - 1 { RowDivider(leading: 18) }
            }
        }
    }

    private var prepCard: some View {
        SectionCard(title: "Before you leave") {
            ForEach(Array(prepItems.enumerated()), id: \.offset) { index, item in
                HStack(spacing: 12) {
                    Image(systemName: "bag")
                        .font(.footnote)
                        .foregroundStyle(Theme.accent)
                    Text(item)
                        .font(.body)
                        .foregroundStyle(Theme.ink)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 12)

                if index < prepItems.count - 1 { RowDivider(leading: 18) }
            }
        }
    }

    private func firstThingCard(_ task: TaskItem) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow(text: "First thing", color: Theme.accent)
            Text(task.title)
                .font(.title3.bold())
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.accentSoft, in: .rect(cornerRadius: Theme.cardRadius))
    }

    private var emptyCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Clear morning")
                .font(.title3.bold())
                .foregroundStyle(Theme.ink)
            Text("Nothing is scheduled. If something's on your mind, dump it and ThinkSort will sort it out.")
                .font(.subheadline)
                .foregroundStyle(Theme.inkSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    private func startDay() {
        var settings = store.settings
        settings.lastMorningBriefing = Date()
        store.settings = settings
        dismiss()
        if let firstThing {
            store.start(taskID: firstThing.id)
            router.show(fullScreen: .oneThing(firstThing.id))
        } else {
            router.selectedTab = .today
        }
    }
}

#Preview {
    MorningBriefingView()
        .environment(BrainStore())
        .environment(AppRouter())
}
