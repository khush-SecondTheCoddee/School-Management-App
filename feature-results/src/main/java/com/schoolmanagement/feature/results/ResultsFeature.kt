package com.schoolmanagement.feature.results

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class MarksEntryState {
    DRAFT,
    FINAL_READY,
    FINAL_SUBMITTED,
}

data class SubjectComponentInput(
    val subjectCode: String,
    val componentName: String,
    val maxMarks: Int,
    val enteredMarks: String = "",
)

data class StudentResultTrendPoint(
    val label: String,
    val percentage: Float,
)

@Composable
fun TeacherMarksEntryScreen(
    inputs: List<SubjectComponentInput>,
    onSaveDraft: (List<SubjectComponentInput>) -> Unit,
    onSubmitFinal: (List<SubjectComponentInput>) -> Unit,
) {
    var localInputs by remember(inputs) { mutableStateOf(inputs) }
    val hasInvalid = localInputs.any { entry ->
        val parsed = entry.enteredMarks.toFloatOrNull()
        parsed == null || parsed < 0 || parsed > entry.maxMarks
    }

    val state = when {
        hasInvalid -> MarksEntryState.DRAFT
        else -> MarksEntryState.FINAL_READY
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Teacher Marks Entry",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        items(localInputs.indices.toList()) { index ->
            val input = localInputs[index]
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "${input.subjectCode} • ${input.componentName}")
                    OutlinedTextField(
                        value = input.enteredMarks,
                        onValueChange = { nextValue ->
                            localInputs = localInputs.toMutableList().also { items ->
                                items[index] = input.copy(enteredMarks = nextValue)
                            }
                        },
                        label = { Text("Marks (max ${input.maxMarks})") },
                        isError = input.enteredMarks.isNotBlank() && run {
                            val parsed = input.enteredMarks.toFloatOrNull()
                            parsed == null || parsed > input.maxMarks
                        },
                    )
                }
            }
        }

        item {
            Text(
                text = "Status: ${state.name}",
                color = if (state == MarksEntryState.FINAL_READY) Color(0xFF1B5E20) else Color(0xFFB71C1C),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onSaveDraft(localInputs) }) {
                    Text("Save Draft")
                }
                Button(
                    onClick = { onSubmitFinal(localInputs) },
                    enabled = state == MarksEntryState.FINAL_READY,
                ) {
                    Text("Submit Final")
                }
            }
        }
    }
}

@Composable
fun StudentParentResultView(
    studentName: String,
    examName: String,
    overallPercentage: Float,
    grade: String,
    trendPoints: List<StudentResultTrendPoint>,
    onDownloadCsv: () -> Unit,
    onDownloadPdf: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "$studentName • $examName", style = MaterialTheme.typography.headlineSmall)
        Text(text = "Overall: $overallPercentage% ($grade)")

        ResultTrendChart(
            points = trendPoints,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color(0xFFF5F5F5)),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDownloadCsv, modifier = Modifier.width(150.dp)) {
                Text("Download CSV")
            }
            Button(onClick = onDownloadPdf, modifier = Modifier.width(150.dp)) {
                Text("Download PDF")
            }
        }
    }
}

@Composable
fun ResultTrendChart(
    points: List<StudentResultTrendPoint>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.padding(8.dp)) {
        if (points.size < 2) return@Canvas

        val stepX = size.width / (points.size - 1)
        val linePoints = points.mapIndexed { index, point ->
            val x = index * stepX
            val y = size.height - (point.percentage.coerceIn(0f, 100f) / 100f * size.height)
            Offset(x, y)
        }

        linePoints.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = Color(0xFF0D47A1),
                start = start,
                end = end,
                strokeWidth = 4f,
            )
        }

        linePoints.forEach { point ->
            drawCircle(
                color = Color(0xFF0D47A1),
                radius = 6f,
                center = point,
            )
        }
    }
}
