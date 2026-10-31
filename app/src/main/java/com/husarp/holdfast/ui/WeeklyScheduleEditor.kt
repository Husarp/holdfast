package com.husarp.holdfast.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.husarp.holdfast.data.QuietSchedule
import java.time.DayOfWeek

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyScheduleEditor(ranges: List<QuietSchedule>, change: (Int, List<QuietSchedule>) -> Unit) {
    var copyDay by remember { mutableStateOf<Int?>(null) }
    var targets by remember { mutableStateOf("") }
    Text("Ranges start on the listed day and can cross midnight. Equal times mean 24 hours. Empty days start no quiet ranges.")
    (1..7).forEach { day ->
        val rows = ranges.filter { day.toString() in it.days.split(",") }
        SettingsSection(DayOfWeek.of(day).name.lowercase().replaceFirstChar { it.uppercase() }) {
            if (rows.isEmpty()) Text("No quiet time")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEachIndexed { index, row ->
                    var edit by remember { mutableStateOf(false) }
                    InputChip(false, { edit = true }, label = { Text("${formatMinute(row.startMinute)}–${formatMinute(row.endMinute)}") },
                        trailingIcon = { IconButton(onClick = { change(day, rows.filterIndexed { i, _ -> i != index }) }) { Text("×") } })
                    if (edit) AlertDialog(onDismissRequest = { edit = false }, title = { Text("Time range") }, text = {
                        Column {
                            TimeChoice("Start", formatMinute(row.startMinute)) { time -> change(day, rows.toMutableList().apply { set(index, row.copy(startMinute = parseMinute(time)!!)) }) }
                            TimeChoice("End", formatMinute(row.endMinute)) { time -> change(day, rows.toMutableList().apply { set(index, row.copy(endMinute = parseMinute(time)!!)) }) }
                        }
                    }, confirmButton = { TextButton(onClick = { edit = false }) { Text("Done") } })
                }
            }
            Row {
                TextButton(onClick = { change(day, rows + QuietSchedule(packageName = "", days = "$day", startMinute = 1200, endMinute = 1260)) }) { Text("Add range") }
                TextButton(onClick = { copyDay = day; targets = "" }) { Text("Copy to other days…") }
            }
        }
    }
    copyDay?.let { source ->
        AlertDialog(onDismissRequest = { copyDay = null }, title = { Text("Copy ${DayOfWeek.of(source).name.lowercase()} ranges") }, text = {
            Column { Text("Replace ranges on the selected days."); DayChips(targets) { targets = it } }
        }, confirmButton = { TextButton(onClick = {
            targets.split(",").mapNotNull(String::toIntOrNull).filter { it != source }.forEach { day ->
                change(day, ranges.filter { source.toString() in it.days.split(",") })
            }; copyDay = null
        }) { Text("Copy") } }, dismissButton = { TextButton(onClick = { copyDay = null }) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyDigestEditor(value: String, change: (String) -> Unit) {
    val entries = com.husarp.holdfast.data.digestEntries(value)
    fun save(day: Int, times: List<java.time.LocalTime>) {
        change((entries.filter { it.first != day } + times.map { day to it }).distinct().sortedWith(compareBy({ it.first }, { it.second })).joinToString(",") { "${it.first}@${it.second}" })
    }
    (1..7).forEach { day ->
        Text(DayOfWeek.of(day).name.lowercase().replaceFirstChar { it.uppercase() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val times = entries.filter { it.first == day }.map { it.second }
            times.forEach { time ->
                TimeChoice("Delivery", time.toString()) { save(day, times - time + java.time.LocalTime.parse(it)) }
                TextButton(onClick = { save(day, times - time) }) { Text("×") }
            }
            var add by remember { mutableStateOf(false) }
            TextButton(onClick = { add = true }) { Text("Add time") }
            if (add) ClockDialog("12:00", { add = false }) { save(day, times + java.time.LocalTime.parse(it)) }
        }
    }
}
