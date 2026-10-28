package io.github.astromg01.nucleo.domain.project

import org.json.JSONObject

/** Strictly parses the model's JSON file manifest before any project files reach staging. */
object ProjectManifestParser {
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
                reason + " A geração será tentada novamente com um diagnóstico explícito.",
            )
        }

        val json = try {
            JSONObject(trimmed.substring(start, end + 1))
        } catch (error: Exception) {
            val hint = if (trimmed.lastOrNull() != '}') {
                "A resposta pode ter sido interrompida pelo limite de saída."
            } else {
                "O modelo pode ter produzido aspas ou quebras de linha sem escape dentro de um arquivo."
            }
            throw IllegalArgumentException("Manifesto de arquivos inválido. $hint", error)
        }

        val filesJson = json.optJSONArray("files")
            ?: throw IllegalArgumentException("O manifesto JSON não contém uma lista 'files' válida.")
        require(filesJson.length() in 1..ProjectFactory.MAX_FILES) {
            "O manifesto precisa conter entre 1 e ${ProjectFactory.MAX_FILES} arquivos."
        }

        val files = (0 until filesJson.length()).map { index ->
            val entry = filesJson.optJSONObject(index)
                ?: throw IllegalArgumentException("A entrada de arquivo ${index + 1} não é um objeto JSON.")
            val path = entry.opt("path") as? String
                ?: throw IllegalArgumentException("A entrada de arquivo ${index + 1} precisa ter um caminho string.")
            val content = entry.opt("content") as? String
                ?: throw IllegalArgumentException("O conteúdo do arquivo ${index + 1} precisa ser uma string completa.")
            require(path.isNotBlank()) { "A entrada de arquivo ${index + 1} não contém caminho." }
            require(content.isNotBlank()) { "O arquivo '$path' está vazio; a geração precisa ser corrigida." }
            GeneratedProjectFile(path = path, content = content)
        }

        val draft = ProjectDraft(name = name, profile = profile, description = description, files = files)
        return draft
    }
}