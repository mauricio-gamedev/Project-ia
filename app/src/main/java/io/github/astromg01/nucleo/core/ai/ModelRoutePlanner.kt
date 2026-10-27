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
        val configured = buildList {
            if (primary.enabled) add(primary)
            addAll(uniqueFallbacks)
        }
        return when (taskType) {
            // Code review and repair spend the route's configured capability budget up front.
            // A stronger route can run first; ties still favor the explicitly configured primary.
            AiTaskType.CODE_REVIEW, AiTaskType.CODE_REPAIR ->
                configured.sortedWith(
                    compareByDescending<ProviderConfiguration> { it.qualityTier }
                        .thenBy { if (it.id == primary.id) 0 else 1 }
                        .thenBy { it.priority }
                        .thenBy { it.providerName.lowercase() }
                )
            // Generation starts with the user's selected primary. A quota/error falls through
            // to alternatives ranked from stronger to more modest capability tiers.
            AiTaskType.CODE_GENERATION ->
                buildList {
                    if (primary.enabled) add(primary)
                    addAll(uniqueFallbacks.sortedWith(
                        compareByDescending<ProviderConfiguration> { it.qualityTier }
                            .thenBy { it.priority }
                            .thenBy { it.providerName.lowercase() }
                    ))
                }
            AiTaskType.CHAT, AiTaskType.PLANNING ->
                buildList {
                    if (primary.enabled) add(primary)
                    addAll(uniqueFallbacks.sortedWith(
                        compareBy<ProviderConfiguration> { it.priority }
                            .thenByDescending { it.qualityTier }
                            .thenBy { it.providerName.lowercase() }
                    ))
                }
        }
    }
}
