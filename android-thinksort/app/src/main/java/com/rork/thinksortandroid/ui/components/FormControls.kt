package com.rork.thinksortandroid.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rork.thinksortandroid.domain.Dates
import com.rork.thinksortandroid.ui.theme.Think
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

fun pickDate(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val date = Dates.localDate(initial)
    DatePickerDialog(context, { _, year, month, day ->
        val time = Dates.zoned(initial).toLocalTime()
        onPicked(Dates.toMillis(LocalDateTime.of(LocalDate.of(year, month + 1, day), time)))
    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
}

fun pickTime(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val zoned = Dates.zoned(initial)
    TimePickerDialog(context, { _, hour, minute ->
        onPicked(Dates.toMillis(LocalDateTime.of(zoned.toLocalDate(), LocalTime.of(hour, minute))))
    }, zoned.hour, zoned.minute, DateFormat.is24HourFormat(context)).show()
}

/** A settings-style row with a label and a trailing switch. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, supporting: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = Think.colors.inkSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Think.colors.accent,
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                uncheckedTrackColor = Think.colors.surfaceMuted,
                uncheckedBorderColor = Think.colors.hairline,
                uncheckedThumbColor = Think.colors.inkTertiary,
            ),
        )
    }
}

/** A settings-style row with a label and a trailing value; tappable when [onClick] is set. */
@Composable
fun ValueRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: androidx.compose.ui.graphics.Color = Think.colors.inkSecondary, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Think.colors.ink, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
    }
}

/** Horizontally scrolling single-select chips. */
@Composable
fun <T> ChipPicker(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            ChoiceChip(label(option), option == selected, { onSelect(option) })
        }
    }
}

/** Day + optional time controls shared by the item and task editors. */
@Composable
fun WhenEditor(
    hasDate: Boolean,
    date: Long,
    hasTime: Boolean,
    onHasDate: (Boolean) -> Unit,
    onDate: (Long) -> Unit,
    onHasTime: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    SwitchRow("Give it a day", hasDate, onHasDate)
    if (hasDate) {
        RowDivider()
        ValueRow("Day", Dates.weekdayLongMonthShortDay(date), valueColor = Think.colors.accent, onClick = { pickDate(context, date, onDate) })
        RowDivider()
        SwitchRow("Set a time", hasTime, onHasTime)
        if (hasTime) {
            RowDivider()
            ValueRow("Time", Dates.time(date), valueColor = Think.colors.accent, onClick = { pickTime(context, date, onDate) })
        }
    }
}
