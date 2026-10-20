import SwiftUI

/// Distraction-free mode: only the one thing happening right now.
struct OneThingView: View {
    let taskID: UUID

    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var celebrate = false

    private var task: TaskItem? { store.task(id: taskID) }

    private var currentStep: String? {
        task?.nextSubtask?.title
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button {
                    if let task { store.saveCheckpoint(taskID: task.id, note: "Stopped here") }
                    dismiss()
                } label: {
                    Label("I'm stopping here", systemImage: "xmark")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(Theme.inkSecondary)
                }
                Spacer()
                if let task, !task.subtasks.isEmpty {
                    Text("\(task.completedSubtaskCount)/\(task.subtasks.count)")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.accent)
                        .monospacedDigit()
                }
            }
            .padding(.horizontal, Theme.gutter)
            .padding(.top, 8)

            Spacer()

            if let task {
                VStack(alignment: .leading, spacing: 18) {
                    Eyebrow(text: "Right now", color: Theme.accent)
                    Text(currentStep ?? task.title)
                        .font(.system(.largeTitle, weight: .bold))
                        .foregroundStyle(Theme.ink)
                        .fixedSize(horizontal: false, vertical: true)
                        .contentTransition(.opacity)

                    if currentStep != nil {
                        Text("Part of \(task.title.lowercasedFirst)")
                            .font(.subheadline)
                            .foregroundStyle(Theme.inkSecondary)
                    }

                    if !task.openPreparationItems.isEmpty {
                        Text("Bring: \(task.openPreparationItems.map(\.title).joined(separator: ", "))")
                            .font(.subheadline)
                            .foregroundStyle(Theme.inkSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Theme.gutter)
                .scaleEffect(celebrate && !reduceMotion ? 1.03 : 1)
                .animation(.snappy(duration: 0.25), value: celebrate)
            } else {
                Text("This task is gone.")
                    .font(.title2)
                    .foregroundStyle(Theme.inkSecondary)
            }

            Spacer()

            if let task {
                VStack(spacing: 12) {
                    Button {
                        advance(task)
                    } label: {
                        Label(currentStep == nil ? "Mark It Done" : "Step Done", systemImage: "checkmark")
                    }
                    .buttonStyle(PrimaryButtonStyle())

                    HStack(spacing: 10) {
                        Button {
                            store.snooze(taskID: task.id, minutes: 30)
                            store.saveCheckpoint(taskID: task.id, note: "Paused")
                            dismiss()
                        } label: {
                            Label("Later", systemImage: "clock")
                        }
                        .buttonStyle(SoftButtonStyle(isCompact: true))

                        Button {
                            dismiss()
                            router.show(sheet: .stuck(task.id))
                        } label: {
                            Label("Stuck", systemImage: "exclamationmark.circle")
                        }
                        .buttonStyle(SoftButtonStyle(tint: Theme.coral, isCompact: true))
                    }

                    Button {
                        dismiss()
                        router.selectedTab = .focus
                    } label: {
                        Label("Start a focus timer", systemImage: "timer")
                            .font(.subheadline)
                            .foregroundStyle(Theme.accent)
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 2)
                }
                .padding(.horizontal, Theme.gutter)
                .padding(.bottom, 16)
            }
        }
        .canvas()
        .sensoryFeedback(.success, trigger: celebrate)
    }

    private func advance(_ task: TaskItem) {
        celebrate.toggle()
        if let next = task.nextSubtask {
            store.toggleSubtask(taskID: task.id, subtaskID: next.id)
            if store.task(id: task.id)?.isOpen != true {
                dismiss()
                router.flash("Finished — that's the whole thing.")
            }
        } else {
            store.complete(taskID: task.id)
            dismiss()
            router.flash("Done. Nice work.")
        }
    }
}

#Preview {
    OneThingView(taskID: BrainStore().tasks.first?.id ?? UUID())
        .environment(BrainStore())
        .environment(AppRouter())
}
