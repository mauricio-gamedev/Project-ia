package io.github.astromg01.nucleo.data.settings

/** Non-secret connection metadata. API keys are deliberately stored elsewhere. */
data class ProviderConfiguration(
    val providerName: String = "OpenAI-compatible",
    val baseUrl: String = "",
    val modelName: String = "",
    val enabled: Boolean = true,
)

