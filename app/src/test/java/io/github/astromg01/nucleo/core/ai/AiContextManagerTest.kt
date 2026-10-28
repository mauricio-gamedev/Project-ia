package io.github.astromg01.nucleo.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiContextManagerTest {
    private val manager = AiContextManager()

    @Test
    fun trimsOldMiddleMessagesButKeepsSystemOriginalTaskAndLatestRequest() {
        val request = AiRequest(
            messages = listOf(
                AiMessage("system", "system-rules".padEnd(350, 's')),
                AiMessage("user", "original-task".padEnd(450, 'o')),
                AiMessage("assistant", "old-answer".padEnd(500, 'a')),
                AiMessage("user", "middle-follow-up".padEnd(450, 'm')),
                AiMessage("assistant", "recent-answer".padEnd(450, 'r')),
                AiMessage("user", "latest-request".padEnd(350, 'l')),
            ),
        )

        val fitted = manager.fit(request, maxInputChars = 2_400)

        assertTrue(estimate(fitted.messages) <= 2_400)
        assertEquals("system-rules".padEnd(350, 's'), fitted.messages.first().content)
        assertTrue(fitted.messages.any { it.content == "original-task".padEnd(450, 'o') })
        assertTrue(fitted.messages.any { it.content == "latest-request".padEnd(350, 'l') })
        assertTrue(fitted.messages.any { it.content.startsWith("Context handoff:") })
        assertTrue(fitted.messages.size < request.messages.size + 1)
    }

    @Test
    fun doesNotSilentlyTruncateAnOversizedMandatoryTask() {
        val request = AiRequest(
            messages = listOf(
                AiMessage("system", "rules"),
                AiMessage("user", "task".padEnd(2_200, 'x')),
            ),
        )

        try {
            manager.fit(request, maxInputChars = 2_000)
            throw AssertionError("Expected context budget failure.")
        } catch (error: AiContextManager.ContextBudgetExceededException) {
            assertTrue(error.message!!.contains("não cabem"))
        }
    }

    @Test
    fun unchangedRequestIsReturnedWhenItAlreadyFits() {
        val request = AiRequest(
            messages = listOf(AiMessage("system", "rules"), AiMessage("user", "small task")),
        )

        assertEquals(request, manager.fit(request, maxInputChars = 2_000))
    }

    private fun estimate(messages: List<AiMessage>): Int =
        messages.sumOf { it.role.length + it.content.length + 8 }
}
