package com.schoolmanagement.app.ui.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.schoolmanagement.app.ui.components.ErrorState
import com.schoolmanagement.app.ui.components.FeatureCard

@Composable
fun NotificationsScreen() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics { contentDescription = "Notifications screen" }
    ) {
        FeatureCard(
            title = "Unified alerts",
            subtitle = "Incidents, announcements, and attendance nudges in one queue."
        )
        ErrorState(
            message = "Could not load recent alerts.",
            onRetry = { }
        )
    }
}
