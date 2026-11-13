package com.monitorung.app.data.remote.api

import com.monitorung.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface MonitorUNGApi {
    @GET("api/status")
    suspend fun getSystemStatus(): Response<com.google.gson.JsonObject>


    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    @GET("api/users/me")
    suspend fun getMyProfile(@Header("Authorization") token: String): Response<UserProfileDto>

    @PUT("api/users/me")
    suspend fun updateMyProfile(
        @Header("Authorization") token: String,
        @Body request: ProfileUpdateRequest
    ): Response<UserProfileDto>

    // Round 27 — FCM device-token registration. The Android client posts
    // the latest FCM token here whenever Firebase refreshes it; the
    // server stores it in users.fcm_token. Send `{"token": ""}` (empty
    // string) to clear on sign-out so a returned device doesn't keep
    // receiving push for the previous user.
    @PUT("api/users/me/fcm-token")
    suspend fun registerFcmToken(
        @Header("Authorization") token: String,
        @Body body: FcmTokenRequest,
    ): Response<Unit>

    @PUT("api/users/me/password")
    suspend fun changeMyPassword(
        @Header("Authorization") token: String,
        @Body request: PasswordChangeRequest
    ): Response<Map<String, String>>

    @GET("api/schedules")
    suspend fun getSchedules(@Header("Authorization") token: String): Response<List<ScheduleDto>>

    @DELETE("api/schedules/{scheduleId}")
    suspend fun deleteSchedule(
        @Header("Authorization") token: String,
        @Path("scheduleId") scheduleId: String
    ): Response<Unit>

    @GET("api/schedules/directory")
    suspend fun getScheduleDirectory(
        @Header("Authorization") token: String
    ): Response<ScheduleDirectoryResponse>

    @POST("api/schedule-requests")
    suspend fun createScheduleRequest(
        @Header("Authorization") token: String,
        @Body request: ScheduleRequestCreateDto
    ): Response<ScheduleRequestDto>

    @GET("api/schedule-requests/mine")
    suspend fun getMyScheduleRequests(
        @Header("Authorization") token: String,
        @Query("status") status: String? = null
    ): Response<ScheduleRequestListDto>

    @GET("api/config/campus")
    suspend fun getCampusConfig(@Header("Authorization") token: String): Response<CampusConfigDto>

    @POST("api/logs/batch")
    suspend fun postBatchLogs(
        @Header("Authorization") token: String,
        @Body request: BatchLogRequest
    ): Response<BatchLogResponse>

}
