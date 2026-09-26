package com.quickgerrit.app.ui.changes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
// Arrangement via layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quickgerrit.app.data.model.ChangeInfo
import com.quickgerrit.app.update.AppUpdater
import com.quickgerrit.app.ui.theme.rememberCodeColors
import com.quickgerrit.app.ui.update.AutoUpdateChecker
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesScreen(
    viewModel: ChangesViewModel,
    onOpenChange: (String) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenDashboard: () -> Unit = {}
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
                    IconButton(onClick = onOpenDashboard) {
                        Icon(Icons.Default.Dashboard, "Dashboard")
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
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Update available: v${info.versionName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "You have ${com.quickgerrit.app.platform.AppConfig.VERSION_NAME}. Opens GitHub in your browser — nothing is downloaded inside the app.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                AppUpdater.openDownloadPage(info)
                                pendingUpdate = null
                            }) { Text("Download now") }
                            TextButton(onClick = { pendingUpdate = null }) { Text("Later") }
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

            // Live search with operator suggestions (refreshes while typing)
            var searchFocused by remember { mutableStateOf(false) }
            // Hide suggestion dropdown once the user starts scrolling the list
            // (especially with an empty query) so it doesn't block the results.
            var suggestionsDismissed by remember { mutableStateOf(false) }
            val focusManager = LocalFocusManager.current
            val listState = rememberLazyListState()

            // Dismiss suggestions once when scroll starts — avoid state writes every frame
            // (those recompositions are a major source of scroll jank).
            val searchIsBlank by rememberUpdatedState(state.search.isBlank())
            LaunchedEffect(listState) {
                var wasScrolling = false
                snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                    if (scrolling && !wasScrolling) {
                        suggestionsDismissed = true
                        if (searchIsBlank) {
                            searchFocused = false
                            focusManager.clearFocus()
                        }
                    }
                    wasScrolling = scrolling
                }
            }

            val suggestions = remember(state.search) {
                val q = state.search.trim().lowercase()
                if (q.isEmpty()) ChangesViewModel.QUERY_SUGGESTIONS.take(8)
                else ChangesViewModel.QUERY_SUGGESTIONS.filter {
                    it.lowercase().contains(q) || q.contains(it.lowercase().substringBefore(':'))
                }.take(8)
            }
            val showSuggestions = searchFocused &&
                !suggestionsDismissed &&
                suggestions.isNotEmpty() &&
                (state.search.isBlank() || suggestions.any { !it.equals(state.search, ignoreCase = true) })

            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = {
                        viewModel.setSearch(it)
                        // Typing again re-enables suggestions after a scroll dismiss
                        suggestionsDismissed = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            searchFocused = focusState.isFocused
                            // Only re-show suggestions on focus if the user is actively
                            // interacting with the field (not after a scroll dismiss).
                            // Typing is what reliably re-enables them.
                        },
                    placeholder = { Text("Search query (live) — e.g. owner:self, project:…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        Row {
                            if (state.isLoading && state.changes.isNotEmpty()) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.CenterVertically),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            if (state.search.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.setSearch("")
                                    viewModel.searchNow()
                                    suggestionsDismissed = true
                                    focusManager.clearFocus()
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            } else {
                                IconButton(onClick = { viewModel.searchNow() }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search now")
                                }
                            }
                        }
                    }
                )
                if (showSuggestions) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column {
                            suggestions.forEach { suggestion ->
                                Text(
                                    text = suggestion,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setSearch(suggestion)
                                            viewModel.searchNow()
                                            searchFocused = false
                                            suggestionsDismissed = true
                                            focusManager.clearFocus()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (suggestion != suggestions.last()) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }

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
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        // Prefetch past the viewport for smoother flings on high-refresh panels.
                        beyondBoundsItemCount = 6
                    ) {
                        items(
                            items = state.changes,
                            key = { it.id },
                            contentType = { "change" }
                        ) { change ->
                            ChangeCard(
                                change = change,
                                onOpen = onOpenChange
                            )
                        }
                        item(key = "footer", contentType = "footer") {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "${state.changes.size} change(s) loaded",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                if (state.hasMore) {
                                    Button(
                                        onClick = { viewModel.loadMore() },
                                        enabled = !state.isLoadingMore,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (state.isLoadingMore) {
                                            CircularProgressIndicator(
                                                Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(
                                            if (state.isLoadingMore) "Loading…"
                                            else "Load more (next 100)"
                                        )
                                    }
                                } else if (state.changes.isNotEmpty()) {
                                    Text(
                                        "End of results",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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
internal fun ChangeCard(change: ChangeInfo, onOpen: (String) -> Unit) {
    val codeColors = rememberCodeColors()
    // Cache derived strings so fling/scroll does not rebuild AnnotatedString / maps each frame.
    val stats = remember(change.insertions, change.deletions, codeColors) {
        codeColors.insertionsDeletionsText(change.insertions, change.deletions)
    }
    val projectBranch = remember(change.project, change.branch) {
        "${change.project} · ${change.branch}"
    }
    val ownerName = remember(change.owner) {
        change.owner?.let { it.displayName ?: it.name ?: "Unknown" }
    }
    val labelPairs = remember(change.labels) {
        change.labels?.mapNotNull { (name, info) ->
            val value = info.all?.maxOfOrNull { it.value ?: 0 } ?: info.value
            if (value != null && value != 0) name to value else null
        }.orEmpty()
    }
    val numberLabel = remember(change.number) { "#${change.number}" }
    // Stable click handler — parent lambdas change identity often and would force recomposition.
    val latestOnOpen by rememberUpdatedState(onOpen)
    val onClick = remember(change.id) { { latestOnOpen(change.id) } }

    // Flat Surface avoids per-frame shadow redraws that cause scroll jitter.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    numberLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(change.status)
                Spacer(Modifier.weight(1f))
                Text(stats, style = MaterialTheme.typography.labelSmall)
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
                projectBranch,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ownerName?.let { name ->
                Text(name, style = MaterialTheme.typography.bodySmall)
            }
            // Lightweight label chips (Surface + Text) — AssistChip is too heavy for long lists.
            if (labelPairs.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    labelPairs.forEach { (name, value) ->
                        val container = if (value > 0) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = container
                        ) {
                            Text(
                                "$name $value",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatusChip(status: String) {
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
