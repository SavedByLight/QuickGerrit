package com.quickgerrit.app.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quickgerrit.app.platform.AppConfig
import com.quickgerrit.app.update.AppUpdater
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    onDismiss: () -> Unit,
    autoCheck: Boolean = true
) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runCheck() {
        scope.launch {
            checking = true
            error = null
            status = null
            update = null
            if (!AppUpdater.isConfigured()) {
                error = "Update checks are not configured (GITHUB_REPO missing)."
                checking = false
                return@launch
            }
            val result = AppUpdater.checkForUpdate()
            checking = false
            result.fold(
                onSuccess = { info ->
                    if (info == null) {
                        status = "You're on the latest version (${AppConfig.VERSION_NAME})"
                    } else {
                        update = info
                    }
                },
                onFailure = { t ->
                    error = t.message ?: "Update check failed"
                }
            )
        }
    }

    LaunchedEffect(autoCheck) {
        if (autoCheck) runCheck()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check for updates") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    checking -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(8.dp))
                        Text("Checking GitHub Releases…")
                    }
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    update != null -> {
                        val info = update!!
                        Text("Version ${info.versionName} is available (you have ${AppConfig.VERSION_NAME}).")
                        Spacer(Modifier.height(8.dp))
                        info.releaseNotes?.takeIf { it.isNotBlank() }?.let {
                            Text("Release notes:", style = MaterialTheme.typography.labelLarge)
                            Text(it.take(1500), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    status != null -> Text(status!!)
                    else -> Text("Tap Check to look for updates.")
                }
            }
        },
        confirmButton = {
            val info = update
            if (info != null) {
                TextButton(onClick = {
                    AppUpdater.openUpdate(info)
                    onDismiss()
                }) { Text("Download") }
            } else if (!checking) {
                TextButton(onClick = { runCheck() }) { Text("Check") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * Button used on the Accounts screen to manually check for updates.
 */
@Composable
fun UpdateCheckButton(
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                if (!AppUpdater.isConfigured()) {
                    status = "Update check not configured (GITHUB_REPO empty)"
                    return@OutlinedButton
                }
                checking = true
                status = null
                scope.launch {
                    val result = AppUpdater.checkForUpdate()
                    checking = false
                    result.fold(
                        onSuccess = { info ->
                            if (info == null) {
                                status = "You're on the latest version (${AppConfig.VERSION_NAME})"
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
            enabled = !checking,
            modifier = Modifier.fillMaxWidth()
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
    }

    val info = update
    if (showDialog && info != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Update available") },
            text = {
                Column {
                    Text("Version ${info.versionName} is available (you have ${AppConfig.VERSION_NAME}).")
                    Spacer(Modifier.height(8.dp))
                    info.releaseNotes?.takeIf { it.isNotBlank() }?.let {
                        Text(it.take(1000), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AppUpdater.openUpdate(info)
                    showDialog = false
                }) { Text("Download") }
            },
            dismissButton = {
                Row {
                    info.htmlUrl?.let { url ->
                        TextButton(onClick = {
                            com.quickgerrit.app.platform.openUrl(url)
                        }) { Text("View on GitHub") }
                    }
                    TextButton(onClick = { showDialog = false }) { Text("Later") }
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
    var checked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (checked || !AppUpdater.isConfigured()) return@LaunchedEffect
        checked = true
        val result = AppUpdater.checkForUpdate()
        result.getOrNull()?.let { onUpdateAvailable(it) }
    }
}
