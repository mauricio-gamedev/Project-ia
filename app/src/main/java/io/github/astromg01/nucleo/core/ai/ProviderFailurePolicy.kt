package io.github.astromg01.nucleo.core.ai

import java.io.IOException

internal data class ProviderFailureDisposition(
    val reason: String,
    val retryable: Boolean,
    val cooldownMillis: Long,
)

/** Consistent per-route failure classification, including the second attempt without JSON mode. */
internal object ProviderFailurePolicy {
    private const val DEFAULT_QUOTA_COOLDOWN_SECONDS = 300L
    private const val MAX_COOLDOWN_SECONDS = 3_600L
    private const val AUTH_COOLDOWN_MILLIS = 15 * 60 * 1_000L
    private const val TRANSIENT_COOLDOWN_MILLIS = 45 * 1_000L
    private const val ROUTE_CONFIGURATION_COOLDOWN_MILLIS = 2 * 60 * 1_000L

    fun classify(error: Exception): ProviderFailureDisposition = when (error) {
        is ProviderHttpException -> classifyHttp(error)
        is IOException -> ProviderFailureDisposition(
            reason = "falha de rede ou timeout",
            retryable = true,
            cooldownMillis = TRANSIENT_COOLDOWN_MILLIS,
        )
        else -> ProviderFailureDisposition(
            reason = "resposta inválida ou falha de compatibilidade",
            retryable = false,
            cooldownMillis = ROUTE_CONFIGURATION_COOLDOWN_MILLIS,
        )
    }

    private fun classifyHttp(error: ProviderHttpException): ProviderFailureDisposition {
        val code = error.statusCode
        val retryable = code == 408 || code == 429 || code in 500..599
        val cooldown = when {
            code == 429 ->
                ((error.retryAfterSeconds ?: DEFAULT_QUOTA_COOLDOWN_SECONDS)
                    .coerceIn(1L, MAX_COOLDOWN_SECONDS)) * 1_000L
            code == 401 || code == 403 -> AUTH_COOLDOWN_MILLIS
            code == 408 || code in 500..599 -> TRANSIENT_COOLDOWN_MILLIS
            else -> ROUTE_CONFIGURATION_COOLDOWN_MILLIS
        }
        return ProviderFailureDisposition(
            reason = "HTTP $code",
            retryable = retryable,
            cooldownMillis = cooldown,
        )
    }
}
