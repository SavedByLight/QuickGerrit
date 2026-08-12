package com.quickgerrit.app.ui.changes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quickgerrit.app.data.model.ChangeInfo
import com.quickgerrit.app.update.AppUpdater
import com.quickgerrit.app.ui.update.AutoUpdateChecker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesScreen(
    viewModel: ChangesViewModel,
    onOpenChange: (String) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val state by viewModel.ui.collectAsState()
    var pendingUpdate by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    // Silent check once per session against GitHub Releases
    AutoUpdateChecker { info ->
        pendingUpdate = info
    }

    Scaffold(
        floatingActionButton = {
            if (state.hasAccounts) {
                FloatingActionButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create change")
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("QuickGerrit")
                        state.activeAccount?.let {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenProjects) {
                        Icon(Icons.Default.Folder, "Projects")
                    }
                    IconButton(onClick = onOpenAccounts) {
                        Icon(Icons.Default.ManageAccounts, "Accounts")
                    }
                    IconButton(onClick = onOpenLogs) {
                        Icon(Icons.Default.BugReport, "Logs")
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Banner when a newer release is available
            pendingUpdate?.let { info ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clickable {
                            // Navigate user to Accounts → Check for updates for full flow
                            onOpenAccounts()
                        }
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SystemUpdate, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Update available: v${info.versionName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Tap to open Accounts and install",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { pendingUpdate = null }) {
                            Icon(Icons.Default.Close, "Dismiss")
                        }
                    }
                }
            }

            if (!state.hasAccounts) {
                EmptyAccountsPrompt(onOpenAccounts)
                return@Column
            }

            // Tabs
            TabRow(selectedTabIndex = ChangeTab.entries.indexOf(state.tab)) {
                ChangeTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label) }
                    )
                }
            }

            // Search
            OutlinedTextField(
                value = state.search,
                onValueChange = { viewModel.setSearch(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Extra query (owner:self, project:…)") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Search, null)
                    }
                }
            )

            when {
                state.isLoading && state.changes.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.load() }) { Text("Retry") }
                        }
                    }
                }
                state.changes.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No changes found")
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.changes, key = { it.id }) { change ->
                            ChangeCard(change = change, onClick = { onOpenChange(change.id) })
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateChangeDialog(
            creating = state.creating,
            error = state.createError,
            onDismiss = {
                showCreate = false
                viewModel.clearCreateResult()
            },
            onCreate = { project, branch, subject, topic, wip ->
                viewModel.createChange(project, branch, subject, topic, wip) { id ->
                    showCreate = false
                    viewModel.clearCreateResult()
                    onOpenChange(id)
                }
            }
        )
    }
}

@Composable
private fun CreateChangeDialog(
    creating: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (project: String, branch: String, subject: String, topic: String, wip: Boolean) -> Unit
) {
    var project by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("master") }
    var subject by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var wip by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = { Text("Create change") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = project,
                    onValueChange = { project = it },
                    label = { Text("Project") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !creating
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Branch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !creating
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !creating,
                    minLines = 2
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !creating
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = wip, onCheckedChange = { wip = it }, enabled = !creating)
                    Text("Work in progress")
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(project, branch, subject, topic, wip) },
                enabled = !creating && project.isNotBlank() && subject.isNotBlank()
            ) {
                if (creating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) { Text("Cancel") }
        }
    )
}


@Composable
private fun EmptyAccountsPrompt(onOpenAccounts: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Login, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Welcome to QuickGerrit", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Add a Gerrit account (URL + username + HTTP password) to start reviewing.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(onClick = onOpenAccounts) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Account")
            }
        }
    }
}

@Composable
private fun ChangeCard(change: ChangeInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${change.number}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(change.status)
                Spacer(Modifier.weight(1f))
                Text(
                    "+${change.insertions} −${change.deletions}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                change.subject,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${change.project} · ${change.branch}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            change.owner?.let { owner ->
                Text(
                    owner.displayName ?: owner.name ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // Simple labels preview
            change.labels?.let { labels ->
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEach { (name, info) ->
                        val value = info.all?.maxOfOrNull { it.value ?: 0 } ?: info.value
                        if (value != null && value != 0) {
                            AssistChip(
                                onClick = {},
                                label = { Text("$name $value") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = when {
                                        value > 0 -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.errorContainer
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (label, color) = when (status.uppercase()) {
        "NEW" -> "Open" to MaterialTheme.colorScheme.primary
        "MERGED" -> "Merged" to MaterialTheme.colorScheme.secondary
        "ABANDONED" -> "Abandoned" to MaterialTheme.colorScheme.error
        else -> status to MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
