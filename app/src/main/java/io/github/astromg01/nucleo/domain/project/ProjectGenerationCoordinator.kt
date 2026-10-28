package io.github.astromg01.nucleo.domain.project

import io.github.astromg01.nucleo.core.ai.AiMessage
import io.github.astromg01.nucleo.core.ai.AiRequest
import io.github.astromg01.nucleo.core.ai.AiResponse
import kotlinx.coroutines.CancellationException

data class ProjectGenerationOutcome(
    val response: AiResponse,
    val scaffold: AndroidScaffoldResult,
    val attempts: Int,
    val readinessIssues: List<String>,
)

/**
 * Retries malformed or obviously incomplete generated projects once before accepting a staged
 * draft. It checks source delimiters and Android cross-file scaffolding; this is not a compiler.
 */
class ProjectGenerationCoordinator {
    suspend fun generate(
        request: AiRequest,
        name: String,
        description: String,
        profile: ProjectProfile,
        generateResponse: suspend (AiRequest) -> AiResponse,
    ): ProjectGenerationOutcome {
        var lastProblem = "A primeira saída não passou na validação."
        for (attempt in 1..MAX_ATTEMPTS) {
            val attemptRequest = if (attempt == 1) request else request.copy(
                messages = request.messages + AiMessage(
                    role = "user",
                    content = """
                        A tentativa anterior do manifesto do projeto foi rejeitada pela validação local.
                        Diagnóstico:
                        $lastProblem

                        Gere novamente o PROJETO COMPLETO como um único objeto JSON válido no formato solicitado.
                        Não devolva apenas uma explicação ou um diff. Cada caminho deve ser único; todo content deve
                        conter o arquivo inteiro, com strings JSON corretamente escapadas e todos os blocos/chaves
                        fechados. Para Android, inclua uma Activity real de entrada e garanta que Manifest, package,
                        plugins Gradle e dependências correspondam ao código.
                    """.trimIndent(),
                ),
            )

            try {
                val response = generateResponse(attemptRequest)
                val draft = ProjectManifestParser.parse(response.text, name, description, profile)
                val scaffold = AndroidProjectScaffold.complete(draft)
                val issues = ProjectSourceIntegrityValidator.inspect(scaffold.draft).toMutableList()
                if (profile == ProjectProfile.ANDROID_APP && scaffold.defaultLauncherAdded) {
                    issues += "A IA não forneceu uma Activity de entrada utilizável; foi necessário criar uma tela de contingência."
                }
                val outcome = ProjectGenerationOutcome(
                    response = response,
                    scaffold = scaffold,
                    attempts = attempt,
                    readinessIssues = issues.distinct().take(MAX_READINESS_ISSUES),
                )
                if (issues.isEmpty() || attempt == MAX_ATTEMPTS) return outcome
                lastProblem = issues.joinToString("\n")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastProblem = error.message?.take(MAX_DIAGNOSTIC_CHARS)
                    ?: "A saída não passou na validação do manifesto e do scaffold."
                if (attempt == MAX_ATTEMPTS) {
                    throw IllegalArgumentException(
                        "A geração falhou na validação após $MAX_ATTEMPTS tentativas. $lastProblem",
                        error,
                    )
                }
            }
        }
        throw IllegalStateException("A geração terminou sem um rascunho validado.")
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val MAX_DIAGNOSTIC_CHARS = 1_000
        const val MAX_READINESS_ISSUES = 20
    }
}