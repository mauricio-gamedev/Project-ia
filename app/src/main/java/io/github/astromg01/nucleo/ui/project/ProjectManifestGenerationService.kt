package io.github.astromg01.nucleo.ui.project

import io.github.astromg01.nucleo.core.ai.AiMessage
import io.github.astromg01.nucleo.core.ai.AiRequest
import io.github.astromg01.nucleo.core.ai.AiResponse
import io.github.astromg01.nucleo.domain.project.ProjectDraft
import io.github.astromg01.nucleo.domain.project.ProjectProfile
import kotlinx.coroutines.CancellationException

internal data class ProjectManifestGenerationResult(
    val response: AiResponse,
    val draft: ProjectDraft,
    val attempts: Int,
)

/**
 * Retries a malformed or incomplete manifest once. Provider/network failures are left to the
 * router and are not blindly repeated here. This only validates the response contract; the caller
 * still performs project structure checks and the real remote Gradle build.
 */
internal class ProjectManifestGenerationService {
    suspend fun generate(
        request: AiRequest,
        name: String,
        description: String,
        profile: ProjectProfile,
        generateResponse: suspend (AiRequest) -> AiResponse,
    ): ProjectManifestGenerationResult {
        var lastManifestError: Exception? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            val currentRequest = if (attempt == 1) request else request.copy(
                messages = request.messages + AiMessage(
                    "user",
                    """
                        A resposta anterior foi rejeitada pelo parser do Núcleo.
                        Motivo: ${lastManifestError?.message ?: "manifesto incompleto"}.
                        Gere novamente o PROJETO INTEIRO, não só o trecho problemático, como um único objeto JSON válido.
                        Cada entrada de files precisa ter path e content como strings não vazias, sem caminhos repetidos.
                        Cada content deve conter o arquivo completo e ter aspas/quebras de linha escapadas como JSON.
                        Não inclua Markdown ou texto fora do objeto JSON.
                    """.trimIndent(),
                ),
            )
            val response = generateResponse(currentRequest)
            try {
                val draft = ProjectManifestParser.parse(
                    responseText = response.text,
                    name = name,
                    description = description,
                    profile = profile,
                )
                return ProjectManifestGenerationResult(response, draft, attempt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastManifestError = error
                if (attempt == MAX_ATTEMPTS) {
                    throw IllegalArgumentException(
                        "A IA não entregou um manifesto completo após $MAX_ATTEMPTS tentativas. " +
                            (error.message ?: "Revise a descrição ou tente outro modelo."),
                        error,
                    )
                }
            }
        }
        throw IllegalStateException("A geração terminou sem um manifesto validado.")
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}