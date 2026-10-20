import Foundation

/// A small, realistic starter set so a brand-new install isn't an empty void.
/// Users can wipe it at any time from Settings.
nonisolated enum SampleData {
    static func starter(now: Date = Date()) -> AppData {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: now)
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: today) ?? today

        func at(_ hour: Int, _ minute: Int, on day: Date) -> Date {
            calendar.date(bySettingHour: hour, minute: minute, second: 0, of: day) ?? day
        }

        var findCard = TaskItem(title: "Find your insurance card")
        findCard.category = .task
        findCard.priority = .now
        findCard.dueDate = at(20, 0, on: today)
        findCard.hasTime = true
        findCard.estimatedMinutes = 10
        findCard.notes = "Check the glovebox and the kitchen drawer."

        var vet = TaskItem(title: "Take dog to vet")
        vet.category = .appointment
        vet.priority = .soon
        vet.dueDate = at(14, 0, on: tomorrow)
        vet.hasTime = true
        vet.estimatedMinutes = 60
        vet.locationName = "Riverside Animal Clinic"
        vet.reminderLeadMinutes = 45
        vet.preparationItems = [
            PreparationItem(title: "Insurance card"),
            PreparationItem(title: "Dog leash"),
            PreparationItem(title: "Dog's medication"),
            PreparationItem(title: "Keys"),
        ]
        vet.dependencyIDs = [findCard.id]

        var callMike = TaskItem(title: "Call Mike about the truck")
        callMike.dueDate = at(15, 0, on: today)
        callMike.hasTime = true
        callMike.estimatedMinutes = 10
        callMike.priority = .soon
        callMike.category = .task

        var paperwork = TaskItem(title: "Finish work paperwork")
        paperwork.dueDate = at(17, 30, on: today)
        paperwork.hasTime = true
        paperwork.estimatedMinutes = 45
        paperwork.priority = .soon

        var prescription = TaskItem(title: "Pick up prescription")
        prescription.dueDate = tomorrow
        prescription.category = .errand
        prescription.estimatedMinutes = 20
        prescription.locationName = "Pharmacy"

        var garage = TaskItem(title: "Clean the garage")
        garage.dueDate = calendar.date(byAdding: .day, value: 3, to: today)
        garage.priority = .later
        garage.estimatedMinutes = 120
        garage.subtasks = TaskBreakdown.steps(for: "garage").map { Subtask(title: $0) }

        var electric = TaskItem(title: "Call the electric company")
        electric.dueDate = calendar.date(byAdding: .day, value: -1, to: today)
        electric.priority = .now
        electric.estimatedMinutes = 10
        electric.category = .task

        var batteries = TaskItem(title: "Buy batteries")
        batteries.category = .errand
        batteries.locationTrigger = LocationTrigger(kind: .arriving, placeName: "Walmart")
        batteries.locationName = "Walmart"
        batteries.priority = .optional

        var data = AppData()
        data.tasks = [findCard, vet, callMike, paperwork, prescription, garage, electric, batteries]
        data.shopping = [
            ShoppingItem(name: "Dog food", store: "Walmart"),
            ShoppingItem(name: "Paper towels", store: "Walmart"),
            ShoppingItem(name: "Coffee", store: nil),
        ]
        data.notes = [
            NoteItem(text: "Idea for the game: let players trade blueprints", isIdea: true),
            NoteItem(text: "Truck warranty runs out in March", isIdea: false),
        ]
        data.history = [
            HistoryEntry(kind: .created, title: "Starter list", detail: "Sample items you can edit or delete"),
        ]
        return data
    }
}
