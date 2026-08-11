package com.quickgerrit.app.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickgerrit.app.util.AppLog
import com.quickgerrit.app.util.LogEntry
import com.quickgerrit.app.util.LogLevel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val entries by AppLog.entries.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var filter by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    val filtered = remember(entries, filter) {
        if (filter.isBlank()) entries
        else entries.filter {
            it.message.contains(filter, ignoreCase = true) ||
                it.tag.contains(filter, ignoreCase = true) ||
                (it.throwableMessage?.contains(filter, ignoreCase = true) == true)
        }
    }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty() && !listState.isScrollInProgress) {
            // Auto-scroll to bottom when new logs arrive if user was near the end
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= entries.size - 3) {
                listState.animateScrollToItem(entries.size.coerceAtLeast(1) - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Logs")
                        Text(
                            "${filtered.size}${if (filter.isNotBlank()) " / ${entries.size}" else ""} entries",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (filtered.isNotEmpty()) {
                                    listState.animateScrollToItem(filtered.size - 1)
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.VerticalAlignBottom, contentDescription = "Scroll to bottom")
                    }
                    IconButton(
                        onClick = {
                            val text = filtered.joinToString("\n") { formatEntry(it) }
                            clipboard.setText(AnnotatedString(text))
                            scope.launch {
                                snackbarHostState.showSnackbar("Logs copied to clipboard")
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Copy all")
                    }
                    IconButton(onClick = { AppLog.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear logs")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Filter…") },
                singleLine = true,
                trailingIcon = {
                    if (filter.isNotEmpty()) {
                        IconButton(onClick = { filter = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear filter")
                        }
                    }
                }
            )

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (entries.isEmpty()) "No logs yet" else "No matches",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filtered, key = { "${it.timeMillis}-${it.message.hashCode()}" }) { entry ->
                            LogRow(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.V -> Color(0xFF9E9E9E)
        LogLevel.D -> Color(0xFF2196F3)
        LogLevel.I -> Color(0xFF4CAF50)
        LogLevel.W -> Color(0xFFFF9800)
        LogLevel.E -> Color(0xFFF44336)
    }
    val bg = when (entry.level) {
        LogLevel.E -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        LogLevel.W -> Color(0xFFFF9800).copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.level.name,
                color = levelColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                entry.timeFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            if (entry.tag != "QuickGerrit") {
                Spacer(Modifier.width(6.dp))
                Text(
                    entry.tag.removePrefix("QuickGerrit."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp
                )
            }
        }
        Text(
            entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        entry.throwableMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}

private fun formatEntry(e: LogEntry): String = buildString {
    append(e.timeFormatted)
    append(" ")
    append(e.level.name)
    append("/")
    append(e.tag)
    append(": ")
    append(e.message)
    e.throwableMessage?.let { append(" | ").append(it) }
}
