package com.monitorung.app.util

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class InstallationIdTest {
    @Test fun idIsAUuidAndSurvivesContextRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = installationId(context)
        assertEquals(first, UUID.fromString(first).toString())
        assertEquals(first, installationId(context.createPackageContext(context.packageName, 0)))
    }
}
