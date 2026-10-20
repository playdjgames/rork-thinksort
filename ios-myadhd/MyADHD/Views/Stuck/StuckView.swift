import SwiftUI

/// "I'm stuck" flow: name the friction, then get one tiny first step.
struct StuckView: View {
    let taskID: UUID

    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss

    @State private var reason: StuckReason?

    private var task: TaskItem? { store.task(id: taskID) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if let reason {
                        resolution(reason)
                    } else {
                        picker
                    }
                }
                .padding(.horizontal, Theme.gutter)
                .padding(.top, 4)
                .padding(.bottom, 24)
            }
            .canvas()
            .scrollIndicators(.hidden)
            .navigationTitle(reason == nil ? "What's making this hard?" : "Let's make it smaller")
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

    private var picker: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let task {
                Text(task.title)
                    .font(.title2.bold())
                    .foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
            }

            VStack(spacing: 0) {
                ForEach(Array(StuckReason.allCases.enumerated()), id: \.element.id) { index, option in
                    Button {
                        withAnimation(.snappy(duration: 0.25)) { reason = option }
                    } label: {
                        HStack(spacing: 14) {
                            IconWell(symbol: option.symbol, size: 38)
                            Text(option.label)
                                .font(.body)
                                .foregroundStyle(Theme.ink)
                                .multilineTextAlignment(.leading)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Image(systemName: "chevron.right")
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(Theme.inkTertiary)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)

                    if index < StuckReason.allCases.count - 1 { RowDivider(leading: 16) }
                }
            }
            .card()
        }
    }

    private func resolution(_ reason: StuckReason) -> some View {
        let step = TaskBreakdown.tinyFirstStep(for: task?.title ?? "this", reason: reason)

        return VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 12) {
                Eyebrow(text: "Start with just this", color: Theme.accent)
                Text(step)
                    .font(.title2.bold())
                    .foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Text("That's the whole ask. You can stop after it.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.inkSecondary)
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.accentSoft, in: .rect(cornerRadius: Theme.cardRadius))

            if reason == .tooBig || reason == .dontKnowWhereToStart, let task, task.subtasks.isEmpty {
                Button {
                    store.breakDown(taskID: task.id)
                    router.flash("Broke it into steps.")
                } label: {
                    Label("Break the whole thing into steps", systemImage: "list.bullet.indent")
                }
                .buttonStyle(SoftButtonStyle(tint: Theme.accent))
            }

            if reason == .notEnoughTime {
                Button {
                    dismiss()
                    router.show(sheet: .timeAvailable)
                } label: {
                    Label("Show me what fits in my time", systemImage: "timer")
                }
                .buttonStyle(SoftButtonStyle(tint: Theme.accent))
            }

            if reason == .needSomething, let task {
                Button {
                    dismiss()
                    router.openTask(task.id)
                } label: {
                    Label("Add what I need to the task", systemImage: "plus.circle")
                }
                .buttonStyle(SoftButtonStyle(tint: Theme.accent))
            }

            VStack(spacing: 10) {
                Button {
                    startStep(step)
                } label: {
                    Label("Start", systemImage: "play.fill")
                }
                .buttonStyle(PrimaryButtonStyle())

                HStack(spacing: 10) {
                    Button {
                        markStepDone(step)
                    } label: {
                        Label("Already Done", systemImage: "checkmark")
                    }
                    .buttonStyle(SoftButtonStyle(isCompact: true))

                    Button {
                        withAnimation(.snappy(duration: 0.2)) { self.reason = nil }
                    } label: {
                        Label("Skip", systemImage: "arrow.right")
                    }
                    .buttonStyle(SoftButtonStyle(isCompact: true))
                }
            }
        }
    }

    private func startStep(_ step: String) {
        guard let task else {
            dismiss()
            return
        }
        if task.subtasks.isEmpty {
            store.addSubtask(step, taskID: task.id)
        } else if !task.subtasks.contains(where: { $0.title == step }) && task.nextSubtask?.title != step {
            store.addSubtask(step, taskID: task.id)
        }
        store.start(taskID: task.id)
        dismiss()
        router.show(fullScreen: .oneThing(task.id))
    }

    private func markStepDone(_ step: String) {
        guard let task else {
            dismiss()
            return
        }
        if let existing = task.subtasks.first(where: { $0.title == step && !$0.isDone }) {
            store.toggleSubtask(taskID: task.id, subtaskID: existing.id)
        }
        dismiss()
        router.flash("Good — that's momentum.")
    }
}

#Preview {
    StuckView(taskID: BrainStore().tasks.first?.id ?? UUID())
        .environment(BrainStore())
        .environment(AppRouter())
}
