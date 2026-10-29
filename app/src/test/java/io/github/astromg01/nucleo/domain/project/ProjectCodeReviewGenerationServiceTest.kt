package io.github.astromg01.nucleo.domain.project

import io.github.astromg01.nucleo.core.ai.AiMessage
import io.github.astromg01.nucleo.core.ai.AiRequest
import io.github.astromg01.nucleo.core.ai.AiResponse
import io.github.astromg01.nucleo.core.ai.AiTaskType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ProjectCodeReviewGenerationServiceTest {
    private val request = AiRequest(
        messages = listOf(
            AiMessage("system", "Return one JSON code-review report."),
            AiMessage("user", "Review the staged source."),
        ),
        jsonObjectMode = true,
        taskType = AiTaskType.CODE_REVIEW,
    )

    @Test
    fun retriesMalformedReviewOnceAndAcceptsFencedJson() = runBlocking {
        var calls = 0
        val result = ProjectCodeReviewGenerationService().review(
            request = request,
            allowedPaths = setOf("src/main.kt"),
            partial = false,
        ) { currentRequest ->
            calls++
            if (calls == 1) {
                AiResponse("I found an issue, but the response is not JSON.", "TestProvider", "test-model")
            } else {
                assertTrue(currentRequest.messages.last().content.contains("Refaça somente o relatório"))
                assertTrue(currentRequest.messages.last().content.contains("JSON válido"))
                AiResponse(
                    "Resultado:\n```json\n" +
                        """{"summary":"A chave { fica dentro }.","findings":[{"severity":"error","path":"src/main.kt","line":3,"problem":"Expressão incompleta {","recommendation":"Fechar }"}]}""" +
                        "\n```",
                    "TestProvider",
                    "test-model",
                )
            }
        }
        assertEquals(2, calls)
        assertEquals(2, result.attempts)
        assertEquals("A chave { fica dentro }.", result.report.summary)
        assertEquals(1, result.report.findings.size)
        assertEquals("src/main.kt", result.report.findings.single().path)
    }

    @Test
    fun doesNotRetryProviderOrNetworkFailures() = runBlocking {
        var calls = 0
        try {
            ProjectCodeReviewGenerationService().review(
                request = request,
                allowedPaths = setOf("src/main.kt"),
                partial = false,
            ) {
                calls++
                throw IOException("offline")
            }
            throw AssertionError("Expected network failure.")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }
        assertEquals(1, calls)
    }

    @Test
    fun doesNotRetryAValidReview() = runBlocking {
        var calls = 0
        val result = ProjectCodeReviewGenerationService().review(
            request = request,
            allowedPaths = setOf("src/main.kt"),
            partial = false,
        ) {
            calls++
            AiResponse("""{"summary":"No issues found.","findings":[]}""", "TestProvider", "test-model")
        }
        assertEquals(1, calls)
        assertEquals(1, result.attempts)
        assertTrue(result.report.findings.isEmpty())
    }
}
