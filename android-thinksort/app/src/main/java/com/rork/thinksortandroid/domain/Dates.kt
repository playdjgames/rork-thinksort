package com.rork.thinksortandroid.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Small java.time helpers that work on epoch-millis, the storage format for all dates. */
object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun zoned(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(zone)
    fun localDate(millis: Long): LocalDate = zoned(millis).toLocalDate()
    fun toMillis(dateTime: LocalDateTime): Long = dateTime.atZone(zone).toInstant().toEpochMilli()
    fun toMillis(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfDay(millis: Long): Long = toMillis(localDate(millis))
    fun addDays(millis: Long, days: Long): Long = zoned(millis).plusDays(days).toInstant().toEpochMilli()
    fun addMonths(millis: Long, months: Long): Long = zoned(millis).plusMonths(months).toInstant().toEpochMilli()
    fun addMinutes(millis: Long, minutes: Long): Long = millis + minutes * 60_000L

    fun atTime(hour: Int, minute: Int, ofDay: Long): Long =
        toMillis(LocalDateTime.of(localDate(ofDay), LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))))

    fun hour(millis: Long): Int = zoned(millis).hour
    fun minutesOfDay(millis: Long): Int = zoned(millis).let { it.hour * 60 + it.minute }
    fun dayOfWeek(millis: Long): DayOfWeek = zoned(millis).dayOfWeek

    fun daysBetween(from: Long, to: Long): Long = ChronoUnit.DAYS.between(localDate(from), localDate(to))

    fun isSameDay(a: Long, b: Long): Boolean = localDate(a) == localDate(b)
    fun isToday(millis: Long, now: Long = System.currentTimeMillis()): Boolean = isSameDay(millis, now)
    fun isTomorrow(millis: Long, now: Long = System.currentTimeMillis()): Boolean = localDate(millis) == localDate(now).plusDays(1)
    fun isYesterday(millis: Long, now: Long = System.currentTimeMillis()): Boolean = localDate(millis) == localDate(now).minusDays(1)

    private val timeFormatter: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
    private val mediumDate: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

    fun time(millis: Long): String = zoned(millis).format(timeFormatter)
    fun weekday(millis: Long): String = zoned(millis).format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
    fun monthDay(millis: Long): String = zoned(millis).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    fun weekdayMonthDay(millis: Long): String = zoned(millis).format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))
    fun weekdayShortMonthDay(millis: Long): String = zoned(millis).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
    fun weekdayLongMonthShortDay(millis: Long): String = zoned(millis).format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
    fun abbreviated(millis: Long): String = zoned(millis).format(mediumDate)
    fun abbreviatedWithTime(millis: Long): String = "${abbreviated(millis)}, ${time(millis)}"

    fun minutesLabel(minutesOfDay: Int): String =
        LocalTime.of(minutesOfDay / 60, minutesOfDay % 60).format(timeFormatter)
}
