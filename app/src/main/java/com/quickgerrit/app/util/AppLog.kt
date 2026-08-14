package com.quickgerrit.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.quickgerrit.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

enum class LogLevel { V, D, I, W, E }

data class LogEntry(
    val timeMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwableMessage: String? = null,
    val stackTrace: String? = null
) {
    val timeFormatted: String
        get() = timeFormat.format(Date(timeMillis))

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }
}

/**
 * Thin wrapper around android.util.Log that also keeps an in-memory buffer
 * for the in-app Logs screen and full-file export to Downloads/QuickGerrit.
 *
 * Filter Logcat with: `adb logcat -s QuickGerrit:*`
 */
object AppLog {
    private const val TAG = "QuickGerrit"
    /** Large enough to keep a full session; export always dumps the entire buffer. */
    private const val MAX_ENTRIES = 20_000

    private val buffer = CopyOnWriteArrayList<LogEntry>()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun v(message: String, tag: String = TAG) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message)
            append(LogLevel.V, tag, message)
        }
    }

    fun d(message: String, tag: String = TAG) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
            append(LogLevel.D, tag, message)
        } else {
            // Still keep a trail in release so the in-app viewer / export is useful
            append(LogLevel.D, tag, message)
        }
    }

    fun i(message: String, tag: String = TAG) {
        Log.i(tag, message)
        append(LogLevel.I, tag, message)
    }

    fun w(message: String, throwable: Throwable? = null, tag: String = TAG) {
        if (throwable != null) Log.w(tag, message, throwable)
        else Log.w(tag, message)
        append(LogLevel.W, tag, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null, tag: String = TAG) {
        if (throwable != null) Log.e(tag, message, throwable)
        else Log.e(tag, message)
        append(LogLevel.E, tag, message, throwable)
    }

    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
    }

    /** Full log text for every entry currently buffered (no truncation). */
    fun dumpFullText(): String = buildString {
        appendLine("QuickGerrit log export")
        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())}")
        appendLine("Entries: ${buffer.size}")
        appendLine("---")
        for (e in buffer) {
            append(e.timeFormatted)
            append(' ')
            append(e.level.name)
            append('/')
            append(e.tag)
            append(": ")
            append(e.message)
            e.throwableMessage?.let {
                append(" | ")
                append(it)
            }
            appendLine()
            e.stackTrace?.let {
                appendLine(it.trimEnd())
            }
        }
    }

    /**
     * Write the **full** in-memory log to:
     * `/storage/emulated/0/Downloads/QuickGerrit/quickgerrit-YYYYMMDD-HHMMSS.log`
     *
     * Uses MediaStore on API 29+ (correct under scoped storage) and a direct
     * file write on older APIs. Returns the absolute path or display path on success.
     */
    fun saveFullLogToDownloads(context: Context): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "quickgerrit-$stamp.log"
        val body = dumpFullText()
        val bytes = body.toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/QuickGerrit"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("Could not create log file in Downloads/QuickGerrit")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw Exception("Could not open log file for writing")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "/storage/emulated/0/Downloads/QuickGerrit/$fileName"
        }

        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, "QuickGerrit")
        if (!dir.exists() && !dir.mkdirs()) {
            throw Exception("Could not create ${dir.absolutePath}")
        }
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        return file.absolutePath
    }

    private fun append(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) {
        val entry = LogEntry(
            timeMillis = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwableMessage = throwable?.let {
                buildString {
                    append(it.javaClass.simpleName)
                    if (!it.message.isNullOrBlank()) append(": ").append(it.message)
                }
            },
            stackTrace = throwable?.stackTraceToString()
        )
        buffer.add(entry)
        while (buffer.size > MAX_ENTRIES) {
            buffer.removeAt(0)
        }
        _entries.value = buffer.toList()
    }
}
