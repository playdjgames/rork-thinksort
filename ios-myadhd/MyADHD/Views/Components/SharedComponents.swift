import SwiftUI

/// Filled teal capsule — the single primary action on a screen.
struct PrimaryButtonStyle: ButtonStyle {
    var tint: Color = Theme.accent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(tint.opacity(configuration.isPressed ? 0.82 : 1), in: .capsule)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.snappy(duration: 0.18), value: configuration.isPressed)
    }
}

/// Outlined capsule on a surface — secondary and tertiary actions.
struct SoftButtonStyle: ButtonStyle {
    var tint: Color = Theme.ink
    var fill: Color = Theme.surface
    var isCompact: Bool = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(isCompact ? .subheadline.weight(.semibold) : .headline)
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity, minHeight: isCompact ? 46 : 54)
            .background(fill, in: .capsule)
            .overlay {
                Capsule().strokeBorder(Theme.hairline, lineWidth: 1)
            }
            .opacity(configuration.isPressed ? 0.7 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.snappy(duration: 0.18), value: configuration.isPressed)
    }
}

/// The Start / Done / Later / I'm Stuck row used on Today and the resume screen.
struct QuickActionRow: View {
    enum Action: Hashable {
        case start, done, later, stuck, resume, notNow
    }

    var actions: [Action]
    var onAction: (Action) -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var isStacked: Bool { dynamicTypeSize >= .accessibility1 || actions.count > 3 }

    var body: some View {
        Group {
            if isStacked {
                VStack(spacing: 10) {
                    ForEach(actions, id: \.self) { action in
                        button(for: action)
                    }
                }
            } else {
                HStack(spacing: 10) {
                    ForEach(actions, id: \.self) { action in
                        button(for: action)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func button(for action: Action) -> some View {
        switch action {
        case .start:
            Button { onAction(.start) } label: {
                Label("Start", systemImage: "play.fill")
            }
            .buttonStyle(PrimaryButtonStyle())
        case .resume:
            Button { onAction(.resume) } label: {
                Label("Resume", systemImage: "play.fill")
            }
            .buttonStyle(PrimaryButtonStyle())
        case .done:
            Button { onAction(.done) } label: {
                Label("Done", systemImage: "checkmark")
            }
            .buttonStyle(SoftButtonStyle(isCompact: true))
        case .later:
            Button { onAction(.later) } label: {
                Label("Later", systemImage: "clock")
            }
            .buttonStyle(SoftButtonStyle(isCompact: true))
        case .notNow:
            Button { onAction(.notNow) } label: {
                Label("Not Now", systemImage: "clock")
            }
            .buttonStyle(SoftButtonStyle(isCompact: true))
        case .stuck:
            Button { onAction(.stuck) } label: {
                Label("I'm Stuck", systemImage: "exclamationmark.circle")
            }
            .buttonStyle(SoftButtonStyle(tint: Theme.coral, isCompact: true))
        }
    }
}

/// Small pill showing a task's category.
struct CategoryTag: View {
    let category: ItemCategory

    var body: some View {
        Label(category.label, systemImage: category.symbol)
            .font(.caption2.weight(.semibold))
            .labelStyle(.titleAndIcon)
            .foregroundStyle(Theme.accent)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(Theme.accentSoft, in: .capsule)
    }
}

/// Thin timeline row: time on the left, a hairline rule, then the title.
struct TimelineRow: View {
    let timeLabel: String
    let title: String
    var isOverdue: Bool = false
    var trailingSymbol: String? = "chevron.right"

    var body: some View {
        HStack(spacing: 14) {
            Text(timeLabel)
                .font(.subheadline)
                .foregroundStyle(isOverdue ? Theme.coral : Theme.inkSecondary)
                .frame(width: 74, alignment: .leading)

            Rectangle()
                .fill(Theme.hairline)
                .frame(width: 1, height: 26)

            Text(title)
                .font(.body)
                .foregroundStyle(Theme.ink)
                .frame(maxWidth: .infinity, alignment: .leading)

            if let trailingSymbol {
                Image(systemName: trailingSymbol)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Theme.inkTertiary)
            }
        }
        .padding(.vertical, 14)
        .contentShape(Rectangle())
    }
}

/// Round check control used across task lists — text always accompanies state.
struct CheckCircle: View {
    let isDone: Bool
    var tint: Color = Theme.accent

    var body: some View {
        ZStack {
            Circle()
                .strokeBorder(isDone ? tint : Theme.hairline, lineWidth: 1.6)
                .frame(width: 26, height: 26)
            if isDone {
                Circle().fill(tint.opacity(0.16)).frame(width: 26, height: 26)
                Image(systemName: "checkmark")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(tint)
            }
        }
        .animation(.snappy(duration: 0.2), value: isDone)
    }
}

/// Section container with an uppercase eyebrow over a white card.
struct SectionCard<Content: View>: View {
    var title: String?
    var tint: Color = Theme.surface
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let title {
                Eyebrow(text: title)
                    .padding(.leading, 4)
            }
            VStack(spacing: 0) { content }
                .card(tint)
        }
    }
}

/// Hairline divider inset to match row content.
struct RowDivider: View {
    var leading: CGFloat = 18

    var body: some View {
        Rectangle()
            .fill(Theme.hairline)
            .frame(height: 1)
            .padding(.leading, leading)
    }
}

struct CountTile: View {
    let title: String
    let count: Int
    let symbol: String

    var body: some View {
        HStack(spacing: 12) {
            IconWell(symbol: symbol, size: 42)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.headline)
                    .foregroundStyle(Theme.ink)
                Text("\(count) task\(count == 1 ? "" : "s")")
                    .font(.footnote)
                    .foregroundStyle(Theme.inkSecondary)
            }
            Spacer(minLength: 4)
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Theme.inkTertiary)
        }
        .padding(14)
        .frame(maxWidth: .infinity)
        .card()
    }
}

/// Screen background used everywhere.
struct CanvasBackground: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(Theme.canvas.ignoresSafeArea())
    }
}

extension View {
    func canvas() -> some View { modifier(CanvasBackground()) }
}

extension TaskItem {
    /// "3:00 PM", "Tonight", "Tomorrow" — the short label used in timelines.
    func timeLabel(now: Date = Date()) -> String {
        guard let due = dueDate else { return "Anytime" }
        let calendar = Calendar.current
        if hasTime {
            let hour = calendar.component(.hour, from: due)
            if calendar.isDateInToday(due), hour >= 19 { return "Tonight" }
            return due.formatted(date: .omitted, time: .shortened)
        }
        if calendar.isDateInToday(due) { return "Today" }
        if calendar.isDateInTomorrow(due) { return "Tomorrow" }
        if calendar.isDateInYesterday(due) { return "Yesterday" }
        return due.formatted(.dateTime.month(.abbreviated).day())
    }

    func whenDescription(now: Date = Date()) -> String {
        var parts: [String] = []
        if let due = dueDate {
            let calendar = Calendar.current
            if calendar.isDateInToday(due) { parts.append("Today") }
            else if calendar.isDateInTomorrow(due) { parts.append("Tomorrow") }
            else if calendar.isDateInYesterday(due) { parts.append("Yesterday") }
            else { parts.append(due.formatted(.dateTime.weekday(.wide).month(.abbreviated).day())) }
            if hasTime { parts.append(due.formatted(date: .omitted, time: .shortened)) }
        }
        if let locationName { parts.append(locationName) }
        if parts.isEmpty, let estimatedMinutes { parts.append("About \(estimatedMinutes) min") }
        if parts.isEmpty { parts.append("No date yet") }
        return parts.joined(separator: " · ")
    }
}
