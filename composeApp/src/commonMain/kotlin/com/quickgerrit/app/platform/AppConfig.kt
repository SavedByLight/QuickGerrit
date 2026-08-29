package com.quickgerrit.app.platform

expect object AppConfig {
    val DEBUG: Boolean
    val VERSION_NAME: String
    val VERSION_CODE: Int
    val GITHUB_REPO: String
}
