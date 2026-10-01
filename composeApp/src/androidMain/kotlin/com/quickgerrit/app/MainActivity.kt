package com.quickgerrit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.quickgerrit.app.ui.navigation.QuickGerritNavGraph
import com.quickgerrit.app.ui.theme.QuickGerritTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Do not lock preferredDisplayModeId. On mid-range devices (e.g. Pixel 10a),
        // forcing a constant high refresh rate while Compose cannot fill every frame
        // causes scroll judder. Flagships (Pixel 10) look fine either way; weaker GPUs
        // need Android's adaptive refresh so Hz can drop under load. Touch scrolling
        // still boosts to 90/120 on modern Pixels when the GPU can keep up.
        setContent {
            QuickGerritTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    QuickGerritNavGraph()
                }
            }
        }
    }
}
