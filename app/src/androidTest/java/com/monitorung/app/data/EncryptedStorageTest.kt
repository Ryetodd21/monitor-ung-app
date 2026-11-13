package com.monitorung.app.data

import android.content.Context
import androidx.core.content.edit
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monitorung.app.util.SecurePrefs
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EncryptedStorageTest {
    @Test fun binaryDatabaseKeyPreservesPendingRowsOnReopen() {
        System.loadLibrary("sqlcipher")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "cipher-test-${UUID.randomUUID()}.db"
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE queue (status TEXT NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        try {
            SupportOpenHelperFactory(key.copyOf()).create(config).use { helper ->
                helper.writableDatabase.execSQL("INSERT INTO queue VALUES ('PENDING')")
            }
            SupportOpenHelperFactory(key.copyOf()).create(config).use { helper ->
                helper.readableDatabase.query("SELECT status FROM queue").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("PENDING", cursor.getString(0))
                }
            }
            val header = ByteArray(16)
            context.getDatabasePath(name).inputStream().use { assertEquals(16, it.read(header)) }
            assertFalse(String(header).startsWith("SQLite format 3"))
        } finally {
            key.fill(0)
            context.deleteDatabase(name)
        }
    }

    @Test fun encryptedPreferencesRemainReadableAcrossReopen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "prefs-test-${UUID.randomUUID()}"
        try {
            SecurePrefs.get(context, name).edit(commit = true) { putString("sample", "pending") }
            assertEquals("pending", SecurePrefs.get(context, name).getString("sample", null))
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
