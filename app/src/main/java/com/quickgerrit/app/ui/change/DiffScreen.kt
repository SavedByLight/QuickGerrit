package com.quickgerrit.app.ui.change

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickgerrit.app.data.model.DiffContent
import com.quickgerrit.app.data.model.DiffInfo
import com.quickgerrit.app.data.repository.GerritRepository
import com.quickgerrit.app.ui.theme.CodeColors
import com.quickgerrit.app.ui.theme.rememberCodeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffScreen(
    changeId: String,
    revisionId: String,
    filePath: String,
    repository: GerritRepository,
    onBack: () -> Unit,
    onEdit: () -> Unit = {}
) {
    var diff by remember { mutableStateOf<DiffInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var unified by remember { mutableStateOf(true) }
    val codeColors = rememberCodeColors()

    LaunchedEffect(changeId, revisionId, filePath) {
        loading = true
        error = null
        try {
            diff = repository.getDiff(changeId, revisionId, filePath)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(filePath.substringAfterLast('/'), maxLines = 1)
                            Spacer(Modifier.width(8.dp))
                            LanguageChip(filePath, codeColors)
                        }
                        Text(filePath, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onEdit) {
                        Text("Edit")
                    }
                    TextButton(onClick = { unified = !unified }) {
                        Text(if (unified) "Side-by-side" else "Unified")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Text(error!!, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
                diff != null -> {
                    if (unified) UnifiedDiffView(diff!!.content.orEmpty(), codeColors)
                    else SideBySideDiffView(diff!!.content.orEmpty(), codeColors)
                }
            }
        }
    }
}

@Composable
private fun LanguageChip(filePath: String, colors: CodeColors) {
    val label = filePath.substringAfterLast('.').ifBlank {
        filePath.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return
    }.uppercase()
    if (label.length > 8) return
    val accent = colors.languageColor(filePath)
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = accent.copy(alpha = 0.18f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun UnifiedDiffView(content: List<DiffContent>, colors: CodeColors) {
    val scroll = rememberScrollState()
    val hScroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .horizontalScroll(hScroll)
            .padding(8.dp)
    ) {
        content.forEach { block ->
            when {
                block.ab != null -> {
                    block.ab.forEach { line ->
                        DiffLine(line, DiffLineType.CONTEXT, colors)
                    }
                }
                else -> {
                    block.a?.forEach { line -> DiffLine(line, DiffLineType.REMOVED, colors) }
                    block.b?.forEach { line -> DiffLine(line, DiffLineType.ADDED, colors) }
                }
            }
        }
    }
}

@Composable
private fun SideBySideDiffView(content: List<DiffContent>, colors: CodeColors) {
    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(4.dp)
    ) {
        Column(Modifier.weight(1f)) {
            content.forEach { block ->
                when {
                    block.ab != null -> block.ab.forEach { DiffLine(it, DiffLineType.CONTEXT, colors, compact = true) }
                    else -> {
                        val a = block.a.orEmpty()
                        val b = block.b.orEmpty()
                        val max = maxOf(a.size, b.size)
                        for (i in 0 until max) {
                            if (i < a.size) DiffLine(a[i], DiffLineType.REMOVED, colors, compact = true)
                            else DiffLine("", DiffLineType.EMPTY, colors, compact = true)
                        }
                    }
                }
            }
        }
        VerticalDivider()
        Column(Modifier.weight(1f)) {
            content.forEach { block ->
                when {
                    block.ab != null -> block.ab.forEach { DiffLine(it, DiffLineType.CONTEXT, colors, compact = true) }
                    else -> {
                        val a = block.a.orEmpty()
                        val b = block.b.orEmpty()
                        val max = maxOf(a.size, b.size)
                        for (i in 0 until max) {
                            if (i < b.size) DiffLine(b[i], DiffLineType.ADDED, colors, compact = true)
                            else DiffLine("", DiffLineType.EMPTY, colors, compact = true)
                        }
                    }
                }
            }
        }
    }
}

private enum class DiffLineType { CONTEXT, ADDED, REMOVED, EMPTY }

@Composable
private fun DiffLine(
    text: String,
    type: DiffLineType,
    colors: CodeColors,
    compact: Boolean = false
) {
    val bg = when (type) {
        DiffLineType.ADDED -> colors.addedBg
        DiffLineType.REMOVED -> colors.removedBg
        DiffLineType.EMPTY -> colors.emptyBg
        DiffLineType.CONTEXT -> colors.contextBg
    }
    val fg = when (type) {
        DiffLineType.ADDED -> colors.addedFg
        DiffLineType.REMOVED -> colors.removedFg
        DiffLineType.EMPTY -> Color.Transparent
        DiffLineType.CONTEXT -> colors.contextFg
    }
    val prefix = when (type) {
        DiffLineType.ADDED -> "+ "
        DiffLineType.REMOVED -> "- "
        else -> "  "
    }
    Text(
        text = prefix + text,
        fontFamily = FontFamily.Monospace,
        fontSize = if (compact) 11.sp else 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        color = fg
    )
}
