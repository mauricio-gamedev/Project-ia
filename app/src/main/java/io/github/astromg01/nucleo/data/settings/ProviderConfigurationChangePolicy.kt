package io.github.astromg01.nucleo.data.settings

/**
 * Detects changes that invalidate provider cooldowns. The primary route ID is stable across
 * model changes, so carrying a timeout/HTTP failure cooldown into a different model can make a
 * newly selected model appear broken even though it has never been tried.
 */
internal object ProviderConfigurationChangePolicy {
    fun shouldClearCooldown(
        previous: ProviderConfiguration,
        updated: ProviderConfiguration,
    ): Boolean {
        return previous.providerName.trim() != updated.providerName.trim() ||
            normalizeBaseUrl(previous.baseUrl) != normalizeBaseUrl(updated.baseUrl) ||
            previous.modelName.trim() != updated.modelName.trim() ||
            previous.enabled != updated.enabled
    }

    private fun normalizeBaseUrl(value: String): String = value.trim().trimEnd('/')
}