package com.rork.thinksortandroid.services

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.rork.thinksortandroid.domain.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class CalendarEvent(
    val id: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val isAllDay: Boolean,
)

/** Read-only calendar access so ThinkSort knows how much free time you have. Never writes. */
object CalendarService {
    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun reload(context: Context) {
        if (!hasPermission(context)) {
            _events.value = emptyList()
            return
        }
        _events.value = withContext(Dispatchers.IO) { query(context) }
    }

    fun clear() {
        _events.value = emptyList()
    }

    private fun query(context: Context): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        val start = Dates.startOfDay(now)
        val end = Dates.addDays(start, 2)
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, start)
        ContentUris.appendId(builder, end)
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val result = mutableListOf<CalendarEvent>()
        try {
            context.contentResolver.query(builder.build(), projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(
                        CalendarEvent(
                            id = cursor.getLong(0),
                            title = cursor.getString(1) ?: "Event",
                            start = cursor.getLong(2),
                            end = cursor.getLong(3),
                            isAllDay = cursor.getInt(4) == 1,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("Calendar", "Could not read calendar: ${e.javaClass.simpleName}")
        }
        return result
    }

    fun eventsOn(day: Long): List<CalendarEvent> = _events.value.filter { Dates.isSameDay(it.start, day) }

    fun minutesUntilNextEvent(now: Long): Int? {
        val next = _events.value.firstOrNull { !it.isAllDay && it.start > now } ?: return null
        return ((next.start - now) / 60_000L).toInt()
    }
}
