package io.github.astromg01.nucleo.domain.task

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import io.github.astromg01.nucleo.data.local.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

/** Reads a bounded, text-only project snapshot through the SAF permission chosen by the user. */
class ProjectSnapshotReader(private val context: Context) {
    suspend fun read(project: ProjectEntity): ProjectSnapshot = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(project.treeUri)
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("Não foi possível abrir a pasta autorizada do projeto.")
        if (!root.canRead()) throw IllegalStateException("O Núcleo não tem mais permissão para ler a pasta do projeto.")

        val paths = mutableListOf<String>()
        val excerpts = mutableListOf<Pair<String, String>>()
        var totalChars = 0
        var visited = 0
        var wasTruncated = false

        fun visit(parentUri: Uri, relativePath: String, depth: Int) {
            if (depth > MAX_DEPTH || visited >= MAX_FILES || totalChars >= MAX_TOTAL_CHARS) {
                wasTruncated = true
                return
            }
            val remaining = MAX_FILES - visited
            val children = queryChildren(treeUri, parentUri, remaining + 1).sortedBy { it.name.lowercase() }
            if (children.size > remaining) wasTruncated = true

            for (child in children.take(remaining)) {
                if (visited >= MAX_FILES || totalChars >= MAX_TOTAL_CHARS) {
                    wasTruncated = true
                    break
                }
                val name = child.name
                if (shouldSkip(name, child.isDirectory)) continue
                val path = if (relativePath.isEmpty()) name else "$relativePath/$name"
                visited++

                if (child.isDirectory) {
                    paths += "$path/"
                    visit(child.uri, path, depth + 1)
                } else {
                    paths += path
                    if (isTextCandidate(name) && excerpts.size < MAX_EXCERPTS && child.size <= MAX_DECLARED_FILE_BYTES) {
                        val text = readSmallText(child.uri)
                        if (text.isNotBlank()) {
                            val cleaned = redactLikelySecrets(text)
                            val bounded = cleaned.take(minOf(MAX_FILE_CHARS, MAX_TOTAL_CHARS - totalChars))
                            if (bounded.isNotBlank()) {
                                val excerpt = if (text.length >= MAX_FILE_CHARS) "$bounded\n[trecho limitado pelo Núcleo AI]" else bounded
                                excerpts += path to excerpt
                                totalChars += excerpt.length
                                if (text.length >= MAX_FILE_CHARS) wasTruncated = true
                            }
                        }
                    }
                }
            }
        }

        visit(root.uri, "", 0)
        ProjectSnapshot(
            projectName = project.name,
            fileCount = visited,
            tree = paths.take(MAX_FILES).joinToString("\n").take(MAX_TREE_CHARS),
            excerpts = excerpts,
            wasTruncated = wasTruncated || visited >= MAX_FILES || totalChars >= MAX_TOTAL_CHARS,
        )
    }

    /** Uses a cursor and stops at a bounded row count rather than materializing every child as DocumentFile. */
    private fun queryChildren(treeUri: Uri, parentUri: Uri, limit: Int): List<SnapshotEntry> {
        if (limit <= 0) return emptyList()
        val parentDocumentId = DocumentsContract.getDocumentId(parentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val entries = ArrayList<SnapshotEntry>(minOf(limit, 32))
        val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            ?: throw IllegalStateException("O provedor do armazenamento não conseguiu listar os arquivos do projeto.")
        cursor.use {
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            if (idIndex < 0 || nameIndex < 0 || mimeIndex < 0) {
                throw IllegalStateException("O provedor de arquivos retornou uma estrutura incompatível.")
            }
            while (entries.size < limit && cursor.moveToNext()) {
                val id = cursor.getString(idIndex) ?: continue
                val rawName = cursor.getString(nameIndex) ?: continue
                val name = sanitizeName(rawName)
                if (name.isBlank()) continue
                val mimeType = cursor.getString(mimeIndex) ?: "application/octet-stream"
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                entries += SnapshotEntry(
                    name = name,
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                    size = size,
                )
            }
        }
        return entries
    }

    private fun readSmallText(uri: Uri): String = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            InputStreamReader(input, Charsets.UTF_8).use { reader ->
                val buffer = CharArray(1024)
                val result = StringBuilder()
                while (result.length < MAX_FILE_CHARS) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, MAX_FILE_CHARS - result.length))
                    if (count <= 0) break
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
        }.orEmpty()
    } catch (_: Exception) {
        ""
    }

    private fun sanitizeName(name: String): String = name
        .replace(Regex("[\\r\\n\\t\\u0000-\\u001F]"), " ")
        .replace('/', '_')
        .replace('\\', '_')
        .take(160)
        .trim()

    private fun shouldSkip(name: String, isDirectory: Boolean): Boolean {
        val lower = name.lowercase()
        if (lower in IGNORED_NAMES) return true
        if (isDirectory) return lower in IGNORED_DIRS
        return lower.startsWith(".env") ||
            lower.contains("secret") || lower.contains("credential") ||
            lower.contains("serviceaccount") || lower.endsWith(".keystore") ||
            lower.endsWith(".jks") || lower.endsWith(".pem") || lower.endsWith(".p12") ||
            lower.endsWith(".pfx") || lower.endsWith(".key") || lower == "local.properties" ||
            lower == "google-services.json"
    }

    private fun isTextCandidate(name: String): Boolean {
        val lower = name.lowercase()
        if (lower in EXTENSIONLESS_TEXT_FILES) return true
        val extension = lower.substringAfterLast('.', "")
        return extension in TEXT_EXTENSIONS
    }

    private fun redactLikelySecrets(input: String): String {
        var value = input.replace(
            Regex("(?im)(api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password|passwd)\\s*([:=])\\s*[\\\"']?[^\\s\\\"'#,;]+"),
            "$1$2[REDACTED]",
        )
        value = value.replace(
            Regex("(?im)(authorization\\s*[:=]\\s*(?:bearer|basic)\\s+)[^\\s\"'#,;]+"),
            "$1[REDACTED]",
        )
        value = value.replace(Regex("(?i)sk-[a-z0-9_-]{16,}"), "[REDACTED_TOKEN]")
        value = value.replace(Regex("(?i)\\bAKIA[0-9A-Z]{16}\\b"), "[REDACTED_AWS_KEY]")
        value = value.replace(Regex("(?i)\\bgh[pousr]_[a-z0-9_]{20,}\\b|\\bgithub_pat_[a-z0-9_]{20,}\\b"), "[REDACTED_GITHUB_TOKEN]")
        return value
    }

    private data class SnapshotEntry(
        val name: String,
        val uri: Uri,
        val isDirectory: Boolean,
        val size: Long,
    )

    companion object {
        private const val MAX_DEPTH = 5
        private const val MAX_FILES = 300
        private const val MAX_EXCERPTS = 18
        private const val MAX_FILE_CHARS = 2_500
        private const val MAX_TOTAL_CHARS = 16_000
        private const val MAX_TREE_CHARS = 8_000
        private const val MAX_DECLARED_FILE_BYTES = 1_000_000L

        private val IGNORED_DIRS = setOf(
            ".git", ".gradle", "build", "node_modules", ".idea", ".kotlin", "dist", "target", "vendor",
            ".next", ".cache", "outputs", "captures",
        )
        private val IGNORED_NAMES = setOf(
            ".ds_store", "thumbs.db", "gradle-wrapper.jar", "package-lock.json", "pnpm-lock.yaml",
            "yarn.lock", "bun.lockb", "bun.lock", "google-services.json", "local.properties",
        )
        private val TEXT_EXTENSIONS = setOf(
            "kt", "kts", "java", "xml", "gradle", "md", "txt", "json", "toml", "yaml", "yml",
            "properties", "py", "ts", "tsx", "js", "jsx", "c", "cc", "cpp", "h", "hpp", "cs",
            "gd", "gdshader", "sh", "bash", "bat", "ps1", "html", "css", "scss", "sql", "swift",
            "dart", "rs", "go", "rb", "php", "vue", "svelte", "ini", "cfg", "conf", "make",
        )
        private val EXTENSIONLESS_TEXT_FILES = setOf(
            "readme", "license", "notice", "makefile", "dockerfile", "procfile", "gemfile", "rakefile",
            "justfile", "authors", "changelog",
        )
    }
}

data class ProjectSnapshot(
    val projectName: String,
    val fileCount: Int,
    val tree: String,
    val excerpts: List<Pair<String, String>>,
    val wasTruncated: Boolean,
) {
    fun asPromptContext(): String = buildString {
        appendLine("Projeto: $projectName")
        appendLine("Arquivos/pastas examinados: $fileCount${if (wasTruncated) " (snapshot limitado por segurança/recursos)" else ""}")
        appendLine("Árvore de arquivos (parcial):")
        appendLine(tree.ifBlank { "(Nenhum nome de arquivo foi lido.)" })
        if (excerpts.isNotEmpty()) {
            appendLine("Trechos de texto selecionados; arquivos secretos e artefatos binários conhecidos são ignorados:")
            excerpts.forEach { (path, content) ->
                appendLine("\n--- ARQUIVO: $path ---")
                appendLine(content)
            }
        }
    }.take(25_000)
}
