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

    /**
     * Unlock high refresh rates (60 / 90 / 120 / 144 / 165) when the panel supports them.
     *
     * Important: we do *not* disable frame-rate power savings. Forcing a constant high Hz on
     * mid-range devices (e.g. Pixel 10a) while Compose cannot fill every frame causes visible
     * scroll judder. Flagships (Pixel 10) can sustain the rate; weaker GPUs need the system
     * to drop Hz under load for smooth scrolling.
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
            val sameRes = display.supportedModes.filter {
                it.physicalWidth == current.physicalWidth &&
                    it.physicalHeight == current.physicalHeight
            }
            // Prefer the highest rate at this resolution so 90/120/144/165 panels are available.
            val best = sameRes.maxByOrNull { it.refreshRate }
                ?: display.supportedModes.maxByOrNull { it.refreshRate }
                ?: return

            if (best.modeId != current.modeId) {
                val attrs = window.attributes
                attrs.preferredDisplayModeId = best.modeId
                window.attributes = attrs
                AppLog.d(
                    "Display mode preferred → ${best.physicalWidth}x${best.physicalHeight} " +
                        "@ ${"%.0f".format(best.refreshRate)} Hz (modeId=${best.modeId}); " +
                        "system may still lower Hz under load for smooth frames"
                )
            } else {
                AppLog.d(
                    "Display already at ${"%.0f".format(current.refreshRate)} Hz " +
                        "(${current.physicalWidth}x${current.physicalHeight})"
                )
            }
            // Leave isFrameRatePowerSavingsBalanced at the platform default (true) so Android
            // can fall back to 60 Hz when the GPU cannot keep up — critical on a-series chips.
        } catch (e: Exception) {
            AppLog.e("enableHighRefreshRate failed", e)
        }
    }
}
