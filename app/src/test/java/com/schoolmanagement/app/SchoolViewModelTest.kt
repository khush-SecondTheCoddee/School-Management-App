package com.schoolmanagement.app

import com.schoolmanagement.core.ResultState
import com.schoolmanagement.data.SchoolRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class SchoolViewModelTest {

    @Test
    fun `dashboard greeting comes from repository module`() {
        val state = SchoolRepository().getDashboardGreeting()

        val value = (state as ResultState.Success).value
        assertEquals("Welcome to the School Management dashboard", value)
    }
}
