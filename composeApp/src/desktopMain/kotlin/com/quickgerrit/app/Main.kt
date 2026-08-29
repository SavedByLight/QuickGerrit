package com.quickgerrit.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.quickgerrit.app.ui.theme.QuickGerritTheme
import com.quickgerrit.app.ui.navigation.QuickGerritNavGraph

fun main() = application {
    val state = rememberWindowState(width = 1280.dp, height = 800.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "QuickGerrit",
        state = state
    ) {
        QuickGerritTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                QuickGerritNavGraph()
            }
        }
    }
}
