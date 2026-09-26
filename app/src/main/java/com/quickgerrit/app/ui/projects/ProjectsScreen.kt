package com.quickgerrit.app.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quickgerrit.app.data.model.ProjectInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onBack: () -> Unit,
    onOpenChange: (String) -> Unit = {},
    onOpenBranches: (String) -> Unit = {}
) {
    val state by viewModel.ui.collectAsState()
    val filtered = remember(state.projects, state.filter) {
        if (state.filter.isBlank()) state.projects
        else state.projects.filter {
            it.name.contains(state.filter, ignoreCase = true) ||
                    (it.description?.contains(state.filter, ignoreCase = true) == true)
        }
    }

    // Project selected for creating a change (null = dialog closed)
    var createForProject by remember { mutableStateOf<String?>(null) }
    var showCreateProject by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects / Repos") },
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
            FloatingActionButton(
                onClick = {
                    viewModel.clearCreateProjectResult()
                    showCreateProject = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create repository")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.filter,
                onValueChange = { viewModel.setFilter(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search projects…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.filter.isBlank()) "No projects" else "No matching projects",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = filtered,
                        key = { it.id.ifBlank { it.name } },
                        contentType = { "project" }
                    ) { project ->
                        ProjectCard(
                            project = project,
                            onOpenBranches = onOpenBranches,
                            onCreateChange = { createForProject = it }
                        )
                    }
                }
            }
        }
    }

    createForProject?.let { projectName ->
        CreateChangeFromProjectDialog(
            project = projectName,
            creating = state.creating,
            error = state.createError,
            onDismiss = {
                if (!state.creating) {
                    createForProject = null
                    viewModel.clearCreateResult()
                }
            },
            onCreate = { branch, subject, topic, wip ->
                viewModel.createChange(
                    project = projectName,
                    branch = branch,
                    subject = subject,
                    topic = topic,
                    workInProgress = wip
                ) { changeId ->
                    createForProject = null
                    viewModel.clearCreateResult()
                    onOpenChange(changeId)
                }
            }
        )
    }

    if (showCreateProject) {
        CreateProjectDialog(
            creating = state.creatingProject,
            error = state.createProjectError,
            onDismiss = {
                if (!state.creatingProject) {
                    showCreateProject = false
                    viewModel.clearCreateProjectResult()
                }
            },
            onCreate = { name, description, parent, emptyCommit, branch ->
                viewModel.createProject(
                    name = name,
                    description = description,
                    parent = parent,
                    createEmptyCommit = emptyCommit,
                    initialBranch = branch
                ) {
                    showCreateProject = false
                    viewModel.clearCreateProjectResult()
                }
            }
        )
    }
}

@Composable
private fun ProjectCard(
    project: ProjectInfo,
    onOpenBranches: (String) -> Unit,
    onCreateChange: (String) -> Unit
) {
    val latestOpen by rememberUpdatedState(onOpenBranches)
    val latestCreate by rememberUpdatedState(onCreateChange)
    val name = project.name
    val onOpen = remember(name) { { latestOpen(name) } }
    val onCreate = remember(name) { { latestCreate(name) } }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, fontWeight = FontWeight.SemiBold)
                project.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                project.state?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    "Tap to view branches",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCreate) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create change",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(
    creating: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        description: String,
        parent: String,
        createEmptyCommit: Boolean,
        initialBranch: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var parent by remember { mutableStateOf("") }
    var initialBranch by remember { mutableStateOf("master") }
    var createEmptyCommit by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = { Text("Create repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Requires Create Project permission on the Gerrit server. " +
                        "If you lack it, creation will fail with a clear error.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project name *") },
                    placeholder = { Text("e.g. team/my-repo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !creating
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !creating,
                    minLines = 2
                )
                OutlinedTextField(
                    value = parent,
                    onValueChange = { parent = it },
                    label = { Text("Parent (optional)") },
                    placeholder = { Text("All-Projects") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !creating
                )
                OutlinedTextField(
                    value = initialBranch,
                    onValueChange = { initialBranch = it },
                    label = { Text("Initial branch") },
                    placeholder = { Text("master or main") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !creating
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = createEmptyCommit,
                        onCheckedChange = { createEmptyCommit = it },
                        enabled = !creating
                    )
                    Text("Create empty initial commit")
                }
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
                onClick = {
                    onCreate(
                        name.trim(),
                        description,
                        parent,
                        createEmptyCommit,
                        initialBranch
                    )
                },
                enabled = !creating && name.isNotBlank()
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
private fun CreateChangeFromProjectDialog(
    project: String,
    creating: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (branch: String, subject: String, topic: String, wip: Boolean) -> Unit
) {
    var branch by remember { mutableStateOf("master") }
    var subject by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var wip by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = { Text("Create change") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Project is fixed (chosen from the list)
                OutlinedTextField(
                    value = project,
                    onValueChange = {},
                    label = { Text("Project") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
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
                    Checkbox(
                        checked = wip,
                        onCheckedChange = { wip = it },
                        enabled = !creating
                    )
                    Text("Work in progress")
                }
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
                onClick = { onCreate(branch, subject, topic, wip) },
                enabled = !creating && subject.isNotBlank()
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
