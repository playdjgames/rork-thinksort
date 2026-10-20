import Foundation

/// Suggests smaller steps for big, vague tasks. Purely heuristic and always editable —
/// the goal is to remove the "where do I even start" barrier, not to be authoritative.
nonisolated enum TaskBreakdown {
    private static let templates: [(keywords: [String], steps: [String])] = [
        (["garage"], ["Get trash bags", "Clear the workbench", "Make keep / donate / trash piles", "Organize tools", "Sweep the floor", "Take the trash outside"]),
        (["clean the house", "clean house", "tidy"], ["Pick up five things", "Clear one flat surface", "Take out the trash", "Wipe the counters", "Run one load of laundry"]),
        (["kitchen"], ["Clear the sink", "Load the dishwasher", "Wipe the counters", "Sweep the floor", "Take out the trash"]),
        (["laundry"], ["Gather the clothes", "Start one load", "Move it to the dryer", "Fold one basket", "Put it away"]),
        (["taxes", "tax"], ["Gather income documents", "Collect receipts", "Open the tax software", "Fill in one section", "Review and submit"]),
        (["paperwork", "forms", "application"], ["Find the paperwork", "Read what's required", "Fill in the easy fields", "Handle the hard fields", "Sign and send it"]),
        (["email", "inbox"], ["Archive anything obvious", "Reply to the quickest one", "Reply to the important one", "Flag what needs more time"]),
        (["groceries", "grocery", "shopping"], ["Check what you're out of", "Write the list", "Grab bags and keys", "Go to the store", "Put everything away"]),
        (["move", "packing", "pack"], ["Get boxes and tape", "Pack one shelf", "Label the box", "Stack it by the door", "Repeat with the next shelf"]),
        (["car", "oil change"], ["Find the service number", "Book a time", "Put it in the calendar", "Set a reminder to leave"]),
        (["website", "app", "project"], ["Write down the goal in one sentence", "List the three next pieces", "Do the smallest piece", "Review what changed"]),
        (["yard", "lawn", "garden"], ["Get the tools out", "Do one section", "Bag the clippings", "Put the tools away"]),
        (["bill", "bills", "budget"], ["Open the account", "List what's due", "Pay the closest due date", "Note the next one"]),
        (["call", "phone"], ["Find the number", "Write down what you need to say", "Make the call", "Note what happened"]),
        (["appointment", "doctor", "dentist", "vet"], ["Confirm the time", "Gather what you need to bring", "Set a leave-by reminder", "Go"]),
    ]

    private static let genericSteps = [
        "Get everything you need in one place",
        "Do the smallest first piece",
        "Do the main part",
        "Finish and put things away",
    ]

    static func steps(for title: String) -> [String] {
        let lower = title.lowercased()
        for template in templates where template.keywords.contains(where: { lower.contains($0) }) {
            return template.steps
        }
        return genericSteps
    }

    /// A single, deliberately tiny first action for the "I'm Stuck" flow.
    static func tinyFirstStep(for title: String, reason: StuckReason) -> String {
        let lower = title.lowercased()
        switch reason {
        case .tooBig, .dontKnowWhereToStart:
            return steps(for: title).first ?? "Spend two minutes on the very first piece"
        case .needSomething:
            if lower.contains("garage") || lower.contains("clean") { return "Write down the one thing you're missing" }
            return "Write down exactly what you need, then get that one thing"
        case .notEnoughTime:
            return "Set a timer for five minutes and stop when it rings"
        case .distracted:
            return "Put your phone in another room, then start a 5-minute timer"
        case .dontWantTo:
            return "Do it badly for two minutes — you can stop after that"
        case .other:
            return "Do the smallest visible piece for two minutes"
        }
    }

    static func preparationSuggestions(for title: String) -> [String] {
        let lower = title.lowercased()
        if lower.contains("vet") { return ["Insurance card", "Leash", "Pet's medication", "Keys"] }
        if lower.contains("dentist") || lower.contains("doctor") { return ["Insurance card", "ID", "Paperwork", "Keys"] }
        if lower.contains("ups") || lower.contains("post office") || lower.contains("package") { return ["The package", "Return label", "Keys"] }
        if lower.contains("gym") || lower.contains("workout") { return ["Gym clothes", "Water bottle", "Headphones"] }
        if lower.contains("flight") || lower.contains("airport") { return ["ID or passport", "Boarding pass", "Charger"] }
        return []
    }
}
