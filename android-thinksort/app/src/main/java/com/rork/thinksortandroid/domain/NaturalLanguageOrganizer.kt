package com.rork.thinksortandroid.domain

import com.rork.thinksortandroid.data.ItemCategory
import com.rork.thinksortandroid.data.LocationTrigger
import com.rork.thinksortandroid.data.LocationTriggerKind
import com.rork.thinksortandroid.data.Priority
import com.rork.thinksortandroid.data.RecurrenceRule
import com.rork.thinksortandroid.data.newId
import com.rork.thinksortandroid.data.nowMillis
import java.time.DayOfWeek

/** A single structured item extracted from natural-language input. */
data class ExtractedItem(
    val id: String = newId(),
    val title: String,
    val category: ItemCategory,
    val dueDate: Long? = null,
    val hasTime: Boolean = false,
    val deadline: Long? = null,
    val estimatedMinutes: Int? = null,
    val priority: Priority = Priority.SOON,
    val locationName: String? = null,
    val locationTrigger: LocationTrigger? = null,
    val recurrence: RecurrenceRule? = null,
    val reminderLeadMinutes: Int? = null,
    val store: String? = null,
    /** Title of another extracted item this one must happen before. */
    val preparationForTitle: String? = null,
    /** True when the organizer could not confidently classify the item. */
    val needsClarification: Boolean = false,
    val clarificationQuestion: String? = null,
    val rawText: String = "",
    val isSelected: Boolean = true,
) {
    fun dayBucketLabel(now: Long = nowMillis()): String {
        val due = dueDate ?: return "No date yet"
        if (Dates.isToday(due, now)) return "Today"
        if (Dates.isTomorrow(due, now)) return "Tomorrow"
        val days = Dates.daysBetween(now, due)
        if (days in 2..6) return Dates.weekday(due)
        return Dates.monthDay(due)
    }
}

data class OrganizerResult(
    val items: List<ExtractedItem> = emptyList(),
    val transcript: String = "",
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * Rule-based, fully on-device engine that turns messy speech into structured items.
 * It never invents information: anything it cannot determine is left empty or
 * surfaced as an explicit clarification for the user.
 */
class NaturalLanguageOrganizer(private val now: Long = nowMillis()) {

    fun organize(input: String): OrganizerResult {
        val transcript = input.trim()
        if (transcript.isEmpty()) return OrganizerResult()
        val items = mutableListOf<ExtractedItem>()
        for (fragment in splitIntoFragments(transcript)) {
            val clause = fragment.trim()
            if (clause.length <= 2) continue
            items.addAll(parseClause(clause))
        }
        return OrganizerResult(linkPreparations(items), transcript)
    }

    /** Parses a single sentence into one item (used for quick capture and onboarding). */
    fun parseSingle(input: String): ExtractedItem? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        return parseClause(trimmed).firstOrNull()
    }

    // region Clause parsing

    private fun parseClause(rawClause: String): List<ExtractedItem> {
        var clause = rawClause
        val lower = clause.lowercase()

        parseShoppingClause(clause)?.let { return it }

        var category = ItemCategory.TASK
        var locationTrigger: LocationTrigger? = null
        var locationName: String? = null
        var recurrence: RecurrenceRule? = null
        var lead: Int? = null
        var deadline: Long? = null
        var dueDate: Long? = null
        var hasTime = false
        var estimate: Int? = null
        var priority = Priority.SOON

        parseLocationTrigger(clause)?.let { (trigger, remainder) ->
            locationTrigger = trigger
            locationName = trigger.placeName
            clause = remainder
            category = ItemCategory.REMINDER
        }
        parseRecurrence(clause)?.let { (rule, remainder) ->
            recurrence = rule
            clause = remainder
        }
        parseLeadTime(clause)?.let { (minutes, remainder) ->
            lead = minutes
            clause = remainder
            category = ItemCategory.REMINDER
        }
        parseDeadline(clause)?.let { (date, remainder) ->
            deadline = date
            dueDate = dueDate ?: date
            clause = remainder
            priority = Priority.SOON
        }
        parseDateTime(clause)?.let { parsed ->
            dueDate = parsed.date
            hasTime = parsed.hasTime
            clause = parsed.remainder
        }
        parseDuration(clause)?.let { (minutes, remainder) ->
            estimate = minutes
            clause = remainder
        }
        if (locationName == null) {
            parsePlace(clause)?.let { (place, remainder) ->
                locationName = place
                clause = remainder
            }
        }

        if (lower.containsAny(listOf("urgent", "asap", "right away", "important", "critical"))) {
            priority = Priority.NOW
        } else if (lower.containsAny(listOf("whenever", "eventually", "sometime", "no rush", "if i have time"))) {
            priority = Priority.OPTIONAL
        }

        category = detectCategory(rawClause, category)

        var dependencyTitle: String? = null
        splitDependency(clause)?.let { (main, prep) ->
            clause = main
            dependencyTitle = prep
        }

        val title = cleanTitle(clause)
        if (title.isEmpty()) return emptyList()

        if (category == ItemCategory.APPOINTMENT && dueDate != null && lead == null) lead = 45

        var needsClarification = false
        var question: String? = null
        if (dueDate == null && locationTrigger == null && category == ItemCategory.TASK &&
            ambiguousPhrases.any { title.lowercase().contains(it) }
        ) {
            needsClarification = true
            question = "Not sure — is “$title” a task or an idea?"
        }

        var item = ExtractedItem(
            title = title,
            category = category,
            dueDate = dueDate,
            hasTime = hasTime,
            deadline = deadline,
            estimatedMinutes = estimate,
            priority = priority,
            locationName = locationName,
            locationTrigger = locationTrigger,
            recurrence = recurrence,
            reminderLeadMinutes = lead,
            needsClarification = needsClarification,
            clarificationQuestion = question,
            rawText = rawClause,
        )

        parseBringTarget(rawClause)?.let { target -> item = item.copy(preparationForTitle = target) }

        val result = mutableListOf(item)
        dependencyTitle?.let { dep ->
            val prepTitle = cleanTitle(dep)
            if (prepTitle.isNotEmpty()) {
                result.add(
                    0,
                    ExtractedItem(
                        title = prepTitle,
                        category = ItemCategory.TASK,
                        dueDate = dueDate,
                        priority = Priority.SOON,
                        preparationForTitle = title,
                        rawText = rawClause,
                    )
                )
            }
        }
        return result
    }

    // endregion

    // region Shopping

    private fun parseShoppingClause(clause: String): List<ExtractedItem>? {
        val lower = clause.lowercase()
        val triggers = listOf("i need to buy ", "i need to pick up ", "buy ", "i need ", "get ", "pick up ", "add to my shopping list ", "shopping list ")
        val trigger = triggers.firstOrNull { lower.startsWith(it) || lower.contains(" $it") } ?: return null
        val index = lower.indexOf(trigger)
        if (index < 0) return null
        val tail = clause.substring(index + trigger.length).trim()
        if (tail.isEmpty()) return null

        val store = storeName(lower)
        val looksLikeGoods = shoppingNouns.any { tail.lowercase().contains(it) }
        if (!looksLikeGoods) return null

        var cleanedTail = tail
        if (store != null) cleanedTail = cleanedTail.removingCaseInsensitive("at ${store.lowercase()}")
        cleanedTail = cleanedTail.removingCaseInsensitive("from the store")

        val names = cleanedTail
            .replace(" and ", ",")
            .split(",")
            .map { it.trimChars(" .;") }
            .filter { it.isNotEmpty() && it.length < 40 }
        if (names.isEmpty()) return null

        return names.map { name ->
            ExtractedItem(title = name.capitalizedFirst, category = ItemCategory.SHOPPING, store = store, rawText = clause)
        }
    }

    private fun storeName(lower: String): String? {
        val stores = listOf("walmart", "target", "costco", "trader joe's", "whole foods", "kroger", "safeway", "aldi", "publix", "cvs", "walgreens", "home depot", "lowe's", "ikea")
        val match = stores.firstOrNull { lower.contains(it) } ?: return null
        return match.split(" ").joinToString(" ") { it.capitalizedFirst }
    }

    // endregion

    // region Category

    private fun detectCategory(original: String, current: ItemCategory): ItemCategory {
        val lower = original.lowercase()
        if (current == ItemCategory.SHOPPING) return ItemCategory.SHOPPING
        return when {
            lower.containsAny(listOf("i have an idea", "idea for", "what if we", "it would be cool", "thinking about maybe", "concept for")) -> ItemCategory.IDEA
            lower.containsAny(listOf("just a note", "note to self", "for the record", "remember that ")) -> ItemCategory.NOTE
            lower.containsAny(listOf("appointment", "meeting with", "dentist", "doctor", "vet at", "interview", "checkup", "consultation")) -> ItemCategory.APPOINTMENT
            lower.containsAny(listOf("remind me", "don't let me forget", "make sure i")) -> ItemCategory.REMINDER
            lower.containsAny(listOf("drop off", "pick up", "mail ", "post office", "ups", "fedex", "dry clean", "gas station", "car wash", "oil change", "return the", "library")) -> ItemCategory.ERRAND
            current == ItemCategory.REMINDER -> ItemCategory.REMINDER
            else -> ItemCategory.TASK
        }
    }

    // endregion

    // region Date & time

    private data class ParsedDate(val date: Long, val hasTime: Boolean, val remainder: String)

    private fun parseDateTime(clause: String): ParsedDate? {
        var remainder = clause
        var day: Long? = null
        var hasTime = false
        val lower = clause.lowercase()
        val today = Dates.startOfDay(now)

        val dayPatterns = listOf(
            "the day after tomorrow" to 2L, "day after tomorrow" to 2L, "tomorrow" to 1L, "today" to 0L,
            "tonight" to 0L, "this evening" to 0L, "this afternoon" to 0L, "this morning" to 0L,
        )
        for ((phrase, offset) in dayPatterns) {
            if (!lower.contains(phrase)) continue
            day = Dates.addDays(today, offset)
            remainder = remainder.removingCaseInsensitive(phrase)
            if (phrase == "tonight" || phrase == "this evening") {
                day = Dates.atTime(19, 0, day)
                hasTime = true
            }
            break
        }

        if (day == null) {
            val weekdays = listOf(
                "sunday" to DayOfWeek.SUNDAY, "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY,
                "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY,
                "saturday" to DayOfWeek.SATURDAY,
            )
            for ((name, weekday) in weekdays) {
                if (!lower.contains(name)) continue
                day = nextOccurrence(weekday)
                remainder = remainder.removingCaseInsensitive("next $name")
                remainder = remainder.removingCaseInsensitive("on $name")
                remainder = remainder.removingCaseInsensitive(name)
                break
            }
        }

        if (day == null && lower.contains("next week")) {
            day = Dates.addDays(today, 7)
            remainder = remainder.removingCaseInsensitive("next week")
        }

        Regex("""in (\d{1,3}) (minutes|minute|min|hours|hour)""", RegexOption.IGNORE_CASE).find(remainder)?.let { match ->
            val amount = match.groupValues[1].toIntOrNull()
            if (amount != null) {
                val isHour = match.groupValues[2].lowercase().startsWith("hour")
                day = Dates.addMinutes(now, if (isHour) amount * 60L else amount.toLong())
                hasTime = true
                remainder = remainder.replace(match.value, " ")
            }
        }

        val timeMatch = Regex("""(?:at )?(\d{1,2})(?::(\d{2}))?\s?(am|pm|a\.m\.|p\.m\.)""", RegexOption.IGNORE_CASE).find(remainder)
        if (timeMatch != null) {
            var hour = timeMatch.groupValues[1].toIntOrNull() ?: 0
            val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
            val period = timeMatch.groupValues[3].lowercase().replace(".", "")
            if (period == "pm" && hour < 12) hour += 12
            if (period == "am" && hour == 12) hour = 0
            val base = day ?: today
            var resolved = Dates.atTime(minOf(hour, 23), minOf(minute, 59), base)
            if (day == null && resolved < now) resolved = Dates.addDays(resolved, 1)
            day = resolved
            hasTime = true
            remainder = remainder.replace(timeMatch.value, " ")
        } else if (lower.contains("noon")) {
            day = Dates.atTime(12, 0, day ?: today)
            hasTime = true
            remainder = remainder.removingCaseInsensitive("noon")
        }

        val resolvedDay = day ?: return null
        remainder = remainder.removingCaseInsensitive(" on ")
        return ParsedDate(resolvedDay, hasTime, remainder)
    }

    private fun nextOccurrence(weekday: DayOfWeek): Long {
        val start = Dates.startOfDay(now)
        for (offset in 1L..7L) {
            val candidate = Dates.addDays(start, offset)
            if (Dates.dayOfWeek(candidate) == weekday) return candidate
        }
        return start
    }

    private fun parseDeadline(clause: String): Pair<Long, String>? {
        val lower = clause.lowercase()
        for (marker in listOf("before ", "by ", "due ")) {
            val index = lower.indexOf(marker)
            if (index < 0) continue
            val tail = clause.substring(index + marker.length)
            val parsed = parseDateTime(tail) ?: continue
            if (parsed.date <= now - 86_400_000L) continue
            return parsed.date to clause.substring(0, index)
        }
        return null
    }

    private fun parseLeadTime(clause: String): Pair<Int, String>? {
        val match = Regex("""(\d{1,3}|half an|an|a|two|three) (minutes|minute|hours|hour) (?:before|ahead)""", RegexOption.IGNORE_CASE)
            .find(clause) ?: return null
        val amountText = match.groupValues[1].lowercase()
        val unit = match.groupValues[2].lowercase()
        val remainder = clause.replace(match.value, " ")
        val amount = when (amountText) {
            "a", "an" -> 1
            "two" -> 2
            "three" -> 3
            "half an" -> return 30 to remainder
            else -> amountText.toIntOrNull() ?: 1
        }
        return (if (unit.startsWith("hour")) amount * 60 else amount) to remainder
    }

    private fun parseDuration(clause: String): Pair<Int, String>? {
        val match = Regex("""(?:takes |about |roughly |around |it's a )?(\d{1,3}) ?(minutes|minute|min|hours|hour)(?: long| job| task)?""", RegexOption.IGNORE_CASE)
            .find(clause) ?: return null
        if (clause.lowercase().contains("${match.value.lowercase()} before")) return null
        val amount = match.groupValues[1].toIntOrNull() ?: return null
        val minutes = if (match.groupValues[2].lowercase().startsWith("hour")) amount * 60 else amount
        if (minutes <= 0 || minutes > 8 * 60) return null
        return minutes to clause.replace(match.value, " ")
    }

    private fun parseRecurrence(clause: String): Pair<RecurrenceRule, String>? {
        val lower = clause.lowercase()
        val map = listOf(
            "every day" to RecurrenceRule.DAILY, "daily" to RecurrenceRule.DAILY, "every weekday" to RecurrenceRule.WEEKDAYS,
            "every week" to RecurrenceRule.WEEKLY, "weekly" to RecurrenceRule.WEEKLY, "every monday" to RecurrenceRule.WEEKLY,
            "every month" to RecurrenceRule.MONTHLY, "monthly" to RecurrenceRule.MONTHLY,
        )
        for ((phrase, rule) in map) {
            if (lower.contains(phrase)) return rule to clause.removingCaseInsensitive(phrase)
        }
        return null
    }

    private fun parseLocationTrigger(clause: String): Pair<LocationTrigger, String>? {
        val lower = clause.lowercase()
        val arriving = listOf("when i get to ", "when i'm at ", "when i am at ", "next time i'm at ", "next time i am at ", "when i arrive at ", "when i'm near ", "when i am near ", "when i get home")
        val leaving = listOf("when i leave ", "when i leave", "before i leave ")

        for (marker in arriving) {
            val index = lower.indexOf(marker)
            if (index < 0) continue
            val (place, remainder) = splitPlaceAndRemainder(clause.substring(index + marker.length))
            val placeName = place.ifEmpty { "home" }
            val action = (clause.substring(0, index) + " " + remainder).trim()
            return LocationTrigger(LocationTriggerKind.ARRIVING, placeName.capitalizedWords) to action
        }
        for (marker in leaving) {
            val index = lower.indexOf(marker)
            if (index < 0) continue
            val (place, remainder) = splitPlaceAndRemainder(clause.substring(index + marker.length))
            if (place.isEmpty()) continue
            val action = (clause.substring(0, index) + " " + remainder).trim()
            return LocationTrigger(LocationTriggerKind.LEAVING, place.capitalizedWords) to action
        }
        return null
    }

    private fun splitPlaceAndRemainder(tail: String): Pair<String, String> {
        for (separator in listOf(", remind me to ", " remind me to ", ", remind me ", ", ", " remind me ")) {
            val index = tail.lowercase().indexOf(separator)
            if (index >= 0) {
                return tail.substring(0, index).trimChars(" ,.") to tail.substring(index + separator.length)
            }
        }
        return tail.trimChars(" ,.") to ""
    }

    private fun parsePlace(clause: String): Pair<String, String>? {
        val match = Regex(""" at (the )?([A-Za-z' ]{3,24}?)(?:$|,|\.)""").find(clause) ?: return null
        val place = match.groupValues[2].trim()
        if (place.isEmpty() || place.split(" ").size > 3) return null
        if (place.lowercase() in listOf("work", "home", "the same time", "least")) return null
        return place.capitalizedWords to clause.replace(match.value, " ")
    }

    private fun splitDependency(clause: String): Pair<String, String>? {
        val lower = clause.lowercase()
        for (marker in listOf(" but i need to ", " but first i need to ", " but i have to ", " but first ", " after i ")) {
            val index = lower.indexOf(marker)
            if (index < 0) continue
            val main = clause.substring(0, index)
            val prep = clause.substring(index + marker.length).removingCaseInsensitive(" first")
            return main to prep
        }
        return null
    }

    private fun parseBringTarget(clause: String): String? {
        val lower = clause.lowercase()
        if (!lower.contains("bring") && !lower.contains("take the")) return null
        for (marker in listOf(" to the ", " for the ", " when i go to ")) {
            val index = lower.indexOf(marker)
            if (index < 0) continue
            val target = clause.substring(index + marker.length).trimChars(" .,")
            if (target.isNotEmpty() && target.length < 30) return target
        }
        return null
    }

    // endregion

    /** Drops preparation links whose parent wasn't found in the same dump. */
    private fun linkPreparations(items: List<ExtractedItem>): List<ExtractedItem> =
        items.map { item ->
            val target = item.preparationForTitle?.lowercase() ?: return@map item
            val hasMatch = items.any { other ->
                other.id != item.id && (other.title.lowercase().contains(target) || target.contains(other.title.lowercase().take(8)))
            }
            if (hasMatch) item else item.copy(preparationForTitle = null)
        }

    companion object {
        private val separators = listOf(
            ", and i ", ", and then ", ", then i ", ", i ", ", and ", "; ", ". ", "\n",
            " and i need to ", " and i have to ", " and i should ", " and i want to ",
            " and remind me ", " also remind me ", " and also ", " also i need to ",
        )

        private val ambiguousPhrases = listOf(
            "finish the website", "the website", "my game", "the app", "the book",
            "the business", "the channel", "the novel", "the album",
        )

        private val shoppingNouns = listOf(
            "groceries", "grocery", "batteries", "dog food", "cat food", "paper towels", "milk",
            "eggs", "bread", "coffee", "toilet paper", "detergent", "shampoo", "trash bags",
            "light bulbs", "soap", "snacks", "water", "diapers", "cheese", "butter", "sugar",
            "rice", "pasta", "chicken", "vegetables", "fruit", "toothpaste", "cereal",
        )

        fun splitIntoFragments(text: String): List<String> {
            var working = listOf(text)
            for (separator in separators) {
                working = working.flatMap { it.lowercasedSplit(separator) }
            }
            return working.map { it.trimChars(" .,;\n\t") }.filter { it.isNotEmpty() }
        }

        private val leadingPhrases = listOf(
            "hey google", "ok google", "thinksort", "i need to remember to", "i have to remember to",
            "remember to", "don't let me forget to", "dont let me forget to", "make sure i",
            "i need to", "i have to", "i should", "i want to", "i gotta", "i've got to",
            "i must", "remind me to", "remind me about", "remind me", "i think i need to",
            "i think", "add", "please", "also", "and", "then", "to",
        )

        fun cleanTitle(input: String): String {
            var text = input
            var changed = true
            while (changed) {
                changed = false
                text = text.trimChars(" ,.;")
                val lower = text.lowercase()
                for (phrase in leadingPhrases) {
                    if (lower.startsWith("$phrase ") || lower == phrase) {
                        text = text.drop(minOf(phrase.length + 1, text.length))
                        changed = true
                        break
                    }
                }
            }
            while (text.contains("  ")) text = text.replace("  ", " ")
            return text.trimChars(" ,.;-").capitalizedFirst
        }
    }
}
