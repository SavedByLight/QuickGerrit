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
        setContent {
            QuickGerritTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    QuickGerritNavGraph()
                }
            }
        }
    }
}
