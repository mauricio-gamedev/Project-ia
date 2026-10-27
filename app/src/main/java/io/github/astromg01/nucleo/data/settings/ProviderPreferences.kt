package io.github.astromg01.nucleo.data.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores non-secret provider metadata and lightweight cooldown state.
 * Provider credentials are held separately by SecretStore using each route ID.
 */
class ProviderPreferences(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): ProviderConfiguration = ProviderConfiguration(
        providerName = preferences.getString(KEY_PROVIDER_NAME, "OpenAI-compatible")
            ?: "OpenAI-compatible",
        baseUrl = preferences.getString(KEY_BASE_URL, "") ?: "",
        modelName = preferences.getString(KEY_MODEL, "") ?: "",
        enabled = preferences.getBoolean(KEY_ENABLED, true),
        id = ProviderConfiguration.DEFAULT_PROVIDER_ID,
    )

    fun save(configuration: ProviderConfiguration) {
        preferences.edit()
            .putString(KEY_PROVIDER_NAME, configuration.providerName.trim().ifEmpty { "OpenAI-compatible" })
            .putString(KEY_BASE_URL, configuration.baseUrl.trim().trimEnd('/'))
            .putString(KEY_MODEL, configuration.modelName.trim())
            .putBoolean(KEY_ENABLED, configuration.enabled)
            .apply()
    }

    /** Alternative routes, ordered by the priority chosen in Settings. The legacy route remains primary. */
    fun loadFallbackProfiles(): List<ProviderConfiguration> {
        val raw = preferences.getString(KEY_FALLBACK_PROFILES, null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            val profiles = (0 until json.length()).mapNotNull { index ->
                val item = json.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id", "")
                if (!isSafeProfileId(id) || id == ProviderConfiguration.DEFAULT_PROVIDER_ID) {
                    return@mapNotNull null
                }
                ProviderConfiguration(
                    id = id,
                    providerName = item.optString("providerName", "OpenAI-compatible"),
                    baseUrl = item.optString("baseUrl", ""),
                    modelName = item.optString("modelName", ""),
                    enabled = item.optBoolean("enabled", true),
                    priority = item.optInt("priority", 100).coerceIn(0, 10_000),
                    qualityTier = item.optInt("qualityTier", 1).coerceIn(
                        ProviderConfiguration.MIN_QUALITY_TIER,
                        ProviderConfiguration.MAX_QUALITY_TIER,
                    ),
                    contextWindowChars = item.optInt("contextWindowChars", 48_000).coerceIn(
                        ProviderConfiguration.MIN_CONTEXT_CHARS,
                        ProviderConfiguration.MAX_CONTEXT_CHARS,
                    ),
                )
            }
            profiles.distinctBy { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFallbackProfiles(profiles: List<ProviderConfiguration>) {
        require(profiles.all { it.id != ProviderConfiguration.DEFAULT_PROVIDER_ID && isSafeProfileId(it.id) }) {
            "Cada rota alternativa precisa de um identificador único válido."
        }
        require(profiles.map { it.id }.distinct().size == profiles.size) {
            "Não é permitido cadastrar duas rotas com o mesmo identificador."
        }
        val json = JSONArray()
        profiles.forEach { profile ->
            json.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("providerName", profile.providerName.trim().ifBlank { "OpenAI-compatible" })
                    .put("baseUrl", profile.baseUrl.trim().trimEnd('/'))
                    .put("modelName", profile.modelName.trim())
                    .put("enabled", profile.enabled)
                    .put("priority", profile.priority.coerceIn(0, 10_000))
                    .put("qualityTier", profile.qualityTier.coerceIn(
                        ProviderConfiguration.MIN_QUALITY_TIER,
                        ProviderConfiguration.MAX_QUALITY_TIER,
                    ))
                    .put("contextWindowChars", profile.contextWindowChars.coerceIn(
                        ProviderConfiguration.MIN_CONTEXT_CHARS,
                        ProviderConfiguration.MAX_CONTEXT_CHARS,
                    )),
            )
        }
        preferences.edit().putString(KEY_FALLBACK_PROFILES, json.toString()).apply()
    }

    fun validate(configuration: ProviderConfiguration): String? =
        ProviderConfigurationValidator.validate(configuration)

    fun cooldownUntilMillis(profileId: String): Long =
        preferences.getLong(cooldownKey(profileId), 0L)

    fun markCooldown(profileId: String, untilMillis: Long) {
        if (isSafeProfileId(profileId)) {
            preferences.edit().putLong(cooldownKey(profileId), untilMillis.coerceAtLeast(0L)).apply()
        }
    }

    fun clearCooldown(profileId: String) {
        if (isSafeProfileId(profileId)) preferences.edit().remove(cooldownKey(profileId)).apply()
    }

    private fun cooldownKey(profileId: String): String = "$KEY_COOLDOWN_PREFIX$profileId"

    private fun isSafeProfileId(value: String): Boolean =
        value.length in 1..80 && value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    companion object {
        private const val PREFS_NAME = "provider_config"
        private const val KEY_PROVIDER_NAME = "provider_name"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model_name"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_FALLBACK_PROFILES = "fallback_profiles_json"
        private const val KEY_COOLDOWN_PREFIX = "provider_cooldown_until_"
    }
}
