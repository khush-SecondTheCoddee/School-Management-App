package com.schoolmanagement.app.ui.shell

import androidx.compose.runtime.Immutable

enum class UserRole {
    Admin,
    Teacher,
    Student,
    Guardian
}

enum class AppPermission {
    ViewDashboard,
    ManageAttendance,
    ViewHomework,
    ViewResults,
    ViewNotifications
}

@Immutable
data class MenuItem(
    val route: String,
    val label: String,
    val permission: AppPermission,
    val iconText: String
)

object RolePermissionMatrix {
    private val permissions = mapOf(
        UserRole.Admin to setOf(
            AppPermission.ViewDashboard,
            AppPermission.ManageAttendance,
            AppPermission.ViewHomework,
            AppPermission.ViewResults,
            AppPermission.ViewNotifications
        ),
        UserRole.Teacher to setOf(
            AppPermission.ViewDashboard,
            AppPermission.ManageAttendance,
            AppPermission.ViewHomework,
            AppPermission.ViewResults,
            AppPermission.ViewNotifications
        ),
        UserRole.Student to setOf(
            AppPermission.ViewDashboard,
            AppPermission.ViewHomework,
            AppPermission.ViewResults,
            AppPermission.ViewNotifications
        ),
        UserRole.Guardian to setOf(
            AppPermission.ViewDashboard,
            AppPermission.ViewHomework,
            AppPermission.ViewResults,
            AppPermission.ViewNotifications
        )
    )

    fun menuFor(role: UserRole, allItems: List<MenuItem>): List<MenuItem> {
        val allowed = permissions[role].orEmpty()
        return allItems.filter { item -> item.permission in allowed }
    }
}
