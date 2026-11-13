package com.monitorung.app.util

import android.content.Context
import android.util.AtomicFile
import androidx.core.util.readText
import androidx.core.util.writeText
import java.io.File
import java.util.UUID

/** App-scoped identifier; logout preserves it and uninstall resets it. */
@Synchronized
fun installationId(context: Context): String {
    val file = AtomicFile(File(context.filesDir, "installation_id"))
    if (file.baseFile.exists()) return UUID.fromString(file.readText()).toString()
    return UUID.randomUUID().toString().also { file.writeText(it) }
}
