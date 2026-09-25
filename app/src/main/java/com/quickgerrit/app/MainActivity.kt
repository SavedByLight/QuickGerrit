package com.quickgerrit.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.quickgerrit.app.ui.navigation.QuickGerritNavGraph
import com.quickgerrit.app.ui.theme.QuickGerritTheme
import com.quickgerrit.app.util.AppLog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enableHighRefreshRate()
        setContent {
            QuickGerritTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    QuickGerritNavGraph()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply in case the system switched modes while paused (foldables, external display).
        enableHighRefreshRate()
    }

    /**
     * Prefer the highest refresh rate the panel supports at the current resolution
     * (60 / 90 / 120 / 144 / 165 Hz, etc.). Without this, many OEMs keep apps at 60 Hz.
     */
    private fun enableHighRefreshRate() {
        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            } ?: return

            val current = display.mode
            // Same physical resolution, highest Hz first — matches 60/90/120/144/165 panels.
            val best = display.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
                }
                .maxByOrNull { it.refreshRate }
                ?: display.supportedModes.maxByOrNull { it.refreshRate }
                ?: return

            if (best.modeId != current.modeId) {
                val attrs = window.attributes
                attrs.preferredDisplayModeId = best.modeId
                window.attributes = attrs
                AppLog.d(
                    "Display mode → ${best.physicalWidth}x${best.physicalHeight} " +
                        "@ ${"%.0f".format(best.refreshRate)} Hz (modeId=${best.modeId})"
                )
            } else {
                AppLog.d(
                    "Display already at ${"%.0f".format(current.refreshRate)} Hz " +
                        "(${current.physicalWidth}x${current.physicalHeight})"
                )
            }

            // Avoid aggressive frame-rate limiting when the device is on battery (API 35+).
            if (Build.VERSION.SDK_INT >= 35) {
                try {
                    @Suppress("NewApi")
                    window.isFrameRatePowerSavingsBalanced = false
                } catch (_: Throwable) {
                    // Older platform builds may lack the setter; ignore.
                }
            }
        } catch (e: Exception) {
            AppLog.e("enableHighRefreshRate failed", e)
        }
    }
}
