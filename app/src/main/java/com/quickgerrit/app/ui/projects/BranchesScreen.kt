package com.quickgerrit.app.ui.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quickgerrit.app.data.model.BranchInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchesScreen(
    viewModel: BranchesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.ui.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    val filtered = remember(state.branches, state.filter) {
        if (state.filter.isBlank()) state.branches
        else state.branches.filter {
            it.shortName.contains(state.filter, ignoreCase = true) ||
                it.revision.contains(state.filter, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Branches")
                        Text(
                            state.project,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create branch")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.filter,
                onValueChange = { viewModel.setFilter(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search branches…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            when {
                state.isLoading && state.branches.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.branches.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.load() }) { Text("Retry") }
                        }
                    }
                }
                filtered.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.filter.isBlank()) "No branches"
                            else "No matching branches",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                "${filtered.size} branch(es)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(filtered, key = { it.ref.ifBlank { it.shortName } }) { branch ->
                            BranchCard(branch)
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateBranchDialog(
            creating = state.creating,
            error = state.createError,
            existingBranches = state.branches.map { it.shortName },
            onDismiss = {
                if (!state.creating) {
                    showCreate = false
                    viewModel.clearCreateResult()
                }
            },
            onCreate = { name, revision ->
                viewModel.createBranch(name, revision) {
                    showCreate = false
                    viewModel.clearCreateResult()
                }
            }
        )
    }
}

@Composable
private fun BranchCard(branch: BranchInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AccountTree,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    branch.shortName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                SelectionContainer {
                    Text(
                        branch.revision,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (branch.ref.isNotBlank() && branch.ref != branch.shortName) {
                    Text(
                        branch.ref,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateBranchDialog(
    creating: Boolean,
    error: String?,
    existingBranches: List<String>,
    onDismiss: () -> Unit,
    onCreate: (name: String, revision: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var revision by remember {
        mutableStateOf(
            existingBranches.firstOrNull {
                it == "master" || it == "main"
            } ?: existingBranches.firstOrNull() ?: "HEAD"
        )
    }

    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = { Text("Create branch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Branch name") },
                    placeholder = { Text("feature/my-branch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !creating
                )
                OutlinedTextField(
                    value = revision,
                    onValueChange = { revision = it },
                    label = { Text("Base revision") },
                    placeholder = { Text("HEAD, master, or commit SHA") },
                    supportingText = {
                        Text("Existing branch name, commit SHA, or HEAD")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !creating
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.trim(), revision.trim()) },
                enabled = !creating && name.isNotBlank() && revision.isNotBlank()
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
