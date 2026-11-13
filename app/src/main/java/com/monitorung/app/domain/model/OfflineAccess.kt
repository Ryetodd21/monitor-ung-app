package com.monitorung.app.domain.model

import java.io.IOException
import javax.net.ssl.SSLException
import retrofit2.HttpException

object OfflineAccess {
    const val DURATION_MS = 24L * 60 * 60 * 1000

    fun isValid(validatedAt: Long, now: Long): Boolean =
        validatedAt > 0 && now >= validatedAt && now - validatedAt < DURATION_MS

    fun isTemporaryFailure(error: Throwable): Boolean = when (error) {
        is SSLException -> false
        is IOException -> true
        is HttpException -> error.code() == 429 || error.code() in 500..599
        else -> false
    }
}
