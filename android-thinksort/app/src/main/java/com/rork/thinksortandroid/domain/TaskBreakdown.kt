package com.rork.thinksortandroid.domain

import com.rork.thinksortandroid.data.StuckReason

/**
 * Suggests smaller steps for big, vague tasks. Purely heuristic and always editable —
 * the goal is to remove the "where do I even start" barrier, not to be authoritative.
 */
object TaskBreakdown {
    private val templates: List<Pair<List<String>, List<String>>> = listOf(
        listOf("garage") to listOf("Get trash bags", "Clear the workbench", "Make keep / donate / trash piles", "Organize tools", "Sweep the floor", "Take the trash outside"),
        listOf("clean the house", "clean house", "tidy") to listOf("Pick up five things", "Clear one flat surface", "Take out the trash", "Wipe the counters", "Run one load of laundry"),
        listOf("kitchen") to listOf("Clear the sink", "Load the dishwasher", "Wipe the counters", "Sweep the floor", "Take out the trash"),
        listOf("laundry") to listOf("Gather the clothes", "Start one load", "Move it to the dryer", "Fold one basket", "Put it away"),
        listOf("taxes", "tax") to listOf("Gather income documents", "Collect receipts", "Open the tax software", "Fill in one section", "Review and submit"),
        listOf("paperwork", "forms", "application") to listOf("Find the paperwork", "Read what's required", "Fill in the easy fields", "Handle the hard fields", "Sign and send it"),
        listOf("email", "inbox") to listOf("Archive anything obvious", "Reply to the quickest one", "Reply to the important one", "Flag what needs more time"),
        listOf("groceries", "grocery", "shopping") to listOf("Check what you're out of", "Write the list", "Grab bags and keys", "Go to the store", "Put everything away"),
        listOf("move", "packing", "pack") to listOf("Get boxes and tape", "Pack one shelf", "Label the box", "Stack it by the door", "Repeat with the next shelf"),
        listOf("car", "oil change") to listOf("Find the service number", "Book a time", "Put it in the calendar", "Set a reminder to leave"),
        listOf("website", "app", "project") to listOf("Write down the goal in one sentence", "List the three next pieces", "Do the smallest piece", "Review what changed"),
        listOf("yard", "lawn", "garden") to listOf("Get the tools out", "Do one section", "Bag the clippings", "Put the tools away"),
        listOf("bill", "bills", "budget") to listOf("Open the account", "List what's due", "Pay the closest due date", "Note the next one"),
        listOf("call", "phone") to listOf("Find the number", "Write down what you need to say", "Make the call", "Note what happened"),
        listOf("appointment", "doctor", "dentist", "vet") to listOf("Confirm the time", "Gather what you need to bring", "Set a leave-by reminder", "Go"),
    )

    private val genericSteps = listOf(
        "Get everything you need in one place",
        "Do the smallest first piece",
        "Do the main part",
        "Finish and put things away",
    )

    fun steps(title: String): List<String> {
        val lower = title.lowercase()
        return templates.firstOrNull { (keywords, _) -> keywords.any { lower.contains(it) } }?.second ?: genericSteps
    }

    /** A single, deliberately tiny first action for the "I'm Stuck" flow. */
    fun tinyFirstStep(title: String, reason: StuckReason): String {
        val lower = title.lowercase()
        return when (reason) {
            StuckReason.TOO_BIG, StuckReason.DONT_KNOW_WHERE_TO_START ->
                steps(title).firstOrNull() ?: "Spend two minutes on the very first piece"
            StuckReason.NEED_SOMETHING ->
                if (lower.contains("garage") || lower.contains("clean")) "Write down the one thing you're missing"
                else "Write down exactly what you need, then get that one thing"
            StuckReason.NOT_ENOUGH_TIME -> "Set a timer for five minutes and stop when it rings"
            StuckReason.DISTRACTED -> "Put your phone in another room, then start a 5-minute timer"
            StuckReason.DONT_WANT_TO -> "Do it badly for two minutes — you can stop after that"
            StuckReason.OTHER -> "Do the smallest visible piece for two minutes"
        }
    }

    fun preparationSuggestions(title: String): List<String> {
        val lower = title.lowercase()
        return when {
            lower.contains("vet") -> listOf("Insurance card", "Leash", "Pet's medication", "Keys")
            lower.contains("dentist") || lower.contains("doctor") -> listOf("Insurance card", "ID", "Paperwork", "Keys")
            lower.contains("ups") || lower.contains("post office") || lower.contains("package") -> listOf("The package", "Return label", "Keys")
            lower.contains("gym") || lower.contains("workout") -> listOf("Gym clothes", "Water bottle", "Headphones")
            lower.contains("flight") || lower.contains("airport") -> listOf("ID or passport", "Boarding pass", "Charger")
            else -> emptyList()
        }
    }
}
