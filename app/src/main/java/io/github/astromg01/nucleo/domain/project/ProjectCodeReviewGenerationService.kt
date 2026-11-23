package io.github.astromg01.nucleo.domain.project

import io.github.astromg01.nucleo.core.ai.AiMessage
import io.github.astromg01.nucleo.core.ai.AiRequest
import io.github.astromg01.nucleo.core.ai.AiResponse
import kotlinx.coroutines.CancellationException

internal data class ProjectCodeReviewGenerationResult(
    val response: AiResponse,
    val report: ProjectCodeReviewReport,
    val attempts: Int,
)

internal class ProjectCodeReviewGenerationService(
    private val reviewer: ProjectCodeReviewService = ProjectCodeReviewService(),
) {
    suspend fun review(
        request: AiRequest,
        allowedPaths: Set<String>,
        partial: Boolean,
        generateResponse: suspend (AiRequest) -> AiResponse,
    ): ProjectCodeReviewGenerationResult {
        var lastParseError: Exception? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            val currentRequest = if (attempt == 1) {
                request
            } else {
                request.copy(
                    messages = request.messages + AiMessage(
                        "user",
                        retryInstruction(lastParseError?.message ?: "JSON ou estrutura inválida"),
                    ),
                )
            }
            val response = generateResponse(currentRequest)
            val report = try {
                reviewer.parseReviewResponse(response.text, allowedPaths, partial)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastParseError = error
                if (attempt < MAX_ATTEMPTS) continue
                throw IllegalArgumentException(
                    "A IA não entregou um relatório de revisão JSON válido após $MAX_ATTEMPTS tentativas. " +
                        "Tente novamente ou escolha um modelo compatível com JSON.",
                    error,
                )
            }
            return ProjectCodeReviewGenerationResult(response, report, attempt)
        }
        throw IllegalStateException("A revisão terminou sem um relatório validado.")
    }

    private fun retryInstruction(reason: String): String {
        val safeReason = reason.replace(Regex("[\\r\\n]+"), " ").take(MAX_REASON_CHARS)
        return """
            A resposta anterior foi rejeitada pela validação do Núcleo.
            Motivo: $safeReason.
            Refaça somente o relatório da análise de código, sem repetir o código-fonte.
            Retorne um único objeto JSON válido na raiz, sem Markdown, cercas ou texto antes/depois.
            Campos obrigatórios: "summary" (string não vazia) e "findings" (array; use [] se não houver achados).
            Cada achado deve ter "severity" ("error", "warning" ou "info"), "path" (caminho exato de um arquivo do snapshot),
            "line" (inteiro positivo ou null), "problem" (string não vazia) e "recommendation" (string).
            Escape aspas e quebras de linha de acordo com JSON. Não invente execução de testes ou compilação.
        """.trimIndent()
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val MAX_REASON_CHARS = 180
    }
}
