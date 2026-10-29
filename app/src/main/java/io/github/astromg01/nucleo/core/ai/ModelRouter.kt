package io.github.astromg01.nucleo.core.ai

import io.github.astromg01.nucleo.core.security.SecretStore
import io.github.astromg01.nucleo.data.settings.ProviderConfiguration
import io.github.astromg01.nucleo.data.settings.ProviderPreferences
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Routes a request through explicitly configured OpenAI-compatible profiles.
 *
 * This is quota-aware failover, not a promise of unlimited free inference. Only configured
 * routes/keys are used; keys are read from Android Keystore-backed SecretStore. Context is
 * fitted to each route before sending, and transient/quota failures cause a persisted cooldown.
 */
class ModelRouter(
    private val localRuntime: LocalModelRuntime,
    private val providerPreferences: ProviderPreferences,
    private val secretStore: SecretStore,
    private val contextManager: AiContextManager = AiContextManager(),
) {
    suspend fun generate(request: AiRequest): AiResponse {
        if (localRuntime.isReady()) {
            try {
                return localRuntime.generate(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A local-runtime failure falls through to the configured remote routes.
            }
        }

        val primary = providerPreferences.load()
        val fallbacks = providerPreferences.loadFallbackProfiles()
        val orderedRoutes = ModelRoutePlanner.order(primary, fallbacks, request.taskType)
        if (orderedRoutes.isEmpty()) {
            throw ProviderConfigurationException(
                "Nenhum provedor habilitado. Ative ou configure pelo menos uma rota nas Configurações."
            )
        }

        val failures = mutableListOf<ProviderRouteFailure>()
        val ready = mutableListOf<Pair<ProviderConfiguration, String>>()
        val coolingDown = mutableListOf<Pair<ProviderConfiguration, Long>>()
        val now = System.currentTimeMillis()

        for (route in orderedRoutes) {
            val validation = providerPreferences.validate(route)
            if (validation != null) {
                failures += ProviderRouteFailure(route.providerName, validation)
                continue
            }
            val key = secretStore.readApiKey(route.id)
            if (key.isNullOrBlank()) {
                failures += ProviderRouteFailure(route.providerName, "nenhuma chave de API salva")
                continue
            }
            val cooldownUntil = providerPreferences.cooldownUntilMillis(route.id)
            if (cooldownUntil > now) {
                coolingDown += route to cooldownUntil
                continue
            }
            ready += route to key
        }

        if (ready.isEmpty()) {
            if (coolingDown.isNotEmpty()) {
                val earliest = coolingDown.minOf { it.second }
                val waitSeconds = ((earliest - now).coerceAtLeast(0L) + 999L) / 1_000L
                coolingDown.forEach { (route, until) ->
                    val seconds = ((until - now).coerceAtLeast(0L) + 999L) / 1_000L
                    failures += ProviderRouteFailure(route.providerName, "em cooldown por mais ${seconds}s", retryable = true)
                }
                throw ModelRoutingException(
                    failures,
                    "Todas as rotas com chave válida estão temporariamente em espera. Tente novamente em aproximadamente ${waitSeconds}s ou configure outro provedor.",
                )
            }
            throw ModelRoutingException(
                failures,
                "Nenhuma rota de IA está pronta. Confira as configurações e adicione pelo menos uma chave de API válida.",
            )
        }

        var lastFailure: Exception? = null
        for ((route, key) in ready) {
            val routeRequest = try {
                contextManager.fit(request, route.contextWindowChars)
            } catch (error: AiContextManager.ContextBudgetExceededException) {
                failures += ProviderRouteFailure(route.providerName, "o pedido excede o orçamento de contexto")
                lastFailure = error
                continue
            }

            try {
                val response = OpenAiCompatibleProvider(route, key).generate(routeRequest)
                providerPreferences.clearCooldown(route.id)
                return response
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ProviderHttpException) {
                // Some compatible providers reject JSON mode. Retry once without the optional
                // parameter, but classify the second failure—not the initial HTTP 400—if fallback fails.
                if (error.statusCode == 400 && routeRequest.jsonObjectMode) {
                    try {
                        val compatibleResponse = OpenAiCompatibleProvider(route, key)
                            .generate(routeRequest.copy(jsonObjectMode = false))
                        providerPreferences.clearCooldown(route.id)
                        return compatibleResponse
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (compatibilityError: Exception) {
                        lastFailure = compatibilityError
                        val disposition = ProviderFailurePolicy.classify(compatibilityError)
                        failures += ProviderRouteFailure(route.providerName, disposition.reason, disposition.retryable)
                        providerPreferences.markCooldown(route.id, System.currentTimeMillis() + disposition.cooldownMillis)
                        continue
                    }
                }
                lastFailure = error
                val disposition = ProviderFailurePolicy.classify(error)
                failures += ProviderRouteFailure(route.providerName, disposition.reason, disposition.retryable)
                providerPreferences.markCooldown(route.id, System.currentTimeMillis() + disposition.cooldownMillis)
            } catch (error: IOException) {
                lastFailure = error
                val disposition = ProviderFailurePolicy.classify(error)
                failures += ProviderRouteFailure(route.providerName, disposition.reason, disposition.retryable)
                providerPreferences.markCooldown(route.id, System.currentTimeMillis() + disposition.cooldownMillis)
            } catch (error: Exception) {
                lastFailure = error
                // Keep the reason generic: provider bodies may contain private prompts or credentials.
                val disposition = ProviderFailurePolicy.classify(error)
                failures += ProviderRouteFailure(route.providerName, disposition.reason, disposition.retryable)
                providerPreferences.markCooldown(route.id, System.currentTimeMillis() + disposition.cooldownMillis)
            }
        }

        val details = failures.take(MAX_FAILURE_DETAILS)
            .joinToString("; ") { "${it.providerName}: ${it.reason}" }
        val extra = if (failures.size > MAX_FAILURE_DETAILS) "; e mais ${failures.size - MAX_FAILURE_DETAILS} rota(s)" else ""
        val message = "Nenhum modelo configurado conseguiu atender esta solicitação. $details$extra"
        val routingFailure = ModelRoutingException(failures, message)
        lastFailure?.let(routingFailure::addSuppressed)
        throw routingFailure
    }

    private companion object {
        const val MAX_FAILURE_DETAILS = 4
    }
}
