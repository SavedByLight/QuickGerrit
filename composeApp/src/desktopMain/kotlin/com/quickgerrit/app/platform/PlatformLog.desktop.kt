package com.quickgerrit.app.platform

import com.quickgerrit.app.util.LogLevel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun platformLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
    val prefix = "[${level.name}/$tag]"
    if (throwable != null) {
        System.err.println("$prefix $message")
        throwable.printStackTrace()
    } else {
        println("$prefix $message")
    }
}

actual fun saveLogToDisk(content: String): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val home = System.getProperty("user.home") ?: "."
    val dir = File(home, "Downloads/QuickGerrit").also { it.mkdirs() }
    val file = File(dir, "quickgerrit-$stamp.log")
    file.writeText(content)
    return file.absolutePath
}
