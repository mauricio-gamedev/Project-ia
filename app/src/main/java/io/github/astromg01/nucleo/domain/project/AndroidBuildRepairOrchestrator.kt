package io.github.astromg01.nucleo.domain.project

import io.github.astromg01.nucleo.core.ai.AiRequest
import io.github.astromg01.nucleo.core.ai.AiResponse
import kotlinx.coroutines.CancellationException

sealed interface AndroidBuildRepairOutcome {
    data class Success(
        val draft: ProjectDraft,
        val stagedProject: StagedProject,
        val artifact: BuiltAndroidArtifact,
        val repairAttempts: Int,
        val repairSummaries: List<String>,
    ) : AndroidBuildRepairOutcome

    data class Failed(
        val draft: ProjectDraft,
        val stagedProject: StagedProject,
        val failure: AndroidBuildFailureException,
        val repairAttempts: Int,
        val repairSummaries: List<String>,
        val validationIssues: List<String> = emptyList(),
        val repairMessage: String? = null,
    ) : AndroidBuildRepairOutcome
}

/**
 * Runs a verified remote build and, only after a completed Gradle failure, may repair and retry.
 * The original staged project remains intact until a repaired revision actually builds.
 * This class never executes generated code locally.
 */
class AndroidBuildRepairOrchestrator(
    private val repairService: ProjectGenerationRepairService = ProjectGenerationRepairService(),
) {
    suspend fun build(
        initialDraft: ProjectDraft,
        initialStagedProject: StagedProject,
        maxAutoRepairAttempts: Int,
        generateRepair: suspend (AiRequest) -> AiResponse,
        stageDraft: suspend (ProjectDraft) -> StagedProject,
        buildStagedProject: suspend (StagedProject) -> BuiltAndroidArtifact,
        discardStagedProject: suspend (StagedProject) -> Unit,
        onStatus: (String) -> Unit = {},
    ): AndroidBuildRepairOutcome {
        require(maxAutoRepairAttempts in 0..MAX_AUTO_REPAIR_ATTEMPTS) {
            "O limite de correções automáticas precisa estar entre zero e dois."
        }

        var workingDraft = initialDraft
        var workingStage = initialStagedProject
        var repairAttempts = 0
        val summaries = mutableListOf<String>()

        while (true) {
            val artifact = try {
                buildStagedProject(workingStage)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: AndroidBuildFailureException) {
                if (repairAttempts >= maxAutoRepairAttempts || failure.diagnostics.isBlank()) {
                    return AndroidBuildRepairOutcome.Failed(
                        workingDraft, workingStage, failure, repairAttempts, summaries.toList(),
                    )
                }

                onStatus(
                    "O Gradle falhou. Preparando correção automática " +
                        (repairAttempts + 1) + " de " + maxAutoRepairAttempts + "…",
                )
                val repair = try {
                    repairService.repairOnce(
                        workingDraft,
                        listOf(
                            "A compilação Android falhou no GitHub Actions: " + failure.runUrl,
                            failure.diagnostics,
                        ),
                        generateRepair,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    return AndroidBuildRepairOutcome.Failed(
                        workingDraft,
                        workingStage,
                        failure,
                        repairAttempts,
                        summaries.toList(),
                        repairMessage = "A correção automática foi interrompida: " +
                            (error.message ?: "a IA não entregou uma proposta válida."),
                    )
                }

                if (!repair.attempted) {
                    return AndroidBuildRepairOutcome.Failed(
                        workingDraft,
                        workingStage,
                        failure,
                        repairAttempts,
                        summaries.toList(),
                        repairMessage = repair.summary,
                    )
                }

                repairAttempts++
                summaries += repair.summary
                val candidateDraft = try {
                    if (repair.draft.profile == ProjectProfile.ANDROID_APP) {
                        AndroidProjectScaffold.complete(repair.draft).draft
                    } else {
                        repair.draft
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    return AndroidBuildRepairOutcome.Failed(
                        workingDraft,
                        workingStage,
                        failure,
                        repairAttempts,
                        summaries.toList(),
                        repairMessage = "A proposta não pôde ser normalizada: " +
                            (error.message ?: "estrutura inválida."),
                    )
                }

                val validationIssues = ProjectGenerationValidator.validate(candidateDraft)
                val candidateStage = try {
                    stageDraft(candidateDraft)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    return AndroidBuildRepairOutcome.Failed(
                        workingDraft,
                        workingStage,
                        failure,
                        repairAttempts,
                        summaries.toList(),
                        repairMessage = "Não foi possível preparar a revisão corrigida: " +
                            (error.message ?: "erro ao criar staging."),
                    )
                }

                if (workingStage.id != initialStagedProject.id) {
                    discardBestEffort(workingStage, discardStagedProject)
                }
                workingDraft = candidateDraft
                workingStage = candidateStage

                if (validationIssues.isNotEmpty()) {
                    return AndroidBuildRepairOutcome.Failed(
                        workingDraft,
                        workingStage,
                        failure,
                        repairAttempts,
                        summaries.toList(),
                        validationIssues = validationIssues,
                        repairMessage = "A proposta foi guardada em revisão separada, mas ainda tem problemas estruturais. " +
                            "O Gradle não será executado até a validação passar.",
                    )
                }

                onStatus("Correção estrutural aprovada. Iniciando nova compilação remota…")
                continue
            }

            if (workingStage.id != initialStagedProject.id) {
                discardBestEffort(initialStagedProject, discardStagedProject)
            }
            return AndroidBuildRepairOutcome.Success(
                workingDraft,
                workingStage,
                artifact,
                repairAttempts,
                summaries.toList(),
            )
        }
    }

    private suspend fun discardBestEffort(
        stagedProject: StagedProject,
        discard: suspend (StagedProject) -> Unit,
    ) {
        try {
            discard(stagedProject)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Stale cache cleanup is also performed by ProjectFactory on a later app session.
        }
    }

    companion object {
        const val MAX_AUTO_REPAIR_ATTEMPTS = 2
    }
}
