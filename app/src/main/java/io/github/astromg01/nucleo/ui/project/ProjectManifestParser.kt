package io.github.astromg01.nucleo.ui.project

import io.github.astromg01.nucleo.domain.project.GeneratedProjectFile
import io.github.astromg01.nucleo.domain.project.ProjectDraft
import io.github.astromg01.nucleo.domain.project.ProjectFactory
import io.github.astromg01.nucleo.domain.project.ProjectProfile
import org.json.JSONObject
import java.util.Locale

/**
 * Parses a model's JSON manifest and rejects missing/empty file payloads before they can be
 * described as a prepared project. Full profile/path validation is performed by ProjectFactory.
 */
internal object ProjectManifestParser {
    fun parse(
        responseText: String,
        name: String,
        description: String,
        profile: ProjectProfile,
    ): ProjectDraft {
        val trimmed = responseText.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) {
            val reason = if (trimmed.isNotEmpty() && trimmed.last() != '}') {
                "A resposta parece ter sido cortada antes do fim."
            } else {
                "A resposta não contém um objeto JSON completo."
            }
            throw IllegalArgumentException(
                "$reason Tente gerar novamente. Se persistir, escolha um modelo com suporte a JSON.",
            )
        }

        val json = try {
            JSONObject(trimmed.substring(start, end + 1))
        } catch (error: Exception) {
            val detail = error.message
                ?.replace(Regex("[\\r\\n]+"), " ")
                ?.take(140)
                ?.let { " Detalhe do parser: $it." }
                .orEmpty()
            throw IllegalArgumentException(
                "A IA devolveu um manifesto JSON inválido. A resposta pode ter sido cortada ou conter aspas/quebras de linha sem escape.$detail Tente novamente com um modelo compatível com JSON.",
                error,
            )
        }

        val filesJson = json.optJSONArray("files")
            ?: throw IllegalArgumentException("O manifesto JSON não contém uma lista 'files' válida.")
        require(filesJson.length() in 1..ProjectFactory.MAX_FILES) {
            "O manifesto precisa conter entre 1 e \${ProjectFactory.MAX_FILES} arquivos."
        }

        val files = ArrayList<GeneratedProjectFile>(filesJson.length())
        val pathKeys = mutableSetOf<String>()
        for (index in 0 until filesJson.length()) {
            val entry = filesJson.optJSONObject(index)
                ?: throw IllegalArgumentException("A entrada de arquivo \${index + 1} não é um objeto JSON válido.")
            val rawPath = entry.opt("path")
            val rawContent = entry.opt("content")
            require(rawPath is String && rawPath.isNotBlank()) {
                "A entrada de arquivo \${index + 1} não contém um caminho de texto válido."
            }
            require(rawContent is String && rawContent.isNotBlank()) {
                "A entrada de arquivo \${index + 1} ('\${rawPath.take(80)}') veio vazia ou sem o conteúdo completo."
            }
            require(rawContent.toByteArray(Charsets.UTF_8).size <= ProjectFactory.MAX_FILE_BYTES) {
                "O arquivo '\${rawPath.take(ProjectFactory.MAX_PATH_CHARS)}' excede o limite de tamanho. Gere-o em partes menores."
            }
            val normalizedPath = rawPath.lowercase(Locale.ROOT)
            require(pathKeys.add(normalizedPath)) {
                "O manifesto contém o caminho duplicado '\${rawPath.take(ProjectFactory.MAX_PATH_CHARS)}'. Gere novamente sem arquivos repetidos."
            }
            files += GeneratedProjectFile(path = rawPath, content = rawContent)
        }

        return ProjectDraft(
            name = name,
            profile = profile,
            description = description,
            files = files,
        )
    }
}