package com.quickgerrit.app.ui.change

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickgerrit.app.data.model.DiffContent
import com.quickgerrit.app.data.model.DiffInfo
import com.quickgerrit.app.data.repository.GerritRepository
import com.quickgerrit.app.ui.theme.CodeColors
import com.quickgerrit.app.ui.theme.SourceLanguage
import com.quickgerrit.app.ui.theme.SyntaxColors
import com.quickgerrit.app.ui.theme.highlightSyntax
import com.quickgerrit.app.ui.theme.languageFromPath
import com.quickgerrit.app.ui.theme.rememberCodeColors
import com.quickgerrit.app.ui.theme.rememberSyntaxColors

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
    val syntaxColors = rememberSyntaxColors()
    val language = remember(filePath) { languageFromPath(filePath) }

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
                    if (unified) {
                        UnifiedDiffView(diff!!.content.orEmpty(), codeColors, syntaxColors, language)
                    } else {
                        SideBySideDiffView(diff!!.content.orEmpty(), codeColors, syntaxColors, language)
                    }
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

/** Flattened line for unified or side-by-side rendering. */
@Immutable
private data class FlatDiffLine(
    val text: String,
    val type: DiffLineType,
    val index: Int
)

private fun flattenUnified(content: List<DiffContent>): List<FlatDiffLine> {
    val out = ArrayList<FlatDiffLine>(256)
    var idx = 0
    for (block in content) {
        when {
            block.ab != null -> {
                for (line in block.ab) {
                    out.add(FlatDiffLine(line, DiffLineType.CONTEXT, idx++))
                }
            }
            else -> {
                block.a?.forEach { line ->
                    out.add(FlatDiffLine(line, DiffLineType.REMOVED, idx++))
                }
                block.b?.forEach { line ->
                    out.add(FlatDiffLine(line, DiffLineType.ADDED, idx++))
                }
            }
        }
    }
    return out
}

private fun flattenSideBySide(content: List<DiffContent>): Pair<List<FlatDiffLine>, List<FlatDiffLine>> {
    val left = ArrayList<FlatDiffLine>(256)
    val right = ArrayList<FlatDiffLine>(256)
    var idx = 0
    for (block in content) {
        when {
            block.ab != null -> {
                for (line in block.ab) {
                    left.add(FlatDiffLine(line, DiffLineType.CONTEXT, idx))
                    right.add(FlatDiffLine(line, DiffLineType.CONTEXT, idx))
                    idx++
                }
            }
            else -> {
                val a = block.a.orEmpty()
                val b = block.b.orEmpty()
                val max = maxOf(a.size, b.size)
                for (i in 0 until max) {
                    if (i < a.size) left.add(FlatDiffLine(a[i], DiffLineType.REMOVED, idx))
                    else left.add(FlatDiffLine("", DiffLineType.EMPTY, idx))
                    if (i < b.size) right.add(FlatDiffLine(b[i], DiffLineType.ADDED, idx))
                    else right.add(FlatDiffLine("", DiffLineType.EMPTY, idx))
                    idx++
                }
            }
        }
    }
    return left to right
}

@Composable
private fun UnifiedDiffView(
    content: List<DiffContent>,
    colors: CodeColors,
    syntax: SyntaxColors,
    language: SourceLanguage
) {
    val lines = remember(content) { flattenUnified(content) }

    // LazyColumn only composes visible lines → smooth scrolling even for multi-thousand-line diffs.
    // softWrap=false keeps monospace alignment; very long lines may clip (use Edit or side-by-side).
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(
            items = lines,
            key = { _, line -> line.index },
            contentType = { _, line -> line.type }
        ) { _, line ->
            DiffLine(line.text, line.type, colors, syntax, language)
        }
    }
}

@Composable
private fun SideBySideDiffView(
    content: List<DiffContent>,
    colors: CodeColors,
    syntax: SyntaxColors,
    language: SourceLanguage
) {
    val (left, right) = remember(content) { flattenSideBySide(content) }

    // Shared vertical scroll via two LazyColumns is hard; use a single LazyColumn of pairs.
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(4.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(
            items = left,
            key = { _, line -> line.index },
            contentType = { _, line -> line.type }
        ) { i, leftLine ->
            val rightLine = right.getOrNull(i) ?: FlatDiffLine("", DiffLineType.EMPTY, leftLine.index)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DiffLine(
                    leftLine.text,
                    leftLine.type,
                    colors,
                    syntax,
                    language,
                    compact = true,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider()
                DiffLine(
                    rightLine.text,
                    rightLine.type,
                    colors,
                    syntax,
                    language,
                    compact = true,
                    modifier = Modifier.weight(1f)
                )
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
    syntax: SyntaxColors,
    language: SourceLanguage,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bg = when (type) {
        DiffLineType.ADDED -> colors.addedBg
        DiffLineType.REMOVED -> colors.removedBg
        DiffLineType.EMPTY -> colors.emptyBg
        DiffLineType.CONTEXT -> colors.contextBg
    }
    val prefixColor = when (type) {
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

    // Highlight is memoized per line; LazyColumn only composes visible rows.
    val highlighted = remember(text, language, syntax, type) {
        if (text.isEmpty() || type == DiffLineType.EMPTY) AnnotatedString("")
        else highlightSyntax(text, language, syntax)
    }

    val line = remember(highlighted, prefix, prefixColor) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = prefixColor, fontWeight = FontWeight.Medium)) {
                append(prefix)
            }
            append(highlighted)
        }
    }

    Text(
        text = line,
        fontFamily = FontFamily.Monospace,
        fontSize = if (compact) 11.sp else 12.sp,
        softWrap = false,
        maxLines = 1,
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        color = syntax.plain,
        style = LocalTextStyle.current.copy(lineHeight = if (compact) 14.sp else 16.sp)
    )
}
