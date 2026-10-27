package io.github.astromg01.nucleo.data.settings

/** Non-secret connection metadata. API keys are deliberately stored elsewhere. */
data class ProviderConfiguration(
    val providerName: String = "OpenAI-compatible",
    val baseUrl: String = "",
    val modelName: String = "",
    val enabled: Boolean = true,
    /** Stable opaque ID used to associate the encrypted credential and cooldown with this route. */
    val id: String = DEFAULT_PROVIDER_ID,
    /** Lower values are attempted earlier for ordinary requests. */
    val priority: Int = 100,
    /** User-assigned capability tier: 1=basic, 2=balanced, 3=stronger. It is a preference, not a benchmark. */
    val qualityTier: Int = 1,
    /** Conservative input-context budget in characters; it is not an exact provider token count. */
    val contextWindowChars: Int = 48_000,
) {
    companion object {
        const val DEFAULT_PROVIDER_ID = "default"
        const val MIN_QUALITY_TIER = 1
        const val MAX_QUALITY_TIER = 3
        const val MIN_CONTEXT_CHARS = 2_000
        const val MAX_CONTEXT_CHARS = 500_000
    }
}
