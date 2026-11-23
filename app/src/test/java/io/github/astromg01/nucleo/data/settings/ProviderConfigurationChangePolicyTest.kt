package io.github.astromg01.nucleo.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigurationChangePolicyTest {
    private fun config(
        provider: String = "OpenRouter",
        baseUrl: String = "https://openrouter.ai/api/v1",
        model: String = "provider/model-a:free",
        enabled: Boolean = true,
    ) = ProviderConfiguration(
        providerName = provider,
        baseUrl = baseUrl,
        modelName = model,
        enabled = enabled,
    )

    @Test
    fun changingModelClearsCooldownForStablePrimaryRouteId() {
        assertTrue(
            ProviderConfigurationChangePolicy.shouldClearCooldown(
                config(model = "provider/model-a:free"),
                config(model = "provider/model-b:free"),
            ),
        )
    }

    @Test
    fun changingEndpointClearsCooldown() {
        assertTrue(
            ProviderConfigurationChangePolicy.shouldClearCooldown(
                config(),
                config(baseUrl = "https://api.example.test/v1"),
            ),
        )
    }

    @Test
    fun equivalentBaseUrlsDoNotInvalidateCooldown() {
        assertFalse(
            ProviderConfigurationChangePolicy.shouldClearCooldown(
                config(baseUrl = "https://openrouter.ai/api/v1/"),
                config(baseUrl = "https://openrouter.ai/api/v1"),
            ),
        )
    }

    @Test
    fun unchangedConfigurationDoesNotInvalidateCooldown() {
        assertFalse(
            ProviderConfigurationChangePolicy.shouldClearCooldown(
                config(),
                config(),
            ),
        )
    }
}