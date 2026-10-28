package io.github.astromg01.nucleo.data.settings

import java.net.URI

/** Pure validation policy so connection configuration can be tested without Android framework code. */
object ProviderConfigurationValidator {
    fun validate(configuration: ProviderConfiguration): String? {
        if (!configuration.enabled) return "O provedor está desativado nas configurações."
        if (configuration.baseUrl.isBlank()) return "Informe a URL base HTTPS do provedor."
        if (configuration.modelName.isBlank()) return "Informe o identificador do modelo."
        if (configuration.id.length !in 1..80 || configuration.id.any {
                !it.isLetterOrDigit() && it != '-' && it != '_'
            }
        ) return "O identificador interno da rota é inválido."
        if (configuration.qualityTier !in ProviderConfiguration.MIN_QUALITY_TIER..ProviderConfiguration.MAX_QUALITY_TIER) {
            return "O nível do modelo deve ficar entre 1 e 3."
        }
        if (configuration.contextWindowChars !in
            ProviderConfiguration.MIN_CONTEXT_CHARS..ProviderConfiguration.MAX_CONTEXT_CHARS
        ) {
            return "O limite de contexto está fora da faixa permitida."
        }
        if (configuration.priority !in 0..10_000) return "A prioridade deve ficar entre 0 e 10000."
        return try {
            val uri = URI(configuration.baseUrl.trim())
            val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]")
            val isLoopback = host == "localhost" || host == "127.0.0.1"
            when {
                !uri.isAbsolute || host.isNullOrBlank() -> "A URL base não é válida."
                uri.userInfo != null || uri.fragment != null || uri.query != null ->
                    "A URL não pode conter credenciais, parâmetros de consulta nem fragmentos."
                uri.scheme.equals("https", ignoreCase = true) -> null
                uri.scheme.equals("http", ignoreCase = true) && isLoopback -> null
                uri.scheme.equals("http", ignoreCase = true) ->
                    "HTTP sem criptografia só é permitido para localhost/127.0.0.1. Provedores remotos precisam usar HTTPS."
                else -> "A URL base precisa usar HTTPS ou HTTP local em loopback."
            }
        } catch (_: Exception) {
            "A URL base não é válida."
        }
    }
}
