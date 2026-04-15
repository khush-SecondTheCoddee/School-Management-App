package com.schoolmanagement.app.ui.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.schoolmanagement.app.ui.components.FeatureCard
import com.schoolmanagement.app.ui.components.LoadingState
import com.schoolmanagement.app.ui.shell.UserRole
import com.schoolmanagement.core.ResultState
import com.schoolmanagement.data.SchoolRepository

@Composable
fun DashboardScreen(role: UserRole) {
    val greeting = when (val state = SchoolRepository().getDashboardGreeting()) {
        is ResultState.Success -> state.value
        is ResultState.Error -> state.message
        ResultState.Loading -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Dashboard screen" },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Hello ${role.name}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (greeting == null) {
            LoadingState(label = "Loading dashboard")
        } else {
            FeatureCard(
                title = "Today at a glance",
                subtitle = greeting
            )
            FeatureCard(
                title = "Accessibility ready",
                subtitle = "UI labels include talkback descriptions and scalable typography."
            )
        }
    }
}
