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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeDetailScreen(
    viewModel: ChangeDetailViewModel,
    onBack: () -> Unit,
    onOpenDiff: (revisionId: String, filePath: String) -> Unit,
    onOpenEditor: (revisionId: String, filePath: String) -> Unit = { _, _ -> }
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
                            onEdit = { path -> onOpenEditor(state.selectedRevision ?: "current", path) }
                        )
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
        Text("+${change.insertions} −${change.deletions}", style = MaterialTheme.typography.labelMedium)
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
                        Text(status, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(path, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "+${info.linesInserted ?: 0} −${info.linesDeleted ?: 0}",
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
