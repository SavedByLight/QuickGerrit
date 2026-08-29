package com.quickgerrit.app.platform

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.quickgerrit.app.util.LogLevel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun platformLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
    when (level) {
        LogLevel.V -> if (throwable != null) Log.v(tag, message, throwable) else Log.v(tag, message)
        LogLevel.D -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
        LogLevel.I -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
        LogLevel.W -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        LogLevel.E -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}

actual fun saveLogToDisk(content: String): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val fileName = "quickgerrit-$stamp.log"
    val bytes = content.toByteArray(Charsets.UTF_8)
    // Prefer app-specific external files dir — no MediaStore Context needed at call site
    val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val dir = File(base, "QuickGerrit")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, fileName)
    FileOutputStream(file).use { it.write(bytes) }
    return file.absolutePath
}
