import SwiftUI

/// Standard task row: check circle, title, supporting line, chevron.
struct TaskRow: View {
    let task: TaskItem
    var showCategory: Bool = false
    var showWhen: Bool = true
    var onToggle: (() -> Void)?

    var body: some View {
        HStack(spacing: 14) {
            if let onToggle {
                Button(action: onToggle) {
                    CheckCircle(isDone: task.status == .completed)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(task.status == .completed ? "Mark \(task.title) not done" : "Mark \(task.title) done")
            }

            VStack(alignment: .leading, spacing: 5) {
                Text(task.title)
                    .font(.body)
                    .foregroundStyle(Theme.ink)
                    .strikethrough(task.status == .completed, color: Theme.inkTertiary)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)

                HStack(spacing: 8) {
                    if showCategory {
                        CategoryTag(category: task.category)
                    }
                    if showWhen {
                        supportingLine
                    }
                }
            }

            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Theme.inkTertiary)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 14)
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private var supportingLine: some View {
        let overdue = task.isOverdue()
        let parts = supportingParts
        if !parts.isEmpty {
            HStack(spacing: 5) {
                if overdue {
                    Image(systemName: "exclamationmark.circle.fill")
                        .font(.caption2)
                        .foregroundStyle(Theme.coral)
                }
                Text(parts.joined(separator: " · "))
                    .font(.footnote)
                    .foregroundStyle(overdue ? Theme.coral : Theme.inkSecondary)
            }
        }
    }

    private var supportingParts: [String] {
        var parts: [String] = []
        if task.isOverdue() {
            parts.append("Overdue")
        } else if task.dueDate != nil {
            parts.append(task.timeLabel())
        }
        if task.status == .inProgress { parts.append("In progress") }
        if !task.subtasks.isEmpty {
            parts.append("\(task.completedSubtaskCount)/\(task.subtasks.count) steps")
        }
        if let trigger = task.locationTrigger {
            parts.append("\(trigger.kind == .arriving ? "At" : "Leaving") \(trigger.placeName)")
        } else if let place = task.locationName, task.dueDate == nil {
            parts.append(place)
        }
        if let minutes = task.estimatedMinutes, parts.count < 2 {
            parts.append("\(minutes) min")
        }
        return parts
    }
}

/// Grouped card of task rows with hairline dividers.
struct TaskGroupCard: View {
    let title: String?
    let tasks: [TaskItem]
    var showCategory: Bool = false
    var onToggle: ((TaskItem) -> Void)?
    var onSelect: (TaskItem) -> Void

    var body: some View {
        SectionCard(title: title) {
            ForEach(Array(tasks.enumerated()), id: \.element.id) { index, task in
                Button {
                    onSelect(task)
                } label: {
                    TaskRow(
                        task: task,
                        showCategory: showCategory,
                        onToggle: onToggle.map { handler in { handler(task) } }
                    )
                }
                .buttonStyle(.plain)

                if index < tasks.count - 1 {
                    RowDivider()
                }
            }
        }
    }
}
