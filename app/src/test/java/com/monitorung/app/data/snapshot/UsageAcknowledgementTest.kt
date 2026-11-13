package com.monitorung.app.data.snapshot

import com.monitorung.app.data.local.dao.AppUsageLogDao
import com.monitorung.app.data.local.entity.AppUsageLogEntity
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAcknowledgementTest {
    @Test
    fun `ACK only changes the exact snapshot even when timestamps collide`() = runBlocking {
        val sent = AppUsageLogEntity(id = 1, studentId = "one", logDate = "2026-11-13",
            logTimestamp = 100, totalScreenTimeMinutes = 10, socialMediaTimeMinutes = 0,
            gameTimeMinutes = 0, unlockCount = 0, usedDuringClass = true, scheduleId = "class-one")
        var stored = sent.copy(totalScreenTimeMinutes = 20)
        val unused = Proxy.newProxyInstance(AppUsageLogDao::class.java.classLoader,
            arrayOf(AppUsageLogDao::class.java)) { _, method, _ -> error("Unexpected ${method.name}") } as AppUsageLogDao
        val dao = object : AppUsageLogDao by unused {
            override suspend fun getByDate(studentId: String, date: String, scheduleId: String?) = stored
            override suspend fun markSyncedIds(ids: List<Long>) {
                if (stored.id in ids) stored = stored.copy(syncStatus = "SYNCED")
            }
            override suspend fun markSynced(snapshots: List<AppUsageLogEntity>) =
                super<AppUsageLogDao>.markSynced(snapshots)
        }
        dao.markSynced(listOf(sent))
        assertEquals("PENDING", stored.syncStatus)
        dao.markSynced(listOf(stored))
        assertEquals("SYNCED", stored.syncStatus)
        stored = sent.copy(id = 2)
        dao.markSynced(listOf(sent))
        assertEquals("PENDING", stored.syncStatus)
    }
}
