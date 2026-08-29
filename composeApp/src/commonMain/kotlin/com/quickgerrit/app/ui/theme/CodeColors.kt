package com.quickgerrit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Shared, conventional colours for diffs and source languages app-wide.
 *
 * Diff palette follows the common Git / GitHub / Gerrit convention:
 *   green = added, red = removed, muted = context.
 * Language colours follow widely used Linguist-style accents for file-type cues.
 */
@Immutable
data class CodeColors(
    // Diff backgrounds (line fill)
    val addedBg: Color,
    val removedBg: Color,
    val contextBg: Color,
    val emptyBg: Color,
    // Diff foreground / prefix
    val addedFg: Color,
    val removedFg: Color,
    val contextFg: Color,
    // File status badges
    val statusAdded: Color,
    val statusDeleted: Color,
    val statusModified: Color,
    val statusRenamed: Color,
    // Fallback when language is unknown
    val languageDefault: Color
) {
    fun languageColor(filePath: String): Color {
        val name = filePath.substringAfterLast('/').lowercase()
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when {
            // Kotlin
            ext == "kt" || ext == "kts" -> Color(0xFFA97BFF)
            // Java
            ext == "java" -> Color(0xFFB07219)
            // C / C++
            ext in setOf("c", "h") -> Color(0xFF555555)
            ext in setOf("cpp", "cc", "cxx", "hpp", "hxx", "hh") -> Color(0xFFF34B7D)
            // C#
            ext == "cs" -> Color(0xFF178600)
            // Go
            ext == "go" -> Color(0xFF00ADD8)
            // Rust
            ext == "rs" -> Color(0xFFDEA584)
            // Python
            ext == "py" || ext == "pyw" -> Color(0xFF3572A5)
            // JavaScript / TypeScript
            ext == "js" || ext == "mjs" || ext == "cjs" -> Color(0xFFF1E05A)
            ext == "ts" || ext == "tsx" -> Color(0xFF3178C6)
            ext == "jsx" -> Color(0xFF61DAFB)
            // Web
            ext == "html" || ext == "htm" -> Color(0xFFE34C26)
            ext == "css" || ext == "scss" || ext == "sass" || ext == "less" -> Color(0xFF563D7C)
            // Shell
            ext in setOf("sh", "bash", "zsh", "ksh") -> Color(0xFF89E051)
            // Ruby
            ext == "rb" || ext == "rake" -> Color(0xFF701516)
            // PHP
            ext == "php" -> Color(0xFF4F5D95)
            // Swift / ObjC
            ext == "swift" -> Color(0xFFFA7343)
            ext in setOf("m", "mm") -> Color(0xFF438EFF)
            // Dart
            ext == "dart" -> Color(0xFF00B4AB)
            // Scala
            ext == "scala" || ext == "sc" -> Color(0xFFC22D40)
            // Groovy
            ext == "groovy" || ext == "gradle" -> Color(0xFF4298B8)
            // XML / JSON / YAML / TOML / properties
            ext in setOf("xml", "xsd", "xsl", "xslt") -> Color(0xFF0060AC)
            ext == "json" || ext == "jsonc" -> Color(0xFF292929)
            ext in setOf("yml", "yaml") -> Color(0xFFCB171E)
            ext == "toml" -> Color(0xFF9C4221)
            ext == "properties" || ext == "prop" -> Color(0xFF2A6277)
            // Markdown / docs
            ext in setOf("md", "markdown", "rst", "adoc") -> Color(0xFF083FA1)
            // SQL
            ext == "sql" -> Color(0xFFE38C00)
            // Protobuf / Thrift
            ext == "proto" -> Color(0xFF2C8EBB)
            // Make / CMake
            name == "makefile" || name == "gnumakefile" || ext == "mk" -> Color(0xFF427819)
            name == "cmakelists.txt" || ext == "cmake" -> Color(0xFFDA3434)
            // Dockerfile
            name == "dockerfile" || name.startsWith("dockerfile.") -> Color(0xFF384D54)
            // Gradle / Maven
            name.endsWith(".gradle") || name.endsWith(".gradle.kts") -> Color(0xFF02303A)
            // Patch / diff
            ext in setOf("diff", "patch") -> Color(0xFFE6FFED)
            else -> languageDefault
        }
    }

    fun statusColor(status: String?): Color = when (status?.uppercase()) {
        "A" -> statusAdded
        "D" -> statusDeleted
        "R", "C" -> statusRenamed
        else -> statusModified
    }

    /** "+12 −3" with conventional green / red spans. */
    fun insertionsDeletionsText(insertions: Int, deletions: Int): AnnotatedString =
        buildAnnotatedString {
            withStyle(SpanStyle(color = addedFg)) { append("+$insertions") }
            append(" ")
            withStyle(SpanStyle(color = removedFg)) { append("−$deletions") }
        }
}

object CodeColorPalette {
    val Light = CodeColors(
        addedBg = Color(0xFF1B5E20).copy(alpha = 0.18f),
        removedBg = Color(0xFFB71C1C).copy(alpha = 0.16f),
        contextBg = Color.Transparent,
        emptyBg = Color.Transparent,
        addedFg = Color(0xFF2E7D32),
        removedFg = Color(0xFFC62828),
        contextFg = Color(0xFF424242),
        statusAdded = Color(0xFF2E7D32),
        statusDeleted = Color(0xFFC62828),
        statusModified = Color(0xFF1565C0),
        statusRenamed = Color(0xFF6A1B9A),
        languageDefault = Color(0xFF616161)
    )

    val Dark = CodeColors(
        addedBg = Color(0xFF00C853).copy(alpha = 0.22f),
        removedBg = Color(0xFFFF5252).copy(alpha = 0.20f),
        contextBg = Color.Transparent,
        emptyBg = Color.Transparent,
        addedFg = Color(0xFF69F0AE),
        removedFg = Color(0xFFFF8A80),
        contextFg = Color(0xFFBDBDBD),
        statusAdded = Color(0xFF69F0AE),
        statusDeleted = Color(0xFFFF8A80),
        statusModified = Color(0xFF90CAF9),
        statusRenamed = Color(0xFFCE93D8),
        languageDefault = Color(0xFF9E9E9E)
    )
}

@Composable
fun rememberCodeColors(darkTheme: Boolean = isSystemInDarkTheme()): CodeColors =
    remember(darkTheme) { if (darkTheme) CodeColorPalette.Dark else CodeColorPalette.Light }
