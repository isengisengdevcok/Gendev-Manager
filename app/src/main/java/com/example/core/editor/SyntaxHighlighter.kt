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

    private const val MAX_HIGHLIGHT_CHARS = 20_000
    private const val MAX_SPANS_PER_RULE = 900

    private val xmlTagRegex = Regex("""</?[a-zA-Z0-9_:-]+""")
    private val xmlAttrRegex = Regex("""[a-zA-Z0-9_:-]+(?==)""")
    private val xmlStringRegex = Regex(""""[^"\n]{0,256}"""")
    private val xmlCommentRegex = Regex("""<!--[^\n]{0,300}-->""")

    private val jsonKeyRegex = Regex(""""([^"\\\n]|\\.){0,128}"(?=\s*:)""")
    private val jsonStringRegex = Regex("""(?<=:\s*)"([^"\\\n]|\\.){0,256}"""")
    private val jsonKeywordRegex = Regex("""\b(true|false|null)\b""")
    private val jsonNumberRegex = Regex("""\b-?\d+(\.\d+)?\b""")

    private val smaliDirectiveRegex = Regex("""\.[a-zA-Z0-9_-]+""")
    private val smaliRegisterRegex = Regex("""\b[vp]\d+\b""")
    private val smaliTypeRegex = Regex("""L[a-zA-Z0-9_/$]+;""")
    private val smaliStringRegex = Regex(""""([^"\\\n]|\\.){0,256}"""")
    private val lineCommentRegex = Regex("""#.*""")

    private val shellKeywordRegex = Regex("""\b(echo|if|then|else|fi|for|do|done|while|export|cd|ls|grep|chmod|chown|su|pm|am|getprop|uname)\b""")
    private val shellVarRegex = Regex("""\$[A-Za-z0-9_{}]+""")

    private val jsKeywordRegex = Regex("""\b(const|let|var|function|return|if|else|for|while|import|export|class|async|await)\b""")
    private val jsStringRegex = Regex("""["'][^"'\n]{0,256}["']""")
    private val jsNumberRegex = Regex("""\b\d+(\.\d+)?(px|dp|em|rem|%)?\b""")
    private val jsCommentRegex = Regex("""//.*""")

    private val propSectionRegex = Regex("""^\[[^\]]+\]""", RegexOption.MULTILINE)
    private val propKeyRegex = Regex("""^[A-Za-z0-9_.-]+(?==)""", RegexOption.MULTILINE)
    private val propCommentRegex = Regex("""^[#!].*""", RegexOption.MULTILINE)

    private val mdHeadingRegex = Regex("""^#{1,6}\s.*""", RegexOption.MULTILINE)
    private val mdCodeRegex = Regex("""`[^`\n]+`""")
    private val mdBoldRegex = Regex("""\*\*[^*\n]+\*\*""")

    fun highlight(
        code: String,
        language: EditorLanguage,
        baseColor: Color,
        searchQuery: String = "",
        highlightingEnabled: Boolean = true
    ): AnnotatedString {
        if (!highlightingEnabled && searchQuery.isBlank()) {
            return buildAnnotatedString {
                append(code)
                addStyle(SpanStyle(color = baseColor), 0, code.length)
            }
        }
        val splitIdx = minOf(code.length, MAX_HIGHLIGHT_CHARS)
        val safeCode = code.substring(0, splitIdx)
        val tail = if (code.length > splitIdx) code.substring(splitIdx) else ""
        return buildAnnotatedString {
            append(safeCode)
            addStyle(SpanStyle(color = baseColor), 0, safeCode.length)

            if (highlightingEnabled) {
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
            }

            if (searchQuery.isNotBlank()) {
                var idx = safeCode.indexOf(searchQuery, ignoreCase = true)
                var count = 0
                while (idx >= 0 && count < MAX_SPANS_PER_RULE) {
                    addStyle(
                        SpanStyle(
                            background = Color(0x88FBBF24),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        ),
                        idx,
                        idx + searchQuery.length
                    )
                    count++
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
        var count = 0
        for (m in regex.findAll(text)) {
            if (count >= MAX_SPANS_PER_RULE) break
            addStyle(style, m.range.first, m.range.last + 1)
            count++
        }
    }

    private fun AnnotatedString.Builder.applyXmlRules(text: String) {
        applyRegex(xmlTagRegex, SpanStyle(color = SyntaxTag, fontWeight = FontWeight.SemiBold), text)
        applyRegex(xmlAttrRegex, SpanStyle(color = SyntaxAttribute), text)
        applyRegex(xmlStringRegex, SpanStyle(color = SyntaxString), text)
        applyRegex(xmlCommentRegex, SpanStyle(color = SyntaxComment), text)
    }

    private fun AnnotatedString.Builder.applyJsonRules(text: String) {
        applyRegex(jsonKeyRegex, SpanStyle(color = SyntaxTag, fontWeight = FontWeight.Medium), text)
        applyRegex(jsonStringRegex, SpanStyle(color = SyntaxString), text)
        applyRegex(jsonKeywordRegex, SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold), text)
        applyRegex(jsonNumberRegex, SpanStyle(color = SyntaxNumber), text)
    }

    private fun AnnotatedString.Builder.applySmaliRules(text: String) {
        applyRegex(smaliDirectiveRegex, SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold), text)
        applyRegex(smaliRegisterRegex, SpanStyle(color = SyntaxRegister), text)
        applyRegex(smaliTypeRegex, SpanStyle(color = SyntaxType), text)
        applyRegex(smaliStringRegex, SpanStyle(color = SyntaxString), text)
        applyRegex(lineCommentRegex, SpanStyle(color = SyntaxComment), text)
    }

    private fun AnnotatedString.Builder.applyShellRules(text: String) {
        applyRegex(shellKeywordRegex, SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold), text)
        applyRegex(shellVarRegex, SpanStyle(color = SyntaxRegister), text)
        applyRegex(xmlStringRegex, SpanStyle(color = SyntaxString), text)
        applyRegex(lineCommentRegex, SpanStyle(color = SyntaxComment), text)
    }

    private fun AnnotatedString.Builder.applyJsCssRules(text: String) {
        applyRegex(jsKeywordRegex, SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold), text)
        applyRegex(jsStringRegex, SpanStyle(color = SyntaxString), text)
        applyRegex(jsNumberRegex, SpanStyle(color = SyntaxNumber), text)
        applyRegex(jsCommentRegex, SpanStyle(color = SyntaxComment), text)
    }

    private fun AnnotatedString.Builder.applyPropertiesRules(text: String) {
        applyRegex(propSectionRegex, SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Bold), text)
        applyRegex(propKeyRegex, SpanStyle(color = SyntaxTag), text)
        applyRegex(propCommentRegex, SpanStyle(color = SyntaxComment), text)
    }

    private fun AnnotatedString.Builder.applyMarkdownRules(text: String) {
        applyRegex(mdHeadingRegex, SpanStyle(color = SyntaxTag, fontWeight = FontWeight.Bold), text)
        applyRegex(mdCodeRegex, SpanStyle(color = SyntaxString), text)
        applyRegex(mdBoldRegex, SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Bold), text)
    }
}
