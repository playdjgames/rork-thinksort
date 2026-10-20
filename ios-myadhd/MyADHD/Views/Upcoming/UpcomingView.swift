import SwiftUI

struct UpcomingView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router

    private var grouped: [(label: String, tasks: [TaskItem])] {
        let calendar = Calendar.current
        let tasks = store.upcomingTasks()
        let buckets = Dictionary(grouping: tasks) { task -> Date in
            calendar.startOfDay(for: task.dueDate ?? Date())
        }
        return buckets
            .sorted { $0.key < $1.key }
            .map { (label: Self.label(for: $0.key), tasks: $0.value.sorted(by: BrainStore.chronologically)) }
    }

    private static func label(for day: Date) -> String {
        let calendar = Calendar.current
        if calendar.isDateInTomorrow(day) { return "Tomorrow" }
        if let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: Date()), to: day).day, days < 7 {
            return day.formatted(.dateTime.weekday(.wide))
        }
        return day.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day())
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                if grouped.isEmpty && store.unscheduledTasks.isEmpty {
                    emptyState
                } else {
                    ForEach(grouped, id: \.label) { group in
                        TaskGroupCard(title: group.label, tasks: group.tasks) { task in
                            store.complete(taskID: task.id)
                        } onSelect: { task in
                            router.openTask(task.id)
                        }
                    }

                    if !store.unscheduledTasks.isEmpty {
                        TaskGroupCard(title: "No date yet", tasks: store.unscheduledTasks, showCategory: true) { task in
                            store.complete(taskID: task.id)
                        } onSelect: { task in
                            router.openTask(task.id)
                        }
                    }
                }
            }
            .padding(.horizontal, Theme.gutter)
            .padding(.top, 8)
            .padding(.bottom, 28)
        }
        .canvas()
        .scrollIndicators(.hidden)
        .navigationTitle("Upcoming")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    router.show(sheet: .quickAdd)
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("Add something")
            }
        }
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 10) {
            IconWell(symbol: "calendar", size: 56)
            Text("Nothing scheduled ahead")
                .font(.title3.bold())
                .foregroundStyle(Theme.ink)
            Text("Anything you say with a day or time attached will land here.")
                .font(.subheadline)
                .foregroundStyle(Theme.inkSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
        .padding(.top, 20)
    }
}

#Preview {
    NavigationStack { UpcomingView() }
        .environment(BrainStore())
        .environment(AppRouter())
}
