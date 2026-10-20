import SwiftUI

/// Signature "Where did I leave off?" screen.
struct LeftOffView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss

    private var task: TaskItem? {
        if let checkpoint = store.checkpoint, let task = store.task(id: checkpoint.taskID), task.isOpen {
            return task
        }
        return store.tasks
            .filter { $0.isOpen && $0.lastStartedAt != nil }
            .max { ($0.lastStartedAt ?? .distantPast) < ($1.lastStartedAt ?? .distantPast) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            closeButton

            if let task {
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        Text(recapSentence(task))
                            .font(.largeTitle.bold())
                            .foregroundStyle(Theme.ink)
                            .fixedSize(horizontal: false, vertical: true)

                        let done = task.subtasks.filter(\.isDone)
                        if !done.isEmpty {
                            completedCard(done, task: task)
                        }

                        if let next = task.nextSubtask {
                            nextStepCard(next, task: task)
                        } else {
                            wrapUpCard(task)
                        }

                        let remaining = task.subtasks.filter { !$0.isDone }.dropFirst()
                        if !remaining.isEmpty {
                            remainingCard(Array(remaining), task: task)
                        }
                    }
                    .padding(.horizontal, Theme.gutter)
                    .padding(.bottom, 20)
                }
                .scrollIndicators(.hidden)

                QuickActionRow(actions: [.resume, .notNow, .stuck]) { handle($0, task: task) }
                    .padding(.horizontal, Theme.gutter)
                    .padding(.bottom, 12)
            } else {
                Spacer()
                VStack(alignment: .leading, spacing: 12) {
                    IconWell(symbol: "pause.circle", size: 58)
                    Text("Nothing in progress")
                        .font(.title.bold())
                        .foregroundStyle(Theme.ink)
                    Text("Start something and ThinkSort will remember exactly where you stopped.")
                        .font(.body)
                        .foregroundStyle(Theme.inkSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, Theme.gutter)
                Spacer()
            }
        }
        .canvas()
    }

    private var closeButton: some View {
        HStack {
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.ink)
                    .frame(width: 40, height: 40)
                    .background(Theme.surfaceMuted, in: .circle)
            }
            .accessibilityLabel("Close")
            Spacer()
        }
        .padding(.horizontal, Theme.gutter)
        .padding(.bottom, 12)
    }

    private func recapSentence(_ task: TaskItem) -> String {
        "You were \(task.title.lowercasedFirst)."
    }

    private func completedCard(_ done: [Subtask], task: TaskItem) -> some View {
        VStack(spacing: 0) {
            ForEach(Array(done.enumerated()), id: \.element.id) { index, subtask in
                HStack(spacing: 14) {
                    CheckCircle(isDone: true)
                    Text(subtask.title)
                        .font(.body)
                        .foregroundStyle(Theme.inkTertiary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 14)

                if index < done.count - 1 { RowDivider() }
            }
        }
        .card()
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Already finished: \(done.map(\.title).joined(separator: ", "))")
    }

    private func nextStepCard(_ subtask: Subtask, task: TaskItem) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Eyebrow(text: "Your next step", color: Theme.accent)
            HStack(spacing: 16) {
                IconWell(symbol: "shippingbox", size: 62, background: Theme.surface)
                Text(subtask.title)
                    .font(.title3.bold())
                    .foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.accentSoft, in: .rect(cornerRadius: Theme.cardRadius))
    }

    private func wrapUpCard(_ task: TaskItem) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow(text: "Your next step", color: Theme.accent)
            Text("Finish it off and mark it done.")
                .font(.title3.bold())
                .foregroundStyle(Theme.ink)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.accentSoft, in: .rect(cornerRadius: Theme.cardRadius))
    }

    private func remainingCard(_ remaining: [Subtask], task: TaskItem) -> some View {
        VStack(spacing: 0) {
            ForEach(Array(remaining.enumerated()), id: \.element.id) { index, subtask in
                HStack(spacing: 14) {
                    CheckCircle(isDone: false)
                    Text(subtask.title)
                        .font(.body)
                        .foregroundStyle(Theme.inkSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 14)

                if index < remaining.count - 1 { RowDivider() }
            }
        }
        .card()
    }

    private func handle(_ action: QuickActionRow.Action, task: TaskItem) {
        switch action {
        case .resume, .start:
            store.start(taskID: task.id)
            dismiss()
            router.show(fullScreen: .oneThing(task.id))
        case .notNow, .later:
            store.saveCheckpoint(taskID: task.id, note: "Picking it up later")
            dismiss()
        case .stuck:
            dismiss()
            router.show(sheet: .stuck(task.id))
        case .done:
            store.complete(taskID: task.id)
            dismiss()
        }
    }
}

#Preview {
    LeftOffView()
        .environment(BrainStore())
        .environment(AppRouter())
}
