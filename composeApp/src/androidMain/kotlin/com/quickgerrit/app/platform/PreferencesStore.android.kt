package com.quickgerrit.app.platform

import android.content.Context
import java.io.File

/**
 * Holds application Context for preference file access.
 * Set from [com.quickgerrit.app.QuickGerritApp.onCreate].
 */
object AndroidContextHolder {
    @Volatile
    var appContext: Context? = null
}

actual fun readPreferencesJson(): String {
    val ctx = AndroidContextHolder.appContext ?: return ""
    val file = File(ctx.filesDir, "quickgerrit_accounts.json")
    return if (file.exists()) file.readText() else ""
}

actual fun writePreferencesJson(json: String) {
    val ctx = AndroidContextHolder.appContext ?: return
    File(ctx.filesDir, "quickgerrit_accounts.json").writeText(json)
}
