package com.schoolmanagement.app.ui.feature.homework

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.schoolmanagement.app.ui.components.EmptyState
import com.schoolmanagement.app.ui.components.FeatureCard

@Composable
fun HomeworkScreen() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics { contentDescription = "Homework screen" }
    ) {
        FeatureCard(
            title = "Homework hub",
            subtitle = "Assignments, due dates, and attachment status"
        )
        EmptyState(
            title = "No homework due today",
            description = "Teachers can post new tasks and families will receive alerts."
        )
    }
}
