package com.quickgerrit.app.platform

expect fun readPreferencesJson(): String
expect fun writePreferencesJson(json: String)
