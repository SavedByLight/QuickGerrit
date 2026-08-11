package com.quickgerrit.app.util

import android.util.Log
import com.quickgerrit.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val throwableMessage: String? = null
) {
    val timeFormatted: String
        get() = timeFormat.format(Date(timeMillis))

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }
}

/**
 * Thin wrapper around android.util.Log that also keeps an in-memory ring buffer
 * for the in-app Logs screen.
 *
 * Filter Logcat with: `adb logcat -s QuickGerrit:*`
 */
object AppLog {
    private const val TAG = "QuickGerrit"
    private const val MAX_ENTRIES = 500

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
            // Still keep a brief trail in release so the in-app viewer is useful
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
            }
        )
        buffer.add(entry)
        while (buffer.size > MAX_ENTRIES) {
            buffer.removeAt(0)
        }
        _entries.value = buffer.toList()
    }
}
