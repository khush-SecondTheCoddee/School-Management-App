package com.schoolmanagement.app.ui.feature.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.schoolmanagement.app.ui.components.FeatureCard

@Composable
fun AttendanceScreen() {
    var className by remember { mutableStateOf("Grade 6 - A") }
    var period by remember { mutableStateOf("Period 1") }
    var teacherNote by remember { mutableStateOf("") }

    BoxWithConstraints {
        val tablet = maxWidth >= 840.dp

        if (tablet) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AttendanceForm(
                    className = className,
                    period = period,
                    teacherNote = teacherNote,
                    onClassNameChanged = { className = it },
                    onPeriodChanged = { period = it },
                    onTeacherNoteChanged = { teacherNote = it },
                    modifier = Modifier.weight(1f)
                )
                AttendanceSummary(
                    className = className,
                    period = period,
                    teacherNote = teacherNote,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AttendanceForm(
                    className = className,
                    period = period,
                    teacherNote = teacherNote,
                    onClassNameChanged = { className = it },
                    onPeriodChanged = { period = it },
                    onTeacherNoteChanged = { teacherNote = it }
                )
                AttendanceSummary(className, period, teacherNote)
            }
        }
    }
}

@Composable
private fun AttendanceForm(
    className: String,
    period: String,
    teacherNote: String,
    onClassNameChanged: (String) -> Unit,
    onPeriodChanged: (String) -> Unit,
    onTeacherNoteChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FeatureCard(
        title = "Staff attendance workflow",
        subtitle = "Responsive form for quick check-in and corrections.",
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = className,
                onValueChange = onClassNameChanged,
                label = { Text("Class section") },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Class section input" }
            )
            OutlinedTextField(
                value = period,
                onValueChange = onPeriodChanged,
                label = { Text("Period") },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Period input" }
            )
            OutlinedTextField(
                value = teacherNote,
                onValueChange = onTeacherNoteChanged,
                label = { Text("Teacher notes") },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Teacher notes input" }
            )
            Button(
                onClick = { },
                modifier = Modifier.semantics { contentDescription = "Submit attendance button" }
            ) {
                Text("Submit attendance")
            }
        }
    }
}

@Composable
private fun AttendanceSummary(
    className: String,
    period: String,
    teacherNote: String,
    modifier: Modifier = Modifier
) {
    FeatureCard(
        title = "Submission preview",
        subtitle = "Review before sending to SIS",
        modifier = modifier
    ) {
        Text("Class: $className")
        Text("Period: $period")
        Text("Notes: ${teacherNote.ifBlank { "No notes" }}")
    }
}
