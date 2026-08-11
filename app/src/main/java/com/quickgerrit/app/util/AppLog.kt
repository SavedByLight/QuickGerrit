package com.quickgerrit.app.util

import android.util.Log
import com.quickgerrit.app.BuildConfig

/**
 * Thin wrapper around android.util.Log.
 * - Verbose / Debug only emitted in debug builds.
 * - Info / Warn / Error always emitted.
 * Use a consistent tag prefix so Logcat filters cleanly: `adb logcat -s QuickGerrit:*`
 */
object AppLog {
    private const val TAG = "QuickGerrit"

    fun v(message: String, tag: String = TAG) {
        if (BuildConfig.DEBUG) Log.v(tag, message)
    }

    fun d(message: String, tag: String = TAG) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(message: String, tag: String = TAG) {
        Log.i(tag, message)
    }

    fun w(message: String, throwable: Throwable? = null, tag: String = TAG) {
        if (throwable != null) Log.w(tag, message, throwable)
        else Log.w(tag, message)
    }

    fun e(message: String, throwable: Throwable? = null, tag: String = TAG) {
        if (throwable != null) Log.e(tag, message, throwable)
        else Log.e(tag, message)
    }
}
