package io.github.astromg01.nucleo.domain.project

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Comparator
import java.util.Locale

enum class ProjectProfile {
    ANDROID_APP,
    WEB_APP,
    PYTHON,
    GENERIC,
}

data class GeneratedProjectFile(
    val path: String,
    val content: String,
)

data class ProjectDraft(
    val name: String,
    val profile: ProjectProfile,
    val description: String = "",
    val files: List<GeneratedProjectFile>,
)

data class StagedFileEvidence(
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class StagedFilePreview(
    val content: String,
    val truncated: Boolean,
    val sizeBytes: Long,
    val sha256: String,
)

enum class ProjectStageStatus {
    REVIEW_REQUIRED,
}

/**
 * A project written into app-private staging. It is source code only:
 * no build is implied by creating this record.
 */
data class StagedProject(
    val id: String,
    val name: String,
    val profile: ProjectProfile,
    val directory: File,
    val files: List<StagedFileEvidence>,
    val createdAtMillis: Long,
    val status: ProjectStageStatus = ProjectStageStatus.REVIEW_REQUIRED,
)

class ProjectDraftValidationException(
    val problems: List<String>,
) : IllegalArgumentException(problems.joinToString(separator = "\n"))

/**
 * Validates AI-produced project manifests and stages accepted text files into a fresh private
 * directory. This class never runs generated code, build scripts, shell commands, or network calls.
 */
class ProjectFactory {
    fun validate(draft: ProjectDraft) {
        prepareFiles(draft)
    }

    /**
     * Reads a bounded preview only when the staged file still matches its recorded size and hash.
     * The preview never executes or modifies generated content.
     */
    @Throws(IOException::class)
    fun readPreview(
        stagedProject: StagedProject,
        evidence: StagedFileEvidence,
        maxChars: Int = MAX_PREVIEW_CHARS,
    ): StagedFilePreview {
        require(maxChars in 1..MAX_PREVIEW_CHARS) {
            "O limite de prévia precisa estar entre 1 e $MAX_PREVIEW_CHARS caracteres."
        }

        val stagePath = stagedProject.directory.toPath().toAbsolutePath().normalize()
        if (Files.isSymbolicLink(stagePath) ||
            !Files.isDirectory(stagePath, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw IOException("A pasta temporária não existe ou não é segura.")
        }
        val realStagePath = try {
            stagePath.toRealPath()
        } catch (error: Exception) {
            throw IOException("Não foi possível verificar a pasta temporária.", error)
        }

        val segments = evidence.relativePath.split('/')
        if (segments.isEmpty() || segments.any { it.isBlank() || it == "." || it == ".." || '\\' in it }) {
            throw IOException("O caminho da prévia não é um caminho relativo seguro.")
        }

        var candidate = stagePath
        for (segment in segments) {
            candidate = candidate.resolve(segment)
            if (Files.isSymbolicLink(candidate)) {
                throw IOException("A prévia recusou um arquivo ou diretório que é link simbólico.")
            }
        }
        candidate = candidate.normalize()
        if (candidate == stagePath || !candidate.startsWith(stagePath)) {
            throw IOException("O caminho da prévia saiu da pasta temporária.")
        }

        val filePath = try {
            candidate.toRealPath()
        } catch (error: Exception) {
            throw IOException("O arquivo temporário não existe ou não pode ser lido.", error)
        }
        if (filePath == realStagePath ||
            !filePath.startsWith(realStagePath) ||
            !Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw IOException("A prévia não aponta para um arquivo regular dentro do staging.")
        }

        val actualSize = try {
            Files.size(filePath)
        } catch (error: Exception) {
            throw IOException("Não foi possível verificar o tamanho do arquivo.", error)
        }
        if (actualSize != evidence.sizeBytes || actualSize > MAX_FILE_BYTES) {
            throw IOException("O tamanho do arquivo temporário mudou após a validação.")
        }
        val bytes = try {
            Files.readAllBytes(filePath)
        } catch (error: Exception) {
            throw IOException("Não foi possível ler o arquivo temporário.", error)
        }
        if (bytes.size.toLong() != evidence.sizeBytes) {
            throw IOException("O arquivo temporário mudou durante a leitura.")
        }

        val digest = sha256(bytes)
        if (digest != evidence.sha256) {
            throw IOException("A integridade do arquivo mudou após a validação; a prévia foi bloqueada.")
        }
        val decoded = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (error: Exception) {
            throw IOException("O conteúdo não é texto UTF-8 válido.", error)
        }

        return StagedFilePreview(
            content = decoded.take(maxChars),
            truncated = decoded.length > maxChars,
            sizeBytes = actualSize,
            sha256 = digest,
        )
    }

    /**
     * Deletes only the selected staging directory when it is a direct child of stagingRoot.
     * This deliberately refuses arbitrary paths so generated content cannot select deletions.
     */
    @Throws(IOException::class)
    fun discard(stagedProject: StagedProject, stagingRoot: File) {
        val requestedRoot = stagingRoot.toPath().toAbsolutePath().normalize()
        if (Files.isSymbolicLink(requestedRoot)) {
            throw IOException("A raiz do staging não pode ser um link simbólico.")
        }
        val rootPath = try {
            requestedRoot.toFile().canonicalFile.toPath()
        } catch (error: Exception) {
            throw IOException("Não foi possível verificar a raiz do staging.", error)
        }

        val requestedStage = stagedProject.directory.toPath().toAbsolutePath().normalize()
        if (Files.isSymbolicLink(requestedStage)) {
            throw IOException("A pasta temporária não pode ser um link simbólico.")
        }
        if (requestedStage.fileName?.toString() != stagedProject.id) {
            throw IOException("O identificador do rascunho não corresponde à pasta temporária.")
        }
        val stagePath = try {
            requestedStage.toFile().canonicalFile.toPath()
        } catch (error: Exception) {
            throw IOException("Não foi possível verificar a pasta temporária.", error)
        }
        if (stagePath.parent != rootPath || !stagePath.startsWith(rootPath)) {
            throw IOException("A limpeza foi bloqueada porque o rascunho não é filho direto da raiz autorizada.")
        }
        if (!Files.exists(stagePath, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isSymbolicLink(stagePath)) {
            throw IOException("A limpeza recusou uma pasta temporária que é link simbólico.")
        }
        try {
            deleteTree(stagePath)
        } catch (error: Exception) {
            throw IOException("Não foi possível remover todos os arquivos temporários.", error)
        }
    }

    @Throws(IOException::class, ProjectDraftValidationException::class)
    fun stage(draft: ProjectDraft, stagingRoot: File): StagedProject {
        val prepared = prepareFiles(draft)
        val requestedRoot = stagingRoot.toPath().toAbsolutePath().normalize()
        if (Files.exists(requestedRoot, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(requestedRoot)) {
            throw IOException("A pasta temporária não pode ser um link simbólico.")
        }

        val rootPath = try {
            Files.createDirectories(requestedRoot)
            requestedRoot.toFile().canonicalFile.toPath()
        } catch (error: Exception) {
            throw IOException("Não foi possível preparar a pasta temporária privada.", error)
        }
        if (!Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(rootPath)) {
            throw IOException("O destino temporário não é uma pasta segura.")
        }

        val stagePath = Files.createTempDirectory(rootPath, safeSlug(draft.name) + "-")
        val records = mutableListOf<StagedFileEvidence>()
        try {
            for (file in prepared) {
                val output = stagePath.resolve(file.path).normalize()
                if (!output.startsWith(stagePath)) {
                    throw IOException("O caminho saiu da pasta temporária: ${file.path}")
                }
                Files.createDirectories(output.parent)
                val realParent = output.parent.toRealPath()
                if (!realParent.startsWith(stagePath.toRealPath())) {
                    throw IOException("O caminho de destino saiu da pasta temporária: ${file.path}")
                }
                Files.newOutputStream(
                    output,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                ).use { stream ->
                    stream.write(file.bytes)
                }
                records += StagedFileEvidence(
                    relativePath = file.path,
                    sizeBytes = file.bytes.size.toLong(),
                    sha256 = sha256(file.bytes),
                )
            }
            return StagedProject(
                id = stagePath.fileName.toString(),
                name = draft.name.trim(),
                profile = draft.profile,
                directory = stagePath.toFile(),
                files = records.toList(),
                createdAtMillis = System.currentTimeMillis(),
            )
        } catch (error: Exception) {
            try {
                deleteTree(stagePath)
            } catch (_: Exception) {
                // Preserve the original write error; staging remains private and can be cleaned later.
            }
            if (error is ProjectDraftValidationException) throw error
            throw IOException("Não foi possível preparar o projeto em staging.", error)
        }
    }

    private fun prepareFiles(draft: ProjectDraft): List<PreparedFile> {
        val problems = linkedSetOf<String>()
        val name = draft.name.trim()
        if (name.isBlank()) problems += "O projeto precisa ter um nome."
        if (name.length > MAX_NAME_CHARS) problems += "O nome do projeto excede $MAX_NAME_CHARS caracteres."
        if (name.any { it.isISOControl() }) problems += "O nome do projeto contém caracteres de controle."
        if (draft.description.length > MAX_DESCRIPTION_CHARS) {
            problems += "A descrição excede $MAX_DESCRIPTION_CHARS caracteres."
        }
        if (draft.files.isEmpty()) problems += "O projeto não contém arquivos."
        if (draft.files.size > MAX_FILES) problems += "O projeto excede o limite de $MAX_FILES arquivos."

        val prepared = mutableListOf<PreparedFile>()
        val pathKeys = linkedMapOf<String, String>()
        var totalBytes = 0L
        for (file in draft.files.take(MAX_FILES + 1)) {
            val pathProblem = validatePath(file.path)
            if (pathProblem != null) {
                problems += pathProblem
                continue
            }
            val key = file.path.lowercase(Locale.ROOT)
            val previous = pathKeys.putIfAbsent(key, file.path)
            if (previous != null) {
                problems += "Caminhos duplicados (sem diferenciar maiúsculas/minúsculas): '$previous' e '${file.path}'."
                continue
            }

            val bytes = file.content.toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_FILE_BYTES) {
                problems += "O arquivo '${file.path}' excede o limite de $MAX_FILE_BYTES bytes."
                continue
            }
            totalBytes += bytes.size
            if (totalBytes > MAX_TOTAL_BYTES) {
                problems += "O projeto excede o limite total de $MAX_TOTAL_BYTES bytes."
                continue
            }
            prepared += PreparedFile(file.path, bytes)
        }

        val validKeys = prepared.map { it.path.lowercase(Locale.ROOT) }.toSet()
        for (file in prepared) {
            val segments = file.path.lowercase(Locale.ROOT).split('/')
            for (index in 1 until segments.size) {
                val parent = segments.take(index).joinToString("/")
                if (parent in validKeys) {
                    problems += "O caminho '${file.path}' entra em conflito com um arquivo usado como pasta ('$parent')."
                }
            }
        }

        val paths = validKeys
        when (draft.profile) {
            ProjectProfile.ANDROID_APP -> {
                if ("settings.gradle.kts" !in paths && "settings.gradle" !in paths) {
                    problems += "O perfil Android exige settings.gradle.kts ou settings.gradle."
                }
                if ("build.gradle.kts" !in paths && "build.gradle" !in paths) {
                    problems += "O perfil Android exige um build.gradle na raiz."
                }
                if ("app/build.gradle.kts" !in paths && "app/build.gradle" !in paths) {
                    problems += "O perfil Android exige app/build.gradle.kts ou app/build.gradle."
                }
                if ("app/src/main/androidmanifest.xml" !in paths) {
                    problems += "O perfil Android exige app/src/main/AndroidManifest.xml."
                }
                if (prepared.none {
                        val lower = it.path.lowercase(Locale.ROOT)
                        lower.startsWith("app/src/main/java/") &&
                            (lower.endsWith(".kt") || lower.endsWith(".java"))
                    }
                ) {
                    problems += "O perfil Android exige ao menos um arquivo Kotlin ou Java em app/src/main/java/."
                }
            }
            ProjectProfile.WEB_APP -> {
                if ("package.json" !in paths) problems += "O perfil web exige package.json."
                if ("index.html" !in paths) problems += "O perfil web exige index.html."
            }
            ProjectProfile.PYTHON -> {
                if ("pyproject.toml" !in paths && "requirements.txt" !in paths) {
                    problems += "O perfil Python exige pyproject.toml ou requirements.txt."
                }
                if (prepared.none { it.path.lowercase(Locale.ROOT).endsWith(".py") }) {
                    problems += "O perfil Python exige ao menos um arquivo .py."
                }
            }
            ProjectProfile.GENERIC -> Unit
        }

        if (problems.isNotEmpty()) {
            throw ProjectDraftValidationException(problems.take(MAX_REPORTED_PROBLEMS))
        }
        return prepared
    }

    private fun validatePath(path: String): String? {
        if (path.isBlank()) return "Um arquivo tem caminho vazio."
        if (path != path.trim()) return "O caminho '$path' contém espaços no início ou no fim."
        if (path.length > MAX_PATH_CHARS) return "O caminho '$path' excede $MAX_PATH_CHARS caracteres."
        if (path.startsWith('/') || path.startsWith('\\') || WINDOWS_DRIVE.matches(path)) {
            return "O caminho '$path' precisa ser relativo à raiz do projeto."
        }
        if ('\\' in path) return "O caminho '$path' deve usar '/' como separador."
        val segments = path.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) {
            return "O caminho '$path' contém um segmento vazio ou traversal."
        }
        if (segments.any { segment ->
                segment.any { it.isISOControl() || it in INVALID_PATH_CHARACTERS } ||
                    segment.endsWith('.') || segment.endsWith(' ')
            }
        ) {
            return "O caminho '$path' contém caracteres incompatíveis ou de controle."
        }
        if (segments.any { it.lowercase(Locale.ROOT) in BLOCKED_DIRECTORIES }) {
            return "O caminho '$path' tenta incluir um diretório gerado/privado bloqueado."
        }

        val name = segments.last().lowercase(Locale.ROOT)
        if (name == ".env" || (name.startsWith(".env.") && name != ".env.example") ||
            name in BLOCKED_SECRET_FILES || SECRET_EXTENSIONS.any { name.endsWith(it) }
        ) {
            return "O arquivo '$path' parece conter segredos e não pode ser incluído no projeto gerado."
        }
        return null
    }

    private fun safeSlug(input: String): String =
        input.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-', '_')
            .take(MAX_SLUG_CHARS)
            .ifBlank { "project" }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun deleteTree(root: Path) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder<Path>()).forEach { Files.deleteIfExists(it) }
        }
    }

    private data class PreparedFile(val path: String, val bytes: ByteArray)

    companion object {
        const val MAX_FILES = 300
        const val MAX_FILE_BYTES = 512 * 1024
        const val MAX_TOTAL_BYTES = 4 * 1024 * 1024
        const val MAX_NAME_CHARS = 80
        const val MAX_DESCRIPTION_CHARS = 4_000
        const val MAX_PATH_CHARS = 240
        const val MAX_PREVIEW_CHARS = 16_000
        private const val MAX_REPORTED_PROBLEMS = 20
        private const val MAX_SLUG_CHARS = 32
        private val WINDOWS_DRIVE = Regex("^[A-Za-z]:.*")
        private val INVALID_PATH_CHARACTERS = setOf('<', '>', ':', '"', '|', '?', '*')
        private val BLOCKED_DIRECTORIES = setOf(
            ".git", ".gradle", "build", "node_modules", "dist", "target", "outputs", "secrets", ".ssh", ".aws",
        )
        private val BLOCKED_SECRET_FILES = setOf(
            "local.properties", "google-services.json", "credentials.json", "service-account.json",
            "id_rsa", "id_ed25519",
        )
        private val SECRET_EXTENSIONS = setOf(".jks", ".keystore", ".pem", ".p12", ".pfx", ".p8", ".key")
    }
}
