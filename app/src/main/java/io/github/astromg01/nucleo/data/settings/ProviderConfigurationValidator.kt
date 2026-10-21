package io.github.astromg01.nucleo.data.settings

import java.net.URI

/** Pure validation policy so connection configuration can be tested without Android framework code. */
object ProviderConfigurationValidator {
    fun validate(configuration: ProviderConfiguration): String? {
        if (!configuration.enabled) return "O provedor está desativado nas configurações."
        if (configuration.baseUrl.isBlank()) return "Informe a URL base HTTPS do provedor."
        if (configuration.modelName.isBlank()) return "Informe o identificador do modelo."
        return try {
            val uri = URI(configuration.baseUrl.trim())
            when {
                !uri.isAbsolute || uri.host.isNullOrBlank() -> "A URL base não é válida."
                !uri.scheme.equals("https", ignoreCase = true) ->
                    "Por segurança, provedores remotos precisam usar HTTPS nesta versão."
                uri.userInfo != null || uri.fragment != null || uri.query != null ->
                    "A URL não pode conter credenciais, parâmetros de consulta nem fragmentos."
                else -> null
            }
        } catch (_: Exception) {
            "A URL base não é válida."
        }
    }
}
