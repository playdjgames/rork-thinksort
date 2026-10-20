import SwiftUI

/// "I only have 10 minutes" — surfaces tasks that actually fit.
struct TimeAvailableView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss

    @State private var minutes: Int = 15

    private let options = [5, 10, 15, 30, 60]

    private var matches: [TaskItem] {
        Array(store.tasksFitting(minutes: minutes).prefix(6))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("How much time do you have?")
                        .font(.title2.bold())
                        .foregroundStyle(Theme.ink)

                    HStack(spacing: 8) {
                        ForEach(options, id: \.self) { option in
                            Button {
                                minutes = option
                            } label: {
                                Text(option >= 60 ? "1h" : "\(option)m")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(minutes == option ? .white : Theme.ink)
                                    .frame(maxWidth: .infinity, minHeight: 46)
                                    .background(minutes == option ? Theme.accent : Theme.surface, in: .capsule)
                                    .overlay { Capsule().strokeBorder(Theme.hairline, lineWidth: minutes == option ? 0 : 1) }
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    if let best = matches.first {
                        VStack(alignment: .leading, spacing: 10) {
                            Eyebrow(text: "\(minutes)-minute task", color: Theme.accent)
                            Text(best.title)
                                .font(.title3.bold())
                                .foregroundStyle(Theme.ink)
                                .fixedSize(horizontal: false, vertical: true)
                            Text(best.whenDescription())
                                .font(.subheadline)
                                .foregroundStyle(Theme.inkSecondary)
                        }
                        .padding(20)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.accentSoft, in: .rect(cornerRadius: Theme.cardRadius))

                        Button {
                            store.start(taskID: best.id)
                            dismiss()
                            router.show(fullScreen: .oneThing(best.id))
                        } label: {
                            Label("Start This One", systemImage: "play.fill")
                        }
                        .buttonStyle(PrimaryButtonStyle())

                        if matches.count > 1 {
                            TaskGroupCard(title: "Other things that fit", tasks: Array(matches.dropFirst())) { task in
                                store.complete(taskID: task.id)
                            } onSelect: { task in
                                dismiss()
                                router.openTask(task.id)
                            }
                        }
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Nothing that short right now")
                                .font(.title3.bold())
                                .foregroundStyle(Theme.ink)
                            Text("Try a longer window, or open a big task and break it into steps.")
                                .font(.subheadline)
                                .foregroundStyle(Theme.inkSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(20)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .card()
                    }
                }
                .padding(.horizontal, Theme.gutter)
                .padding(.top, 4)
                .padding(.bottom, 24)
            }
            .canvas()
            .scrollIndicators(.hidden)
            .navigationTitle("Time available")
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
}

/// "I'm running late" — triage what happens next without touching appointments.
struct RunningLateView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss

    private var todayTasks: [TaskItem] { store.todayTasks() }
    private var next: TaskItem? { todayTasks.first }
    private var rest: [TaskItem] { Array(todayTasks.dropFirst()) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if let next {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(headline(for: next))
                                .font(.title2.bold())
                                .foregroundStyle(Theme.ink)
                                .fixedSize(horizontal: false, vertical: true)
                            Text("Nothing gets cancelled unless you say so.")
                                .font(.subheadline)
                                .foregroundStyle(Theme.inkSecondary)
                        }

                        VStack(alignment: .leading, spacing: 10) {
                            Eyebrow(text: "Next", color: Theme.accent)
                            Text(next.title)
                                .font(.title3.bold())
                                .foregroundStyle(Theme.ink)
                            if !next.openPreparationItems.isEmpty {
                                Text("Grab: \(next.openPreparationItems.map(\.title).joined(separator: ", "))")
                                    .font(.subheadline)
                                    .foregroundStyle(Theme.inkSecondary)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .padding(20)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.accentSoft, in: .rect(cornerRadius: Theme.cardRadius))

                        if !rest.isEmpty {
                            SectionCard(title: "Push to later") {
                                ForEach(Array(rest.enumerated()), id: \.element.id) { index, task in
                                    HStack(spacing: 12) {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(task.title)
                                                .font(.body)
                                                .foregroundStyle(Theme.ink)
                                            Text(task.whenDescription())
                                                .font(.caption)
                                                .foregroundStyle(Theme.inkTertiary)
                                        }
                                        Spacer(minLength: 6)
                                        Button("Tomorrow") {
                                            store.postpone(taskID: task.id)
                                        }
                                        .font(.footnote.weight(.semibold))
                                        .foregroundStyle(Theme.accent)
                                    }
                                    .padding(.horizontal, 18)
                                    .padding(.vertical, 12)

                                    if index < rest.count - 1 { RowDivider() }
                                }
                            }

                            Button {
                                for task in rest where task.category != .appointment {
                                    store.postpone(taskID: task.id)
                                }
                                dismiss()
                                router.flash("Cleared the rest of today.")
                            } label: {
                                Label("Push everything else to tomorrow", systemImage: "arrow.uturn.forward")
                            }
                            .buttonStyle(SoftButtonStyle(tint: Theme.accent))
                        }

                        Button {
                            store.start(taskID: next.id)
                            dismiss()
                            router.show(fullScreen: .oneThing(next.id))
                        } label: {
                            Label("Go Do This", systemImage: "figure.walk")
                        }
                        .buttonStyle(PrimaryButtonStyle())
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Nothing is waiting on you")
                                .font(.title2.bold())
                                .foregroundStyle(Theme.ink)
                            Text("Your day is clear — take the pressure off.")
                                .font(.subheadline)
                                .foregroundStyle(Theme.inkSecondary)
                        }
                        .padding(20)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .card()
                    }
                }
                .padding(.horizontal, Theme.gutter)
                .padding(.top, 4)
                .padding(.bottom, 24)
            }
            .canvas()
            .scrollIndicators(.hidden)
            .navigationTitle("Running late")
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

    private func headline(for task: TaskItem) -> String {
        if task.hasTime, let due = task.dueDate {
            return "You're running late for \(due.formatted(date: .omitted, time: .shortened)) — \(task.title.lowercasedFirst)."
        }
        return "Let's get you moving."
    }
}

/// "What am I forgetting?"
struct ForgettingView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss

    private var items: [TaskItem] { store.forgottenItems() }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if items.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Nothing slipping")
                                .font(.title2.bold())
                                .foregroundStyle(Theme.ink)
                            Text("Nothing is overdue, stalled, or blocking something else.")
                                .font(.subheadline)
                                .foregroundStyle(Theme.inkSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(20)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .card()
                    } else {
                        Text("These are the ones quietly slipping:")
                            .font(.title3)
                            .foregroundStyle(Theme.inkSecondary)

                        ForEach(items) { task in
                            Button {
                                dismiss()
                                router.openTask(task.id)
                            } label: {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text(task.title)
                                        .font(.headline)
                                        .foregroundStyle(Theme.ink)
                                        .multilineTextAlignment(.leading)
                                    Text(flagReason(task))
                                        .font(.subheadline)
                                        .foregroundStyle(task.isOverdue() ? Theme.coral : Theme.inkSecondary)
                                        .multilineTextAlignment(.leading)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(18)
                                .card()
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, Theme.gutter)
                .padding(.top, 4)
                .padding(.bottom, 24)
            }
            .canvas()
            .scrollIndicators(.hidden)
            .navigationTitle("What am I forgetting?")
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

    private func flagReason(_ task: TaskItem) -> String {
        if task.isOverdue() { return "Overdue — was due \(task.timeLabel())" }
        if task.postponeCount >= 2 { return "You've moved this \(task.postponeCount) times" }
        let dependents = store.dependents(of: task).filter(\.isOpen)
        if let first = dependents.first { return "\(first.title) is waiting on this" }
        if !task.openPreparationItems.isEmpty {
            return "Still need: \(task.openPreparationItems.map(\.title).joined(separator: ", "))"
        }
        return task.whenDescription()
    }
}

/// "What do I need before I go?"
struct BeforeYouGoView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss

    private var entries: [(task: TaskItem, items: [PreparationItem])] {
        store.preparationItemsBeforeLeaving()
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if entries.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Nothing to grab")
                                .font(.title2.bold())
                                .foregroundStyle(Theme.ink)
                            Text("None of today's or tomorrow's tasks have things you need to bring. You can add them from any task.")
                                .font(.subheadline)
                                .foregroundStyle(Theme.inkSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(20)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .card()
                    } else {
                        Text("Before you leave, grab:")
                            .font(.title2.bold())
                            .foregroundStyle(Theme.ink)

                        ForEach(entries, id: \.task.id) { entry in
                            SectionCard(title: "\(entry.task.title) · \(entry.task.timeLabel())") {
                                ForEach(Array(entry.items.enumerated()), id: \.element.id) { index, item in
                                    Button {
                                        store.togglePreparationItem(taskID: entry.task.id, itemID: item.id)
                                    } label: {
                                        HStack(spacing: 14) {
                                            CheckCircle(isDone: item.isDone)
                                            Text(item.title)
                                                .font(.body)
                                                .foregroundStyle(Theme.ink)
                                                .frame(maxWidth: .infinity, alignment: .leading)
                                        }
                                        .padding(.horizontal, 18)
                                        .padding(.vertical, 14)
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.plain)

                                    if index < entry.items.count - 1 { RowDivider() }
                                }
                            }
                        }

                        Button {
                            for entry in entries {
                                for item in entry.items {
                                    store.togglePreparationItem(taskID: entry.task.id, itemID: item.id)
                                }
                            }
                            dismiss()
                            router.flash("All packed.")
                        } label: {
                            Label("I've Got Everything", systemImage: "checkmark")
                        }
                        .buttonStyle(PrimaryButtonStyle())
                    }
                }
                .padding(.horizontal, Theme.gutter)
                .padding(.top, 4)
                .padding(.bottom, 24)
            }
            .canvas()
            .scrollIndicators(.hidden)
            .navigationTitle("Before you go")
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
}
