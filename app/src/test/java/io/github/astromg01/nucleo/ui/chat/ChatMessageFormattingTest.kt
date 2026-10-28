package io.github.astromg01.nucleo.ui.chat

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageFormattingTest {
    @Test
    fun rendersHeadingsBulletsAndInlineMarkdownAsReadableText() {
        val rendered = formatChatMarkdown("# Título\n\n- item **forte**\nLinha com `código`")

        assertEquals("Título\n\n• item forte\nLinha com código", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == FontWeight.SemiBold })
    }

    @Test
    fun fencedCodeIsKeptButFenceMarkersAreHidden() {
        val rendered = formatChatMarkdown("Exemplo:\n```kotlin\nval total = 2\n```")

        assertEquals("Exemplo:\nval total = 2", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.fontFamily != null })
    }
}