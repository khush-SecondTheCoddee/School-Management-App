package com.schoolmanagement.data

import com.schoolmanagement.core.ResultState

class SchoolRepository {
    fun getDashboardGreeting(): ResultState<String> =
        ResultState.Success("Welcome to the School Management dashboard")
}
