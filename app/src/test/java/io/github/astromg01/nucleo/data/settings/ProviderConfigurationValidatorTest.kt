package io.github.astromg01.nucleo.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderConfigurationValidatorTest {
    @Test
    fun acceptsHttpsEndpointWithoutEmbeddedCredentials() {
        val config = ProviderConfiguration(
            providerName = "Provider A",
            baseUrl = "https://api.example.test/v1",
            modelName = "model-x",
        )
        assertNull(ProviderConfigurationValidator.validate(config))
    }

    @Test
    fun rejectsCleartextHttpEndpoint() {
        val config = ProviderConfiguration(
            baseUrl = "http://api.example.test/v1",
            modelName = "model-x",
        )
        assertEquals(
            "Por segurança, provedores remotos precisam usar HTTPS nesta versão.",
            ProviderConfigurationValidator.validate(config),
        )
    }

    @Test
    fun rejectsCredentialsOrQueryInBaseUrl() {
        val config = ProviderConfiguration(
            baseUrl = "https://user:password@api.example.test/v1?token=secret",
            modelName = "model-x",
        )
        assertEquals(
            "A URL não pode conter credenciais, parâmetros de consulta nem fragmentos.",
            ProviderConfigurationValidator.validate(config),
        )
    }

    @Test
    fun requiresModelName() {
        val config = ProviderConfiguration(
            baseUrl = "https://api.example.test/v1",
            modelName = "",
        )
        assertEquals(
            "Informe o identificador do modelo.",
            ProviderConfigurationValidator.validate(config),
        )
    }

    @Test
    fun rejectsDisabledProvider() {
        val config = ProviderConfiguration(
            baseUrl = "https://api.example.test/v1",
            modelName = "model-x",
            enabled = false,
        )
        assertEquals(
            "O provedor está desativado nas configurações.",
            ProviderConfigurationValidator.validate(config),
        )
    }
}
