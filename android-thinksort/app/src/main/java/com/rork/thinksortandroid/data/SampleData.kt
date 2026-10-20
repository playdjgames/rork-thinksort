package com.rork.thinksortandroid.data

import com.rork.thinksortandroid.domain.Dates
import com.rork.thinksortandroid.domain.TaskBreakdown

/** A small, realistic starter set so a brand-new install isn't an empty void. */
object SampleData {
    fun starter(now: Long = nowMillis()): AppData {
        val today = Dates.startOfDay(now)
        val tomorrow = Dates.addDays(today, 1)

        val findCard = TaskItem(
            title = "Find your insurance card",
            priority = Priority.NOW,
            dueDate = Dates.atTime(20, 0, today),
            hasTime = true,
            estimatedMinutes = 10,
            notes = "Check the glovebox and the kitchen drawer.",
        )
        val vet = TaskItem(
            title = "Take dog to vet",
            category = ItemCategory.APPOINTMENT,
            dueDate = Dates.atTime(14, 0, tomorrow),
            hasTime = true,
            estimatedMinutes = 60,
            locationName = "Riverside Animal Clinic",
            reminderLeadMinutes = 45,
            preparationItems = listOf("Insurance card", "Dog leash", "Dog's medication", "Keys").map { PreparationItem(title = it) },
            dependencyIDs = listOf(findCard.id),
        )
        val callMike = TaskItem(
            title = "Call Mike about the truck",
            dueDate = Dates.atTime(15, 0, today),
            hasTime = true,
            estimatedMinutes = 10,
        )
        val paperwork = TaskItem(
            title = "Finish work paperwork",
            dueDate = Dates.atTime(17, 30, today),
            hasTime = true,
            estimatedMinutes = 45,
        )
        val prescription = TaskItem(
            title = "Pick up prescription",
            dueDate = tomorrow,
            category = ItemCategory.ERRAND,
            estimatedMinutes = 20,
            locationName = "Pharmacy",
        )
        val garage = TaskItem(
            title = "Clean the garage",
            dueDate = Dates.addDays(today, 3),
            priority = Priority.LATER,
            estimatedMinutes = 120,
            subtasks = TaskBreakdown.steps("garage").map { Subtask(title = it) },
        )
        val electric = TaskItem(
            title = "Call the electric company",
            dueDate = Dates.addDays(today, -1),
            priority = Priority.NOW,
            estimatedMinutes = 10,
        )
        val batteries = TaskItem(
            title = "Buy batteries",
            category = ItemCategory.ERRAND,
            locationTrigger = LocationTrigger(LocationTriggerKind.ARRIVING, "Walmart"),
            locationName = "Walmart",
            priority = Priority.OPTIONAL,
        )

        return AppData(
            tasks = listOf(findCard, vet, callMike, paperwork, prescription, garage, electric, batteries),
            shopping = listOf(
                ShoppingItem(name = "Dog food", store = "Walmart"),
                ShoppingItem(name = "Paper towels", store = "Walmart"),
                ShoppingItem(name = "Coffee"),
            ),
            notes = listOf(
                NoteItem(text = "Idea for the game: let players trade blueprints", isIdea = true),
                NoteItem(text = "Truck warranty runs out in March"),
            ),
            history = listOf(
                HistoryEntry(kind = HistoryKind.CREATED, title = "Starter list", detail = "Sample items you can edit or delete"),
            ),
        )
    }
}
