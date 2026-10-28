package io.github.astromg01.nucleo.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Lightweight, safe Markdown presentation for chat text. It deliberately renders content as text
 * instead of interpreting HTML or loading remote resources.
 */
internal fun formatChatMarkdown(
    markdown: String,
    headingColor: Color = Color(0xFFC6B9FF),
    codeBackground: Color = Color(0xFF202027),
    codeForeground: Color = Color(0xFFF7F7FA),
): AnnotatedString {
    val lines = markdown.split('\n')
    return buildAnnotatedString {
        var inCodeBlock = false
        var codeFenceLength = 0
        var hasRenderedLine = false

        lines.forEach { rawLine ->
            val trimmed = rawLine.trimStart()
            val openingFenceLength = trimmed.takeWhile { it == '\u0060' }.length
            if (openingFenceLength >= 3) {
                val fenceSuffix = trimmed.drop(openingFenceLength)
                if (!inCodeBlock && !fenceSuffix.contains('\u0060')) {
                    inCodeBlock = true
                    codeFenceLength = openingFenceLength
                    return@forEach
                }
                if (inCodeBlock && openingFenceLength >= codeFenceLength && fenceSuffix.isBlank()) {
                    inCodeBlock = false
                    codeFenceLength = 0
                    return@forEach
                }
            }

            if (hasRenderedLine) append('\n')
            hasRenderedLine = true

            if (inCodeBlock) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = codeForeground,
                        background = codeBackground,
                    ),
                ) {
                    append(rawLine)
                }
                return@forEach
            }

            val heading = Regex("^#{1,3}\\s+").find(rawLine)?.value
            var line = if (heading != null) rawLine.removePrefix(heading) else rawLine
            if (heading != null) {
                withStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = headingColor),
                ) {
                    appendInlineMarkdown(line, codeBackground, codeForeground)
                }
                return@forEach
            }

            val bullet = Regex("^(\\s*)[-*]\\s+").find(line)
            if (bullet != null) {
                append(bullet.groupValues[1])
                append("• ")
                line = line.removeRange(0, bullet.value.length)
            }
            appendInlineMarkdown(line, codeBackground, codeForeground)
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    codeBackground: Color,
    codeForeground: Color,
) {
    var index = 0
    while (index < text.length) {
        // Preserve escaped Markdown punctuation as literal text.
        if (text[index] == '\\' && index + 1 < text.length &&
            (text[index + 1] in "\\*_{}[]()#+-.!>" || text[index + 1] == '\u0060')
        ) {
            append(text[index + 1])
            index += 2
            continue
        }

        val token = when {
            text.startsWith("**", index) -> "**"
            text.startsWith("__", index) -> "__"
            text[index] == '*' -> "*"
            text[index] == '_' && (index == 0 || !text[index - 1].isLetterOrDigit()) -> "_"
            text[index] == '\u0060' -> "\u0060"
            else -> null
        }

        val close = token?.let { text.indexOf(it, index + it.length) } ?: -1
        val underscoreDelimiter = token == "_" || token == "__"
        val closingBoundaryValid = !underscoreDelimiter ||
            close < 0 ||
            close + token!!.length >= text.length ||
            !text[close + token.length].isLetterOrDigit()

        if (token != null && close > index + token.length && closingBoundaryValid) {
            val value = text.substring(index + token.length, close)
            when (token) {
                "**", "__" -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(value) }
                "*", "_" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(value) }
                "\u0060" -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = codeForeground,
                        background = codeBackground,
                    ),
                ) { append(value) }
            }
            index = close + token.length
        } else {
            append(text[index])
            index++
        }
    }
}