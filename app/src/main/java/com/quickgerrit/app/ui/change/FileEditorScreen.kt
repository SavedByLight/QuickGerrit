package com.quickgerrit.app.ui.change

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickgerrit.app.data.repository.GerritRepository
import com.quickgerrit.app.ui.theme.highlightSyntax
import com.quickgerrit.app.ui.theme.languageFromPath
import com.quickgerrit.app.ui.theme.rememberCodeColors
import com.quickgerrit.app.ui.theme.rememberSyntaxColors
import kotlinx.coroutines.launch

/**
 * Built-in monospace text editor for a single file in a Gerrit change.
 *
 * Flow:
 * 1. Load file content from the selected revision
 * 2. Edit in-app (BasicTextField, no external editor)
 * 3. Save → writes into the Gerrit *change edit*
 * 4. Publish → turns the change edit into a new patch set
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreen(
    changeId: String,
    revisionId: String,
    filePath: String,
    project: String = "",
    branch: String = "master",
    repository: GerritRepository,
    onBack: () -> Unit,
    onPublished: () -> Unit = {}
) {
    var content by remember { mutableStateOf("") }
    var original by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val dirty = content != original
    val codeColors = rememberCodeColors()
    val syntaxColors = rememberSyntaxColors()
    val language = remember(filePath) { languageFromPath(filePath) }
    val highlighted = remember(content, language, syntaxColors) {
        highlightSyntax(content, language, syntaxColors)
    }

    LaunchedEffect(changeId, revisionId, filePath, project, branch) {
        loading = true
        error = null
        try {
            val text = repository.getFileContentForEdit(
                changeId = changeId,
                revisionId = revisionId,
                filePath = filePath,
                project = project,
                branch = branch
            )
            content = text
            original = text
            if (text.isEmpty()) {
                status = "New or empty file — type content and Save to add it to the change edit"
            }
        } catch (e: Exception) {
            error = e.message ?: "Failed to load file"
        } finally {
            loading = false
        }
    }

    fun save() {
        scope.launch {
            saving = true
            error = null
            status = null
            try {
                repository.putEditFile(changeId, filePath, content)
                original = content
                status = "Saved to change edit"
                snackbar.showSnackbar("Saved to change edit")
            } catch (e: Exception) {
                error = e.message
                snackbar.showSnackbar(e.message ?: "Save failed")
            } finally {
                saving = false
            }
        }
    }

    fun publish() {
        scope.launch {
            // Auto-save if dirty
            if (dirty) {
                saving = true
                try {
                    repository.putEditFile(changeId, filePath, content)
                    original = content
                } catch (e: Exception) {
                    error = e.message
                    saving = false
                    snackbar.showSnackbar(e.message ?: "Save failed")
                    return@launch
                }
                saving = false
            }
            publishing = true
            error = null
            try {
                repository.publishEdit(changeId)
                status = "Published as new patch set"
                snackbar.showSnackbar("Published as new patch set")
                onPublished()
            } catch (e: Exception) {
                error = e.message
                snackbar.showSnackbar(e.message ?: "Publish failed")
            } finally {
                publishing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                filePath.substringAfterLast('/'),
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.width(8.dp))
                            val ext = filePath.substringAfterLast('.').uppercase()
                            if (ext.isNotBlank() && ext.length <= 8 && ext != filePath.uppercase()) {
                                val accent = codeColors.languageColor(filePath)
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = accent.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        ext,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Text(
                            filePath,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { save() },
                        enabled = !loading && !saving && !publishing && dirty
                    ) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, "Save to change edit")
                        }
                    }
                    IconButton(
                        onClick = { publish() },
                        enabled = !loading && !saving && !publishing
                    ) {
                        if (publishing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Publish, "Publish patch set")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        when {
                            loading -> "Loading…"
                            dirty -> "Unsaved changes"
                            status != null -> status!!
                            else -> "In-app editor · Save writes a change edit"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                        color = if (dirty) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { save() },
                        enabled = !loading && dirty && !saving && !publishing
                    ) { Text("Save") }
                    Button(
                        onClick = { publish() },
                        enabled = !loading && !saving && !publishing
                    ) { Text("Publish") }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null && content.isEmpty() -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onBack) { Text("Back") }
                    }
                }
                else -> {
                    // Highlighted monospace editor: coloured Text under transparent BasicTextField
                    val vScroll = rememberScrollState()
                    val hScroll = rememberScrollState()
                    val mono = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = syntaxColors.plain
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .verticalScroll(vScroll)
                            .horizontalScroll(hScroll)
                    ) {
                        Text(
                            text = highlighted,
                            style = mono,
                            modifier = Modifier.fillMaxWidth()
                        )
                        BasicTextField(
                            value = content,
                            onValueChange = { content = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = mono.copy(color = Color.Transparent),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}
