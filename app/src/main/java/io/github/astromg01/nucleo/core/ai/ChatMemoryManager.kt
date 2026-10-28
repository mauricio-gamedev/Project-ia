package io.github.astromg01.nucleo.core.ai

import io.github.astromg01.nucleo.data.local.ChatMessageEntity
import io.github.astromg01.nucleo.data.local.MemoryEntity
import java.util.UUID

/** Local memory policy for the main conversation; task/planning records are excluded. */
class ChatMemoryManager {
    fun createExplicitMemory(userMessage: String): MemoryEntity? {
        val match = MEMORY_REQUESTS.firstNotNullOfOrNull { it.find(userMessage) } ?: return null
        val value = match.groupValues.getOrNull(1)?.trim()?.trim('"', ' ', '.', '!', '?').orEmpty()
        if (value.length !in 8..600 || CREDENTIAL_HINTS.any { it.containsMatchIn(value) }) return null
        val preference = PREFERENCE_HINTS.any { value.startsWith(it, ignoreCase = true) }
        val kind = if (preference) "chat_preference" else "chat_fact"
        val stableId = UUID.nameUUIDFromBytes((kind + ":" + value.lowercase()).toByteArray(Charsets.UTF_8)).toString()
        return MemoryEntity(id = "chat-" + stableId, kind = kind, title = value.take(90), content = value)
    }

    fun buildPromptContext(query: String, summary: MemoryEntity?, savedMemories: List<MemoryEntity>, maxChars: Int = 5_000): String {
        require(maxChars >= 256)
        val queryTerms = terms(query)
        val facts = savedMemories.filter { it.kind == "chat_fact" || it.kind == "chat_preference" }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<MemoryEntity> {
                queryTerms.intersect(terms(it.content + " " + it.title)).size * 10 +
                    if (it.kind == "chat_preference") 4 else 0
            }.thenByDescending { it.updatedAt })
        return buildString {
            if (summary?.kind == "chat_summary" && summary.content.isNotBlank()) {
                appendLine("Resumo durável da conversa principal (contexto aproximado; prevalece o histórico recente):")
                appendLine(summary.content.take(3_200))
            }
            if (facts.isNotEmpty() && length < maxChars) {
                if (isNotEmpty()) appendLine()
                appendLine("Fatos e preferências que o usuário pediu para guardar localmente:")
                facts.take(8).forEach { memory ->
                    val entry = "- [" + memory.kind + "] " + memory.content.take(700) + "\n"
                    if (length + entry.length <= maxChars) append(entry)
                }
            }
        }.take(maxChars)
    }

    fun shouldSummarize(summary: MemoryEntity?, messages: List<ChatMessageEntity>): Boolean {
        val chat = messages.filter { it.role == "user" || it.role == "assistant" }
        val newer = if (summary == null) chat else chat.filter { it.createdAt > summary.updatedAt }
        return newer.size >= 12 && newer.count { it.role == "user" } >= 6 && newer.lastOrNull()?.role == "assistant"
    }

    fun summaryInput(summary: MemoryEntity?, messages: List<ChatMessageEntity>): String = buildString {
        if (summary != null && summary.content.isNotBlank()) {
            appendLine("Resumo anterior:")
            appendLine(summary.content.take(3_200))
            appendLine()
        }
        appendLine("Novas mensagens em ordem cronológica:")
        messages.filter { it.role == "user" || it.role == "assistant" }.takeLast(36).forEach { message ->
            val speaker = if (message.role == "user") "Usuário" else "Núcleo AI"
            appendLine(speaker + ": " + message.content.take(1_600))
        }
    }.take(9_000)

    private fun terms(value: String): Set<String> = value.lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 4 && it !in STOP_WORDS }.toSet()

    private companion object {
        val MEMORY_REQUESTS = listOf(
            Regex("(?is)^\\s*(?:por favor[, ]*)?(?:lembre-se de que|lembre que|lembra que|guarde na memória que|guarda na memória que|salve na memória que|salva na memória que|memorize que|não esqueça que|nao esquece que)\\s+(.+?)\\s*[.!?]*\\s*$"),
        )
        val PREFERENCE_HINTS = listOf("eu prefiro", "prefiro ", "quero que", "não quero que", "nao quero que", "sempre ")
        val CREDENTIAL_HINTS = listOf(
            Regex("(?i)\\b(api[ _-]?key|chave de api|senha|password|token|client secret|segredo|cpf|cartão de crédito|cartao de credito)\\b"),
        )
        val STOP_WORDS = setOf("para", "com", "como", "mais", "menos", "isso", "essa", "esse", "sobre", "quando", "onde", "porque", "que", "uma", "umas", "uns", "dos", "das", "the", "that", "this", "from", "your", "with", "about")
    }
}
