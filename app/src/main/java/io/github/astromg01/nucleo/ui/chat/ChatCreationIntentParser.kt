package io.github.astromg01.nucleo.ui.chat

import io.github.astromg01.nucleo.domain.project.ProjectProfile

internal data class ProjectCreationIntent(
    val profile: ProjectProfile,
)

/**
 * Recognizes explicit project-building requests so they can open the existing Project Factory
 * inside the conversation instead of being answered as ordinary chat text.
 *
 * This parser is intentionally conservative: it requires both a creation action and a project
 * target, and it never treats analysis/review requests as a request to generate a new project.
 */
internal object ChatCreationIntentParser {
    private val CREATION_ACTION = Regex(
        "(?iu)\\b(?:crie|criar|cria|construa|construir|desenvolva|desenvolver|" +
            "implemente|implementar|gere|gerar|monta|montar|faça|fazer|" +
            "build|create|develop|implement|make)\\b|" +
            "(?iu)\\bquero\\s+(?:criar|fazer|desenvolver|montar|construir)\\b",
    )
    private val PROJECT_TARGET = Regex(
        "(?iu)\\b(?:apk|app|aplicativo|aplicação|site|website|web|landing\\s+page|" +
            "página|pagina|projeto|project|programa|software|sistema|jogo|game|bot|" +
            "ferramenta|tool|api|cli)\\b",
    )
    private val WEB_TARGET = Regex(
        "(?iu)\\b(?:site|website|web|landing\\s+page|frontend|front-end|página|pagina)\\b",
    )
    private val PYTHON_TARGET = Regex("(?iu)\\bpython\\b")
    private val ANDROID_TARGET = Regex(
        "(?iu)\\b(?:android|apk|app|aplicativo|aplicação|mobile|celular)\\b",
    )

    fun parse(input: String): ProjectCreationIntent? {
        val request = input.trim().take(4_000)
        if (request.isBlank()) return null
        if (!CREATION_ACTION.containsMatchIn(request) || !PROJECT_TARGET.containsMatchIn(request)) {
            return null
        }

        val profile = when {
            WEB_TARGET.containsMatchIn(request) -> ProjectProfile.WEB_APP
            PYTHON_TARGET.containsMatchIn(request) -> ProjectProfile.PYTHON
            ANDROID_TARGET.containsMatchIn(request) -> ProjectProfile.ANDROID_APP
            else -> ProjectProfile.GENERIC
        }
        return ProjectCreationIntent(profile)
    }
}
