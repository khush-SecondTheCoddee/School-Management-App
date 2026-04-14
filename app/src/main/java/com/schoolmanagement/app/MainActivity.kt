package com.schoolmanagement.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.schoolmanagement.feature.dashboard.DashboardEntry

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppShell()
            }
        }
    }
}

enum class AppDestination {
    Dashboard,
    Attendance,
    Homework,
    Results,
    Notifications
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppShell() {
    var destination by remember { mutableStateOf(AppDestination.Dashboard) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("School Management (${BuildConfig.APP_ENV})") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SingleChoiceSegmentedButtonRow {
                AppDestination.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = destination == item,
                        onClick = { destination = item },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AppDestination.entries.size
                        ),
                        label = { Text(item.name) }
                    )
                }
            }

            when (destination) {
                AppDestination.Dashboard -> DashboardEntry()
                AppDestination.Attendance -> Text("Attendance feature module")
                AppDestination.Homework -> Text("Homework feature module")
                AppDestination.Results -> Text("Results feature module")
                AppDestination.Notifications -> Text("Notifications feature module")
            }
        }
    }
}
