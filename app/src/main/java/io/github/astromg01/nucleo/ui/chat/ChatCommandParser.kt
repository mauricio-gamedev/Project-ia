package io.github.astromg01.nucleo.ui.chat

import java.util.Locale

internal enum class ChatCommandType {
    NEW,
    CLEAR,
    DELETE,
    HELP,
    CHATS,
    UNKNOWN,
}

internal data class ParsedChatCommand(
    val type: ChatCommandType,
    val argument: String = "",
)

/** Parses only explicit slash commands; ordinary user messages are left untouched. */
internal object ChatCommandParser {
    fun parse(input: String): ParsedChatCommand? {
        val trimmed = input.trim()
        if (!trimmed.startsWith("/")) return null

        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        val token = parts.first().lowercase(Locale.ROOT)
        val argument = parts.getOrNull(1).orEmpty()
        val type = when (token) {
            "/new", "/novo" -> ChatCommandType.NEW
            "/clear", "/limpar" -> ChatCommandType.CLEAR
            "/delete", "/excluir", "/apagar" -> ChatCommandType.DELETE
            "/help", "/ajuda" -> ChatCommandType.HELP
            "/chats", "/conversas" -> ChatCommandType.CHATS
            else -> ChatCommandType.UNKNOWN
        }
        return ParsedChatCommand(type, argument)
    }
}
