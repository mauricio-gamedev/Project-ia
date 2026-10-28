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
 * instead of interpreting HTML or remote resources.
 */
internal fun formatChatMarkdown(
    markdown: String,
    headingColor: Color = Color(0xFFC6B9FF),
): AnnotatedString {
    val lines = markdown.split('\n')
    return buildAnnotatedString {
        var inCodeBlock = false
        var hasRenderedLine = false

        lines.forEach { rawLine ->
            if (rawLine.trimStart().startsWith("```")) {
                inCodeBlock = !inCodeBlock
                return@forEach
            }

            if (hasRenderedLine) append('\n')
            hasRenderedLine = true

            if (inCodeBlock) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFF202027),
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
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = headingColor,
                    ),
                ) {
                    appendInlineMarkdown(line)
                }
                return@forEach
            }

            val bullet = Regex("^(\\s*)[-*]\\s+").find(line)
            if (bullet != null) {
                append(bullet.groupValues[1])
                append("• ")
                line = line.removeRange(0, bullet.value.length)
            }
            appendInlineMarkdown(line)
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
    var index = 0
    while (index < text.length) {
        val token = when {
            text.startsWith("**", index) -> "**"
            text.startsWith("__", index) -> "__"
            text[index] == '*' -> "*"
            text[index] == '_' -> "_"
            text[index] == '`' -> "`"
            else -> null
        }

        val close = token?.let { text.indexOf(it, index + it.length) } ?: -1
        if (token != null && close > index + token.length) {
            val value = text.substring(index + token.length, close)
            when (token) {
                "**", "__" -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(value) }
                "*", "_" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(value) }
                "`" -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFF202027),
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