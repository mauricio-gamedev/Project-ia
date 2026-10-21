package io.github.astromg01.nucleo.core.ai

import io.github.astromg01.nucleo.data.settings.ProviderConfiguration
import io.github.astromg01.nucleo.data.settings.ProviderConfigurationValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/** Generic adapter for providers exposing an OpenAI-style chat-completions endpoint. */
class OpenAiCompatibleProvider(
    private val configuration: ProviderConfiguration,
    private val apiKey: String,
) : AiProvider {
    override val id: String = configuration.providerName.ifBlank { "OpenAI-compatible" }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(100, TimeUnit.SECONDS)
        .build()

    override suspend fun generate(request: AiRequest): AiResponse = withContext(Dispatchers.IO) {
        val validation = ProviderConfigurationValidator.validate(configuration)
        if (validation != null) throw ProviderConfigurationException(validation)
        if (apiKey.isBlank()) {
            throw ProviderConfigurationException("Cadastre a chave de API nas configurações do Núcleo.")
        }
        require(request.messages.isNotEmpty()) { "A solicitação precisa conter mensagens." }

        val endpoint = endpointFor(configuration.baseUrl)
        val messages = JSONArray()
        request.messages.forEach { message ->
            messages.put(
                JSONObject()
                    .put("role", normalizeRole(message.role))
                    .put("content", message.content),
            )
        }
        val payload = JSONObject()
            .put("model", configuration.modelName)
            .put("messages", messages)
            .put("temperature", request.temperature.coerceIn(0.0, 2.0))
        request.maxOutputTokens?.takeIf { it > 0 }?.let { payload.put("max_tokens", it) }

        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .post(body)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // Never surface arbitrary provider response bodies: they can echo user data or secrets.
                throw ProviderHttpException(response.code)
            }
            val root = JSONObject(responseBody)
            val choices = root.optJSONArray("choices")
                ?: throw IllegalStateException("Resposta incompatível: campo 'choices' ausente.")
            val first = choices.optJSONObject(0)
                ?: throw IllegalStateException("Resposta incompatível: nenhuma escolha retornada.")
            val message = first.optJSONObject("message")
                ?: throw IllegalStateException("Resposta incompatível: mensagem ausente.")
            val text = message.optString("content", "").trim()
            if (text.isBlank()) throw IllegalStateException("O modelo retornou uma resposta vazia.")
            AiResponse(text, id, configuration.modelName)
        }
    }

    private fun normalizeRole(role: String): String = when (role.lowercase()) {
        "system", "assistant", "user", "tool" -> role.lowercase()
        else -> "user"
    }

    private fun endpointFor(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        val uri = URI(trimmed)
        if (uri.path?.endsWith("/chat/completions") == true) return trimmed
        return "$trimmed/chat/completions"
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
