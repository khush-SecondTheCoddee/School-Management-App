package com.schoolmanagement.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SchoolViewModelTest {

    @Test
    fun `dashboard metrics are computed from in-memory state`() {
        val vm = SchoolViewModel()

        assertEquals(96, vm.avgAttendance())
        assertEquals(3.866, vm.avgGpa(), 0.01)
        assertEquals(4.766, vm.avgTeacherScore(), 0.01)
    }

    @Test
    fun `publish bulletin prepends new announcement`() {
        val vm = SchoolViewModel()

        vm.addDailyAnnouncement()

        assertEquals("Daily Bulletin", vm.announcements.first().title)
    }
}
