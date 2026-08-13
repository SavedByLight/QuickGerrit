package com.quickgerrit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Token colours for lightweight, language-aware syntax highlighting.
 * Tuned for readability on both light and dark surfaces.
 */
@Immutable
data class SyntaxColors(
    val plain: Color,
    val keyword: Color,
    val type: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotation: Color,
    val function: Color,
    val property: Color,
    val operator: Color,
    val punctuation: Color
)

object SyntaxColorPalette {
    val Light = SyntaxColors(
        plain = Color(0xFF1E1E1E),
        keyword = Color(0xFF0033B3),
        type = Color(0xFF267F99),
        string = Color(0xFF067D17),
        number = Color(0xFF1750EB),
        comment = Color(0xFF8C8C8C),
        annotation = Color(0xFF9E880D),
        function = Color(0xFF795E26),
        property = Color(0xFF871094),
        operator = Color(0xFF000000),
        punctuation = Color(0xFF000000)
    )

    val Dark = SyntaxColors(
        plain = Color(0xFFD4D4D4),
        keyword = Color(0xFF569CD6),
        type = Color(0xFF4EC9B0),
        string = Color(0xFFCE9178),
        number = Color(0xFFB5CEA8),
        comment = Color(0xFF6A9955),
        annotation = Color(0xFFDCDCAA),
        function = Color(0xFFDCDCAA),
        property = Color(0xFF9CDCFE),
        operator = Color(0xFFD4D4D4),
        punctuation = Color(0xFFD4D4D4)
    )
}

@Composable
fun rememberSyntaxColors(darkTheme: Boolean = isSystemInDarkTheme()): SyntaxColors =
    remember(darkTheme) { if (darkTheme) SyntaxColorPalette.Dark else SyntaxColorPalette.Light }

enum class SourceLanguage {
    KOTLIN, JAVA, PYTHON, JAVASCRIPT, TYPESCRIPT, C_FAMILY, GO, RUST,
    SHELL, XML, HTML, JSON, YAML, PROPERTIES, SQL, MARKDOWN, PLAIN
}

fun languageFromPath(filePath: String): SourceLanguage {
    val name = filePath.substringAfterLast('/').lowercase()
    val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when {
        ext == "kt" || ext == "kts" -> SourceLanguage.KOTLIN
        ext == "java" -> SourceLanguage.JAVA
        ext == "py" || ext == "pyw" -> SourceLanguage.PYTHON
        ext in setOf("js", "mjs", "cjs", "jsx") -> SourceLanguage.JAVASCRIPT
        ext in setOf("ts", "tsx") -> SourceLanguage.TYPESCRIPT
        ext in setOf("c", "h", "cpp", "cc", "cxx", "hpp", "hxx", "hh", "cs", "m", "mm") ->
            SourceLanguage.C_FAMILY
        ext == "go" -> SourceLanguage.GO
        ext == "rs" -> SourceLanguage.RUST
        ext in setOf("sh", "bash", "zsh", "ksh") || name == "makefile" || name.endsWith(".mk") ->
            SourceLanguage.SHELL
        ext in setOf("xml", "xsd", "xsl", "xslt", "svg") -> SourceLanguage.XML
        ext in setOf("html", "htm") -> SourceLanguage.HTML
        ext in setOf("json", "jsonc") -> SourceLanguage.JSON
        ext in setOf("yml", "yaml") -> SourceLanguage.YAML
        ext in setOf("properties", "prop", "ini", "toml") -> SourceLanguage.PROPERTIES
        ext == "sql" -> SourceLanguage.SQL
        ext in setOf("md", "markdown", "rst", "adoc") -> SourceLanguage.MARKDOWN
        else -> SourceLanguage.PLAIN
    }
}

private data class Rule(
    val regex: Regex,
    val style: (SyntaxColors) -> SpanStyle
)

/**
 * Highlight [text] for [language]. Safe for single lines (diff) or full buffers (editor).
 * Uses ordered regex rules; earlier matches win via a simple occupancy scan.
 */
fun highlightSyntax(
    text: String,
    language: SourceLanguage,
    colors: SyntaxColors
): AnnotatedString {
    if (text.isEmpty() || language == SourceLanguage.PLAIN) {
        return AnnotatedString(text)
    }
    val rules = rulesFor(language)
    if (rules.isEmpty()) return AnnotatedString(text)

    val n = text.length
    val styles = arrayOfNulls<SpanStyle>(n)
    val occupied = BooleanArray(n)

    for (rule in rules) {
        rule.regex.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            var free = true
            for (i in start until end) {
                if (occupied[i]) {
                    free = false
                    break
                }
            }
            if (free) {
                val style = rule.style(colors)
                for (i in start until end) {
                    occupied[i] = true
                    styles[i] = style
                }
            }
        }
    }

    return buildAnnotatedString {
        var i = 0
        while (i < n) {
            val style = styles[i]
            var j = i + 1
            while (j < n && styles[j] == style) j++
            if (style != null) {
                withStyle(style) { append(text.substring(i, j)) }
            } else {
                append(text.substring(i, j))
            }
            i = j
        }
    }
}

private fun kw(vararg words: String): String =
    words.joinToString("|") { Regex.escape(it) }

private fun rulesFor(language: SourceLanguage): List<Rule> = when (language) {
    SourceLanguage.KOTLIN -> listOf(
        Rule(Regex("""//.*""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex("""/\*[\s\S]*?\*/""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`""")) {
            SpanStyle(color = it.string)
        },
        Rule(Regex("""@\w+""")) { SpanStyle(color = it.annotation) },
        Rule(
            Regex(
                """\b(?:${kw(
                    "package", "import", "class", "interface", "object", "fun", "val", "var",
                    "if", "else", "when", "for", "while", "do", "return", "break", "continue",
                    "try", "catch", "finally", "throw", "in", "is", "as", "typeof",
                    "true", "false", "null", "this", "super", "typealias", "sealed", "data",
                    "enum", "annotation", "companion", "init", "constructor", "override",
                    "open", "abstract", "final", "private", "protected", "public", "internal",
                    "suspend", "inline", "noinline", "crossinline", "reified", "lateinit",
                    "const", "operator", "infix", "tailrec", "external", "expect", "actual",
                    "by", "where", "out", "vararg"
                )})\b"""
            )
        ) { SpanStyle(color = it.keyword, fontWeight = FontWeight.SemiBold) },
        Rule(
            Regex(
                """\b(?:${kw(
                    "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Char",
                    "String", "Any", "Unit", "Nothing", "List", "MutableList", "Map",
                    "MutableMap", "Set", "MutableSet", "Array", "Pair", "Triple"
                )})\b"""
            )
        ) { SpanStyle(color = it.type) },
        Rule(Regex("""\b\d[\d_]*(\.\d[\d_]*)?([eE][+-]?\d+)?[fFlL]?\b""")) {
            SpanStyle(color = it.number)
        },
        Rule(Regex("""\b([A-Z][A-Za-z0-9_]*)\b""")) { SpanStyle(color = it.type) },
        Rule(Regex("""\b([a-z_][A-Za-z0-9_]*)\s*(?=\()""")) { SpanStyle(color = it.function) }
    )

    SourceLanguage.JAVA -> listOf(
        Rule(Regex("""//.*""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex("""/\*[\s\S]*?\*/""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])'""")) { SpanStyle(color = it.string) },
        Rule(Regex("""@\w+""")) { SpanStyle(color = it.annotation) },
        Rule(
            Regex(
                """\b(?:${kw(
                    "package", "import", "class", "interface", "enum", "extends", "implements",
                    "public", "private", "protected", "static", "final", "abstract", "native",
                    "synchronized", "volatile", "transient", "void", "return", "new", "this",
                    "super", "if", "else", "for", "while", "do", "switch", "case", "default",
                    "break", "continue", "try", "catch", "finally", "throw", "throws",
                    "true", "false", "null", "instanceof", "assert", "record", "sealed",
                    "permits", "var", "yield"
                )})\b"""
            )
        ) { SpanStyle(color = it.keyword, fontWeight = FontWeight.SemiBold) },
        Rule(
            Regex(
                """\b(?:${kw(
                    "int", "long", "short", "byte", "float", "double", "boolean", "char",
                    "String", "Object", "List", "Map", "Set", "Optional"
                )})\b"""
            )
        ) { SpanStyle(color = it.type) },
        Rule(Regex("""\b\d[\d_]*(\.\d[\d_]*)?([eE][+-]?\d+)?[fFdDlL]?\b""")) {
            SpanStyle(color = it.number)
        },
        Rule(Regex("""\b([A-Z][A-Za-z0-9_]*)\b""")) { SpanStyle(color = it.type) },
        Rule(Regex("""\b([a-z_][A-Za-z0-9_]*)\s*(?=\()""")) { SpanStyle(color = it.function) }
    )

    SourceLanguage.PYTHON -> listOf(
        Rule(Regex("""#.*""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex("""(?s)'''.*?'''|\"\"\".*?\"\"\"""")) { SpanStyle(color = it.string) },
        Rule(Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""")) { SpanStyle(color = it.string) },
        Rule(Regex("""@\w+""")) { SpanStyle(color = it.annotation) },
        Rule(
            Regex(
                """\b(?:${kw(
                    "def", "class", "return", "if", "elif", "else", "for", "while", "break",
                    "continue", "pass", "import", "from", "as", "try", "except", "finally",
                    "raise", "with", "yield", "lambda", "global", "nonlocal", "assert",
                    "True", "False", "None", "and", "or", "not", "in", "is", "del",
                    "async", "await", "match", "case"
                )})\b"""
            )
        ) { SpanStyle(color = it.keyword, fontWeight = FontWeight.SemiBold) },
        Rule(Regex("""\b\d[\d_]*(\.\d[\d_]*)?([eE][+-]?\d+)?\b""")) { SpanStyle(color = it.number) },
        Rule(Regex("""\b([A-Z][A-Za-z0-9_]*)\b""")) { SpanStyle(color = it.type) },
        Rule(Regex("""\b([a-z_][A-Za-z0-9_]*)\s*(?=\()""")) { SpanStyle(color = it.function) }
    )

    SourceLanguage.JAVASCRIPT, SourceLanguage.TYPESCRIPT -> listOf(
        Rule(Regex("""//.*""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex("""/\*[\s\S]*?\*/""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`""")) {
            SpanStyle(color = it.string)
        },
        Rule(
            Regex(
                """\b(?:${kw(
                    "function", "var", "let", "const", "class", "extends", "constructor",
                    "return", "if", "else", "for", "while", "do", "switch", "case", "default",
                    "break", "continue", "try", "catch", "finally", "throw", "new", "this",
                    "super", "typeof", "instanceof", "in", "of", "void", "delete",
                    "true", "false", "null", "undefined", "import", "export", "from", "as",
                    "async", "await", "yield", "static", "get", "set", "enum", "interface",
                    "type", "implements", "public", "private", "protected", "readonly",
                    "namespace", "module", "declare", "abstract"
                )})\b"""
            )
        ) { SpanStyle(color = it.keyword, fontWeight = FontWeight.SemiBold) },
        Rule(Regex("""\b\d[\d_]*(\.\d[\d_]*)?([eE][+-]?\d+)?\b""")) { SpanStyle(color = it.number) },
        Rule(Regex("""\b([A-Z][A-Za-z0-9_]*)\b""")) { SpanStyle(color = it.type) },
        Rule(Regex("""\b([a-z_$][A-Za-z0-9_$]*)\s*(?=\()""")) { SpanStyle(color = it.function) }
    )

    SourceLanguage.C_FAMILY -> listOf(
        Rule(Regex("""//.*""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex("""/\*[\s\S]*?\*/""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex("""#\s*\w+.*""")) { SpanStyle(color = it.annotation) },
        Rule(Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])'""")) { SpanStyle(color = it.string) },
        Rule(
            Regex(
                """\b(?:${kw(
                    "auto", "break", "case", "char", "const", "continue", "default", "do",
                    "double", "else", "enum", "extern", "float", "for", "goto", "if", "int",
                    "long", "register", "return", "short", "signed", "sizeof", "static",
                    "struct", "switch", "typedef", "union", "unsigned", "void", "volatile",
                    "while", "class", "public", "private", "protected", "namespace", "using",
                    "template", "typename", "virtual", "override", "final", "nullptr",
                    "true", "false", "bool", "new", "delete", "this", "friend", "inline",
                    "constexpr", "noexcept"
                )})\b"""
            )
        ) { SpanStyle(color = it.keyword, fontWeight = FontWeight.SemiBold) },
        Rule(Regex("""\b\d[\d']*(\.\d[\d']*)?([eE][+-]?\d+)?[uUlLfF]*\b""")) {
            SpanStyle(color = it.number)
        },
        Rule(Regex("""\b([A-Z][A-Za-z0-9_]*)\b""")) { SpanStyle(color = it.type) },
        Rule(Regex("""\b([a-z_][A-Za-z0-9_]*)\s*(?=\()""")) { SpanStyle(color = it.function) }
    )

    SourceLanguage.GO -> listOf(
        Rule(Regex("""//.*""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex("""/\*[\s\S]*?\*/""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex(""""(?:\\.|[^"\\])*"|`[^`]*`""")) { SpanStyle(color = it.string) },
        Rule(
            Regex(
                """\b(?:${kw(
                    "package", "import", "func", "return", "var", "const", "type", "struct",
                    "interface", "map", "chan", "go", "defer", "if", "else", "for", "range",
                    "switch", "case", "default", "break", "continue", "fallthrough", "select",
                    "true", "false", "nil", "iota"
                )})\b"""
            )
        ) { SpanStyle(color = it.keyword, fontWeight = FontWeight.SemiBold) },
        Rule(Regex("""\b\d[\d_]*(\.\d[\d_]*)?\b""")) { SpanStyle(color = it.number) },
        Rule(Regex("""\b([A-Z][A-Za-z0-9_]*)\b""")) { SpanStyle(color = it.type) },
        Rule(Regex("""\b([a-z_][A-Za-z0-9_]*)\s*(?=\()""")) { SpanStyle(color = it.function) }
    )

    SourceLanguage.RUST -> listOf(
        Rule(Regex("""//.*""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex("""/\*[\s\S]*?\*/""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])'""")) { SpanStyle(color = it.string) },
        Rule(Regex("""#\[.*?\]|#!\[.*?\]""")) { SpanStyle(color = it.annotation) },
        Rule(
            Regex(
                """\b(?:${kw(
                    "fn", "let", "mut", "const", "static", "struct", "enum", "impl", "trait",
                    "pub", "use", "mod", "crate", "self", "super", "return", "if", "else",
                    "match", "loop", "while", "for", "in", "break", "continue", "where",
                    "as", "ref", "move", "async", "await", "dyn", "true", "false", "Some",
                    "None", "Ok", "Err", "type", "unsafe", "extern"
                )})\b"""
            )
        ) { SpanStyle(color = it.keyword, fontWeight = FontWeight.SemiBold) },
        Rule(Regex("""\b\d[\d_]*(\.\d[\d_]*)?\b""")) { SpanStyle(color = it.number) },
        Rule(Regex("""\b([A-Z][A-Za-z0-9_]*)\b""")) { SpanStyle(color = it.type) },
        Rule(Regex("""\b([a-z_][A-Za-z0-9_]*)\s*(?=\()""")) { SpanStyle(color = it.function) }
    )

    SourceLanguage.SHELL -> listOf(
        Rule(Regex("""#.*""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""")) { SpanStyle(color = it.string) },
        Rule(
            Regex(
                """\b(?:${kw(
                    "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case",
                    "esac", "function", "return", "in", "select", "time", "until", "export",
                    "local", "readonly", "declare", "set", "unset", "shift", "trap", "source",
                    "true", "false"
                )})\b"""
            )
        ) { SpanStyle(color = it.keyword, fontWeight = FontWeight.SemiBold) },
        Rule(Regex("""\$\{?\w+\}?""")) { SpanStyle(color = it.property) },
        Rule(Regex("""\b\d+\b""")) { SpanStyle(color = it.number) }
    )

    SourceLanguage.XML, SourceLanguage.HTML -> listOf(
        Rule(Regex("""<!--[\s\S]*?-->""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex("""</?[A-Za-z][\w:-]*""")) { SpanStyle(color = it.keyword, fontWeight = FontWeight.SemiBold) },
        Rule(Regex("""[A-Za-z_:][\w:.-]*(?=\s*=)""")) { SpanStyle(color = it.property) },
        Rule(Regex(""""[^"]*"|'[^']*'""")) { SpanStyle(color = it.string) },
        Rule(Regex("""&[a-zA-Z]+;|&#\d+;""")) { SpanStyle(color = it.number) }
    )

    SourceLanguage.JSON -> listOf(
        Rule(Regex(""""(?:\\.|[^"\\])*"(?=\s*:)""")) { SpanStyle(color = it.property) },
        Rule(Regex(""""(?:\\.|[^"\\])*"""")) { SpanStyle(color = it.string) },
        Rule(Regex("""\b(?:true|false|null)\b""")) {
            SpanStyle(color = it.keyword, fontWeight = FontWeight.SemiBold)
        },
        Rule(Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""")) { SpanStyle(color = it.number) }
    )

    SourceLanguage.YAML -> listOf(
        Rule(Regex("""#.*""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex("""^(\s*)([A-Za-z0-9_.-]+)(?=\s*:)""", RegexOption.MULTILINE)) {
            SpanStyle(color = it.property)
        },
        Rule(Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""")) { SpanStyle(color = it.string) },
        Rule(Regex("""\b(?:true|false|null|yes|no|on|off)\b""", RegexOption.IGNORE_CASE)) {
            SpanStyle(color = it.keyword, fontWeight = FontWeight.SemiBold)
        },
        Rule(Regex("""\b\d+(\.\d+)?\b""")) { SpanStyle(color = it.number) }
    )

    SourceLanguage.PROPERTIES -> listOf(
        Rule(Regex("""[#!].*""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex("""^[^=:\s][^=:]*""", RegexOption.MULTILINE)) { SpanStyle(color = it.property) },
        Rule(Regex(""""(?:\\.|[^"\\])*"""")) { SpanStyle(color = it.string) }
    )

    SourceLanguage.SQL -> listOf(
        Rule(Regex("""--.*""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex("""/\*[\s\S]*?\*/""")) { SpanStyle(color = it.comment, fontStyle = FontStyle.Italic) },
        Rule(Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""")) { SpanStyle(color = it.string) },
        Rule(
            Regex(
                """\b(?:${kw(
                    "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "INSERT", "INTO", "VALUES",
                    "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "INDEX",
                    "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "AS", "ORDER", "BY",
                    "GROUP", "HAVING", "LIMIT", "OFFSET", "DISTINCT", "NULL", "TRUE", "FALSE",
                    "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT", "DEFAULT",
                    "UNION", "ALL", "EXISTS", "IN", "BETWEEN", "LIKE", "IS", "CASE", "WHEN",
                    "THEN", "ELSE", "END"
                )})\b""",
                RegexOption.IGNORE_CASE
            )
        ) { SpanStyle(color = it.keyword, fontWeight = FontWeight.SemiBold) },
        Rule(Regex("""\b\d+(\.\d+)?\b""")) { SpanStyle(color = it.number) }
    )

    SourceLanguage.MARKDOWN -> listOf(
        Rule(Regex("""(?m)^#{1,6}\s.*$""")) {
            SpanStyle(color = it.keyword, fontWeight = FontWeight.Bold)
        },
        Rule(Regex("""`[^`]+`""")) { SpanStyle(color = it.string) },
        Rule(Regex("""\*\*[^*]+\*\*|__[^_]+__""")) {
            SpanStyle(color = it.plain, fontWeight = FontWeight.Bold)
        },
        Rule(Regex("""\*[^*]+\*|_[^_]+_""")) {
            SpanStyle(color = it.plain, fontStyle = FontStyle.Italic)
        },
        Rule(Regex("""\[([^\]]+)\]\([^)]+\)""")) { SpanStyle(color = it.property) }
    )

    SourceLanguage.PLAIN -> emptyList()
}
