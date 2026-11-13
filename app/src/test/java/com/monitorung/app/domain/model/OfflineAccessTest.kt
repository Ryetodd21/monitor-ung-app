package com.monitorung.app.domain.model

import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class OfflineAccessTest {
    private val validatedAt = 1_800_000_000_000L

    @Test fun accessEndsExactlyAfter24Hours() {
        assertTrue(OfflineAccess.isValid(validatedAt, validatedAt))
        assertTrue(OfflineAccess.isValid(validatedAt, validatedAt + OfflineAccess.DURATION_MS - 1))
        assertFalse(OfflineAccess.isValid(validatedAt, validatedAt + OfflineAccess.DURATION_MS))
        assertFalse(OfflineAccess.isValid(validatedAt, validatedAt + OfflineAccess.DURATION_MS + 1))
    }

    @Test fun missingValidationAndClockRollbackDenyAccess() {
        assertFalse(OfflineAccess.isValid(0, validatedAt))
        assertFalse(OfflineAccess.isValid(validatedAt, validatedAt - 1))
    }

    @Test fun networkOutageAllowsFallbackButTlsFailureDoesNot() {
        assertTrue(OfflineAccess.isTemporaryFailure(IOException("offline")))
        assertFalse(OfflineAccess.isTemporaryFailure(SSLHandshakeException("certificate")))
        assertFalse(OfflineAccess.isTemporaryFailure(IllegalStateException("invalid response")))
    }

    @Test fun serverOutageAllowsFallbackButAuthRejectionDoesNot() {
        for (code in listOf(429, 500, 502, 503, 504, 530)) {
            assertTrue("HTTP $code", OfflineAccess.isTemporaryFailure(httpError(code)))
        }
        for (code in listOf(400, 401, 403, 404, 422)) {
            assertFalse("HTTP $code", OfflineAccess.isTemporaryFailure(httpError(code)))
        }
    }

    private fun httpError(code: Int) = HttpException(Response.error<Unit>(code, "".toResponseBody()))
}
