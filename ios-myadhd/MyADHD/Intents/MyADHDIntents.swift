import AppIntents
import Foundation

// MARK: - Shared helpers

nonisolated enum IntentSupport {
    @MainActor
    static var store: BrainStore { IntentBridge.shared.activeStore }

    @MainActor
    static func route(_ route: IntentRoute) {
        IntentBridge.shared.requestRoute(route)
    }

    /// The task a pronoun like "that" most plausibly refers to: what the user is
    /// actively working on, then the saved checkpoint, then the recommended next thing.
    @MainActor
    static func focusedTask() -> TaskItem? {
        let store = store
        if let inProgress = store.tasks.first(where: { $0.status == .inProgress && $0.isOpen }) {
            return inProgress
        }
        if let checkpointID = store.checkpoint?.taskID, let task = store.task(id: checkpointID), task.isOpen {
            return task
        }
        return store.nextAction()?.task
    }

    static func dialog(_ text: String) -> IntentDialog {
        IntentDialog(stringLiteral: text)
    }
}

// MARK: - Capture

nonisolated struct AddToMyADHDIntent: AppIntent {
    static var title: LocalizedStringResource = "Add to ThinkSort"
    static var description = IntentDescription("Capture a task, reminder or shopping item in your own words.")
    static var openAppWhenRun: Bool = false

    @Parameter(title: "What do you want to add?", requestValueDialog: "What should I add?")
    var text: String

    static var parameterSummary: some ParameterSummary {
        Summary("Add \(\.$text) to ThinkSort")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let confirmation = IntentSupport.store.quickCapture(text)
        IntentSupport.route(.toast(confirmation))
        return .result(dialog: IntentSupport.dialog(confirmation))
    }
}

nonisolated struct AddShoppingItemIntent: AppIntent {
    static var title: LocalizedStringResource = "Add to Shopping List"
    static var description = IntentDescription("Add something to your ThinkSort shopping list.")
    static var openAppWhenRun: Bool = false

    @Parameter(title: "Item", requestValueDialog: "What should I add to your list?")
    var item: String

    static var parameterSummary: some ParameterSummary {
        Summary("Add \(\.$item) to the ThinkSort shopping list")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        IntentSupport.store.addShoppingItem(item)
        let message = "Added \(item.lowercasedFirst) to your shopping list."
        IntentSupport.route(.toast(message))
        return .result(dialog: IntentSupport.dialog(message))
    }
}

nonisolated struct OpenBrainDumpIntent: AppIntent {
    static var title: LocalizedStringResource = "Brain Dump"
    static var description = IntentDescription("Open ThinkSort and start a brain dump.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        IntentSupport.route(.brainDump)
        return .result(dialog: IntentSupport.dialog("Go ahead — tell me everything."))
    }
}

// MARK: - Questions

nonisolated struct WhatsNextIntent: AppIntent {
    static var title: LocalizedStringResource = "What's Next"
    static var description = IntentDescription("Hear the one thing ThinkSort suggests doing now.")
    static var openAppWhenRun: Bool = false

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        guard let next = IntentSupport.store.nextAction() else {
            return .result(dialog: IntentSupport.dialog("Nothing is waiting on you right now. Enjoy it."))
        }
        let sentence = "\(next.task.title). \(next.reason)"
        return .result(dialog: IntentSupport.dialog(sentence))
    }
}

nonisolated struct WhatsComingUpIntent: AppIntent {
    static var title: LocalizedStringResource = "What's Coming Up"
    static var description = IntentDescription("Hear what's on your plate today and tomorrow.")
    static var openAppWhenRun: Bool = false

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let store = IntentSupport.store
        let today = store.todayTasks()
        let upcoming = Array(store.upcomingTasks().prefix(3))

        var parts: [String] = []
        if today.isEmpty {
            parts.append("Nothing is scheduled for today.")
        } else {
            let titles = today.prefix(3).map { task -> String in
                task.hasTime
                    ? "\(task.title.lowercasedFirst) at \(task.timeLabel())"
                    : task.title.lowercasedFirst
            }
            parts.append("Today: \(BrainStore.list(titles)).")
        }
        if let first = upcoming.first {
            parts.append("Next up after that, \(first.title.lowercasedFirst) \(first.timeLabel().lowercased()).")
        }
        return .result(dialog: IntentSupport.dialog(parts.joined(separator: " ")))
    }
}

nonisolated struct WhatAmIForgettingIntent: AppIntent {
    static var title: LocalizedStringResource = "What Am I Forgetting"
    static var description = IntentDescription("Surface overdue and repeatedly postponed items.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let items = IntentSupport.store.forgottenItems()
        IntentSupport.route(.forgetting)
        guard !items.isEmpty else {
            return .result(dialog: IntentSupport.dialog("Nothing looks forgotten. You're on top of it."))
        }
        let titles = items.prefix(3).map { $0.title.lowercasedFirst }
        return .result(dialog: IntentSupport.dialog("You might be forgetting \(BrainStore.list(titles))."))
    }
}

nonisolated struct WhereDidILeaveOffIntent: AppIntent {
    static var title: LocalizedStringResource = "Where Did I Leave Off"
    static var description = IntentDescription("Get back the thread of what you were doing.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let summary = IntentSupport.store.leftOffSummary()
        IntentSupport.route(.leftOff)
        return .result(dialog: IntentSupport.dialog(summary))
    }
}

nonisolated struct WhatDoINeedBeforeILeaveIntent: AppIntent {
    static var title: LocalizedStringResource = "What Do I Need Before I Leave"
    static var description = IntentDescription("Check what you need to bring for today's plans.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let groups = IntentSupport.store.preparationItemsBeforeLeaving()
        IntentSupport.route(.beforeYouGo)
        guard !groups.isEmpty else {
            return .result(dialog: IntentSupport.dialog("Nothing is marked as something to bring."))
        }
        let sentences = groups.prefix(2).map { group in
            "For \(group.task.title.lowercasedFirst): \(BrainStore.list(group.items.map { $0.title.lowercasedFirst }))"
        }
        return .result(dialog: IntentSupport.dialog(sentences.joined(separator: ". ") + "."))
    }
}

// MARK: - Actions

nonisolated struct StartNextTaskIntent: AppIntent {
    static var title: LocalizedStringResource = "Start My Next Task"
    static var description = IntentDescription("Begin the task ThinkSort recommends, in focus mode.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        guard let next = IntentSupport.store.nextAction() else {
            return .result(dialog: IntentSupport.dialog("There's nothing open to start."))
        }
        IntentSupport.store.start(taskID: next.task.id)
        IntentSupport.route(.oneThing(next.task.id))
        return .result(dialog: IntentSupport.dialog("Starting \(next.task.title.lowercasedFirst). Just this one thing."))
    }
}

nonisolated struct MarkThatDoneIntent: AppIntent {
    static var title: LocalizedStringResource = "Mark That Done"
    static var description = IntentDescription("Complete whatever you're currently working on.")
    static var openAppWhenRun: Bool = false

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        guard let task = IntentSupport.focusedTask() else {
            return .result(dialog: IntentSupport.dialog("I'm not sure which one you mean — nothing is in progress."))
        }
        IntentSupport.store.complete(taskID: task.id)
        let message = "Done: \(task.title.lowercasedFirst)."
        IntentSupport.route(.toast(message))
        return .result(dialog: IntentSupport.dialog(message))
    }
}

nonisolated struct MoveToTomorrowIntent: AppIntent {
    static var title: LocalizedStringResource = "Move That to Tomorrow"
    static var description = IntentDescription("Postpone the current task by a day, guilt-free.")
    static var openAppWhenRun: Bool = false

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        guard let task = IntentSupport.focusedTask() else {
            return .result(dialog: IntentSupport.dialog("I'm not sure which one you mean."))
        }
        let tomorrow = Calendar.current.date(
            byAdding: .day,
            value: 1,
            to: Calendar.current.startOfDay(for: Date())
        ) ?? Date()
        IntentSupport.store.postpone(taskID: task.id, to: tomorrow)
        let message = "Moved \(task.title.lowercasedFirst) to tomorrow."
        IntentSupport.route(.toast(message))
        return .result(dialog: IntentSupport.dialog(message))
    }
}

nonisolated struct RemindMeLaterIntent: AppIntent {
    static var title: LocalizedStringResource = "Remind Me Later"
    static var description = IntentDescription("Snooze the current task for a while.")
    static var openAppWhenRun: Bool = false

    @Parameter(title: "Minutes", default: 30)
    var minutes: Int

    static var parameterSummary: some ParameterSummary {
        Summary("Remind me in \(\.$minutes) minutes")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        guard let task = IntentSupport.focusedTask() else {
            return .result(dialog: IntentSupport.dialog("There's nothing to snooze right now."))
        }
        let safeMinutes = max(5, min(minutes, 12 * 60))
        IntentSupport.store.snooze(taskID: task.id, minutes: safeMinutes)
        let message = "I'll bring \(task.title.lowercasedFirst) back in \(safeMinutes) minutes."
        IntentSupport.route(.toast(message))
        return .result(dialog: IntentSupport.dialog(message))
    }
}

nonisolated struct ImStuckIntent: AppIntent {
    static var title: LocalizedStringResource = "I'm Stuck"
    static var description = IntentDescription("Get unstuck on whatever you're avoiding.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        guard let task = IntentSupport.focusedTask() else {
            IntentSupport.route(.brainDump)
            return .result(dialog: IntentSupport.dialog("Nothing is in progress. Tell me what's on your mind instead."))
        }
        IntentSupport.route(.stuck(task.id))
        return .result(dialog: IntentSupport.dialog("Okay. Let's make \(task.title.lowercasedFirst) smaller."))
    }
}

// MARK: - Routines

nonisolated struct StartMorningIntent: AppIntent {
    static var title: LocalizedStringResource = "Start My Morning"
    static var description = IntentDescription("Open your morning briefing.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let count = IntentSupport.store.todayTasks().count
        IntentSupport.route(.morningBriefing)
        let body = count == 0
            ? "Your day is clear so far."
            : "You have \(count) thing\(count == 1 ? "" : "s") today. Let's take the first one."
        return .result(dialog: IntentSupport.dialog("Good morning. \(body)"))
    }
}

nonisolated struct StartEveningResetIntent: AppIntent {
    static var title: LocalizedStringResource = "Evening Reset"
    static var description = IntentDescription("Close out today and set up tomorrow.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let done = IntentSupport.store.completedToday().count
        IntentSupport.route(.eveningReset)
        let body = done == 0
            ? "Let's set up tomorrow."
            : "You finished \(done) thing\(done == 1 ? "" : "s") today."
        return .result(dialog: IntentSupport.dialog(body))
    }
}

// MARK: - App Shortcuts

nonisolated struct MyADHDShortcuts: AppShortcutsProvider {
    static var shortcutTileColor: ShortcutTileColor { .teal }

    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: WhatsNextIntent(),
            phrases: [
                "What's next in \(.applicationName)",
                "What should I do now in \(.applicationName)",
                "Ask \(.applicationName) what's next",
            ],
            shortTitle: "What's Next",
            systemImageName: "arrow.right.circle.fill"
        )
        AppShortcut(
            intent: AddToMyADHDIntent(),
            phrases: [
                "Add to \(.applicationName)",
                "Remember this in \(.applicationName)",
                "Capture this in \(.applicationName)",
            ],
            shortTitle: "Add Something",
            systemImageName: "plus.circle.fill"
        )
        AppShortcut(
            intent: WhereDidILeaveOffIntent(),
            phrases: [
                "Where did I leave off in \(.applicationName)",
                "What was I doing in \(.applicationName)",
            ],
            shortTitle: "Where I Left Off",
            systemImageName: "bookmark.fill"
        )
        AppShortcut(
            intent: WhatAmIForgettingIntent(),
            phrases: [
                "What am I forgetting in \(.applicationName)",
                "Am I forgetting anything in \(.applicationName)",
            ],
            shortTitle: "What Am I Forgetting",
            systemImageName: "exclamationmark.triangle.fill"
        )
        AppShortcut(
            intent: WhatsComingUpIntent(),
            phrases: [
                "What's coming up in \(.applicationName)",
                "What's on my plate in \(.applicationName)",
            ],
            shortTitle: "What's Coming Up",
            systemImageName: "calendar"
        )
        AppShortcut(
            intent: StartNextTaskIntent(),
            phrases: [
                "Start my next task in \(.applicationName)",
                "Start the next thing in \(.applicationName)",
            ],
            shortTitle: "Start Next Task",
            systemImageName: "play.circle.fill"
        )
        AppShortcut(
            intent: MarkThatDoneIntent(),
            phrases: [
                "Mark that done in \(.applicationName)",
                "I finished that in \(.applicationName)",
            ],
            shortTitle: "Mark Done",
            systemImageName: "checkmark.circle.fill"
        )
        AppShortcut(
            intent: ImStuckIntent(),
            phrases: [
                "I'm stuck in \(.applicationName)",
                "Help me get unstuck in \(.applicationName)",
            ],
            shortTitle: "I'm Stuck",
            systemImageName: "lifepreserver.fill"
        )
        AppShortcut(
            intent: WhatDoINeedBeforeILeaveIntent(),
            phrases: [
                "What do I need before I leave in \(.applicationName)",
                "What should I bring in \(.applicationName)",
            ],
            shortTitle: "Before You Go",
            systemImageName: "bag.fill"
        )
        AppShortcut(
            intent: StartMorningIntent(),
            phrases: [
                "Start my morning in \(.applicationName)",
                "Morning briefing in \(.applicationName)",
            ],
            shortTitle: "Start My Morning",
            systemImageName: "sun.horizon.fill"
        )
    }
}
