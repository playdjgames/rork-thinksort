import Foundation

/// A single structured item extracted from natural-language input.
nonisolated struct ExtractedItem: Identifiable, Hashable, Sendable {
    var id: UUID = UUID()
    var title: String
    var category: ItemCategory
    var dueDate: Date?
    var hasTime: Bool = false
    var deadline: Date?
    var estimatedMinutes: Int?
    var priority: Priority = .soon
    var locationName: String?
    var locationTrigger: LocationTrigger?
    var recurrence: RecurrenceRule?
    var reminderLeadMinutes: Int?
    var store: String?
    /// Title of another extracted item this one must happen before.
    var preparationForTitle: String?
    /// True when the organizer could not confidently classify the item.
    var needsClarification: Bool = false
    var clarificationQuestion: String?
    var rawText: String = ""
    var isSelected: Bool = true

    var dayBucketLabel: String {
        guard let dueDate else { return "No date yet" }
        let calendar = Calendar.current
        if calendar.isDateInToday(dueDate) { return "Today" }
        if calendar.isDateInTomorrow(dueDate) { return "Tomorrow" }
        if let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: Date()), to: calendar.startOfDay(for: dueDate)).day, days > 1, days < 7 {
            return dueDate.formatted(.dateTime.weekday(.wide))
        }
        return dueDate.formatted(.dateTime.month(.abbreviated).day())
    }
}

nonisolated struct OrganizerResult: Sendable {
    var items: [ExtractedItem] = []
    var transcript: String = ""

    var isEmpty: Bool { items.isEmpty }
}

/// Rule-based, fully on-device engine that turns messy speech into structured items.
/// It never invents information: anything it cannot determine is left empty or
/// surfaced as an explicit clarification for the user.
nonisolated struct NaturalLanguageOrganizer: Sendable {
    private let calendar: Calendar
    private let now: Date

    init(now: Date = Date(), calendar: Calendar = .current) {
        self.now = now
        self.calendar = calendar
    }

    // MARK: - Entry point

    func organize(_ input: String) -> OrganizerResult {
        let transcript = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !transcript.isEmpty else { return OrganizerResult() }

        let fragments = Self.splitIntoFragments(transcript)
        var items: [ExtractedItem] = []

        for fragment in fragments {
            let clause = fragment.trimmingCharacters(in: .whitespacesAndNewlines)
            guard clause.count > 2 else { continue }
            items.append(contentsOf: parseClause(clause))
        }

        items = linkPreparations(items)
        return OrganizerResult(items: items, transcript: transcript)
    }

    /// Parses a single spoken sentence into one item (used for quick capture and Siri).
    func parseSingle(_ input: String) -> ExtractedItem? {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return parseClause(trimmed).first
    }

    // MARK: - Fragmenting

    private static let separators = [
        ", and i ", ", and then ", ", then i ", ", i ", ", and ", "; ", ". ", "\n",
        " and i need to ", " and i have to ", " and i should ", " and i want to ",
        " and remind me ", " also remind me ", " and also ", " also i need to ",
    ]

    static func splitIntoFragments(_ text: String) -> [String] {
        var working = [text]
        for separator in separators {
            var next: [String] = []
            for chunk in working {
                let parts = chunk.lowercasedSplit(on: separator)
                next.append(contentsOf: parts)
            }
            working = next
        }
        return working
            .map { $0.trimmingCharacters(in: CharacterSet(charactersIn: " .,;\n\t")) }
            .filter { !$0.isEmpty }
    }

    // MARK: - Clause parsing

    private func parseClause(_ rawClause: String) -> [ExtractedItem] {
        var clause = rawClause
        let lower = clause.lowercased()

        // Multi-item shopping clause: "I need batteries, dog food, and paper towels"
        if let shoppingItems = parseShoppingClause(clause) {
            return shoppingItems
        }

        var item = ExtractedItem(title: "", category: .task, rawText: rawClause)

        // Location trigger first — it also carries the actionable remainder.
        if let (trigger, remainder) = parseLocationTrigger(clause) {
            item.locationTrigger = trigger
            item.locationName = trigger.placeName
            clause = remainder
            item.category = .reminder
        }

        // Recurrence.
        if let (rule, remainder) = parseRecurrence(clause) {
            item.recurrence = rule
            clause = remainder
        }

        // Relative reminder lead ("two hours before", "30 minutes before").
        if let (lead, remainder) = parseLeadTime(clause) {
            item.reminderLeadMinutes = lead
            clause = remainder
            item.category = .reminder
        }

        // Deadline ("before Friday", "by Friday").
        if let (deadline, remainder) = parseDeadline(clause) {
            item.deadline = deadline
            item.dueDate = item.dueDate ?? deadline
            clause = remainder
            item.priority = .soon
        }

        // Date / time.
        if let parsed = parseDateTime(clause) {
            item.dueDate = parsed.date
            item.hasTime = parsed.hasTime
            clause = parsed.remainder
        }

        // Duration ("takes about 10 minutes", "it's a 20 minute job").
        if let (minutes, remainder) = parseDuration(clause) {
            item.estimatedMinutes = minutes
            clause = remainder
        }

        // Explicit place ("at Walmart", "at the post office").
        if item.locationName == nil, let (place, remainder) = parsePlace(clause) {
            item.locationName = place
            clause = remainder
        }

        // Priority words.
        if lower.containsAny(["urgent", "asap", "right away", "important", "critical"]) {
            item.priority = .now
        } else if lower.containsAny(["whenever", "eventually", "sometime", "no rush", "if i have time"]) {
            item.priority = .optional
        }

        // Category detection.
        item.category = detectCategory(original: rawClause, current: item.category)

        // Dependency clause: "clean the garage, but I need to buy trash bags first"
        var dependencyTitle: String?
        if let (mainPart, prepPart) = splitDependency(clause) {
            clause = mainPart
            dependencyTitle = prepPart
        }

        item.title = Self.cleanTitle(clause)
        guard !item.title.isEmpty else { return [] }

        if item.category == .appointment, item.dueDate != nil, item.reminderLeadMinutes == nil {
            item.reminderLeadMinutes = 45
        }

        // Ambiguous: a long-running project phrase with no date and no clear verb signal.
        if item.dueDate == nil, item.locationTrigger == nil,
           Self.ambiguousPhrases.contains(where: { item.title.lowercased().contains($0) }),
           item.category == .task {
            item.needsClarification = true
            item.clarificationQuestion = "Not sure — is “\(item.title)” a task or an idea?"
        }

        var result = [item]

        if let dependencyTitle {
            let prepTitle = Self.cleanTitle(dependencyTitle)
            if !prepTitle.isEmpty {
                var prep = ExtractedItem(
                    title: prepTitle,
                    category: .task,
                    dueDate: item.dueDate.flatMap { calendar.date(byAdding: .day, value: -0, to: $0) },
                    priority: .soon,
                    preparationForTitle: item.title,
                    rawText: rawClause
                )
                prep.hasTime = false
                result.insert(prep, at: 0)
            }
        }

        // "remember to bring X to the vet" → preparation item for a matching task.
        if let bringTarget = parseBringTarget(rawClause) {
            result[result.count - 1].preparationForTitle = bringTarget
        }

        return result
    }

    private static let ambiguousPhrases = [
        "finish the website", "the website", "my game", "the app", "the book",
        "the business", "the channel", "the novel", "the album",
    ]

    // MARK: - Shopping

    private func parseShoppingClause(_ clause: String) -> [ExtractedItem]? {
        let lower = clause.lowercased()
        let triggers = ["i need to buy ", "i need to pick up ", "buy ", "i need ", "get ", "pick up ", "add to my shopping list ", "shopping list "]
        guard let trigger = triggers.first(where: { lower.hasPrefix($0) || lower.contains(" \($0)") }) else { return nil }

        // Only treat as shopping when the phrase reads like a list of goods.
        guard let range = lower.range(of: trigger) else { return nil }
        let tail = String(clause[range.upperBound...]).trimmingCharacters(in: .whitespaces)
        guard !tail.isEmpty else { return nil }

        let store = Self.storeName(in: lower)
        let looksLikeGoods = Self.shoppingNouns.contains { tail.lowercased().contains($0) }
        let hasListShape = tail.contains(",") || tail.lowercased().contains(" and ")
        guard looksLikeGoods || (hasListShape && store != nil) || (store != nil && looksLikeGoods) else { return nil }
        guard looksLikeGoods else { return nil }

        var cleanedTail = tail
        if let store, let storeRange = cleanedTail.lowercased().range(of: "at \(store.lowercased())") {
            cleanedTail.removeSubrange(storeRange)
        }
        if let storeRange = cleanedTail.lowercased().range(of: "from the store") {
            cleanedTail.removeSubrange(storeRange)
        }

        let names = cleanedTail
            .replacingOccurrences(of: " and ", with: ",")
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: CharacterSet(charactersIn: " .;")) }
            .filter { !$0.isEmpty && $0.count < 40 }

        guard !names.isEmpty else { return nil }

        return names.map { name in
            ExtractedItem(
                title: name.capitalizedFirst,
                category: .shopping,
                store: store,
                rawText: clause
            )
        }
    }

    private static let shoppingNouns = [
        "groceries", "grocery", "batteries", "dog food", "cat food", "paper towels", "milk",
        "eggs", "bread", "coffee", "toilet paper", "detergent", "shampoo", "trash bags",
        "light bulbs", "soap", "snacks", "water", "diapers", "cheese", "butter", "sugar",
        "rice", "pasta", "chicken", "vegetables", "fruit", "toothpaste", "cereal",
    ]

    private static func storeName(in lower: String) -> String? {
        let stores = ["walmart", "target", "costco", "trader joe's", "whole foods", "kroger", "safeway", "aldi", "publix", "cvs", "walgreens", "home depot", "lowe's", "ikea"]
        guard let match = stores.first(where: { lower.contains($0) }) else { return nil }
        return match.split(separator: " ").map { String($0).capitalizedFirst }.joined(separator: " ")
    }

    // MARK: - Category

    private func detectCategory(original: String, current: ItemCategory) -> ItemCategory {
        let lower = original.lowercased()
        if current == .shopping { return .shopping }

        if lower.containsAny(["i have an idea", "idea for", "what if we", "it would be cool", "thinking about maybe", "concept for"]) {
            return .idea
        }
        if lower.containsAny(["just a note", "note to self", "for the record", "remember that "]) {
            return .note
        }
        if lower.containsAny(["appointment", "meeting with", "dentist", "doctor", "vet at", "interview", "checkup", "consultation"]) {
            return .appointment
        }
        if lower.containsAny(["remind me", "don't let me forget", "make sure i"]) {
            return .reminder
        }
        if lower.containsAny(["drop off", "pick up", "mail ", "post office", "ups", "fedex", "dry clean", "gas station", "car wash", "oil change", "return the", "library"]) {
            return .errand
        }
        return current == .reminder ? .reminder : .task
    }

    // MARK: - Date & time

    private struct ParsedDate {
        var date: Date
        var hasTime: Bool
        var remainder: String
    }

    private func parseDateTime(_ clause: String) -> ParsedDate? {
        var remainder = clause
        var day: Date?
        var hasTime = false
        let lower = clause.lowercased()

        let dayPatterns: [(String, Int)] = [
            ("the day after tomorrow", 2),
            ("day after tomorrow", 2),
            ("tomorrow", 1),
            ("today", 0),
            ("tonight", 0),
            ("this evening", 0),
            ("this afternoon", 0),
            ("this morning", 0),
        ]
        for (phrase, offset) in dayPatterns where lower.contains(phrase) {
            day = calendar.date(byAdding: .day, value: offset, to: calendar.startOfDay(for: now))
            remainder = remainder.removingCaseInsensitive(phrase)
            if phrase == "tonight" || phrase == "this evening" {
                day = calendar.date(bySettingHour: 19, minute: 0, second: 0, of: day ?? now)
                hasTime = true
            }
            break
        }

        if day == nil {
            let weekdays = ["sunday": 1, "monday": 2, "tuesday": 3, "wednesday": 4, "thursday": 5, "friday": 6, "saturday": 7]
            for (name, index) in weekdays where lower.contains(name) {
                day = nextOccurrence(ofWeekday: index)
                remainder = remainder.removingCaseInsensitive("next \(name)")
                remainder = remainder.removingCaseInsensitive("on \(name)")
                remainder = remainder.removingCaseInsensitive(name)
                break
            }
        }

        if lower.contains("next week"), day == nil {
            day = calendar.date(byAdding: .day, value: 7, to: calendar.startOfDay(for: now))
            remainder = remainder.removingCaseInsensitive("next week")
        }

        // "in 30 minutes" / "in 2 hours"
        if let match = Self.regexMatch(pattern: #"in (\d{1,3}) (minute|minutes|min|hour|hours)"#, in: remainder),
           let amount = Int(match.groups.first ?? "") {
            let unitIsHour = (match.groups.count > 1 ? match.groups[1] : "").hasPrefix("hour")
            day = calendar.date(byAdding: unitIsHour ? .hour : .minute, value: amount, to: now)
            hasTime = true
            remainder = remainder.replacingOccurrences(of: match.matched, with: " ")
        }

        // Explicit clock time.
        if let timeMatch = Self.regexMatch(pattern: #"(?:at )?(\d{1,2})(?::(\d{2}))?\s?(am|pm|a\.m\.|p\.m\.)"#, in: remainder) {
            var hour = Int(timeMatch.groups.first ?? "") ?? 0
            let minute = Int(timeMatch.groups.count > 1 ? timeMatch.groups[1] : "") ?? 0
            let period = (timeMatch.groups.count > 2 ? timeMatch.groups[2] : "").replacingOccurrences(of: ".", with: "")
            if period == "pm", hour < 12 { hour += 12 }
            if period == "am", hour == 12 { hour = 0 }
            let base = day ?? calendar.startOfDay(for: now)
            if let withTime = calendar.date(bySettingHour: min(hour, 23), minute: min(minute, 59), second: 0, of: base) {
                var resolved = withTime
                if day == nil, resolved < now {
                    resolved = calendar.date(byAdding: .day, value: 1, to: resolved) ?? resolved
                }
                day = resolved
                hasTime = true
            }
            remainder = remainder.replacingOccurrences(of: timeMatch.matched, with: " ")
        } else if lower.contains("noon") {
            let base = day ?? calendar.startOfDay(for: now)
            day = calendar.date(bySettingHour: 12, minute: 0, second: 0, of: base)
            hasTime = true
            remainder = remainder.removingCaseInsensitive("noon")
        }

        guard let day else { return nil }
        remainder = remainder.removingCaseInsensitive(" on ")
        return ParsedDate(date: day, hasTime: hasTime, remainder: remainder)
    }

    private func nextOccurrence(ofWeekday weekday: Int) -> Date {
        let start = calendar.startOfDay(for: now)
        for offset in 1...7 {
            if let candidate = calendar.date(byAdding: .day, value: offset, to: start),
               calendar.component(.weekday, from: candidate) == weekday {
                return candidate
            }
        }
        return start
    }

    private func parseDeadline(_ clause: String) -> (Date, String)? {
        let lower = clause.lowercased()
        let markers = ["before ", "by ", "due "]
        for marker in markers {
            guard let range = lower.range(of: marker) else { continue }
            let tail = String(clause[range.upperBound...])
            // Avoid matching "before I leave" (a trigger, not a date).
            guard let parsed = parseDateTime(tail), parsed.date > now.addingTimeInterval(-86_400) else { continue }
            let head = String(clause[..<range.lowerBound])
            return (parsed.date, head)
        }
        return nil
    }

    private func parseLeadTime(_ clause: String) -> (Int, String)? {
        guard let match = Self.regexMatch(
            pattern: #"(\d{1,3}|an|a|two|three|half an) (minute|minutes|hour|hours) (?:before|ahead)"#,
            in: clause
        ) else { return nil }
        let amountText = match.groups.first ?? ""
        let unit = match.groups.count > 1 ? match.groups[1] : ""
        let amount: Int
        switch amountText {
        case "a", "an": amount = 1
        case "two": amount = 2
        case "three": amount = 3
        case "half an": return (30, clause.replacingOccurrences(of: match.matched, with: " "))
        default: amount = Int(amountText) ?? 1
        }
        let minutes = unit.hasPrefix("hour") ? amount * 60 : amount
        return (minutes, clause.replacingOccurrences(of: match.matched, with: " "))
    }

    private func parseDuration(_ clause: String) -> (Int, String)? {
        guard let match = Self.regexMatch(
            pattern: #"(?:takes |about |roughly |around |it's a )?(\d{1,3}) ?(minute|minutes|min|hour|hours)(?: long| job| task)?"#,
            in: clause
        ) else { return nil }
        // Skip if this is a lead-time or relative time phrase handled elsewhere.
        if clause.lowercased().contains("\(match.matched) before") { return nil }
        guard let amount = Int(match.groups.first ?? "") else { return nil }
        let unit = match.groups.count > 1 ? match.groups[1] : ""
        let minutes = unit.hasPrefix("hour") ? amount * 60 : amount
        guard minutes > 0, minutes <= 8 * 60 else { return nil }
        return (minutes, clause.replacingOccurrences(of: match.matched, with: " "))
    }

    private func parseRecurrence(_ clause: String) -> (RecurrenceRule, String)? {
        let lower = clause.lowercased()
        let map: [(String, RecurrenceRule)] = [
            ("every day", .daily), ("daily", .daily), ("every weekday", .weekdays),
            ("every week", .weekly), ("weekly", .weekly), ("every monday", .weekly),
            ("every month", .monthly), ("monthly", .monthly),
        ]
        for (phrase, rule) in map where lower.contains(phrase) {
            return (rule, clause.removingCaseInsensitive(phrase))
        }
        return nil
    }

    private func parseLocationTrigger(_ clause: String) -> (LocationTrigger, String)? {
        let lower = clause.lowercased()
        let arrivingMarkers = ["when i get to ", "when i'm at ", "when i am at ", "next time i'm at ", "next time i am at ", "when i arrive at ", "when i'm near ", "when i am near ", "when i get home"]
        let leavingMarkers = ["when i leave ", "when i leave", "before i leave "]

        for marker in arrivingMarkers {
            guard let range = lower.range(of: marker) else { continue }
            let tail = String(clause[range.upperBound...])
            let (place, remainder) = Self.splitPlaceAndRemainder(tail)
            let placeName = place.isEmpty ? "home" : place
            let head = String(clause[..<range.lowerBound])
            let action = (head + " " + remainder).trimmingCharacters(in: .whitespaces)
            return (LocationTrigger(kind: .arriving, placeName: placeName.capitalizedWords), action)
        }
        for marker in leavingMarkers {
            guard let range = lower.range(of: marker) else { continue }
            let tail = String(clause[range.upperBound...])
            let (place, remainder) = Self.splitPlaceAndRemainder(tail)
            guard !place.isEmpty else { continue }
            let head = String(clause[..<range.lowerBound])
            let action = (head + " " + remainder).trimmingCharacters(in: .whitespaces)
            return (LocationTrigger(kind: .leaving, placeName: place.capitalizedWords), action)
        }
        return nil
    }

    private static func splitPlaceAndRemainder(_ tail: String) -> (String, String) {
        let separators = [", remind me to ", " remind me to ", ", remind me ", ", ", " remind me "]
        for separator in separators {
            if let range = tail.lowercased().range(of: separator) {
                let place = String(tail[..<range.lowerBound])
                let remainder = String(tail[range.upperBound...])
                return (place.trimmingCharacters(in: CharacterSet(charactersIn: " ,.")), remainder)
            }
        }
        return (tail.trimmingCharacters(in: CharacterSet(charactersIn: " ,.")), "")
    }

    private func parsePlace(_ clause: String) -> (String, String)? {
        guard let match = Self.regexMatch(pattern: #" at (the )?([A-Za-z' ]{3,24}?)(?:$|,|\.)"#, in: clause) else { return nil }
        let place = (match.groups.last ?? "").trimmingCharacters(in: .whitespaces)
        guard !place.isEmpty, place.split(separator: " ").count <= 3 else { return nil }
        let blocked = ["work", "home", "the same time", "least"]
        guard !blocked.contains(place.lowercased()) else { return nil }
        return (place.capitalizedWords, clause.replacingOccurrences(of: match.matched, with: " "))
    }

    private func splitDependency(_ clause: String) -> (String, String)? {
        let markers = [" but i need to ", " but first i need to ", " but i have to ", " but first ", " after i "]
        let lower = clause.lowercased()
        for marker in markers {
            guard let range = lower.range(of: marker) else { continue }
            let main = String(clause[..<range.lowerBound])
            var prep = String(clause[range.upperBound...])
            prep = prep.removingCaseInsensitive(" first")
            return (main, prep)
        }
        return nil
    }

    private func parseBringTarget(_ clause: String) -> String? {
        let lower = clause.lowercased()
        guard lower.contains("bring") || lower.contains("take the") else { return nil }
        let markers = [" to the ", " for the ", " when i go to "]
        for marker in markers {
            guard let range = lower.range(of: marker) else { continue }
            let target = String(clause[range.upperBound...]).trimmingCharacters(in: CharacterSet(charactersIn: " .,"))
            guard !target.isEmpty, target.count < 30 else { continue }
            return target
        }
        return nil
    }

    // MARK: - Linking

    /// Converts items marked as preparation into preparation links on their parent item.
    private func linkPreparations(_ items: [ExtractedItem]) -> [ExtractedItem] {
        var result = items
        for index in result.indices {
            guard let target = result[index].preparationForTitle?.lowercased() else { continue }
            let match = result.firstIndex { other in
                other.id != result[index].id &&
                (other.title.lowercased().contains(target) || target.contains(other.title.lowercased().prefix(8)))
            }
            if match == nil {
                result[index].preparationForTitle = nil
            }
        }
        return result
    }

    // MARK: - Helpers

    static func cleanTitle(_ input: String) -> String {
        var text = input
        let leadingPhrases = [
            "hey siri", "myadhd", "my adhd", "i need to remember to", "i have to remember to",
            "remember to", "don't let me forget to", "dont let me forget to", "make sure i",
            "i need to", "i have to", "i should", "i want to", "i gotta", "i've got to",
            "i must", "remind me to", "remind me about", "remind me", "i think i need to",
            "i think", "add", "please", "also", "and", "then", "to",
        ]
        var changed = true
        while changed {
            changed = false
            var lower = text.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: " ,.;"))
            for phrase in leadingPhrases where lower.hasPrefix(phrase + " ") || lower == phrase {
                let dropCount = min(phrase.count + 1, text.count)
                text = String(text.trimmingCharacters(in: CharacterSet(charactersIn: " ,.;")).dropFirst(dropCount))
                lower = text.lowercased()
                changed = true
                break
            }
        }
        text = text
            .replacingOccurrences(of: "  ", with: " ")
            .trimmingCharacters(in: CharacterSet(charactersIn: " ,.;-"))
        return text.capitalizedFirst
    }

    private struct RegexMatch {
        var matched: String
        var groups: [String]
    }

    private static func regexMatch(pattern: String, in text: String) -> RegexMatch? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return nil }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        guard let match = regex.firstMatch(in: text, options: [], range: range),
              let matchedRange = Range(match.range, in: text) else { return nil }
        var groups: [String] = []
        for index in 1..<match.numberOfRanges {
            if let groupRange = Range(match.range(at: index), in: text) {
                groups.append(String(text[groupRange]).lowercased())
            }
        }
        return RegexMatch(matched: String(text[matchedRange]), groups: groups)
    }
}

// MARK: - String helpers

nonisolated extension String {
    var capitalizedFirst: String {
        guard let first else { return self }
        return String(first).uppercased() + dropFirst()
    }

    var capitalizedWords: String {
        split(separator: " ").map { String($0).capitalizedFirst }.joined(separator: " ")
    }

    func containsAny(_ needles: [String]) -> Bool {
        needles.contains { contains($0) }
    }

    func removingCaseInsensitive(_ needle: String) -> String {
        guard let range = lowercased().range(of: needle.lowercased()) else { return self }
        var copy = self
        copy.replaceSubrange(range, with: " ")
        return copy.replacingOccurrences(of: "  ", with: " ")
    }

    func lowercasedSplit(on separator: String) -> [String] {
        var results: [String] = []
        var remaining = self
        while let range = remaining.lowercased().range(of: separator.lowercased()) {
            results.append(String(remaining[..<range.lowerBound]))
            remaining = String(remaining[range.lowerBound...])
            remaining = String(remaining.dropFirst(separator.count))
        }
        results.append(remaining)
        return results
    }
}
