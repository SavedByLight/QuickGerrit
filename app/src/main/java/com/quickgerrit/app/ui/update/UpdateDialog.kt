package com.quickgerrit.app.ui.update

import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.quickgerrit.app.BuildConfig
import com.quickgerrit.app.update.AppUpdater
import kotlinx.coroutines.launch

@Composable
fun UpdateCheckButton(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // User returned from unknown-sources settings; try install again if we still have a URI
    }

    fun ensureInstallPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        if (context.packageManager.canRequestPackageInstalls()) return true
        val intent = android.content.Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri()
        )
        installPermissionLauncher.launch(intent)
        return false
    }

    OutlinedButton(
        onClick = {
            if (!AppUpdater.isConfigured()) {
                status = "Update check not configured (GITHUB_REPO empty)"
                return@OutlinedButton
            }
            checking = true
            status = null
            scope.launch {
                val result = AppUpdater.checkForUpdate(context)
                checking = false
                result.fold(
                    onSuccess = { info ->
                        if (info == null) {
                            status = "You're on the latest version (${BuildConfig.VERSION_NAME})"
                        } else {
                            update = info
                            showDialog = true
                        }
                    },
                    onFailure = { e ->
                        status = "Check failed: ${e.message}"
                    }
                )
            }
        },
        enabled = !checking && !downloading,
        modifier = modifier.fillMaxWidth()
    ) {
        if (checking) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Checking…")
        } else {
            Icon(Icons.Default.SystemUpdate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Check for updates")
        }
    }

    status?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    if (showDialog && update != null) {
        val info = update!!
        AlertDialog(
            onDismissRequest = { if (!downloading) showDialog = false },
            icon = { Icon(Icons.Default.SystemUpdate, null) },
            title = { Text("Update available") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Version ${info.versionName} is available (you have ${BuildConfig.VERSION_NAME}).",
                        fontWeight = FontWeight.Medium
                    )
                    info.releaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Spacer(Modifier.height(12.dp))
                        Text("Release notes", style = MaterialTheme.typography.labelMedium)
                        Text(notes.take(800), style = MaterialTheme.typography.bodySmall)
                    }
                    if (downloading) {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Downloading…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!ensureInstallPermission()) {
                            status = "Allow install from this source, then try again"
                            return@Button
                        }
                        downloading = true
                        scope.launch {
                            val dl = AppUpdater.downloadApk(context, info)
                            downloading = false
                            dl.fold(
                                onSuccess = { uri ->
                                    showDialog = false
                                    AppUpdater.installApk(context, uri)
                                },
                                onFailure = { e ->
                                    status = "Download failed: ${e.message}"
                                    showDialog = false
                                }
                            )
                        }
                    },
                    enabled = !downloading
                ) {
                    Text(if (downloading) "Downloading…" else "Download & install")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            AppUpdater.openReleasePage(context, info)
                        },
                        enabled = !downloading
                    ) { Text("View on GitHub") }
                    TextButton(
                        onClick = { showDialog = false },
                        enabled = !downloading
                    ) { Text("Later") }
                }
            }
        )
    }
}

/**
 * Silently checks for updates once per process (e.g. from main screen).
 * Calls [onUpdateAvailable] when a newer release exists.
 */
@Composable
fun AutoUpdateChecker(
    onUpdateAvailable: (AppUpdater.UpdateInfo) -> Unit = {}
) {
    val context = LocalContext.current
    var checked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (checked || !AppUpdater.isConfigured()) return@LaunchedEffect
        checked = true
        val result = AppUpdater.checkForUpdate(context)
        result.getOrNull()?.let { onUpdateAvailable(it) }
    }
}
