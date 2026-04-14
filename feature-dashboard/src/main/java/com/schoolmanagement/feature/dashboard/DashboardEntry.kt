package com.schoolmanagement.feature.dashboard

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.schoolmanagement.core.AppLogger
import com.schoolmanagement.core.ResultState
import com.schoolmanagement.data.SchoolRepository

@Composable
fun DashboardEntry() {
    val greeting = when (val state = SchoolRepository().getDashboardGreeting()) {
        is ResultState.Success -> state.value
        is ResultState.Error -> state.message
        ResultState.Loading -> "Loading dashboard..."
    }
    AppLogger.info("DashboardEntry", greeting)
    Text(text = greeting)
}
