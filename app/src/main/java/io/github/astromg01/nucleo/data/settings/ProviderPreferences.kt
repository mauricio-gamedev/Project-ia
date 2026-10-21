package io.github.astromg01.nucleo.data.settings

import android.content.Context
import android.content.SharedPreferences

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
    )

    fun save(configuration: ProviderConfiguration) {
        preferences.edit()
            .putString(KEY_PROVIDER_NAME, configuration.providerName.trim().ifEmpty { "OpenAI-compatible" })
            .putString(KEY_BASE_URL, configuration.baseUrl.trim().trimEnd('/'))
            .putString(KEY_MODEL, configuration.modelName.trim())
            .putBoolean(KEY_ENABLED, configuration.enabled)
            .apply()
    }

    fun validate(configuration: ProviderConfiguration): String? = ProviderConfigurationValidator.validate(configuration)

    companion object {
        private const val PREFS_NAME = "provider_config"
        private const val KEY_PROVIDER_NAME = "provider_name"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model_name"
        private const val KEY_ENABLED = "enabled"
    }
}
