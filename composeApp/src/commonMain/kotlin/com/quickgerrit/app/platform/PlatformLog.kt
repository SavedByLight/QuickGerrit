package com.quickgerrit.app.platform

import com.quickgerrit.app.util.LogLevel

expect fun platformLog(level: LogLevel, tag: String, message: String, throwable: Throwable?)

expect fun saveLogToDisk(content: String): String
