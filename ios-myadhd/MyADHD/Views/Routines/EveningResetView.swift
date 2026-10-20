import SwiftUI

struct EveningResetView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss

    @State private var moveToTomorrow: Set<UUID> = []
    @State private var prepareTonight: Set<UUID> = []

    private var completed: [TaskItem] { store.completedToday() }
    private var unfinished: [TaskItem] { store.unfinishedToday() }

    private var tomorrowPrep: [(task: TaskItem, items: [PreparationItem])] {
        store.preparationItemsBeforeLeaving().filter { entry in
            guard let due = entry.task.dueDate else { return false }
            return Calendar.current.isDateInTomorrow(due)
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Text("Evening reset")
                        .font(.largeTitle.bold())
                        .foregroundStyle(Theme.ink)
                    Text("Two minutes to clear tomorrow's runway.")
                        .font(.title3)
                        .foregroundStyle(Theme.inkSecondary)

                    doneCard

                    if !unfinished.isEmpty {
                        moveCard
                    }

                    if !tomorrowPrep.isEmpty {
                        prepareCard
                    }

                    summaryCard

                    Button {
                        confirm()
                    } label: {
                        Label("Confirm and Reset", systemImage: "checkmark")
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
            .onAppear {
                moveToTomorrow = Set(unfinished.map(\.id))
                prepareTonight = Set(tomorrowPrep.map(\.task.id))
            }
        }
        .presentationDetents([.large])
        .presentationContentInteraction(.scrolls)
    }

    private var doneCard: some View {
        SectionCard(title: "What you got done") {
            if completed.isEmpty {
                Text("Nothing checked off today — that happens. Tomorrow's a clean start.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.inkSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                ForEach(Array(completed.enumerated()), id: \.element.id) { index, task in
                    HStack(spacing: 14) {
                        CheckCircle(isDone: true)
                        Text(task.title)
                            .font(.body)
                            .foregroundStyle(Theme.inkSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)

                    if index < completed.count - 1 { RowDivider() }
                }
            }
        }
    }

    private var moveCard: some View {
        SectionCard(title: "What didn't get done") {
            ForEach(Array(unfinished.enumerated()), id: \.element.id) { index, task in
                Button {
                    toggle(task.id, in: $moveToTomorrow)
                } label: {
                    HStack(spacing: 14) {
                        CheckCircle(isDone: moveToTomorrow.contains(task.id))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(task.title)
                                .font(.body)
                                .foregroundStyle(Theme.ink)
                            Text(moveToTomorrow.contains(task.id) ? "Moving to tomorrow" : "Leaving it where it is")
                                .font(.caption)
                                .foregroundStyle(Theme.inkTertiary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if index < unfinished.count - 1 { RowDivider() }
            }
        }
    }

    private var prepareCard: some View {
        SectionCard(title: "Prepare tonight") {
            ForEach(Array(tomorrowPrep.enumerated()), id: \.element.task.id) { index, entry in
                Button {
                    toggle(entry.task.id, in: $prepareTonight)
                } label: {
                    HStack(spacing: 14) {
                        CheckCircle(isDone: prepareTonight.contains(entry.task.id))
                        VStack(alignment: .leading, spacing: 3) {
                            Text(entry.task.title)
                                .font(.body)
                                .foregroundStyle(Theme.ink)
                            Text("Grab: \(entry.items.map(\.title).joined(separator: ", "))")
                                .font(.footnote)
                                .foregroundStyle(Theme.inkSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if index < tomorrowPrep.count - 1 { RowDivider() }
            }
        }
    }

    private var summaryCard: some View {
        HStack(spacing: 0) {
            summaryColumn(count: completed.count, label: "Done")
            Divider().frame(height: 42)
            summaryColumn(count: moveToTomorrow.count, label: "To tomorrow")
            Divider().frame(height: 42)
            summaryColumn(count: prepareTonight.count, label: "Prep tonight")
        }
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity)
        .card()
    }

    private func summaryColumn(count: Int, label: String) -> some View {
        VStack(spacing: 4) {
            Text("\(count)")
                .font(.title.bold())
                .monospacedDigit()
                .foregroundStyle(Theme.accent)
            Text(label)
                .font(.caption)
                .foregroundStyle(Theme.inkSecondary)
        }
        .frame(maxWidth: .infinity)
    }

    private func toggle(_ id: UUID, in set: Binding<Set<UUID>>) {
        if set.wrappedValue.contains(id) {
            set.wrappedValue.remove(id)
        } else {
            set.wrappedValue.insert(id)
        }
    }

    private func confirm() {
        store.moveAllToTomorrow(Array(moveToTomorrow))

        // Turn tonight's prep into a real task so it isn't just a good intention.
        let tonight = Calendar.current.date(bySettingHour: 20, minute: 0, second: 0, of: Date()) ?? Date()
        for entry in tomorrowPrep where prepareTonight.contains(entry.task.id) {
            let title = "Get ready for \(entry.task.title.lowercasedFirst)"
            guard !store.openTasks.contains(where: { $0.title == title }) else { continue }
            var prep = TaskItem(title: title)
            prep.dueDate = tonight
            prep.hasTime = true
            prep.priority = .soon
            prep.estimatedMinutes = 10
            prep.subtasks = entry.items.map { Subtask(title: $0.title) }
            store.add(prep)
            store.addDependency(prep.id, to: entry.task.id)
        }

        var settings = store.settings
        settings.lastEveningReset = Date()
        store.settings = settings

        dismiss()
        router.flash("Tomorrow's set up.")
    }
}

#Preview {
    EveningResetView()
        .environment(BrainStore())
        .environment(AppRouter())
}
