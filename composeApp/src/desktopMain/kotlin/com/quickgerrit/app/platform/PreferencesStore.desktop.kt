package com.quickgerrit.app.platform

import java.io.File

private fun prefsFile(): File {
    val home = System.getProperty("user.home") ?: "."
    val dir = File(home, ".quickgerrit").also { it.mkdirs() }
    return File(dir, "accounts.json")
}

actual fun readPreferencesJson(): String {
    val f = prefsFile()
    return if (f.exists()) f.readText() else ""
}

actual fun writePreferencesJson(json: String) {
    prefsFile().writeText(json)
}
