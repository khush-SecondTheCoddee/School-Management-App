package com.schoolmanagement.app.ui.feature.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.schoolmanagement.app.ui.components.FeatureCard

@Composable
fun ResultsScreen() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics { contentDescription = "Results screen" }
    ) {
        FeatureCard(
            title = "Assessment trends",
            subtitle = "Color-safe progress cards and grade distributions."
        )
        FeatureCard(
            title = "Student progress",
            subtitle = "Supports large text and screen-reader friendly labels by default."
        )
    }
}
