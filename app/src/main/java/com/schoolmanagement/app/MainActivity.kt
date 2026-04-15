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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.schoolmanagement.feature.auth.AuthBackendAdapter
import com.schoolmanagement.feature.auth.AuthGate
import com.schoolmanagement.feature.auth.AuthSessionStorage
import com.schoolmanagement.feature.attendance.AttendanceEntry
import com.schoolmanagement.feature.dashboard.DashboardEntry
import com.schoolmanagement.feature.notifications.NotificationsFeature
import com.schoolmanagement.feature.notifications.NotificationsInbox

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val adapter = remember {
                    AuthBackendAdapter(
                        auth = FirebaseAuth.getInstance(),
                        firestore = FirebaseFirestore.getInstance(),
                        storage = AuthSessionStorage(applicationContext)
                    )
                }
                AuthGate(adapter = adapter) {
                    AppShell(initialRoute = intent?.getStringExtra(EXTRA_NOTIFICATION_ROUTE) ?: NotificationRouteStore.latestRoute)
                }
            }
        }
    }

    companion object {
        const val EXTRA_NOTIFICATION_ROUTE = "notification_route"
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
private fun AppShell(initialRoute: String?) {
    var destination by remember { mutableStateOf(AppDestination.Dashboard) }
    var pendingRoute by remember(initialRoute) { mutableStateOf(initialRoute) }

    LaunchedEffect(initialRoute) {
        if (!initialRoute.isNullOrBlank()) {
            destination = AppDestination.Notifications
        }
    }

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
            pendingRoute?.let {
                Text(
                    text = "Deep link route: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

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
                AppDestination.Attendance -> AttendanceEntry()
                AppDestination.Homework -> Text("Homework feature module")
                AppDestination.Results -> Text("Results feature module")
                AppDestination.Notifications -> NotificationsInbox(
                    initialItems = NotificationsFeature.sampleInbox(),
                    onDeepLinkRoute = { route ->
                        pendingRoute = route
                        destination = when {
                            route.startsWith("attendance") -> AppDestination.Attendance
                            route.startsWith("homework") -> AppDestination.Homework
                            route.startsWith("results") -> AppDestination.Results
                            else -> AppDestination.Notifications
                        }
                    },
                )
            }
        }
    }
}
