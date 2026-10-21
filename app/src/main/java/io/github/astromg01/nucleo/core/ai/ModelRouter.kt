package io.github.astromg01.nucleo.core.ai

import io.github.astromg01.nucleo.core.security.SecretStore
import io.github.astromg01.nucleo.data.settings.ProviderPreferences

/** Policy-based routing. Local inference is a declared extension point, not falsely advertised as active. */
class ModelRouter(
    private val localRuntime: LocalModelRuntime,
    private val providerPreferences: ProviderPreferences,
    private val secretStore: SecretStore,
) {
    suspend fun generate(request: AiRequest): AiResponse {
        if (localRuntime.isReady()) {
            try {
                return localRuntime.generate(request)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A local-runtime failure falls through to the configured remote adapter.
            }
        }

        val configuration = providerPreferences.load()
        val validation = providerPreferences.validate(configuration)
        if (validation != null) throw ProviderConfigurationException(validation)

        val apiKey = secretStore.readApiKey()
            ?: throw ProviderConfigurationException("Nenhuma chave de API foi salva. Abra Configurações para conectar um provedor.")
        return OpenAiCompatibleProvider(configuration, apiKey).generate(request)
    }
}
