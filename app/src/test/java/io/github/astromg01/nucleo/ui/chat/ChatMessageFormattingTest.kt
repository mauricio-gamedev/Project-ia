package io.github.astromg01.nucleo.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageFormattingTest {
    @Test
    fun rendersHeadingsBulletsAndInlineMarkdownAsReadableText() {
        val rendered = formatChatMarkdown("# Título\n\n- item **forte**\nLinha com \u0060código\u0060")

        assertEquals("Título\n\n• item forte\nLinha com código", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == FontWeight.SemiBold })
    }

    @Test
    fun fencedCodeIsKeptButFenceMarkersAreHidden() {
        val rendered = formatChatMarkdown("Exemplo:\n\u0060\u0060\u0060kotlin\nval total = 2\n\u0060\u0060\u0060")

        assertEquals("Exemplo:\nval total = 2", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.fontFamily != null })
    }

    @Test
    fun shorterFenceInsideLongerFenceDoesNotCloseCodeBlock() {
        val rendered = formatChatMarkdown(
            "\u0060\u0060\u0060\u0060\n\u0060\u0060\u0060 is code, not the closing fence\nval total = 2\n\u0060\u0060\u0060\u0060",
        )

        assertEquals("\u0060\u0060\u0060 is code, not the closing fence\nval total = 2", rendered.text)
    }

    @Test
    fun codeForegroundAndBackgroundCanBeMatchedToLightTheme() {
        val foreground = Color(0xFF222222)
        val background = Color(0xFFE8E8EE)
        val rendered = formatChatMarkdown(
            "\u0060code\u0060",
            codeForeground = foreground,
            codeBackground = background,
        )

        assertEquals("code", rendered.text)
        assertTrue(rendered.spanStyles.any {
            it.item.color == foreground && it.item.background == background
        })
    }

    @Test
    fun escapedDelimitersRemainLiteral() {
        val rendered = formatChatMarkdown("\\*literal\\*")

        assertEquals("*literal*", rendered.text)
    }

    @Test
    fun underscoresInsideIdentifiersRemainLiteral() {
        val rendered = formatChatMarkdown("foo_bar_baz")

        assertEquals("foo_bar_baz", rendered.text)
    }
}