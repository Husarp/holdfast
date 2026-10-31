package com.husarp.holdfast.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun OptionRow(label: String, help: String? = null, control: @Composable () -> Unit) {
    ListItem(headlineContent = { Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f, fill = false))
        if (help != null) HelpButton(label, help)
    } }, trailingContent = control)
}

@Composable
fun DayChips(value: String, change: (String) -> Unit) {
    val selected = value.split(",").mapNotNull(String::toIntOrNull)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..7).forEach { day ->
            FilterChip(day in selected, { change((if (day in selected) selected - day else selected + day).sorted().joinToString(",")) },
                label = { Text(DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault()), Modifier.wrapContentWidth(unbounded = true), maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockDialog(initial: String, close: () -> Unit, save: (String) -> Unit) {
    val minute = parseMinute(initial) ?: 480
    val state = rememberTimePickerState(minute / 60, minute % 60, DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(onDismissRequest = close, title = { Text("Choose time") },
        text = { TimePicker(state) }, confirmButton = { TextButton(onClick = { save(formatMinute(state.hour * 60 + state.minute)); close() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
fun TimeChoice(label: String, value: String, change: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    val minute = parseMinute(value) ?: 0
    val calendar = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, minute / 60); set(java.util.Calendar.MINUTE, minute % 60) }
    OutlinedButton(onClick = { show = true }) { Text("$label: ${DateFormat.getTimeFormat(LocalContext.current).format(calendar.time)}") }
    if (show) ClockDialog(value, { show = false }, change)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MinuteChoices(label: String, value: String, change: (String) -> Unit, emptyLabel: String? = null, allowZero: Boolean = false) {
    var custom by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    Text(label, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (emptyLabel != null) FilterChip(value.isEmpty(), { change("") }, label = { Text(emptyLabel) })
        (if (allowZero) listOf(0, 5, 15, 30, 60, 180) else listOf(5, 15, 30, 60, 180)).forEach { minutes ->
            FilterChip(value == minutes.toString(), { change(minutes.toString()) }, label = { Text("$minutes min") })
        }
        FilterChip(value.isNotEmpty() && value.toIntOrNull() !in listOf(0, 5, 15, 30, 60, 180), { input = value; custom = true }, label = { Text(if (value.isNotEmpty() && value.toIntOrNull() !in listOf(0, 5, 15, 30, 60, 180)) "$value min" else "Custom…") })
    }
    if (custom) AlertDialog(onDismissRequest = { custom = false }, title = { Text(label) }, text = {
        OutlinedTextField(input, { input = it }, label = { Text("Minutes") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }, confirmButton = { TextButton(enabled = (input.toIntOrNull() ?: -1) >= if (allowZero) 0 else 1, onClick = { change(input.toInt().toString()); custom = false }) { Text("OK") } }, dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancel") } })
}
