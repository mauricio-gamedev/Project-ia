package io.github.astromg01.nucleo.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatCommandParserTest {
    @Test
    fun leavesNormalMessagesUntouched() {
        assertNull(ChatCommandParser.parse("Vamos corrigir esse projeto"))
        assertNull(ChatCommandParser.parse("   "))
    }

    @Test
    fun recognizesEnglishAndPortugueseAliases() {
        assertEquals(ChatCommandType.CLEAR, ChatCommandParser.parse("/clear")?.type)
        assertEquals(ChatCommandType.CLEAR, ChatCommandParser.parse("/LIMPAR")?.type)
        assertEquals(ChatCommandType.NEW, ChatCommandParser.parse("/novo")?.type)
        assertEquals(ChatCommandType.DELETE, ChatCommandParser.parse("/delete")?.type)
        assertEquals(ChatCommandType.CHATS, ChatCommandParser.parse("/conversas")?.type)
        assertEquals(ChatCommandType.HELP, ChatCommandParser.parse("/ajuda")?.type)
    }

    @Test
    fun unknownSlashCommandsAreRecognizedLocally() {
        val command = ChatCommandParser.parse("/not-a-command args")
        assertEquals(ChatCommandType.UNKNOWN, command?.type)
        assertEquals("args", command?.argument)
    }
}
