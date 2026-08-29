package com.quickgerrit.app.platform

import java.awt.Desktop
import java.net.URI

actual fun openUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    } catch (t: Throwable) {
        System.err.println("openUrl failed: $t")
    }
}
