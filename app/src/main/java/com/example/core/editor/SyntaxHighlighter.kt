package com.example.core.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.example.core.ui.theme.SyntaxAttribute
import com.example.core.ui.theme.SyntaxComment
import com.example.core.ui.theme.SyntaxKeyword
import com.example.core.ui.theme.SyntaxNumber
import com.example.core.ui.theme.SyntaxRegister
import com.example.core.ui.theme.SyntaxString
import com.example.core.ui.theme.SyntaxTag
import com.example.core.ui.theme.SyntaxType

object SyntaxHighlighter {

    fun highlight(
        code: String,
        language: EditorLanguage,
        baseColor: Color,
        searchQuery: String = ""
    ): AnnotatedString {
        // Highlight up to 35_000 chars for 60fps responsiveness while keeping full length for OffsetMapping.Identity
        val splitIdx = minOf(code.length, 35_000)
        val safeCode = code.substring(0, splitIdx)
        val tail = if (code.length > splitIdx) code.substring(splitIdx) else ""
        return buildAnnotatedString {
            append(safeCode)
            addStyle(SpanStyle(color = baseColor), 0, safeCode.length)

            when (language) {
                EditorLanguage.XML, EditorLanguage.HTML -> applyXmlRules(safeCode)
                EditorLanguage.JSON -> applyJsonRules(safeCode)
                EditorLanguage.SMALI -> applySmaliRules(safeCode)
                EditorLanguage.SHELL -> applyShellRules(safeCode)
                EditorLanguage.JAVASCRIPT, EditorLanguage.CSS -> applyJsCssRules(safeCode)
                EditorLanguage.PROPERTIES, EditorLanguage.INI -> applyPropertiesRules(safeCode)
                EditorLanguage.MARKDOWN -> applyMarkdownRules(safeCode)
                else -> Unit
            }

            if (searchQuery.isNotBlank()) {
                var idx = safeCode.indexOf(searchQuery, ignoreCase = true)
                while (idx >= 0) {
                    addStyle(
                        SpanStyle(
                            background = Color(0x88FBBF24),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        ),
                        idx,
                        idx + searchQuery.length
                    )
                    idx = safeCode.indexOf(searchQuery, idx + searchQuery.length, ignoreCase = true)
                }
            }

            if (tail.isNotEmpty()) {
                val tailStart = length
                append(tail)
                addStyle(SpanStyle(color = baseColor), tailStart, length)
            }
        }
    }

    private fun AnnotatedString.Builder.applyRegex(regex: Regex, style: SpanStyle, text: String) {
        for (m in regex.findAll(text)) {
            addStyle(style, m.range.first, m.range.last + 1)
        }
    }

    private fun AnnotatedString.Builder.applyXmlRules(text: String) {
        applyRegex(Regex("""</?[a-zA-Z0-9_:-]+"""), SpanStyle(color = SyntaxTag, fontWeight = FontWeight.SemiBold), text)
        applyRegex(Regex("""[a-zA-Z0-9_:-]+(?==)"""), SpanStyle(color = SyntaxAttribute), text)
        applyRegex(Regex(""""[^"]*""""), SpanStyle(color = SyntaxString), text)
        applyRegex(Regex("""<!--[\s\S]*?-->"""), SpanStyle(color = SyntaxComment), text)
    }

    private fun AnnotatedString.Builder.applyJsonRules(text: String) {
        applyRegex(Regex(""""([^"\\]|\\.)*"(?=\s*:)"""), SpanStyle(color = SyntaxTag, fontWeight = FontWeight.Medium), text)
        applyRegex(Regex("""(?<=:\s*)"([^"\\]|\\.)*""""), SpanStyle(color = SyntaxString), text)
        applyRegex(Regex("""\b(true|false|null)\b"""), SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold), text)
        applyRegex(Regex("""\b-?\d+(\.\d+)?\b"""), SpanStyle(color = SyntaxNumber), text)
    }

    private fun AnnotatedString.Builder.applySmaliRules(text: String) {
        applyRegex(Regex("""\.[a-zA-Z0-9_-]+"""), SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold), text)
        applyRegex(Regex("""\b[vp]\d+\b"""), SpanStyle(color = SyntaxRegister), text)
        applyRegex(Regex("""L[a-zA-Z0-9_/$]+;"""), SpanStyle(color = SyntaxType), text)
        applyRegex(Regex(""""([^"\\]|\\.)*""""), SpanStyle(color = SyntaxString), text)
        applyRegex(Regex("""#.*"""), SpanStyle(color = SyntaxComment), text)
    }

    private fun AnnotatedString.Builder.applyShellRules(text: String) {
        applyRegex(Regex("""\b(echo|if|then|else|fi|for|do|done|while|export|cd|ls|grep|chmod|chown|su|pm|am|getprop|uname)\b"""), SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold), text)
        applyRegex(Regex("""\$[A-Za-z0-9_{}]+"""), SpanStyle(color = SyntaxRegister), text)
        applyRegex(Regex(""""[^"]*""""), SpanStyle(color = SyntaxString), text)
        applyRegex(Regex("""#.*"""), SpanStyle(color = SyntaxComment), text)
    }

    private fun AnnotatedString.Builder.applyJsCssRules(text: String) {
        applyRegex(Regex("""\b(const|let|var|function|return|if|else|for|while|import|export|class|async|await)\b"""), SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold), text)
        applyRegex(Regex("""["'][^"']*["']"""), SpanStyle(color = SyntaxString), text)
        applyRegex(Regex("""\b\d+(\.\d+)?(px|dp|em|rem|%)?\b"""), SpanStyle(color = SyntaxNumber), text)
        applyRegex(Regex("""//.*|/\*[\s\S]*?\*/"""), SpanStyle(color = SyntaxComment), text)
    }

    private fun AnnotatedString.Builder.applyPropertiesRules(text: String) {
        applyRegex(Regex("""^\[[^\]]+\]""", RegexOption.MULTILINE), SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Bold), text)
        applyRegex(Regex("""^[A-Za-z0-9_.-]+(?==)""", RegexOption.MULTILINE), SpanStyle(color = SyntaxTag), text)
        applyRegex(Regex("""^[#!].*""", RegexOption.MULTILINE), SpanStyle(color = SyntaxComment), text)
    }

    private fun AnnotatedString.Builder.applyMarkdownRules(text: String) {
        applyRegex(Regex("""^#{1,6}\s.*""", RegexOption.MULTILINE), SpanStyle(color = SyntaxTag, fontWeight = FontWeight.Bold), text)
        applyRegex(Regex("""`[^`]+`"""), SpanStyle(color = SyntaxString), text)
        applyRegex(Regex("""\*\*[^*]+\*\*"""), SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Bold), text)
    }
}
