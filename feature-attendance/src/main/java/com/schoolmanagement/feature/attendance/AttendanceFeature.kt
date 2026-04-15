package com.schoolmanagement.feature.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class AttendanceStatus {
    Present,
    Late,
    Absent
}

private data class StudentAttendance(
    val id: String,
    val name: String,
    val rollNumber: String
)

private val sampleClassStudents = mapOf(
    "Grade 6 - A" to listOf(
        StudentAttendance("s-601", "Aarav Sharma", "01"),
        StudentAttendance("s-602", "Mia Johnson", "02"),
        StudentAttendance("s-603", "Noah Lee", "03"),
        StudentAttendance("s-604", "Priya Das", "04")
    ),
    "Grade 6 - B" to listOf(
        StudentAttendance("s-605", "Liam Brown", "01"),
        StudentAttendance("s-606", "Emma Wilson", "02"),
        StudentAttendance("s-607", "Rohan Patel", "03")
    ),
    "Grade 7 - A" to listOf(
        StudentAttendance("s-701", "Olivia Martin", "01"),
        StudentAttendance("s-702", "James White", "02"),
        StudentAttendance("s-703", "Saanvi Gupta", "03")
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceEntry(modifier: Modifier = Modifier) {
    val classNames = remember { sampleClassStudents.keys.toList() }
    var selectedClass by remember { mutableStateOf(classNames.first()) }
    val statusByStudent = remember { mutableStateMapOf<String, AttendanceStatus>() }

    val students = sampleClassStudents[selectedClass].orEmpty()

    fun applyBulkStatus(status: AttendanceStatus) {
        students.forEach { student ->
            statusByStudent[student.id] = status
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Teacher Attendance Marking")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            classNames.forEach { className ->
                FilterChip(
                    selected = selectedClass == className,
                    onClick = { selectedClass = className },
                    label = { Text(className) }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Bulk actions")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { applyBulkStatus(AttendanceStatus.Present) }) {
                        Text("Mark all present")
                    }
                    OutlinedButton(onClick = { applyBulkStatus(AttendanceStatus.Absent) }) {
                        Text("Mark all absent")
                    }
                    OutlinedButton(onClick = { applyBulkStatus(AttendanceStatus.Late) }) {
                        Text("Mark all late")
                    }
                }
                TextButton(onClick = { statusByStudent.clear() }) {
                    Text("Clear class marks")
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(students, key = { it.id }) { student ->
                val selectedStatus = statusByStudent[student.id] ?: AttendanceStatus.Present
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${student.rollNumber}. ${student.name}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AttendanceStatus.entries.forEach { status ->
                                Row {
                                    RadioButton(
                                        selected = selectedStatus == status,
                                        onClick = { statusByStudent[student.id] = status }
                                    )
                                    Text(status.name)
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(onClick = { /* TODO: hook to repository */ }) {
            Text("Submit attendance")
        }
    }
}
