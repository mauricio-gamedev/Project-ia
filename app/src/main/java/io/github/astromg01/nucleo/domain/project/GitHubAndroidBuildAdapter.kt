package io.github.astromg01.nucleo.domain.project

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class GitHubBuildRepository(
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val htmlUrl: String,
)

data class BuiltAndroidArtifact(
    val file: File,
    val sha256: String,
    val repositoryUrl: String,
    val branch: String,
    val runUrl: String,
)

/**
 * Builds Android projects in a user-owned PRIVATE GitHub repository with GitHub Actions.
 *
 * Project files are uploaded as one verified ZIP. The build workflow runs with read-only
 * repository permissions and receives no provider API keys. Gradle scripts are still executable
 * code: callers must obtain explicit consent before invoking this adapter.
 */
class GitHubAndroidBuildAdapter(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
    suspend fun verifyRepository(owner: String, repository: String, token: String): GitHubBuildRepository =
        withContext(Dispatchers.IO) {
            validateIdentity(owner, repository, token)
            val response = apiCall(
                method = "GET",
                url = apiUrl("repos", owner, repository),
                token = token,
            )
            val json = JSONObject(response)
            if (!json.optBoolean("private", false)) {
                throw IOException("O repositório de build precisa ser privado. Não envie o código gerado para um repositório público.")
            }
            val permissions = json.optJSONObject("permissions")
            if (permissions?.optBoolean("push", false) != true) {
                throw IOException("O token não tem permissão de escrita nesse repositório. Use um token restrito ao repositório privado com Contents: Read and write.")
            }
            val defaultBranch = json.optString("default_branch", "").trim()
            if (defaultBranch.isBlank() || !isSafeBranch(defaultBranch)) {
                throw IOException("O repositório precisa ter uma branch padrão inicializada (por exemplo, main). Crie um README e tente novamente.")
            }
            GitHubBuildRepository(
                owner = owner,
                name = repository,
                defaultBranch = defaultBranch,
                htmlUrl = json.optString("html_url", "https://github.com/$owner/$repository"),
            )
        }

    suspend fun build(
        stagedProject: StagedProject,
        stagingRoot: File,
        owner: String,
        repository: String,
        token: String,
        onStatus: (String) -> Unit = {},
    ): BuiltAndroidArtifact = withContext(Dispatchers.IO) {
        if (stagedProject.profile != ProjectProfile.ANDROID_APP) {
            throw IOException("A compilação de APK aceita somente projetos do perfil Android.")
        }
        validateIdentity(owner, repository, token)
        onStatus("Verificando o repositório privado…")
        val target = verifyRepository(owner, repository, token)
        onStatus("Verificando o workflow de compilação…")
        ensureWorkflow(target, token)

        val sourceArchive = File(context.cacheDir, "nucleo-source-${UUID.randomUUID()}.zip")
        val artifactArchive = File(context.cacheDir, "nucleo-artifact-${UUID.randomUUID()}.zip")
        val apkOutput = File(context.cacheDir, "nucleo-built-${UUID.randomUUID()}.apk")
        try {
            val count = ProjectFactory().writeZip(stagedProject, FileOutputStream(sourceArchive))
            if (count != stagedProject.files.size || !sourceArchive.isFile || sourceArchive.length() !in 1..MAX_SOURCE_ARCHIVE_BYTES) {
                throw IOException("O ZIP do projeto não passou na verificação de tamanho/quantidade de arquivos.")
            }

            onStatus("Preparando uma branch privada temporária com o código-fonte…")
            val reference = JSONObject(apiCall(
                "GET",
                apiUrl("repos", owner, repository, "git", "ref", "heads", target.defaultBranch),
                token,
            ))
            val baseSha = reference.optJSONObject("object")?.optString("sha").orEmpty()
            if (!baseSha.matches(Regex("[a-fA-F0-9]{40}"))) {
                throw IOException("O GitHub não retornou o commit atual da branch padrão.")
            }
            val baseCommit = JSONObject(apiCall(
                "GET",
                apiUrl("repos", owner, repository, "git", "commits", baseSha),
                token,
            ))
            val baseTreeSha = baseCommit.optJSONObject("tree")?.optString("sha").orEmpty()
            if (!baseTreeSha.matches(Regex("[a-fA-F0-9]{40}"))) {
                throw IOException("O GitHub não retornou uma árvore Git válida.")
            }

            val archiveBase64 = Base64.encodeToString(sourceArchive.readBytes(), Base64.NO_WRAP)
            val blobSha = JSONObject(apiCall(
                "POST",
                apiUrl("repos", owner, repository, "git", "blobs"),
                token,
                JSONObject().put("content", archiveBase64).put("encoding", "base64"),
            )).optString("sha")
            if (!blobSha.matches(Regex("[a-fA-F0-9]{40}"))) {
                throw IOException("O GitHub não aceitou o arquivo de código-fonte.")
            }

            val tree = JSONArray().put(
                JSONObject()
                    .put("path", SOURCE_ARCHIVE_PATH)
                    .put("mode", "100644")
                    .put("type", "blob")
                    .put("sha", blobSha),
            )
            val newTreeSha = JSONObject(apiCall(
                "POST",
                apiUrl("repos", owner, repository, "git", "trees"),
                token,
                JSONObject().put("base_tree", baseTreeSha).put("tree", tree),
            )).optString("sha")
            if (!newTreeSha.matches(Regex("[a-fA-F0-9]{40}"))) {
                throw IOException("O GitHub não conseguiu preparar a árvore da compilação.")
            }

            val branch = "nucleo-build-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
            val commitSha = JSONObject(apiCall(
                "POST",
                apiUrl("repos", owner, repository, "git", "commits"),
                token,
                JSONObject()
                    .put("message", "Núcleo AI: enviar fonte Android para build")
                    .put("tree", newTreeSha)
                    .put("parents", JSONArray().put(baseSha)),
            )).optString("sha")
            if (!commitSha.matches(Regex("[a-fA-F0-9]{40}"))) {
                throw IOException("O GitHub não conseguiu criar o commit de build.")
            }
            apiCall(
                "POST",
                apiUrl("repos", owner, repository, "git", "refs"),
                token,
                JSONObject().put("ref", "refs/heads/$branch").put("sha", commitSha),
            )

            onStatus("Enviando o trabalho de compilação para o GitHub Actions…")
            apiCall(
                "POST",
                apiUrl("repos", owner, repository, "actions", "workflows", WORKFLOW_FILE, "dispatches"),
                token,
                JSONObject().put("ref", branch),
            )

            onStatus("Gradle está compilando o APK. Isso pode levar alguns minutos…")
            val run = awaitRun(target, branch, token, onStatus)
            val runUrl = run.optString("html_url", target.htmlUrl + "/actions")
            if (run.optString("conclusion") != "success") {
                throw IOException("A compilação falhou no GitHub Actions. Abra a execução para ver o erro: $runUrl")
            }

            onStatus("Compilação concluída. Baixando e verificando o artefato…")
            val artifacts = JSONObject(apiCall(
                "GET",
                apiUrl("repos", owner, repository, "actions", "runs", run.optLong("id").toString(), "artifacts"),
                token,
            )).optJSONArray("artifacts") ?: JSONArray()
            val artifact = (0 until artifacts.length())
                .mapNotNull { artifacts.optJSONObject(it) }
                .firstOrNull { it.optString("name") == ARTIFACT_NAME && !it.optBoolean("expired", false) }
                ?: throw IOException("O build terminou, mas o APK não foi publicado como artefato. Abra o log: $runUrl")
            val artifactId = artifact.optLong("id")
            if (artifactId <= 0L) throw IOException("O GitHub retornou um identificador de artefato inválido.")
            downloadArtifactZip(owner, repository, artifactId, token, artifactArchive)
            val digest = extractAndVerifyApk(artifactArchive, apkOutput)
            onStatus("APK compilado e verificado. Salve o arquivo no dispositivo.")
            BuiltAndroidArtifact(
                file = apkOutput,
                sha256 = digest,
                repositoryUrl = target.htmlUrl,
                branch = branch,
                runUrl = runUrl,
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            apkOutput.delete()
            throw cancelled
        } finally {
            sourceArchive.delete()
            artifactArchive.delete()
        }
    }

    private suspend fun awaitRun(
        target: GitHubBuildRepository,
        branch: String,
        token: String,
        onStatus: (String) -> Unit,
    ): JSONObject {
        var run: JSONObject? = null
        var lastStatus = ""
        repeat(MAX_POLL_ATTEMPTS) { attempt ->
            delay(POLL_INTERVAL_MILLIS)
            val list = JSONObject(apiCall(
                "GET",
                apiUrl("repos", target.owner, target.name, "actions", "workflows", WORKFLOW_FILE, "runs")
                    .newBuilder()
                    .addQueryParameter("branch", branch)
                    .addQueryParameter("event", "workflow_dispatch")
                    .addQueryParameter("per_page", "10")
                    .build(),
                token,
            )).optJSONArray("workflow_runs") ?: JSONArray()
            val candidate = (0 until list.length())
                .mapNotNull { list.optJSONObject(it) }
                .firstOrNull { it.optString("head_branch") == branch && it.optString("event") == "workflow_dispatch" }
            if (candidate != null) {
                val current = JSONObject(apiCall(
                    "GET",
                    apiUrl("repos", target.owner, target.name, "actions", "runs", candidate.optLong("id").toString()),
                    token,
                ))
                run = current
                val status = current.optString("status")
                if (status != lastStatus) {
                    lastStatus = status
                    onStatus(when (status) {
                        "queued", "requested" -> "Build na fila do GitHub Actions…"
                        "in_progress" -> "GitHub Actions está compilando o projeto Android…"
                        "completed" -> "GitHub Actions terminou a compilação."
                        else -> "Estado do build: $status."
                    })
                }
                if (status == "completed") {
                    if (current.optString("conclusion") != "success") {
                        val url = current.optString("html_url", target.htmlUrl + "/actions")
                        throw IOException("O Gradle não conseguiu compilar este projeto Android. Veja o log de build: $url")
                    }
                    return current
                }
            } else if (attempt == 3) {
                onStatus("Aguardando o GitHub iniciar o build… confira se Actions está habilitado no repositório.")
            }
        }
        val url = run?.optString("html_url")?.takeIf { it.isNotBlank() } ?: target.htmlUrl + "/actions"
        throw IOException("O GitHub Actions não concluiu o build dentro do limite de espera. A execução pode continuar em segundo plano: $url")
    }

    private suspend fun ensureWorkflow(target: GitHubBuildRepository, token: String) {
        val url = apiUrl("repos", target.owner, target.name, "contents", ".github", "workflows", WORKFLOW_FILE)
            .newBuilder()
            .addQueryParameter("ref", target.defaultBranch)
            .build()
        val existing = try {
            val response = apiCall("GET", url, token)
            JSONObject(response)
        } catch (error: GitHubBuildException) {
            if (error.statusCode == 404) null else throw error
        }
        if (existing != null) {
            val raw = existing.optString("content", "")
            val decoded = runCatching { String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault("")
            if (!decoded.contains(WORKFLOW_MARKER)) {
                throw IOException(
                    "Já existe um workflow com esse nome no repositório e ele não pertence ao Núcleo AI. " +
                        "Renomeie o workflow existente ou use um repositório privado dedicado; o Núcleo não vai sobrescrevê-lo.",
                )
            }
            if (decoded == WORKFLOW_CONTENT) return
            updateWorkflowFile(target, token, existing.optString("sha"), url)
            return
        }
        updateWorkflowFile(target, token, sha = null, url = url)
    }

    private fun updateWorkflowFile(
        target: GitHubBuildRepository,
        token: String,
        sha: String?,
        url: HttpUrl,
    ) {
        val body = JSONObject()
            .put("message", "Núcleo AI: instalar workflow de build Android")
            .put("content", Base64.encodeToString(WORKFLOW_CONTENT.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            .put("branch", target.defaultBranch)
        if (!sha.isNullOrBlank()) body.put("sha", sha)
        try {
            apiCall("PUT", url, token, body)
        } catch (error: GitHubBuildException) {
            if (error.statusCode == 403 || error.statusCode == 404) {
                throw IOException(
                    "O token precisa de Contents: Read and write e Workflows: Read and write no repositório privado.",
                )
            }
            throw error
        }
    }

    private fun downloadArtifactZip(
        owner: String,
        repository: String,
        artifactId: Long,
        token: String,
        destination: File,
    ) {
        val request = Request.Builder()
            .url(apiUrl("repos", owner, repository, "actions", "artifacts", artifactId.toString(), "zip"))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw GitHubBuildException(response.code, "O GitHub não permitiu baixar o artefato.")
            val body = response.body ?: throw IOException("O download do artefato retornou um corpo vazio.")
            val declared = body.contentLength()
            if (declared > MAX_ARTIFACT_ARCHIVE_BYTES) throw IOException("O artefato excede o limite de download.")
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_ARTIFACT_ARCHIVE_BYTES) throw IOException("O artefato excedeu o limite de download.")
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
    }

    private fun extractAndVerifyApk(archive: File, destination: File): String {
        var expectedSha: String? = null
        var extracted = false
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val leaf = entry.name.substringAfterLast('/').substringAfterLast('\\')
                when {
                    (leaf == "app-debug.apk" || leaf.endsWith(".apk")) && !extracted -> {
                        FileOutputStream(destination).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            val digest = MessageDigest.getInstance("SHA-256")
                            var fileBytes = 0L
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                fileBytes += count
                                totalBytes += count
                                if (fileBytes > MAX_APK_BYTES || totalBytes > MAX_APK_BYTES) {
                                    throw IOException("O APK excede o limite de tamanho permitido.")
                                }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                            val actual = digest.digest().joinToString("") { "%02x".format(it) }
                            extracted = true
                        }
                    }
                    leaf.endsWith(".sha256", ignoreCase = true) && entry.size <= 2_048L -> {
                        expectedSha = zip.readBytes().toString(Charsets.UTF_8)
                            .trim()
                            .split(Regex("\\s+"))
                            .firstOrNull()
                            ?.lowercase()
                    }
                    else -> while (zip.read(ByteArray(4 * 1024)) >= 0) { /* ignore unrelated artifact members */ }
                }
                zip.closeEntry()
            }
        }
        if (!extracted || !destination.isFile || destination.length() <= 0L) {
            destination.delete()
            throw IOException("O artefato baixado não contém um APK válido.")
        }
        val actualSha = sha256File(destination)
        if (expectedSha != null && expectedSha != actualSha) {
            destination.delete()
            throw IOException("O SHA-256 do APK não corresponde ao checksum publicado pelo GitHub Actions.")
        }
        return actualSha
    }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun validateIdentity(owner: String, repository: String, token: String) {
        require(owner.matches(OWNER_PATTERN)) { "O proprietário do repositório GitHub não é válido." }
        require(repository.matches(REPOSITORY_PATTERN)) { "O nome do repositório GitHub não é válido." }
        require(token.isNotBlank() && token.length <= 512 && !token.any { it.isWhitespace() }) {
            "Cole um token pessoal do GitHub válido. O token não é enviado ao modelo de IA."
        }
    }

    private fun isSafeBranch(branch: String): Boolean =
        branch.length <= 100 && branch.none { it.isWhitespace() || it == '?' || it == '*' || it == '[' || it == ']' || it == ':' } &&
            !branch.startsWith('/') && !branch.endsWith('/') && ".." !in branch && "@{" !in branch

    private fun apiUrl(vararg segments: String): HttpUrl {
        val builder = API_BASE.toHttpUrl().newBuilder()
        segments.forEach(builder::addPathSegment)
        return builder.build()
    }

    private fun apiCall(
        method: String,
        url: HttpUrl,
        token: String,
        body: JSONObject? = null,
    ): String {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
        val jsonBody = body?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
        builder.method(method, when (method) {
            "GET" -> null
            "DELETE" -> null
            else -> jsonBody ?: "{}".toRequestBody(JSON_MEDIA_TYPE)
        })
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401 -> "O GitHub recusou o token. Confirme se ele está válido."
                    403 -> "O GitHub bloqueou a operação. Confira as permissões do token, a cota da API e se Actions está habilitado."
                    404 -> "O GitHub não encontrou o repositório, workflow ou recurso solicitado. Confira owner/nome e permissões."
                    409, 422 -> "O GitHub recusou a alteração Git. Verifique se o repositório tem uma branch padrão inicializada."
                    429 -> "O GitHub limitou as requisições. Tente novamente mais tarde."
                    in 500..599 -> "O GitHub apresentou uma falha temporária."
                    else -> "O GitHub respondeu HTTP ${response.code}."
                }
                throw GitHubBuildException(response.code, message)
            }
            return text
        }
    }

    class GitHubBuildException(
        val statusCode: Int,
        message: String,
    ) : IOException(message)

    companion object {
        private const val API_VERSION = "2022-11-28"
        private const val API_BASE = "https://api.github.com"
        private const val SOURCE_ARCHIVE_PATH = "nucleo-source.zip"
        private const val WORKFLOW_FILE = "nucleo-android-build.yml"
        private const val ARTIFACT_NAME = "nucleo-android-apk"
        private const val MAX_SOURCE_ARCHIVE_BYTES = 4L * 1024L * 1024L
        private const val MAX_ARTIFACT_ARCHIVE_BYTES = 160L * 1024L * 1024L
        private const val MAX_APK_BYTES = 120L * 1024L * 1024L
        private const val MAX_POLL_ATTEMPTS = 48
        private const val POLL_INTERVAL_MILLIS = 10_000L
        private val OWNER_PATTERN = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37})")
        private val REPOSITORY_PATTERN = Regex("[A-Za-z0-9_.-]{1,100}")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val WORKFLOW_MARKER = "# NUCLEO_AI_ANDROID_BUILD_WORKFLOW_V1"
        private val WORKFLOW_CONTENT = """
            # NUCLEO_AI_ANDROID_BUILD_WORKFLOW_V1
            name: Núcleo AI Android APK builder

            on:
              workflow_dispatch:

            permissions:
              contents: read

            jobs:
              build:
                runs-on: ubuntu-latest
                timeout-minutes: 25
                permissions:
                  contents: read
                steps:
                  - name: Checkout private build workspace
                    uses: actions/checkout@v4
                  - name: Set up JDK 17
                    uses: actions/setup-java@v4
                    with:
                      distribution: temurin
                      java-version: '17'
                  - name: Keep a stable debug signing key for repeat builds
                    uses: actions/cache@v4
                    with:
                      path: ~/.android/debug.keystore
                      key: nucleo-ai-debug-signing-key-v1
                  - name: Locate Android SDK manager
                    id: android-sdk
                    shell: bash
                    run: |
                      set -euo pipefail
                      test -d "§ANDROID_HOME/cmdline-tools"
                      sdkmanager_path="$(find "§ANDROID_HOME/cmdline-tools" -type f -name sdkmanager -print | sort -V | tail -n 1)"
                      test -n "§sdkmanager_path"
                      echo "sdkmanager=§sdkmanager_path" >> "§GITHUB_OUTPUT"
                  - name: Install Android SDK packages
                    shell: bash
                    run: |
                      set -euo pipefail
                      yes | "§{{ steps.android-sdk.outputs.sdkmanager }} --licenses >/dev/null || true
                      "§{{ steps.android-sdk.outputs.sdkmanager }} "platforms;android-36" "build-tools;36.0.0" "platform-tools"
                  - name: Download and verify Gradle 8.13
                    shell: bash
                    run: |
                      set -euo pipefail
                      curl --fail --location --retry 3 --retry-delay 2 "https://services.gradle.org/distributions/gradle-8.13-bin.zip" --output /tmp/gradle-bin.zip
                      echo "20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78  /tmp/gradle-bin.zip" | sha256sum --check
                      unzip -q /tmp/gradle-bin.zip -d /tmp/nucleo-gradle
                  - name: Unpack and validate source project
                    shell: bash
                    run: |
                      set -euo pipefail
                      test -s nucleo-source.zip
                      mkdir -p project-source
                      unzip -q nucleo-source.zip -d project-source
                      test -s project-source/settings.gradle.kts || test -s project-source/settings.gradle
                      test -s project-source/build.gradle.kts || test -s project-source/build.gradle
                      test -s project-source/app/build.gradle.kts || test -s project-source/app/build.gradle
                      test -s project-source/app/src/main/AndroidManifest.xml
                  - name: Compile debug APK
                    shell: bash
                    run: |
                      set -euo pipefail
                      "/tmp/nucleo-gradle/gradle-8.13/bin/gradle" --no-daemon --stacktrace -p project-source :app:assembleDebug
                  - name: Verify APK and checksum
                    shell: bash
                    run: |
                      set -euo pipefail
                      apk="project-source/app/build/outputs/apk/debug/app-debug.apk"
                      test -s "§apk"
                      "§ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose "§apk"
                      mkdir -p dist
                      cp "§apk" dist/app-debug.apk
                      (cd dist && sha256sum app-debug.apk > app-debug.apk.sha256)
                  - name: Publish APK artifact
                    uses: actions/upload-artifact@v4
                    with:
                      name: nucleo-android-apk
                      path: dist/*
                      if-no-files-found: error
                      retention-days: 7
        """.trimIndent().replace("§", "$")
    }
}
