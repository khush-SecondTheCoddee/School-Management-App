package com.schoolmanagement.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.schoolmanagement.app.BuildConfig
import com.schoolmanagement.app.ui.feature.attendance.AttendanceScreen
import com.schoolmanagement.app.ui.feature.dashboard.DashboardScreen
import com.schoolmanagement.app.ui.feature.homework.HomeworkScreen
import com.schoolmanagement.app.ui.feature.notifications.NotificationsScreen
import com.schoolmanagement.app.ui.feature.results.ResultsScreen
import com.schoolmanagement.app.ui.shell.AppPermission
import com.schoolmanagement.app.ui.shell.MenuItem
import com.schoolmanagement.app.ui.shell.RolePermissionMatrix
import com.schoolmanagement.app.ui.shell.UserRole

object AppRoutes {
    const val Dashboard = "dashboard"
    const val Attendance = "attendance"
    const val Homework = "homework"
    const val Results = "results"
    const val Notifications = "notifications"
}

private val allMenuItems = listOf(
    MenuItem(AppRoutes.Dashboard, "Dashboard", AppPermission.ViewDashboard, "D"),
    MenuItem(AppRoutes.Attendance, "Attendance", AppPermission.ManageAttendance, "A"),
    MenuItem(AppRoutes.Homework, "Homework", AppPermission.ViewHomework, "H"),
    MenuItem(AppRoutes.Results, "Results", AppPermission.ViewResults, "R"),
    MenuItem(AppRoutes.Notifications, "Alerts", AppPermission.ViewNotifications, "N")
)

@Composable
fun SchoolRootApp() {
    var role by rememberSaveable { mutableStateOf(UserRole.Teacher) }
    val menuItems = remember(role) { RolePermissionMatrix.menuFor(role, allMenuItems) }
    val startDestination = menuItems.firstOrNull()?.route ?: AppRoutes.Dashboard
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    LaunchedEffect(role) {
        if (currentRoute !in menuItems.map { it.route }) {
            navController.navigate(startDestination) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("School Management (${BuildConfig.APP_ENV})")
                },
                actions = {
                    RoleSwitcher(role = role, onRoleSelected = { role = it })
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val tabletLayout = maxWidth >= 840.dp

            if (tabletLayout) {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        modifier = Modifier.semantics {
                            contentDescription = "Primary navigation rail"
                        }
                    ) {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination?.route
                        menuItems.forEach { item ->
                            NavigationRailItem(
                                selected = currentDestination == item.route,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Text(
                                        text = item.iconText,
                                        modifier = Modifier.semantics {
                                            contentDescription = "${item.label} icon"
                                        }
                                    )
                                },
                                label = { Text(item.label) }
                            )
                        }
                    }
                    AppNavHost(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        navController = navController,
                        startDestination = startDestination,
                        role = role
                    )
                }
            } else {
                Scaffold(
                    bottomBar = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination?.route
                        NavigationBar {
                            menuItems.forEach { item ->
                                NavigationBarItem(
                                    selected = currentDestination == item.route,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Text(
                                            text = item.iconText,
                                            modifier = Modifier.semantics {
                                                contentDescription = "${item.label} icon"
                                            }
                                        )
                                    },
                                    label = { Text(item.label) }
                                )
                            }
                        }
                    }
                ) { contentPadding ->
                    AppNavHost(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        navController = navController,
                        startDestination = startDestination,
                        role = role
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleSwitcher(role: UserRole, onRoleSelected: (UserRole) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
        UserRole.entries.forEach { item ->
            androidx.compose.material3.FilterChip(
                selected = item == role,
                onClick = { onRoleSelected(item) },
                label = {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            )
        }
    }
}

@Composable
private fun AppNavHost(
    modifier: Modifier,
    navController: androidx.navigation.NavHostController,
    startDestination: String,
    role: UserRole
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = startDestination
    ) {
        composable(AppRoutes.Dashboard) { DashboardScreen(role = role) }
        composable(AppRoutes.Attendance) { AttendanceScreen() }
        composable(AppRoutes.Homework) { HomeworkScreen() }
        composable(AppRoutes.Results) { ResultsScreen() }
        composable(AppRoutes.Notifications) { NotificationsScreen() }
    }
}
