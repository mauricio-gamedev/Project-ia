package io.github.astromg01.nucleo.core.ai

/** Neutral chat message contract; intentionally independent of any provider SDK. */
data class AiMessage(
    val role: String,
    val content: String,
)

enum class AiTaskType {
    CHAT,
    PLANNING,
    CODE_GENERATION,
    CODE_REVIEW,
    CODE_REPAIR,
}

data class AiRequest(
    val messages: List<AiMessage>,
    val temperature: Double = 0.2,
    val maxOutputTokens: Int? = null,
    /** Ask compatible providers for a syntactically valid JSON object when supported. */
    val jsonObjectMode: Boolean = false,
    /** Used to pick an appropriate route and preserve task context during failover. */
    val taskType: AiTaskType = AiTaskType.CHAT,
)

data class AiResponse(
    val text: String,
    val providerName: String,
    val modelName: String,
)

interface AiProvider {
    val id: String
    suspend fun generate(request: AiRequest): AiResponse
}

/** A future native runtime can implement this without changing the app's UI or task engine. */
interface LocalModelRuntime {
    fun isReady(): Boolean
    suspend fun generate(request: AiRequest): AiResponse
}

class LocalModelUnavailableException : IllegalStateException(
    "Nenhum runtime de modelo local foi instalado nesta versão.",
)

class ProviderConfigurationException(message: String) : IllegalStateException(message)

/** A provider status error with a deliberately sanitized user-facing message. */
class ProviderHttpException(
    val statusCode: Int,
    /** Optional HTTP Retry-After value, capped by the adapter before reaching the router. */
    val retryAfterSeconds: Long? = null,
) : IllegalStateException(
    "O provedor respondeu HTTP $statusCode. Confira endpoint, modelo, chave e cota do serviço.",
) {
    val isRetryable: Boolean = statusCode == 408 || statusCode == 429 || statusCode in 500..599
}

data class ProviderRouteFailure(
    val providerName: String,
    val reason: String,
)

class ModelRoutingException(
    val failures: List<ProviderRouteFailure>,
    message: String,
) : IllegalStateException(message)
