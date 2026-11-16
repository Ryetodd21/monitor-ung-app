package com.monitorung.app.data.repository

import com.monitorung.app.data.remote.dto.ScheduleDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleOwnerKeyTest {
    private val dto = ScheduleDto(
        id = "3e7f1420-7928-5e32-9ff9-0bfa7486da71",
        studentId = "e0d5ee4c-72f8-4350-b722-68a8b4fd03fb", // server students.id
        dayOfWeek = 6, startTime = "08:00", endTime = "10:00",
        campusId = null, room = "R.K 1.2", subjectNameRaw = "Photography",
        lecturerNameRaw = null, subjectId = "sub", lecturerId = null,
        isActive = true, subjectName = "Photography", lecturerName = null,
    )

    @Test
    fun `entity is keyed by the logged-in user id not the server student id`() {
        val owner = "efce0afd-9d39-4c37-8f0f-5ec635dc7bac" // users.id from login
        assertEquals(owner, dto.toEntity(owner).studentId)
        assertEquals("Photography", dto.toEntity(owner).subjectName)
    }
}
