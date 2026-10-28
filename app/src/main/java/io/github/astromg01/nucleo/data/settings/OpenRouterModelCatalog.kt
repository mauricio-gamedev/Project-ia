package io.github.astromg01.nucleo.data.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Public catalog metadata. Prices are published provider rates, not a promise of account access. */
data class OpenRouterCatalogModel(
    val id: String,
    val name: String,
    val contextLengthTokens: Int,
    val promptPricePerMillionUsd: Double?,
    val completionPricePerMillionUsd: Double?,
    val description: String,
) {
    val isListedFree: Boolean
        get() = promptPricePerMillionUsd == 0.0 && completionPricePerMillionUsd == 0.0
}

/** Kept pure so response validation can be regression-tested without network access. */
object OpenRouterCatalogParser {
    fun parse(json: String): List<OpenRouterCatalogModel> {
        val data = JSONObject(json).optJSONArray("data")
            ?: throw IllegalArgumentException("O catálogo da OpenRouter retornou uma resposta inválida (campo data ausente).")
        val parsed = mutableListOf<OpenRouterCatalogModel>()
        val seenIds = mutableSetOf<String>()
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isBlank() || !seenIds.add(id)) continue
            val pricing = item.optJSONObject("pricing")
            val prompt = parseNonNegativePrice(pricing?.opt("prompt"))
            val completion = parseNonNegativePrice(pricing?.opt("completion"))
            val context = item.optLong("context_length", 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            parsed += OpenRouterCatalogModel(
                id = id,
                name = item.optString("name", "").trim().ifBlank { id },
                contextLengthTokens = context,
                promptPricePerMillionUsd = prompt?.times(1_000_000.0),
                completionPricePerMillionUsd = completion?.times(1_000_000.0),
                description = item.optString("description", "").take(1_000),
            )
        }
        return parsed
    }

    private fun parseNonNegativePrice(raw: Any?): Double? =
        raw?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
}

/** Fetches the official OpenRouter catalog on demand; never persists credentials or catalog data. */
class OpenRouterModelCatalog(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun fetch(apiKey: String? = null): List<OpenRouterCatalogModel> = withContext(Dispatchers.IO) {
        val url = CATALOG_URL.toHttpUrl().newBuilder()
            .addQueryParameter("output_modalities", "text")
            .addQueryParameter("sort", "most-popular")
            .build()
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
        apiKey?.trim()?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401, 403 -> "Catálogo indisponível: a OpenRouter recusou a consulta. Confirme se a chave da OpenRouter está configurada."
                    429 -> "Catálogo indisponível temporariamente por limite de consultas. Tente novamente mais tarde."
                    in 500..599 -> "Catálogo indisponível: o serviço da OpenRouter apresentou uma falha temporária."
                    else -> "Catálogo indisponível: resposta HTTP ${response.code}."
                }
                throw IOException(message)
            }
            val body = response.body?.string()
                ?: throw IOException("Catálogo indisponível: a resposta veio vazia.")
            if (body.length > MAX_RESPONSE_CHARS) {
                throw IOException("Catálogo indisponível: a resposta excedeu o limite de segurança.")
            }
            try {
                OpenRouterCatalogParser.parse(body)
            } catch (_: Exception) {
                throw IOException("Catálogo indisponível: a resposta JSON não pôde ser validada.")
            }
        }
    }

    companion object {
        private const val CATALOG_URL = "https://openrouter.ai/api/v1/models"
        private const val MAX_RESPONSE_CHARS = 5_000_000
    }
}
