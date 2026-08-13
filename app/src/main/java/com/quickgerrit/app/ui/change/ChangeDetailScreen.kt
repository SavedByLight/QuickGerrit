package com.quickgerrit.app.ui.change

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quickgerrit.app.data.model.ChangeInfo
import com.quickgerrit.app.data.model.FileInfo
import com.quickgerrit.app.ui.changes.StatusChip
import com.quickgerrit.app.ui.theme.rememberCodeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeDetailScreen(
    viewModel: ChangeDetailViewModel,
    onBack: () -> Unit,
    onOpenDiff: (revisionId: String, filePath: String) -> Unit,
    onOpenEditor: (revisionId: String, filePath: String, project: String, branch: String) -> Unit = { _, _, _, _ -> }
) {
    val state by viewModel.ui.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.change?.let { "#${it.number}" } ?: "Change",
                        maxLines = 1
                    )
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.load() }) { Text("Retry") }
                }
            }
            state.change != null -> {
                val change = state.change!!
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        HeaderSection(change)
                    }
                    item {
                        LabelsSection(change)
                    }
                    item {
                        RevisionsSection(
                            change = change,
                            selected = state.selectedRevision,
                            onSelect = { viewModel.selectRevision(it) }
                        )
                    }
                    item {
                        FilesSection(
                            files = state.files,
                            revisionId = state.selectedRevision ?: "current",
                            onOpen = { path -> onOpenDiff(state.selectedRevision ?: "current", path) },
                            onEdit = { path ->
                                onOpenEditor(
                                    state.selectedRevision ?: "current",
                                    path,
                                    change.project,
                                    change.branch
                                )
                            }
                        )
                    }
                    if (change.status.equals("NEW", ignoreCase = true)) {
                        item {
                            EditOpenChangeSection(
                                change = change,
                                inProgress = state.actionInProgress,
                                onSaveTopic = { viewModel.updateTopic(it) },
                                onSaveMessage = { viewModel.updateCommitMessageAndPublish(it) },
                                onPublishEdit = { viewModel.publishChangeEdit() },
                                onDiscardEdit = { viewModel.discardChangeEdit() },
                                onEditFile = { path ->
                                    onOpenEditor(
                                        state.selectedRevision ?: "current",
                                        path,
                                        change.project,
                                        change.branch
                                    )
                                },
                                filePaths = state.files.keys
                                    .filter { it != "/COMMIT_MSG" && it != "/MERGE_LIST" }
                                    .sorted(),
                                repoQuery = state.repoFileQuery,
                                repoMatches = state.repoFileMatches,
                                repoSearching = state.repoFileSearching,
                                repoSearchError = state.repoFileSearchError,
                                onRepoQueryChange = { viewModel.setRepoFileQuery(it) },
                                onSearchRepo = { viewModel.searchRepoFiles(it) }
                            )
                        }
                    }
                    item {
                        ReviewSection(
                            message = state.reviewMessage,
                            codeReview = state.codeReviewScore,
                            verified = state.verifiedScore,
                            inProgress = state.actionInProgress,
                            onMessage = { viewModel.setReviewMessage(it) },
                            onCodeReview = { viewModel.setCodeReview(it) },
                            onVerified = { viewModel.setVerified(it) },
                            onSubmitReview = { viewModel.submitReview() }
                        )
                    }
                    item {
                        ActionsSection(
                            change = change,
                            inProgress = state.actionInProgress,
                            onAbandon = { viewModel.abandon() },
                            onRestore = { viewModel.restore() },
                            onSubmit = { viewModel.submit() },
                            onWip = { viewModel.setWip() },
                            onReady = { viewModel.setReady() }
                        )
                    }
                    item {
                        MessagesSection(change)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(change: ChangeInfo) {
    val codeColors = rememberCodeColors()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(change.status)
            if (change.workInProgress == true) {
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = {}, label = { Text("WIP") })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(change.subject, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("${change.project} · ${change.branch}", style = MaterialTheme.typography.bodyMedium)
        change.owner?.let {
            Text("Owner: ${it.displayName ?: it.name}", style = MaterialTheme.typography.bodySmall)
        }
        Text("Updated: ${change.updated.take(19).replace('T', ' ')}", style = MaterialTheme.typography.bodySmall)
        Text(
            codeColors.insertionsDeletionsText(change.insertions, change.deletions),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun EditOpenChangeSection(
    change: ChangeInfo,
    inProgress: Boolean,
    onSaveTopic: (String) -> Unit,
    onSaveMessage: (String) -> Unit,
    onPublishEdit: () -> Unit,
    onDiscardEdit: () -> Unit,
    onEditFile: (String) -> Unit,
    filePaths: List<String>,
    repoQuery: String = "",
    repoMatches: List<String> = emptyList(),
    repoSearching: Boolean = false,
    repoSearchError: String? = null,
    onRepoQueryChange: (String) -> Unit = {},
    onSearchRepo: (String) -> Unit = {}
) {
    var topic by remember(change.topic) { mutableStateOf(change.topic.orEmpty()) }
    val currentMessage = change.revisions
        ?.get(change.currentRevision)
        ?.commit
        ?.message
        ?: change.subject
    var commitMsg by remember(change.currentRevision, currentMessage) {
        mutableStateOf(currentMessage)
    }
    var showNewFile by remember { mutableStateOf(false) }
    var newFilePath by remember { mutableStateOf("") }

    Column {
        Text("Edit this change", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Update topic, commit message, or files. File saves go into a change edit; publish to create a new patch set.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            label = { Text("Topic") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !inProgress
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSaveTopic(topic) },
            enabled = !inProgress,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save topic") }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = commitMsg,
            onValueChange = { commitMsg = it },
            label = { Text("Commit message") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            enabled = !inProgress
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSaveMessage(commitMsg) },
            enabled = !inProgress && commitMsg.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save message & publish patch set") }

        Spacer(Modifier.height(16.dp))
        Text("Files", style = MaterialTheme.typography.titleSmall)
        Text(
            "Tap a file to open the in-app editor",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        filePaths.forEach { path ->
            OutlinedButton(
                onClick = { onEditFile(path) },
                enabled = !inProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(path, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Browse files in the repository",
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            "Search by path (Gerrit returns matching files from the full tree, not only files already in this change). Tap a result to open the editor.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = repoQuery,
            onValueChange = { onRepoQueryChange(it) },
            label = { Text("Search path…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !inProgress,
            placeholder = { Text("e.g. README, .kt, src/") },
            trailingIcon = {
                IconButton(
                    onClick = { onSearchRepo(repoQuery) },
                    enabled = !inProgress && repoQuery.isNotBlank()
                ) {
                    if (repoSearching) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, "Search")
                    }
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSearchRepo(repoQuery) },
            enabled = !inProgress && repoQuery.isNotBlank() && !repoSearching,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Search repository files") }

        repoSearchError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (repoMatches.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${repoMatches.size} match(es) — tap to edit",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(4.dp))
            // Selectable results box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    repoMatches.forEach { path ->
                        Text(
                            path,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !inProgress) { onEditFile(path) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                        HorizontalDivider()
                    }
                }
            }
        } else if (repoQuery.isNotBlank() && !repoSearching && repoSearchError == null) {
            Text(
                "No matches. Try a shorter substring (e.g. “kt” or “README”).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newFilePath,
            onValueChange = { newFilePath = it },
            label = { Text("Or open exact path / new file") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !inProgress,
            placeholder = { Text("path/to/file") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val p = newFilePath.trim().trimStart('/')
                if (p.isNotEmpty()) onEditFile(p)
            },
            enabled = !inProgress && newFilePath.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open path in editor") }

        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onPublishEdit,
                enabled = !inProgress,
                modifier = Modifier.weight(1f)
            ) { Text("Publish edit") }
            OutlinedButton(
                onClick = onDiscardEdit,
                enabled = !inProgress,
                modifier = Modifier.weight(1f)
            ) { Text("Discard edit") }
        }
    }

    if (showNewFile) {
        AlertDialog(
            onDismissRequest = { showNewFile = false },
            title = { Text("Edit file by path") },
            text = {
                Column {
                    Text(
                        "Enter a path relative to the repo root. For a new file, save content in the editor to create it in the change edit.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFilePath,
                        onValueChange = { newFilePath = it },
                        label = { Text("path/to/file") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = newFilePath.trim().trimStart('/')
                        if (p.isNotEmpty()) {
                            showNewFile = false
                            onEditFile(p)
                            newFilePath = ""
                        }
                    },
                    enabled = newFilePath.isNotBlank()
                ) { Text("Open editor") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFile = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LabelsSection(change: ChangeInfo) {
    val labels = change.labels ?: return
    Column {
        Text("Labels", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        labels.forEach { (name, info) ->
            val values = info.all?.mapNotNull { it.value }?.distinct()?.sorted() ?: emptyList()
            Text("$name: ${values.joinToString()}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RevisionsSection(
    change: ChangeInfo,
    selected: String?,
    onSelect: (String) -> Unit
) {
    val revs = change.revisions ?: return
    Column {
        Text("Patch sets / Commits", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        revs.entries.sortedByDescending { it.value.number }.forEach { (sha, rev) ->
            val isSelected = sha == selected
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelect(sha) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("PS ${rev.number} · ${sha.take(8)}", fontWeight = FontWeight.SemiBold)
                    rev.commit?.let { c ->
                        Text(c.subject, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${c.author?.name} · ${c.author?.date?.take(19)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesSection(
    files: Map<String, FileInfo>,
    revisionId: String,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit = {}
) {
    val codeColors = rememberCodeColors()
    Column {
        Text("Files (${files.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        files.entries
            .filter { it.key != "/COMMIT_MSG" && it.key != "/MERGE_LIST" }
            .sortedBy { it.key }
            .forEach { (path, info) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        Modifier
                            .clickable { onOpen(path) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val status = when (info.status) {
                            "A" -> "A"
                            "D" -> "D"
                            "R" -> "R"
                            else -> "M"
                        }
                        Text(
                            status,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = codeColors.statusColor(status)
                        )
                        Spacer(Modifier.width(8.dp))
                        // Language accent from file extension
                        val lang = codeColors.languageColor(path)
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = lang.copy(alpha = 0.16f),
                            modifier = Modifier.size(width = 4.dp, height = 28.dp)
                        ) {}
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(path, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                codeColors.insertionsDeletionsText(
                                    info.linesInserted ?: 0,
                                    info.linesDeleted ?: 0
                                ),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (info.status != "D") {
                            TextButton(onClick = { onEdit(path) }) {
                                Text("Edit")
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
    }
}

@Composable
private fun ReviewSection(
    message: String,
    codeReview: Int,
    verified: Int,
    inProgress: Boolean,
    onMessage: (String) -> Unit,
    onCodeReview: (Int) -> Unit,
    onVerified: (Int) -> Unit,
    onSubmitReview: () -> Unit
) {
    Column {
        Text("Review", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = message,
            onValueChange = onMessage,
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(Modifier.height(8.dp))
        Text("Code-Review")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-2, -1, 0, 1, 2).forEach { score ->
                FilterChip(
                    selected = codeReview == score,
                    onClick = { onCodeReview(score) },
                    label = { Text(score.toString()) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Verified")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-1, 0, 1).forEach { score ->
                FilterChip(
                    selected = verified == score,
                    onClick = { onVerified(score) },
                    label = { Text(score.toString()) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSubmitReview,
            enabled = !inProgress,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (inProgress) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Publish Review")
        }
    }
}

@Composable
private fun ActionsSection(
    change: ChangeInfo,
    inProgress: Boolean,
    onAbandon: () -> Unit,
    onRestore: () -> Unit,
    onSubmit: () -> Unit,
    onWip: () -> Unit,
    onReady: () -> Unit
) {
    Column {
        Text("Actions", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            when (change.status.uppercase()) {
                "NEW" -> {
                    if (change.workInProgress == true) {
                        Button(onClick = onReady, enabled = !inProgress) {
                            Text("Mark Active")
                        }
                    } else {
                        OutlinedButton(onClick = onWip, enabled = !inProgress) {
                            Text("Mark WIP")
                        }
                    }
                    OutlinedButton(onClick = onAbandon, enabled = !inProgress) {
                        Text("Abandon")
                    }
                    if (change.submittable == true) {
                        Button(onClick = onSubmit, enabled = !inProgress) {
                            Text("Submit")
                        }
                    }
                }
                "ABANDONED" -> {
                    OutlinedButton(onClick = onRestore, enabled = !inProgress) {
                        Text("Restore")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagesSection(change: ChangeInfo) {
    val messages = change.messages ?: return
    Column {
        Text("Messages", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        messages.takeLast(15).reversed().forEach { msg ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "${msg.author?.name ?: "System"} · ${msg.date.take(19)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(msg.message.trim(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
