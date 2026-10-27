package io.github.astromg01.nucleo.export

import androidx.annotation.Keep
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.Locale

/**
 * Shizuku UserService that can write one explicitly requested ZIP into public Downloads.
 *
 * The app streams bytes over Binder; this service never reads the app-private staging folder
 * and never runs shell commands. Only a sanitized filename is accepted.
 */
@Keep
class ShizukuExportService : IShizukuExportService.Stub() {
    private var output: FileOutputStream? = null
    private var temporaryFile: File? = null
    private var destinationFile: File? = null
    private var writtenBytes = 0L

    @Keep
    constructor()

    @Synchronized
    override fun beginExport(requestedName: String): String {
        if (output != null) {
            throw IllegalStateException("Já existe uma exportação Shizuku em andamento.")
        }

        try {
            val destinationDirectory = prepareDestinationDirectory()
            val baseName = safeBaseName(requestedName)
            var candidate = File(destinationDirectory, "$baseName.zip")
            var suffix = 2
            while (candidate.exists()) {
                candidate = File(destinationDirectory, "$baseName-$suffix.zip")
                suffix++
                if (suffix > 1000) throw IOException("Não foi possível encontrar um nome de ZIP livre.")
            }

            val temporary = File.createTempFile(".nucleo-export-", ".part", destinationDirectory)
            temporaryFile = temporary
            destinationFile = candidate
            output = FileOutputStream(temporary, false)
            writtenBytes = 0L
            return candidate.name
        } catch (error: Exception) {
            cleanupPartialExport()
            throw IllegalStateException(
                "Shizuku não conseguiu criar um arquivo em Download/NucleoAIProjects. Confirme se o Shizuku está iniciado e autorizado.",
                error,
            )
        }
    }

    @Synchronized
    override fun writeChunk(chunk: ByteArray) {
        val stream = output ?: throw IllegalStateException("Nenhuma exportação Shizuku foi iniciada.")
        if (chunk.isEmpty()) return
        if (chunk.size > MAX_CHUNK_BYTES) {
            throw IllegalArgumentException("O bloco enviado excedeu o limite seguro.")
        }
        if (writtenBytes + chunk.size > MAX_ARCHIVE_BYTES) {
            throw IllegalArgumentException("O ZIP excedeu o limite de tamanho da exportação.")
        }
        try {
            stream.write(chunk)
            writtenBytes += chunk.size
        } catch (error: IOException) {
            cleanupPartialExport()
            throw IllegalStateException("Falha ao gravar o ZIP pelo Shizuku.", error)
        }
    }

    @Synchronized
    override fun finishExport(): String {
        val stream = output ?: throw IllegalStateException("Nenhuma exportação Shizuku está aberta.")
        val temporary = temporaryFile ?: throw IllegalStateException("Arquivo temporário da exportação ausente.")
        val destination = destinationFile ?: throw IllegalStateException("Destino da exportação ausente.")
        try {
            if (writtenBytes <= 0L) throw IOException("O ZIP recebido está vazio.")
            stream.flush()
            stream.fd.sync()
            stream.close()
            output = null

            if (destination.exists()) {
                throw IOException("O arquivo de destino já existe; nenhum arquivo foi sobrescrito.")
            }
            Files.move(temporary.toPath(), destination.toPath())
            temporaryFile = null
            destinationFile = null
            writtenBytes = 0L
            return destination.absolutePath
        } catch (error: Exception) {
            cleanupPartialExport()
            throw IllegalStateException(
                "Não foi possível finalizar o ZIP em Download/NucleoAIProjects. Nenhum arquivo existente foi sobrescrito.",
                error,
            )
        }
    }

    @Synchronized
    override fun abortExport() {
        cleanupPartialExport()
    }

    @Keep
    @Synchronized
    override fun destroy() {
        cleanupPartialExport()
        System.exit(0)
    }

    private fun prepareDestinationDirectory(): File {
        val downloads = File("/sdcard/Download").canonicalFile
        if (!downloads.isDirectory) {
            throw IOException("A pasta pública Download não está acessível para o processo Shizuku.")
        }
        val directory = File(downloads, "NucleoAIProjects")
        if (Files.isSymbolicLink(directory.toPath())) {
            throw IOException("A pasta de exportação não pode ser um link simbólico.")
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Não foi possível criar Download/NucleoAIProjects.")
        }
        val canonical = directory.canonicalFile
        if (!canonical.isDirectory || canonical.parentFile != downloads) {
            throw IOException("O caminho de exportação saiu da pasta Download.")
        }
        return canonical
    }

    private fun safeBaseName(raw: String): String {
        val withoutExtension = if (raw.lowercase(Locale.ROOT).endsWith(".zip")) {
            raw.dropLast(4)
        } else {
            raw
        }
        return withoutExtension
            .map { character ->
                if (character.isLetterOrDigit() || character in listOf(' ', '-', '_', '.')) character else '-'
            }
            .joinToString("")
            .trim()
            .trim('.')
            .take(55)
            .ifBlank { "nucleo-project" }
    }

    private fun cleanupPartialExport() {
        try {
            output?.close()
        } catch (_: Exception) {
            // Best-effort cleanup after a partial write.
        }
        output = null
        try {
            temporaryFile?.delete()
        } catch (_: Exception) {
            // Best-effort cleanup of only this export's temporary file.
        }
        temporaryFile = null
        destinationFile = null
        writtenBytes = 0L
    }

    private companion object {
        const val MAX_CHUNK_BYTES = 256 * 1024
        const val MAX_ARCHIVE_BYTES = 8L * 1024 * 1024
    }
}
