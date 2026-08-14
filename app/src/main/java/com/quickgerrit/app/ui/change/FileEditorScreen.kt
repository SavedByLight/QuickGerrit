package com.quickgerrit.app.ui.change

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Built-in monospace text editor for a single file in a Gerrit change.
 *
 * Flow:
 * 1. Load file content from the selected revision
 * 2. Edit in-app (BasicTextField + TextFieldState)
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
    val textState = rememberTextFieldState()
    var content by remember { mutableStateOf("") }
    var original by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Keep [content] in sync with the TextFieldState for dirty checks / save / highlight
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }
            .distinctUntilChanged()
            .collect { content = it }
    }

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
            textState.setTextAndPlaceCursorAtEnd(text)
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
            // Always read from the live text field state
            val body = textState.text.toString()
            try {
                repository.putEditFile(changeId, filePath, body)
                content = body
                original = body
                status = "Saved to change edit (${body.length} chars)"
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
            // ALWAYS write the file into the change edit first. Skipping save when
            // body == original left Gerrit with no open edit, so publish failed.
            val body = textState.text.toString()
            saving = true
            error = null
            try {
                repository.putEditFile(changeId, filePath, body)
                content = body
                original = body
                status = "Saved to change edit"
            } catch (e: Exception) {
                error = e.message
                saving = false
                snackbar.showSnackbar(e.message ?: "Save failed — cannot publish")
                return@launch
            }
            saving = false

            publishing = true
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
                        enabled = !loading && !saving && !publishing
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when {
                            loading -> "Loading…"
                            status != null -> status!!
                            dirty -> "Unsaved changes"
                            else -> "In-app editor · Save writes a change edit"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = { save() },
                        enabled = !loading && !saving && !publishing
                    ) { Text("Save") }
                    Spacer(Modifier.width(8.dp))
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
                    // Multi-line monospace editor. No horizontalScroll on the outer box —
                    // unbounded width was collapsing multi-line layout into one visual line.
                    val vScroll = rememberScrollState()
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
                    ) {
                        Text(
                            text = highlighted,
                            style = mono,
                            modifier = Modifier.fillMaxWidth()
                        )
                        BasicTextField(
                            state = textState,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = mono.copy(color = Color.Transparent),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            lineLimits = TextFieldLineLimits.MultiLine()
                        )
                    }
                }
            }
        }
    }
}
