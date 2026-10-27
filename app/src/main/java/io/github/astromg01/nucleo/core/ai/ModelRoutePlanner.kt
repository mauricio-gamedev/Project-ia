package io.github.astromg01.nucleo.core.ai

import io.github.astromg01.nucleo.data.settings.ProviderConfiguration

/**
 * Deterministic route ordering. The configured primary remains first; after it fails, code
 * generation/review/repair can escalate to higher user-assigned quality tiers. Tier is metadata
 * chosen by the user, not a claim that the router independently benchmarked the model.
 */
object ModelRoutePlanner {
    fun order(
        primary: ProviderConfiguration,
        fallbackProfiles: List<ProviderConfiguration>,
        taskType: AiTaskType,
    ): List<ProviderConfiguration> {
        val uniqueFallbacks = fallbackProfiles
            .filter { it.enabled && it.id != primary.id }
            .distinctBy { it.id }
        val orderedFallbacks = when (taskType) {
            AiTaskType.CODE_GENERATION, AiTaskType.CODE_REVIEW, AiTaskType.CODE_REPAIR ->
                uniqueFallbacks.sortedWith(
                    compareByDescending<ProviderConfiguration> { it.qualityTier }
                        .thenBy { it.priority }
                        .thenBy { it.providerName.lowercase() }
                )
            AiTaskType.CHAT, AiTaskType.PLANNING ->
                uniqueFallbacks.sortedWith(
                    compareBy<ProviderConfiguration> { it.priority }
                        .thenByDescending { it.qualityTier }
                        .thenBy { it.providerName.lowercase() }
                )
        }
        return buildList {
            if (primary.enabled) add(primary)
            addAll(orderedFallbacks)
        }
    }
}
