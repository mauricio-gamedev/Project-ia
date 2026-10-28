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
        val requestStartedAtMillis = monotonicMillis()
        for ((route, key) in ready) {
            val routeStartedAtMillis = monotonicMillis()
            val routeRequest = try {
                contextManager.fit(request, route.contextWindowChars)
            } catch (error: AiContextManager.ContextBudgetExceededException) {
                failures += ProviderRouteFailure(route.providerName, "o pedido excede o orçamento de contexto")
                lastFailure = error
                continue
            }

            val timeoutBudgetMillis = ProviderTimeoutPolicy.remainingBudgetMillis(
                requestStartedAtMillis = requestStartedAtMillis,
                routeStartedAtMillis = routeStartedAtMillis,
                nowMillis = monotonicMillis(),
            )
            if (timeoutBudgetMillis == null) {
                failures += ProviderRouteFailure(
                    route.providerName,
                    "o limite de tempo total da solicitação foi atingido",
                    retryable = true,
                )
                break
            }

            try {
                val response = OpenAiCompatibleProvider(route, key, timeoutBudgetMillis).generate(routeRequest)
                providerPreferences.clearCooldown(route.id)
                return response
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ProviderHttpException) {
                // Some compatible providers reject JSON mode. Retry this route once without the
                // optional parameter before deciding it has failed.
                if (error.statusCode == 400 && routeRequest.jsonObjectMode) {
                    val retryBudgetMillis = ProviderTimeoutPolicy.remainingBudgetMillis(
                        requestStartedAtMillis = requestStartedAtMillis,
                        routeStartedAtMillis = routeStartedAtMillis,
                        nowMillis = monotonicMillis(),
                    )
                    if (retryBudgetMillis != null) {
                        try {
                            val compatibleResponse = OpenAiCompatibleProvider(route, key, retryBudgetMillis)
                                .generate(routeRequest.copy(jsonObjectMode = false))
                            providerPreferences.clearCooldown(route.id)
                            return compatibleResponse
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (compatibilityError: Exception) {
                            lastFailure = compatibilityError
                            val retryable = compatibilityError is IOException ||
                                (compatibilityError is ProviderHttpException &&
                                    (compatibilityError.statusCode == 408 || compatibilityError.statusCode == 429 ||
                                        compatibilityError.statusCode in 500..599))
                            failures += ProviderRouteFailure(
                                route.providerName,
                                "a tentativa sem modo JSON também falhou",
                                retryable = retryable,
                            )
                            val compatibilityCooldown = when (compatibilityError) {
                                is ProviderHttpException -> when {
                                    compatibilityError.statusCode == 429 ->
                                        ((compatibilityError.retryAfterSeconds ?: DEFAULT_QUOTA_COOLDOWN_SECONDS)
                                            .coerceIn(1L, MAX_COOLDOWN_SECONDS)) * 1_000L
                                    compatibilityError.statusCode == 401 || compatibilityError.statusCode == 403 ->
                                        AUTH_COOLDOWN_MILLIS
                                    compatibilityError.statusCode == 408 || compatibilityError.statusCode in 500..599 ->
                                        TRANSIENT_COOLDOWN_MILLIS
                                    else -> ROUTE_CONFIGURATION_COOLDOWN_MILLIS
                                }
                                is IOException -> TRANSIENT_COOLDOWN_MILLIS
                                else -> ROUTE_CONFIGURATION_COOLDOWN_MILLIS
                            }
                            providerPreferences.markCooldown(route.id, System.currentTimeMillis() + compatibilityCooldown)
                            continue
                        }
                    } else {
                        lastFailure = error
                        failures += ProviderRouteFailure(
                            route.providerName,
                            "sem tempo restante para repetir sem modo JSON",
                            retryable = true,
                        )
                        providerPreferences.markCooldown(
                            route.id,
                            System.currentTimeMillis() + TRANSIENT_COOLDOWN_MILLIS,
                        )
                        continue
                    }
                }
                lastFailure = error
                failures += ProviderRouteFailure(
                    route.providerName,
                    "HTTP ${error.statusCode}",
                    retryable = error.statusCode == 408 || error.statusCode == 429 || error.statusCode in 500..599,
                )
                val cooldownMillis = when {
                    error.statusCode == 429 ->
                        ((error.retryAfterSeconds ?: DEFAULT_QUOTA_COOLDOWN_SECONDS).coerceIn(1L, MAX_COOLDOWN_SECONDS)) * 1_000L
                    error.statusCode == 401 || error.statusCode == 403 -> AUTH_COOLDOWN_MILLIS
                    error.statusCode == 408 || error.statusCode in 500..599 -> TRANSIENT_COOLDOWN_MILLIS
                    else -> ROUTE_CONFIGURATION_COOLDOWN_MILLIS
                }
                providerPreferences.markCooldown(route.id, System.currentTimeMillis() + cooldownMillis)
            } catch (error: IOException) {
                lastFailure = error
                failures += ProviderRouteFailure(route.providerName, "falha de rede ou timeout", retryable = true)
                providerPreferences.markCooldown(route.id, System.currentTimeMillis() + TRANSIENT_COOLDOWN_MILLIS)
            } catch (error: Exception) {
                lastFailure = error
                // Keep the reason generic: provider bodies may contain private prompts or credentials.
                failures += ProviderRouteFailure(route.providerName, "resposta inválida ou falha de compatibilidade")
                providerPreferences.markCooldown(route.id, System.currentTimeMillis() + ROUTE_CONFIGURATION_COOLDOWN_MILLIS)
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

    private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L

    private companion object {
        const val DEFAULT_QUOTA_COOLDOWN_SECONDS = 300L
        const val MAX_COOLDOWN_SECONDS = 3_600L
        const val AUTH_COOLDOWN_MILLIS = 15 * 60 * 1_000L
        const val TRANSIENT_COOLDOWN_MILLIS = 45 * 1_000L
        const val ROUTE_CONFIGURATION_COOLDOWN_MILLIS = 2 * 60 * 1_000L
        const val MAX_FAILURE_DETAILS = 4
    }
}
