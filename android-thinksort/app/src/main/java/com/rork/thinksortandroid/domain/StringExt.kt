package com.rork.thinksortandroid.domain

val String.capitalizedFirst: String
    get() = if (isEmpty()) this else this[0].uppercaseChar() + substring(1)

val String.lowercasedFirst: String
    get() = if (isEmpty()) this else this[0].lowercaseChar() + substring(1)

val String.capitalizedWords: String
    get() = split(" ").filter { it.isNotEmpty() }.joinToString(" ") { it.capitalizedFirst }

fun String.containsAny(needles: List<String>): Boolean = needles.any { contains(it) }

/** Replaces the first case-insensitive occurrence of [needle] with a space. */
fun String.removingCaseInsensitive(needle: String): String {
    val index = indexOf(needle, ignoreCase = true)
    if (index < 0) return this
    return (substring(0, index) + " " + substring(index + needle.length)).replace("  ", " ")
}

fun String.lowercasedSplit(separator: String): List<String> {
    val results = mutableListOf<String>()
    var remaining = this
    while (true) {
        val index = remaining.indexOf(separator, ignoreCase = true)
        if (index < 0) break
        results.add(remaining.substring(0, index))
        remaining = remaining.substring(index + separator.length)
    }
    results.add(remaining)
    return results
}

fun String.trimChars(chars: String): String = trim { it in chars }

fun naturalList(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    2 -> "${items[0]} and ${items[1]}"
    else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
}
