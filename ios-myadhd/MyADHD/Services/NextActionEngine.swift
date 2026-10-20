import Foundation

nonisolated struct NextAction: Identifiable, Hashable, Sendable {
    var id: UUID { task.id }
    var task: TaskItem
    var reason: String
    var score: Double
}

/// Chooses the single most sensible thing to do right now.
/// Considers time of day, overdue state, priority, duration, dependencies,
/// preparation needs, calendar pressure and what the user already started.
nonisolated struct NextActionEngine: Sendable {
    var now: Date = Date()
    var availableMinutes: Int?
    var minutesUntilNextEvent: Int?
    var nextEventTitle: String?

    func recommend(from tasks: [TaskItem], checkpoint: Checkpoint?) -> NextAction? {
        let candidates = tasks.filter { $0.isOpen && $0.status != .waiting }
        guard !candidates.isEmpty else { return nil }

        let blockedIDs = Set(candidates.flatMap { task -> [UUID] in
            // A task is blocked while any of its dependencies are still open.
            let hasOpenDependency = task.dependencyIDs.contains { dependencyID in
                tasks.first { $0.id == dependencyID }?.isOpen ?? false
            }
            return hasOpenDependency ? [task.id] : []
        })

        var scored: [NextAction] = []
        for task in candidates {
            if blockedIDs.contains(task.id) { continue }
            if let fit = availableMinutes, let estimate = task.estimatedMinutes, estimate > fit { continue }
            if let window = minutesUntilNextEvent, window < 25, let estimate = task.estimatedMinutes, estimate > window { continue }
            let (score, reason) = evaluate(task, allTasks: tasks, checkpoint: checkpoint)
            scored.append(NextAction(task: task, reason: reason, score: score))
        }

        if scored.isEmpty {
            // Everything is blocked — surface the unblocking prerequisite instead.
            let prerequisites = candidates.filter { task in
                candidates.contains { $0.dependencyIDs.contains(task.id) }
            }
            if let first = prerequisites.first {
                return NextAction(task: first, reason: "Something else is waiting on this one.", score: 1)
            }
            guard let fallback = candidates.first else { return nil }
            return NextAction(task: fallback, reason: reasonText(for: fallback), score: 1)
        }

        return scored.max { $0.score < $1.score }
    }

    private func evaluate(_ task: TaskItem, allTasks: [TaskItem], checkpoint: Checkpoint?) -> (Double, String) {
        var score = task.priority.weight
        var reasons: [String] = []

        if task.isOverdue(now: now) {
            score += 45
            reasons.append("it's overdue")
        }

        if task.status == .inProgress || checkpoint?.taskID == task.id {
            score += 35
            reasons.append("you already started it")
        }

        if let due = task.dueDate {
            if Calendar.current.isDateInToday(due) {
                score += 25
                if task.hasTime {
                    let minutes = Calendar.current.dateComponents([.minute], from: now, to: due).minute ?? 0
                    if minutes > 0, minutes <= 120 {
                        score += Double(120 - minutes) / 4
                        reasons.append("it's at \(due.formatted(date: .omitted, time: .shortened))")
                    }
                } else {
                    reasons.append("it's on today's list")
                }
            } else if Calendar.current.isDateInTomorrow(due) {
                score += 6
            }
        }

        // Preparation work for something soon should jump the queue.
        if let parent = allTasks.first(where: { $0.dependencyIDs.contains(task.id) && $0.isOpen }) {
            score += 30
            if let parentDue = parent.dueDate {
                let when = Calendar.current.isDateInTomorrow(parentDue) ? "tomorrow" : parentDue.formatted(.dateTime.weekday(.wide))
                reasons.append("you need it for \(parent.title.lowercasedFirst) \(when)")
            } else {
                reasons.append("\(parent.title) depends on it")
            }
        }

        if let estimate = task.estimatedMinutes {
            if estimate <= 15 {
                score += 12
                reasons.append("it should only take about \(estimate) minutes")
            }
            if let window = minutesUntilNextEvent, estimate <= window {
                score += 6
            }
        }

        if task.postponeCount >= 2 {
            score += 10
            reasons.append("you've moved it \(task.postponeCount) times already")
        }

        if let trigger = task.locationTrigger {
            score -= 15
            reasons.append("it's set for \(trigger.kind == .arriving ? "when you get to" : "when you leave") \(trigger.placeName)")
        }

        if task.category == .idea || task.category == .note {
            score -= 60
        }

        let hour = Calendar.current.component(.hour, from: now)
        if hour >= 21, (task.estimatedMinutes ?? 30) > 45 {
            score -= 12
        }

        let text = reasons.isEmpty ? reasonText(for: task) : reasons.prefix(2).joined(separator: ", and ").capitalizedFirst + "."
        return (score, text)
    }

    private func reasonText(for task: TaskItem) -> String {
        if let due = task.dueDate, task.hasTime {
            return "It's scheduled for \(due.formatted(date: .omitted, time: .shortened))."
        }
        if let estimate = task.estimatedMinutes {
            return "It's a short one — about \(estimate) minutes."
        }
        return "It's the clearest thing you can move forward right now."
    }

    /// Tasks that fit into a stated pocket of time.
    func tasksFitting(minutes: Int, in tasks: [TaskItem]) -> [TaskItem] {
        tasks
            .filter { $0.isOpen && $0.category != .idea && $0.category != .note }
            .filter { ($0.estimatedMinutes ?? defaultEstimate(for: $0)) <= minutes }
            .sorted { lhs, rhs in
                let left = lhs.estimatedMinutes ?? defaultEstimate(for: lhs)
                let right = rhs.estimatedMinutes ?? defaultEstimate(for: rhs)
                if lhs.priority.weight != rhs.priority.weight { return lhs.priority.weight > rhs.priority.weight }
                return left < right
            }
    }

    func defaultEstimate(for task: TaskItem) -> Int {
        if !task.subtasks.isEmpty { return task.subtasks.count * 10 }
        switch task.category {
        case .shopping: return 30
        case .errand: return 30
        case .appointment: return 60
        case .reminder: return 5
        default: return 20
        }
    }
}

nonisolated extension String {
    var lowercasedFirst: String {
        guard let first else { return self }
        return String(first).lowercased() + dropFirst()
    }
}
