package com.monitorung.app.data.repository

import com.monitorung.app.data.remote.api.MonitorUNGApi
import com.monitorung.app.data.remote.dto.ScheduleRequestCreateDto
import com.monitorung.app.domain.repository.AuthRepository
import com.monitorung.app.domain.repository.ScheduleRequest
import com.monitorung.app.domain.repository.ScheduleRequestRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRequestRepositoryImpl @Inject constructor(
    private val api: MonitorUNGApi,
    private val authRepository: AuthRepository
) : ScheduleRequestRepository {

    override suspend fun createRequest(
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        room: String?,
        subjectId: String?,
        lecturerId: String?,
        subjectNameRaw: String?,
        lecturerNameRaw: String?,
        description: String
    ): Result<ScheduleRequest> {
        return try {
            val token = authRepository.getAccessToken() ?: return Result.failure(Exception("No token"))
            val dto = ScheduleRequestCreateDto(
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                room = room,
                subjectId = subjectId,
                lecturerId = lecturerId,
                subjectNameRaw = subjectNameRaw,
                lecturerNameRaw = lecturerNameRaw,
                description = description
            )
            val response = api.createScheduleRequest("Bearer $token", dto)
            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure(Exception("Create schedule request response body is empty"))
                Result.success(body.toDomain())
            } else {
                Result.failure(Exception("Failed to submit request: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMyRequests(status: String?): Result<List<ScheduleRequest>> {
        return try {
            val token = authRepository.getAccessToken() ?: return Result.failure(Exception("No token"))
            val response = api.getMyScheduleRequests("Bearer $token", status)
            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure(Exception("Get schedule requests response body is empty"))
                val requests = body.requests.map { it.toDomain() }
                Result.success(requests)
            } else {
                Result.failure(Exception("Failed to fetch requests: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun com.monitorung.app.data.remote.dto.ScheduleRequestDto.toDomain() = ScheduleRequest(
        id = id,
        requesterId = requesterId,
        studentId = studentId,
        requestType = requestType,
        description = description,
        status = status,
        adminNotes = adminNotes,
        processedBy = processedBy,
        processedAt = processedAt,
        createdAt = createdAt,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        room = room,
        subjectId = subjectId,
        lecturerId = lecturerId,
        subjectNameRaw = subjectNameRaw,
        lecturerNameRaw = lecturerNameRaw,
        campusId = campusId,
        subjectName = subjectName,
        lecturerName = lecturerName,
        studentName = studentName
    )
}
