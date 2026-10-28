package io.github.astromg01.nucleo.core.ai

import io.github.astromg01.nucleo.data.local.ChatMessageEntity
import io.github.astromg01.nucleo.data.local.MemoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMemoryManagerTest {
    private val manager = ChatMemoryManager()

    @Test
    fun explicitRememberCommandCreatesLocalFact() {
        val memory = manager.createExplicitMemory("Lembre que meu projeto atual se chama Núcleo AI")!!
        assertEquals("chat_fact", memory.kind)
        assertTrue(memory.content.contains("meu projeto atual se chama Núcleo AI"))
    }

    @Test
    fun preferenceIsTaggedAndCredentialLikeValuesAreRejected() {
        val preference = manager.createExplicitMemory("Guarda na memória que eu prefiro respostas curtas e diretas")!!
        assertEquals("chat_preference", preference.kind)
        assertNull(manager.createExplicitMemory("Lembre que minha API key é abcdefghijklmnop"))
        assertNull(manager.createExplicitMemory("Lembre que eu"))
    }

    @Test
    fun promptIgnoresUnrelatedTaskMemories() {
        val summary = MemoryEntity(id = "chat-summary-main", kind = "chat_summary", title = "Resumo", content = "O projeto atual é o Núcleo AI.")
        val fact = MemoryEntity(id = "chat-pref", kind = "chat_preference", title = "Estilo", content = "Prefiro passos objetivos.")
        val task = MemoryEntity(id = "task-old", kind = "task_plan", title = "Tarefa antiga", content = "Construir um jogo de zumbis.")
        val context = manager.buildPromptContext("Como continuo o Núcleo AI?", summary, listOf(task, fact))
        assertTrue(context.contains("Núcleo AI"))
        assertTrue(context.contains("Prefiro passos objetivos"))
        assertFalse(context.contains("jogo de zumbis"))
    }

    @Test
    fun summaryRequiresEnoughNewCompleteConversation() {
        val turns = buildList {
            repeat(6) { index ->
                add(ChatMessageEntity(role = "user", content = "pedido " + index, createdAt = 10L + index * 2))
                add(ChatMessageEntity(role = "assistant", content = "resposta " + index, createdAt = 11L + index * 2))
            }
        }
        assertTrue(manager.shouldSummarize(null, turns))
        val oldSummary = MemoryEntity(id = "summary", kind = "chat_summary", title = "Resumo", content = "antigo", updatedAt = 1L)
        assertTrue(manager.shouldSummarize(oldSummary, turns))
        assertFalse(manager.shouldSummarize(null, turns.take(8)))
    }
}
