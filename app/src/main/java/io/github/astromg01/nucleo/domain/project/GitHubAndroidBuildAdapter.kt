package io.github.astromg01.nucleo.domain.project

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

private val SHA256_DIGEST_PATTERN = Regex("[0-9a-fA-F]{64}")
private const val MAX_DIAGNOSTIC_CHARS = 8_000
private const val MAX_DIAGNOSTIC_LINES = 90

/** Decode plain-text fallback logs without accidentally surfacing binary ZIP data in the UI or AI prompt. */
internal fun readableRawBuildLog(bytes: ByteArray, maxChars: Int): String? {
    if (bytes.isEmpty() || maxChars <= 0) return null
    val decoded = bytes.toString(Charsets.UTF_8)
    if (decoded.isBlank()) return null
    val readableCount = decoded.count { it == '\n' || it == '\r' || it == '\t' || !it.isISOControl() }
    if (readableCount.toDouble() / decoded.length < 0.90) return null
    return decoded.trim().takeLast(maxChars).takeIf { it.isNotBlank() }
}

internal fun verifyPublishedApkChecksum(expectedChecksum: String?, actualChecksum: String) {
    val expected = expectedChecksum?.trim()?.takeIf { SHA256_DIGEST_PATTERN.matches(it) }
        ?: throw IOException("O artefato não contém um SHA-256 válido para o APK.")
    if (!SHA256_DIGEST_PATTERN.matches(actualChecksum) || !expected.equals(actualChecksum, ignoreCase = true)) {
        throw IOException("O SHA-256 do APK não corresponde ao checksum publicado pelo GitHub Actions.")
    }
}

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


/** Sanitized build failure with the run URL and the most relevant Gradle/compiler diagnostics. */
class AndroidBuildFailureException(
    val runUrl: String,
    val conclusion: String,
    val diagnostics: String,
) : IOException(
    "O Gradle não conseguiu compilar o projeto (resultado: " + conclusion + "). " +
        (if (diagnostics.isBlank()) "Abra a execução para consultar o log." else "Os erros principais foram capturados para a correção assistida.") +
        " Execução: " + runUrl,
)

internal fun classifyGitHubBuildFailure(statusCode: Int, providerMessage: String = ""): String {
    val detail = providerMessage.lowercase()
    return when (statusCode) {
        401 -> "O GitHub recusou a sessão OAuth (HTTP 401). Ela pode ter expirado ou sido revogada; sincronize ou reconecte a conta."
        403 -> when {
            detail.contains("protected") || detail.contains("rule violations") ||
                detail.contains("cannot update this protected ref") ->
                "A conta GitHub foi autenticada, mas uma regra de proteção da branch bloqueou a instalação/atualização do workflow. Use um repositório privado de build dedicado com uma branch padrão que permita essa configuração, ou ajuste a regra por um caminho autorizado. O Núcleo não vai contornar a proteção."
            detail.contains("rate limit") || detail.contains("api rate limit") ->
                "O GitHub limitou as requisições da API. Aguarde a cota reiniciar e tente novamente."
            detail.contains("resource not accessible") || detail.contains("insufficient") ->
                "A sessão existe, mas não tem permissão para este recurso. Confirme os escopos repo/workflow, acesso de escrita ao repositório e se GitHub Actions está habilitado."
            else ->
                "O GitHub bloqueou a operação (HTTP 403). A conta pode estar conectada, mas falta permissão de escrita/workflow, a cota da API foi atingida ou Actions está desabilitado."
        }
        404 -> "O GitHub não encontrou o repositório, workflow ou recurso. Em repositórios privados, confirme owner/nome e se a conta autorizada tem acesso."
        409, 422 -> when {
            detail.contains("protected") || detail.contains("rule violations") ->
                "Uma regra de proteção ou política do repositório bloqueou a alteração. Use uma configuração de build autorizada; o Núcleo não vai ignorar regras de branch."
            else ->
                "O GitHub recusou a alteração Git (HTTP $statusCode). Confira se a branch padrão está inicializada e se a referência ainda existe."
        }
        429 -> "O GitHub limitou as requisições (HTTP 429). Aguarde e tente novamente."
        in 500..599 -> "O GitHub apresentou uma falha temporária (HTTP $statusCode). Tente novamente."
        else -> "O GitHub respondeu HTTP $statusCode durante a operação de build."
    }
}

internal fun sanitizeBuildDiagnostics(raw: String): String {
    var safe = raw
        .replace(Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s]+")) {
            it.groupValues[1] + "[REDACTED]"
        }
        .replace(Regex("\\bgh[pousr]_[A-Za-z0-9_]{20,}\\b"), "[REDACTED]")
        .replace(Regex("\\bgithub_pat_[A-Za-z0-9_]{20,}\\b"), "[REDACTED]")
        .replace(Regex("\\bsk-[A-Za-z0-9_-]{15,}\\b"), "[REDACTED]")
        .replace(Regex("\\bAKIA[0-9A-Z]{16}\\b"), "[REDACTED]")
        .replace(Regex("\\bAIza[0-9A-Za-z_-]{30,}\\b"), "[REDACTED]")
        .replace(Regex("(?i)((?:api[_-]?key|(?:access|refresh)?[_-]?token|client[_-]?secret|password|secret|credential)\\s*[=:]\\s*)[^\\s,;]+")) {
            it.groupValues[1] + "[REDACTED]"
        }
    val important = Regex(
        "(?i)(^\\s*e: |\\berror:|unresolved reference|cannot find symbol|compilation failed|failed with an exception|what went wrong|execution failed|could not resolve|could not find|manifest merger failed|duplicate class|type mismatch|expecting [a-z]+|not enough information to infer|is not abstract and does not implement|resource linking failed|aapt:|\\.kt:[0-9]+:[0-9]+|\\.java:[0-9]+:[0-9]+)"
    )
    val lines = safe.lineSequence().toList()
    var selected = lines.filter { important.containsMatchIn(it) }
    if (selected.isEmpty()) selected = lines.takeLast(40)
    safe = selected.takeLast(MAX_DIAGNOSTIC_LINES).joinToString("\n")
    return safe.takeLast(MAX_DIAGNOSTIC_CHARS)
}

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
                throw IOException(
                    "A conta GitHub autorizada não tem permissão de escrita confirmada neste repositório. " +
                        "Confirme que você pode enviar commits para ele, que o repositório é privado e que a autorização OAuth inclui repo e workflow. " +
                        "Uma regra de proteção também pode bloquear a operação mesmo com a conta conectada.",
                )
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
            val conclusion = run.optString("conclusion", "unknown")
            if (conclusion != "success") {
                onStatus("O GitHub Actions terminou com falha. Recolhendo os erros principais do Gradle…")
                val diagnostics = collectBuildDiagnostics(
                    owner = owner,
                    repository = repository,
                    runId = run.optLong("id"),
                    token = token,
                )
                throw AndroidBuildFailureException(runUrl, conclusion, diagnostics)
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
                    return current
                }
            } else if (attempt == 3) {
                onStatus("Aguardando o GitHub iniciar o build… confira se Actions está habilitado no repositório.")
            }
        }
        val url = run?.optString("html_url")?.takeIf { it.isNotBlank() } ?: target.htmlUrl + "/actions"
        throw IOException("O GitHub Actions não concluiu o build dentro do limite de espera. A execução pode continuar em segundo plano: $url")
    }

    private fun collectBuildDiagnostics(
        owner: String,
        repository: String,
        runId: Long,
        token: String,
    ): String {
        if (runId <= 0L) return ""
        return try {
            val url = apiUrl("repos", owner, repository, "actions", "runs", runId.toString(), "jobs")
                .newBuilder()
                .addQueryParameter("per_page", "50")
                .build()
            val jobs = JSONObject(apiCall("GET", url, token)).optJSONArray("jobs") ?: JSONArray()
            val failedJobs = (0 until jobs.length())
                .mapNotNull { jobs.optJSONObject(it) }
                .filter { it.optString("conclusion") == "failure" }
                .take(MAX_DIAGNOSTIC_JOBS)
            val raw = buildString {
                failedJobs.forEach { job ->
                    val id = job.optLong("id")
                    if (id <= 0L) return@forEach
                    appendLine("===== FAILED JOB: " + job.optString("name", "Android build") + " =====")
                    appendLine(downloadJobLogs(owner, repository, id, token))
                    appendLine()
                }
            }
            sanitizeBuildDiagnostics(raw)
        } catch (_: Exception) {
            ""
        }
    }

    private fun downloadJobLogs(
        owner: String,
        repository: String,
        jobId: Long,
        token: String,
    ): String {
        val request = Request.Builder()
            .url(apiUrl("repos", owner, repository, "actions", "jobs", jobId.toString(), "logs"))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw GitHubBuildException(response.code, "O GitHub não permitiu consultar o log da compilação.")
            }
            val body = response.body ?: return ""
            val archive = ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_BUILD_LOG_ARCHIVE_BYTES) {
                        throw IOException("O arquivo de log excedeu o limite de diagnóstico.")
                    }
                    archive.write(buffer, 0, count)
                }
            }
            val bytes = archive.toByteArray()
            if (bytes.isEmpty()) return "O GitHub retornou um log vazio para o job $jobId."

            // The Actions API normally returns a ZIP archive. Some gateways/proxies can
            // return decoded plain text instead; don't pass that text into ZipInputStream.
            val isZipArchive = bytes.size >= 4 &&
                bytes[0] == 'P'.code.toByte() &&
                bytes[1] == 'K'.code.toByte()
            if (!isZipArchive) {
                return readableRawBuildLog(bytes, MAX_RAW_LOG_CHARS)
                    ?: "O GitHub retornou um log em formato de texto ilegível para o job $jobId."
            }

            val combined = StringBuilder()
            var foundEntry = false
            try {
                ZipInputStream(bytes.inputStream()).use { zip ->
                    while (combined.length < MAX_RAW_LOG_CHARS) {
                        val entry = zip.nextEntry ?: break
                        foundEntry = true
                        if (!entry.isDirectory) {
                            combined.appendLine("LOG: " + entry.name.take(160))
                            val entryBytes = readZipEntryAtMost(zip, MAX_BYTES_PER_LOG_ENTRY)
                            combined.appendLine(entryBytes.toString(Charsets.UTF_8))
                            if (entryBytes.size >= MAX_BYTES_PER_LOG_ENTRY) {
                                combined.appendLine("[log truncado pelo limite de tamanho]")
                            }
                            combined.appendLine()
                        }
                        zip.closeEntry()
                    }
                }
            } catch (_: java.util.zip.ZipException) {
                return readableRawBuildLog(bytes, MAX_RAW_LOG_CHARS)
                    ?: "O GitHub retornou um arquivo de log ZIP inválido para o job $jobId."
            }
            if (!foundEntry) {
                // A gateway can return plain text despite a ZIP-like header. Use the body only
                // when it is mostly printable text; otherwise avoid showing binary bytes.
                return readableRawBuildLog(bytes, MAX_RAW_LOG_CHARS)
                    ?: "O GitHub retornou um arquivo de log ZIP sem entradas para o job $jobId."
            }
            // Build/compiler errors tend to be near the end; preserve the tail when limiting.
            return combined.toString().takeLast(MAX_RAW_LOG_CHARS)
        }
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
            val detail = error.message.orEmpty()
            if (error.statusCode == 403 && detail.contains("regra de proteção", ignoreCase = true)) {
                throw IOException(detail, error)
            }
            if (error.statusCode == 403 || error.statusCode == 404) {
                throw IOException(
                    "O GitHub não conseguiu instalar o workflow. Confirme Contents: Read and write, Workflows: Read and write, acesso à branch padrão e regras de proteção do repositório. $detail",
                    error,
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
        try {
            var expectedSha: String? = null
            var checksumEntrySeen = false
            var extracted = false
            var totalBytes = 0L
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    when (entry.name) {
                        "app-debug.apk" -> {
                            if (extracted) throw IOException("O artefato contém mais de um APK de depuração.")
                            FileOutputStream(destination).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var fileBytes = 0L
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    fileBytes += count
                                    totalBytes += count
                                    if (fileBytes > MAX_APK_BYTES || totalBytes > MAX_APK_BYTES) {
                                        throw IOException("O APK excede o limite de tamanho permitido.")
                                    }
                                    output.write(buffer, 0, count)
                                }
                            }
                            extracted = true
                        }
                        "app-debug.apk.sha256" -> {
                            if (checksumEntrySeen) throw IOException("O artefato contém checksums duplicados.")
                            checksumEntrySeen = true
                            val checksumBytes = readZipEntryAtMost(zip, MAX_CHECKSUM_ENTRY_BYTES)
                            if (checksumBytes.size >= MAX_CHECKSUM_ENTRY_BYTES) {
                                throw IOException("O checksum publicado excedeu o limite esperado.")
                            }
                            expectedSha = checksumBytes.toString(Charsets.UTF_8)
                                .trim()
                                .split(Regex("\\s+"))
                                .firstOrNull()
                        }
                        else -> throw IOException("O artefato contém um arquivo inesperado: ${entry.name}")
                    }
                    zip.closeEntry()
                }
            }
            if (!extracted || !destination.isFile || destination.length() <= 0L) {
                throw IOException("O artefato baixado não contém app-debug.apk válido.")
            }
            val actualSha = sha256File(destination)
            verifyPublishedApkChecksum(expectedSha, actualSha)
            return actualSha
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }
    private fun readZipEntryAtMost(input: ZipInputStream, maxBytes: Int): ByteArray {
        require(maxBytes > 0) { "O limite de leitura do ZIP precisa ser positivo." }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(256)
        var total = 0
        while (total < maxBytes) {
            val wanted = minOf(buffer.size, maxBytes - total)
            val count = input.read(buffer, 0, wanted)
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
            total += count
        }
        // The caller closes the entry after this bounded read; closeEntry() safely discards
        // the remainder, so a long log entry does not discard all diagnostic output.
        return output.toByteArray()
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
            "A sessão OAuth do GitHub está ausente ou inválida. Reconecte a conta em Configurações; não é necessário colar um token pessoal."
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
                val providerMessage = runCatching {
                    org.json.JSONObject(text).optString("message").take(400)
                }.getOrDefault("")
                throw GitHubBuildException(
                    response.code,
                    classifyGitHubBuildFailure(response.code, providerMessage),
                )
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
        private const val MAX_SOURCE_ARCHIVE_BYTES = 8L * 1024L * 1024L
        private const val MAX_ARTIFACT_ARCHIVE_BYTES = 160L * 1024L * 1024L
        private const val MAX_APK_BYTES = 120L * 1024L * 1024L
        private const val MAX_BUILD_LOG_ARCHIVE_BYTES = 8 * 1024 * 1024
        private const val MAX_BYTES_PER_LOG_ENTRY = 1_500_000
        private const val MAX_CHECKSUM_ENTRY_BYTES = 2_048
        private const val MAX_RAW_LOG_CHARS = 200_000
        private const val MAX_DIAGNOSTIC_JOBS = 3
        private const val MAX_POLL_ATTEMPTS = 120
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
                    with:
                      persist-credentials: false
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
                      yes | "§{{ steps.android-sdk.outputs.sdkmanager }}" --licenses >/dev/null || true
                      "§{{ steps.android-sdk.outputs.sdkmanager }}" "platforms;android-36" "build-tools;36.0.0" "platform-tools"
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
        """.trimIndent().replace("§", "\$")
    }
}
