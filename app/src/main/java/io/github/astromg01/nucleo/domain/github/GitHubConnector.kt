package io.github.astromg01.nucleo.domain.github

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import io.github.astromg01.nucleo.core.security.SecretStore
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

const val GITHUB_OAUTH_TOKEN_ID = "github_oauth_access_token"

data class GitHubDeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
)

data class GitHubOAuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresInSeconds: Long? = null,
    val refreshTokenExpiresInSeconds: Long? = null,
)

data class GitHubRepository(
    val owner: String,
    val name: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val htmlUrl: String,
    val description: String,
    val canPush: Boolean,
) {
    val fullName: String get() = "$owner/$name"
}

data class GitHubRepositoryContext(
    val repository: GitHubRepository,
    val branch: String,
    val filePaths: List<String>,
    val selectedFiles: Map<String, String>,
) {
    fun toPromptContext(maxChars: Int = 18_000): String = buildString {
        appendLine("GitHub repository: ${repository.fullName}")
        appendLine("Visibility: ${if (repository.isPrivate) "private" else "public"}")
        appendLine("Default branch: $branch")
        if (repository.description.isNotBlank()) appendLine("Description: ${repository.description}")
        appendLine()
        appendLine("Repository file tree (truncated):")
        filePaths.take(100).forEach { appendLine("- $it") }
        selectedFiles.forEach { (path, source) ->
            if (length < maxChars) {
                appendLine()
                appendLine("----- FILE: $path -----")
                append(source.take((maxChars - length).coerceAtLeast(0)))
                appendLine()
                appendLine("----- END FILE -----")
            }
        }
    }.take(maxChars)
}

/**
 * GitHub OAuth Device Flow with encrypted-token handoff and read-only repository context.
 * No credentials are persisted here or sent to model providers. Repository writes remain behind
 * the Project Factory's explicit build consent. OAuth scopes "repo workflow" are broad, so the
 * UI must explain their reach and the app operates on the repository selected by the user.
 */
class GitHubConnector(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun requestDeviceAuthorization(clientId: String): GitHubDeviceAuthorization =
        withContext(Dispatchers.IO) {
            validateClientId(clientId)
            val json = JSONObject(postForm(
                DEVICE_CODE_URL,
                mapOf("client_id" to clientId.trim(), "scope" to "repo workflow offline_access"),
            ))
            GitHubDeviceAuthorization(
                deviceCode = json.optString("device_code").takeIf { it.isNotBlank() }
                    ?: throw IOException("O GitHub não retornou um código de dispositivo."),
                userCode = json.optString("user_code").takeIf { it.isNotBlank() }
                    ?: throw IOException("O GitHub não retornou o código que deve ser autorizado."),
                verificationUri = json.optString("verification_uri").takeIf { it.isNotBlank() }
                    ?: throw IOException("O GitHub não retornou o endereço de autorização."),
                expiresInSeconds = json.optLong("expires_in", 900L).coerceIn(60L, 1800L),
                intervalSeconds = json.optLong("interval", 5L).coerceIn(5L, 30L),
            )
        }

    suspend fun awaitAccessToken(
        clientId: String,
        authorization: GitHubDeviceAuthorization,
        onStatus: (String) -> Unit = {},
    ): GitHubOAuthTokens = withContext(Dispatchers.IO) {
        validateClientId(clientId)
        val deadline = System.nanoTime() + authorization.expiresInSeconds * 1_000_000_000L
        var interval = authorization.intervalSeconds.coerceIn(5L, 30L)
        while (System.nanoTime() < deadline) {
            delay(interval * 1_000L)
            val json = JSONObject(postForm(
                ACCESS_TOKEN_URL,
                mapOf(
                    "client_id" to clientId.trim(),
                    "device_code" to authorization.deviceCode,
                    "grant_type" to DEVICE_GRANT_TYPE,
                ),
            ))
            json.optString("access_token").takeIf { it.isNotBlank() }?.let { accessToken ->
                return@withContext GitHubOAuthTokens(
                    accessToken = accessToken,
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                    expiresInSeconds = json.optLong("expires_in", 0L).takeIf { it > 0L },
                    refreshTokenExpiresInSeconds = json.optLong("refresh_token_expires_in", 0L).takeIf { it > 0L },
                )
            }
            when (json.optString("error")) {
                "authorization_pending" -> onStatus("Aguardando você autorizar o Núcleo AI no GitHub…")
                "slow_down" -> {
                    interval = (interval + 5L).coerceAtMost(60L)
                    onStatus("O GitHub pediu para reduzir a frequência de consulta…")
                }
                "access_denied" -> throw IOException("A autorização do GitHub foi recusada.")
                "expired_token" -> throw IOException("O código de autorização expirou. Inicie a conexão novamente.")
                "incorrect_client_credentials" -> throw IOException("O Client ID do OAuth App não foi reconhecido pelo GitHub.")
                "device_flow_disabled" -> throw IOException("Ative Device Flow nas configurações do OAuth App e tente novamente.")
                else -> throw IOException("O GitHub não concluiu a autorização. Verifique o OAuth App e tente novamente.")
            }
        }
        throw IOException("O tempo de autorização expirou. Inicie a conexão novamente.")
    }

    suspend fun refreshAccessToken(clientId: String, refreshToken: String): GitHubOAuthTokens =
        withContext(Dispatchers.IO) {
            validateClientId(clientId)
            validateAccessToken(refreshToken)
            val json = JSONObject(postForm(
                ACCESS_TOKEN_URL,
                mapOf(
                    "client_id" to clientId.trim(),
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                ),
            ))
            val access = json.optString("access_token").takeIf { it.isNotBlank() }
                ?: throw IOException(
                    if (json.optString("error") == "bad_refresh_token") {
                        "A sessão GitHub expirou. Reconecte sua conta para continuar."
                    } else {
                        "O GitHub não conseguiu renovar a sessão. Reconecte sua conta se o problema continuar."
                    },
                )
            GitHubOAuthTokens(
                accessToken = access,
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                expiresInSeconds = json.optLong("expires_in", 0L).takeIf { it > 0L },
                refreshTokenExpiresInSeconds = json.optLong("refresh_token_expires_in", 0L).takeIf { it > 0L },
            )
        }

    suspend fun listRepositories(accessToken: String): List<GitHubRepository> =
        withContext(Dispatchers.IO) {
            validateAccessToken(accessToken)
            buildList {
                for (page in 1..3) {
                    val url = "$API_BASE/user/repos".toHttpUrl().newBuilder()
                        .addQueryParameter("visibility", "all")
                        .addQueryParameter("affiliation", "owner,collaborator,organization_member")
                        .addQueryParameter("sort", "updated")
                        .addQueryParameter("per_page", "100")
                        .addQueryParameter("page", page.toString())
                        .build()
                    val items = JSONArray(apiGet(url.toString(), accessToken))
                    if (items.length() == 0) break
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        val owner = item.optJSONObject("owner")?.optString("login").orEmpty()
                        val name = item.optString("name").orEmpty()
                        if (owner.isBlank() || name.isBlank()) continue
                        add(GitHubRepository(
                            owner = owner,
                            name = name,
                            isPrivate = item.optBoolean("private", false),
                            defaultBranch = item.optString("default_branch").orEmpty(),
                            htmlUrl = item.optString("html_url", "https://github.com/$owner/$name"),
                            description = item.optString("description").orEmpty(),
                            canPush = item.optJSONObject("permissions")?.optBoolean("push", false) == true,
                        ))
                    }
                    if (items.length() < 100) break
                }
            }.distinctBy { it.fullName.lowercase() }
        }

    suspend fun fetchRepositoryContext(
        accessToken: String,
        repository: GitHubRepository,
        maxChars: Int = 18_000,
    ): GitHubRepositoryContext = withContext(Dispatchers.IO) {
        validateAccessToken(accessToken)
        validateRepository(repository)
        val metadata = JSONObject(apiGet("$API_BASE/repos/${repository.owner}/${repository.name}", accessToken))
        val branch = metadata.optString("default_branch").ifBlank { repository.defaultBranch }
        if (branch.isBlank()) throw IOException("O repositório não possui uma branch padrão inicializada.")
        val treeUrl = "$API_BASE/repos/${repository.owner}/${repository.name}/git/trees/${encodePathSegment(branch)}?recursive=1"
        val treeJson = JSONObject(apiGet(treeUrl, accessToken))
        val tree = treeJson.optJSONArray("tree") ?: JSONArray()
        val allPaths = mutableListOf<String>()
        val sizes = mutableMapOf<String, Long>()
        for (index in 0 until tree.length()) {
            val entry = tree.optJSONObject(index) ?: continue
            if (entry.optString("type") != "blob") continue
            val filePath = entry.optString("path").takeIf { it.isNotBlank() } ?: continue
            allPaths += filePath
            sizes[filePath] = entry.optLong("size", 0L)
        }
        val preferred = listOf(
            "README.md", "settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml", "package.json", "pubspec.yaml", "index.html",
        )
        val selectedPaths = buildList {
            preferred.forEach { wanted -> allPaths.firstOrNull { it.equals(wanted, ignoreCase = true) }?.let(::add) }
            allPaths.filter { filePath ->
                val lower = filePath.lowercase()
                (lower.endsWith(".kt") || lower.endsWith(".java") || lower.endsWith(".ts") ||
                    lower.endsWith(".tsx") || lower.endsWith(".js") || lower.endsWith(".py") ||
                    lower.endsWith(".gd") || lower.endsWith(".html") || lower.endsWith(".md")) &&
                    !lower.contains("/build/") && !lower.contains("/.git/") &&
                    !lower.endsWith("lock") && !lower.contains("generated")
            }.take(8).forEach { if (it !in this) add(it) }
        }.distinct().take(10)

        val selectedFiles = linkedMapOf<String, String>()
        var budget = (maxChars - allPaths.take(100).sumOf { it.length + 3 } - 800).coerceAtLeast(2_000)
        for (filePath in selectedPaths) {
            if (budget <= 0) break
            if ((sizes[filePath] ?: 0L) !in 1..256_000) continue
            try {
                val url = "$API_BASE/repos/${repository.owner}/${repository.name}/contents/${encodePath(filePath)}?ref=${encodeQuery(branch)}"
                val fileJson = JSONObject(apiGet(url, accessToken))
                if (fileJson.optString("encoding") != "base64") continue
                val encoded = fileJson.optString("content").replace(Regex("\\s+"), "")
                val source = Base64.decode(encoded, Base64.DEFAULT).toString(Charsets.UTF_8)
                val limit = minOf(3_000, budget)
                selectedFiles[filePath] = source.take(limit)
                budget -= minOf(source.length, limit) + filePath.length + 40
            } catch (error: Exception) {
                if (filePath.equals("README.md", ignoreCase = true)) {
                    throw IOException("Não foi possível ler o README do repositório conectado.", error)
                }
            }
        }
        val updatedRepo = repository.copy(
            isPrivate = metadata.optBoolean("private", repository.isPrivate),
            defaultBranch = branch,
            htmlUrl = metadata.optString("html_url", repository.htmlUrl),
            description = metadata.optString("description", repository.description).orEmpty(),
            canPush = metadata.optJSONObject("permissions")?.optBoolean("push", repository.canPush) ?: repository.canPush,
        )
        GitHubRepositoryContext(updatedRepo, branch, allPaths.take(100), selectedFiles)
    }

    private fun apiGet(url: String, accessToken: String): String {
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401 -> "O GitHub não aceitou a conexão. Reconecte sua conta."
                    403 -> "O GitHub recusou o acesso. Verifique permissões, organização e limites da API."
                    404 -> "O GitHub não encontrou o repositório ou você não tem permissão para acessá-lo."
                    429 -> "O GitHub limitou as requisições. Tente novamente mais tarde."
                    in 500..599 -> "O GitHub apresentou uma falha temporária."
                    else -> "O GitHub respondeu HTTP ${response.code}."
                }
                throw GitHubConnectorException(response.code, message)
            }
            return body
        }
    }

    private fun postForm(url: String, values: Map<String, String>): String {
        val form = FormBody.Builder().apply { values.forEach { (key, value) -> add(key, value) } }.build()
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .post(form).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GitHubConnectorException(response.code, "Não foi possível autorizar com o GitHub.")
            return body
        }
    }

    private fun validateClientId(clientId: String) {
        require(clientId.trim().matches(Regex("[A-Za-z0-9.]{10,80}"))) {
            "Informe um Client ID válido do OAuth App do GitHub com Device Flow habilitado."
        }
    }

    private fun validateAccessToken(token: String) {
        require(token.isNotBlank() && token.length <= 2_048 && !token.any { it.isWhitespace() }) {
            "A conexão GitHub não está válida. Conecte a conta novamente."
        }
    }

    private fun validateRepository(repository: GitHubRepository) {
        require(repository.owner.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})")) &&
            repository.name.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) {
            "Identidade do repositório GitHub inválida."
        }
    }

    private fun encodePath(path: String): String = path.split("/").joinToString("/") { encodePathSegment(it) }

    private fun encodePathSegment(value: String): String = value.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
        val unsigned = byte.toInt() and 0xff
        if ((unsigned in 'a'.code..'z'.code) || (unsigned in 'A'.code..'Z'.code) ||
            (unsigned in '0'.code..'9'.code) || unsigned in listOf('-'.code, '_'.code, '.'.code, '~'.code)
        ) unsigned.toChar().toString() else "%%%02X".format(unsigned)
    }

    private fun encodeQuery(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    class GitHubConnectorException(val statusCode: Int, message: String) : IOException(message)

    companion object {
        private const val API_BASE = "https://api.github.com"
        private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
        private const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
        private const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        private const val API_VERSION = "2022-11-28"
    }
}

/** OAuth Client ID and selected repository are metadata; the access token belongs in SecretStore. */
class GitHubConnectorPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("github_connector", Context.MODE_PRIVATE)

    fun clientId(): String = preferences.getString(KEY_CLIENT_ID, "").orEmpty()
    fun saveClientId(value: String) { preferences.edit().putString(KEY_CLIENT_ID, value.trim()).apply() }

    /** Repository whose snippets may be sent to the AI, only when chat context is enabled. */
    fun selectedRepository(): GitHubRepository? = readRepository(
        KEY_OWNER, KEY_REPOSITORY, KEY_PRIVATE, KEY_BRANCH, KEY_URL, KEY_DESCRIPTION, KEY_CAN_PUSH,
    )

    fun saveSelectedRepository(repository: GitHubRepository) = saveRepository(
        repository, KEY_OWNER, KEY_REPOSITORY, KEY_PRIVATE, KEY_BRANCH, KEY_URL, KEY_DESCRIPTION, KEY_CAN_PUSH,
    )

    /** Dedicated target for APK builds. Falls back to the chat repository until separately set. */
    fun selectedBuildRepository(): GitHubRepository? = readRepository(
        KEY_BUILD_OWNER, KEY_BUILD_REPOSITORY, KEY_BUILD_PRIVATE, KEY_BUILD_BRANCH, KEY_BUILD_URL,
        KEY_BUILD_DESCRIPTION, KEY_BUILD_CAN_PUSH,
    )

    fun saveSelectedBuildRepository(repository: GitHubRepository) = saveRepository(
        repository, KEY_BUILD_OWNER, KEY_BUILD_REPOSITORY, KEY_BUILD_PRIVATE, KEY_BUILD_BRANCH,
        KEY_BUILD_URL, KEY_BUILD_DESCRIPTION, KEY_BUILD_CAN_PUSH,
    )

    private fun readRepository(
        ownerKey: String,
        repositoryKey: String,
        privateKey: String,
        branchKey: String,
        urlKey: String,
        descriptionKey: String,
        pushKey: String,
    ): GitHubRepository? {
        val owner = preferences.getString(ownerKey, "").orEmpty()
        val name = preferences.getString(repositoryKey, "").orEmpty()
        if (owner.isBlank() || name.isBlank()) return null
        return GitHubRepository(
            owner = owner,
            name = name,
            isPrivate = preferences.getBoolean(privateKey, false),
            defaultBranch = preferences.getString(branchKey, "").orEmpty(),
            htmlUrl = preferences.getString(urlKey, "https://github.com/$owner/$name").orEmpty(),
            description = preferences.getString(descriptionKey, "").orEmpty(),
            canPush = preferences.getBoolean(pushKey, false),
        )
    }

    private fun saveRepository(
        repository: GitHubRepository,
        ownerKey: String,
        repositoryKey: String,
        privateKey: String,
        branchKey: String,
        urlKey: String,
        descriptionKey: String,
        pushKey: String,
    ) {
        preferences.edit()
            .putString(ownerKey, repository.owner)
            .putString(repositoryKey, repository.name)
            .putBoolean(privateKey, repository.isPrivate)
            .putString(branchKey, repository.defaultBranch)
            .putString(urlKey, repository.htmlUrl)
            .putString(descriptionKey, repository.description)
            .putBoolean(pushKey, repository.canPush)
            .apply()
    }

    fun clearSelectedRepository() {
        preferences.edit()
            .remove(KEY_OWNER).remove(KEY_REPOSITORY).remove(KEY_PRIVATE)
            .remove(KEY_BRANCH).remove(KEY_URL).remove(KEY_DESCRIPTION).remove(KEY_CAN_PUSH)
            .remove(KEY_BUILD_OWNER).remove(KEY_BUILD_REPOSITORY).remove(KEY_BUILD_PRIVATE)
            .remove(KEY_BUILD_BRANCH).remove(KEY_BUILD_URL).remove(KEY_BUILD_DESCRIPTION)
            .remove(KEY_BUILD_CAN_PUSH)
            .apply()
    }

    companion object {
        private const val KEY_CLIENT_ID = "oauth_client_id"
        private const val KEY_OWNER = "selected_owner"
        private const val KEY_REPOSITORY = "selected_repository"
        private const val KEY_PRIVATE = "selected_private"
        private const val KEY_BRANCH = "selected_default_branch"
        private const val KEY_URL = "selected_url"
        private const val KEY_DESCRIPTION = "selected_description"
        private const val KEY_CAN_PUSH = "selected_can_push"
        private const val KEY_BUILD_OWNER = "build_owner"
        private const val KEY_BUILD_REPOSITORY = "build_repository"
        private const val KEY_BUILD_PRIVATE = "build_private"
        private const val KEY_BUILD_BRANCH = "build_default_branch"
        private const val KEY_BUILD_URL = "build_url"
        private const val KEY_BUILD_DESCRIPTION = "build_description"
        private const val KEY_BUILD_CAN_PUSH = "build_can_push"
    }
}

/** Keeps OAuth tokens encrypted and resumes pending Device Flow after Android recreates the activity. */
class GitHubOAuthSession(
    context: Context,
    private val secretStore: io.github.astromg01.nucleo.core.security.SecretStore,
    private val connector: GitHubConnector = GitHubConnector(),
) {
    private val preferences = context.getSharedPreferences("github_connector_session", Context.MODE_PRIVATE)
    private val connectorPreferences = GitHubConnectorPreferences(context)

    @Synchronized
    fun save(tokens: GitHubOAuthTokens) {
        secretStore.saveApiKey(GITHUB_OAUTH_TOKEN_ID, tokens.accessToken)
        if (!tokens.refreshToken.isNullOrBlank()) {
            secretStore.saveApiKey(GITHUB_OAUTH_REFRESH_TOKEN_ID, tokens.refreshToken)
        } else {
            secretStore.removeApiKey(GITHUB_OAUTH_REFRESH_TOKEN_ID)
        }
        val expiry = tokens.expiresInSeconds?.let { System.currentTimeMillis() + it * 1_000L } ?: 0L
        preferences.edit().putLong(KEY_ACCESS_EXPIRY, expiry).apply()
        clearPendingAuthorization()
    }

    fun hasStoredAccessToken(): Boolean =
        !secretStore.readApiKey(GITHUB_OAUTH_TOKEN_ID).isNullOrBlank()

    /** Store the short-lived device code encrypted because Android may suspend/recreate the app
     * while the user authorizes the OAuth app in the external browser. */
    @Synchronized
    fun savePendingAuthorization(authorization: GitHubDeviceAuthorization) {
        secretStore.saveApiKey(PENDING_DEVICE_CODE_ID, authorization.deviceCode)
        preferences.edit()
            .putString(KEY_PENDING_USER_CODE, authorization.userCode)
            .putString(KEY_PENDING_VERIFICATION_URI, authorization.verificationUri)
            .putLong(KEY_PENDING_EXPIRES_AT, System.currentTimeMillis() + authorization.expiresInSeconds * 1_000L)
            .putLong(KEY_PENDING_INTERVAL, authorization.intervalSeconds.coerceIn(5L, 60L))
            .apply()
    }

    @Synchronized
    fun pendingAuthorization(): GitHubDeviceAuthorization? {
        val deviceCode = secretStore.readApiKey(PENDING_DEVICE_CODE_ID)
        val expiresAt = preferences.getLong(KEY_PENDING_EXPIRES_AT, 0L)
        val remainingMillis = expiresAt - System.currentTimeMillis()
        if (deviceCode.isNullOrBlank() || remainingMillis <= 0L) {
            clearPendingAuthorization()
            return null
        }
        val userCode = preferences.getString(KEY_PENDING_USER_CODE, "").orEmpty()
        val verificationUri = preferences.getString(KEY_PENDING_VERIFICATION_URI, "").orEmpty()
        if (userCode.isBlank() || verificationUri.isBlank()) {
            clearPendingAuthorization()
            return null
        }
        return GitHubDeviceAuthorization(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUri = verificationUri,
            expiresInSeconds = (remainingMillis + 999L) / 1_000L,
            intervalSeconds = preferences.getLong(KEY_PENDING_INTERVAL, 5L).coerceIn(5L, 60L),
        )
    }

    @Synchronized
    fun clearPendingAuthorization() {
        secretStore.removeApiKey(PENDING_DEVICE_CODE_ID)
        preferences.edit()
            .remove(KEY_PENDING_USER_CODE)
            .remove(KEY_PENDING_VERIFICATION_URI)
            .remove(KEY_PENDING_EXPIRES_AT)
            .remove(KEY_PENDING_INTERVAL)
            .apply()
    }

    suspend fun validAccessToken(): String? {
        refreshMutex.lock()
        try {
            val token = secretStore.readApiKey(GITHUB_OAUTH_TOKEN_ID) ?: return null
            val expiry = preferences.getLong(KEY_ACCESS_EXPIRY, 0L)
            if (expiry == 0L || System.currentTimeMillis() < expiry - REFRESH_EARLY_MILLIS) return token
            val refresh = secretStore.readApiKey(GITHUB_OAUTH_REFRESH_TOKEN_ID)
                ?: throw IOException("A sessão GitHub expirou. Reconecte sua conta em Configurações.")
            val refreshed = connector.refreshAccessToken(connectorPreferences.clientId(), refresh)
            save(refreshed)
            return refreshed.accessToken
        } finally {
            refreshMutex.unlock()
        }
    }

    @Synchronized
    fun disconnect() {
        secretStore.removeApiKey(GITHUB_OAUTH_TOKEN_ID)
        secretStore.removeApiKey(GITHUB_OAUTH_REFRESH_TOKEN_ID)
        clearPendingAuthorization()
        preferences.edit().remove(KEY_ACCESS_EXPIRY).apply()
        connectorPreferences.clearSelectedRepository()
    }

    companion object {
        const val GITHUB_OAUTH_REFRESH_TOKEN_ID = "github_oauth_refresh_token"
        private const val PENDING_DEVICE_CODE_ID = "github_oauth_pending_device_code"
        private const val KEY_ACCESS_EXPIRY = "access_expires_at"
        private const val KEY_PENDING_USER_CODE = "pending_user_code"
        private const val KEY_PENDING_VERIFICATION_URI = "pending_verification_uri"
        private const val KEY_PENDING_EXPIRES_AT = "pending_expires_at"
        private const val KEY_PENDING_INTERVAL = "pending_interval"
        private const val REFRESH_EARLY_MILLIS = 2 * 60 * 1_000L
        private val refreshMutex = Mutex()
    }
}
