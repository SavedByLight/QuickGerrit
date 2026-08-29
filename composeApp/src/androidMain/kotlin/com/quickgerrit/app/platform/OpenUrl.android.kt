package com.quickgerrit.app.platform

import android.content.Intent
import android.net.Uri

actual fun openUrl(url: String) {
    val ctx = AndroidContextHolder.appContext ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(intent)
}
