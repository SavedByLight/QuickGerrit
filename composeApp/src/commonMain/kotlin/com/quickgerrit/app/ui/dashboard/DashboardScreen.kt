package com.quickgerrit.app.ui.dashboard

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
import androidx.compose.ui.unit.dp
import com.quickgerrit.app.ui.changes.ChangeCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenChange: (String) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenChanges: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val state by viewModel.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Your Dashboard")
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
                    IconButton(onClick = onOpenChanges) {
                        Icon(Icons.Default.List, "All changes")
                    }
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
        when {
            !state.hasAccounts -> {
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Login,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Welcome to QuickGerrit", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Add a Gerrit account (URL + username + HTTP password) to see your dashboard.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Button(onClick = onOpenAccounts) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Account")
                        }
                    }
                }
            }

            state.isLoading && state.sections.all { it.changes.isEmpty() } -> {
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    state.error?.let { err ->
                        item {
                            Text(
                                err,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    state.sections.forEach { section ->
                        val hide = section.def.hideIfEmpty &&
                            !section.isLoading &&
                            section.changes.isEmpty() &&
                            section.error == null
                        if (hide) return@forEach

                        item(key = "header_${section.def.id}") {
                            SectionHeader(
                                title = section.def.name,
                                count = if (section.isLoading) null else section.changes.size,
                                isLoading = section.isLoading
                            )
                        }

                        when {
                            section.isLoading && section.changes.isEmpty() -> {
                                item(key = "loading_${section.def.id}") {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }

                            section.error != null && section.changes.isEmpty() -> {
                                item(key = "err_${section.def.id}") {
                                    Text(
                                        section.error ?: "",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            section.changes.isEmpty() -> {
                                item(key = "empty_${section.def.id}") {
                                    Text(
                                        "No changes",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }

                            else -> {
                                items(
                                    items = section.changes,
                                    key = { "${section.def.id}_${it.id}" }
                                ) { change ->
                                    Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                        ChangeCard(
                                            change = change,
                                            onClick = { onOpenChange(change.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int?, isLoading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        when {
            isLoading -> {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            count != null -> {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
