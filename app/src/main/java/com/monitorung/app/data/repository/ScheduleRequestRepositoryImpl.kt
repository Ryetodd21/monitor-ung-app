package com.monitorung.app.data.repository

import com.monitorung.app.data.remote.api.MonitorUNGApi
import com.monitorung.app.data.remote.dto.ScheduleRequestCreateDto
import com.monitorung.app.domain.repository.AuthRepository
import com.monitorung.app.domain.repository.ScheduleRequest
import com.monitorung.app.domain.repository.ScheduleRequestRepository
import com.google.gson.JsonParser
import retrofit2.Response
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
        description: String,
        campusId: String?
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
                campusId = campusId,
                description = description
            )
            val response = api.createScheduleRequest("Bearer $token", dto)
            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure(Exception("Create schedule request response body is empty"))
                Result.success(body.toDomain())
            } else {
                // A bare "Failed to submit request: 422" told the student nothing.
                // FastAPI returns {"detail": ...} for both validation (422, a list
                // of field errors) and HTTPException (400/403/404, a string), so
                // surface the field names the server actually rejected.
                Result.failure(Exception(describeError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Turn a non-2xx `POST /api/schedule-requests` response into a message a
     * student can act on. Validation failures name the offending field; other
     * failures keep the status code as a fallback.
     */
    private fun describeError(response: Response<*>): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        val detail = raw
            ?.let { runCatching { JsonParser.parseString(it).asJsonObject.get("detail") }.getOrNull() }
        val fieldMessages = when {
            detail == null || detail.isJsonNull -> emptyList()
            detail.isJsonArray -> detail.asJsonArray.mapNotNull { element ->
                val obj = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
                val field = obj.get("loc")?.takeIf { it.isJsonArray }
                    ?.asJsonArray?.mapNotNull { it.asString }
                    ?.lastOrNull { it != "body" }
                val message = obj.get("msg")?.takeIf { it.isJsonPrimitive }?.asString
                when {
                    field != null && message != null -> "$field: $message"
                    message != null -> message
                    else -> null
                }
            }
            detail.isJsonPrimitive -> listOf(detail.asString)
            else -> emptyList()
        }
        return if (fieldMessages.isNotEmpty()) {
            "Request jadwal ditolak: ${fieldMessages.joinToString("; ")}"
        } else {
            "Request jadwal gagal dikirim (HTTP ${response.code()})"
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
