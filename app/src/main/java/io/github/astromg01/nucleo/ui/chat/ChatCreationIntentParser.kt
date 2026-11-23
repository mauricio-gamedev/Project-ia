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
 * target, and it leaves discussion, analysis, and review requests in the normal chat.
 */
internal object ChatCreationIntentParser {
    private val CREATION_ACTION = Regex(
        "(?iu)(?:\\b(?:crie|criar|cria|criasse|criaria|criem|construa|construir|" +
            "desenvolva|desenvolver|desenvolvesse|implemente|implementar|gere|gerar|" +
            "monta|montar|faça|fazer|faz|build|create|develop|implement|make)\\b|" +
            "\\bquero\\s+(?:criar|fazer|desenvolver|montar|construir|um|uma)\\b|" +
            "\\bme\\s+ajud[ae]\\s+a\\s+(?:criar|fazer|desenvolver|montar)\\b|" +
            "\\bpreciso\\s+(?:criar|fazer|desenvolver|montar|construir|de\\s+um|de\\s+uma)\\b)",
    )
    private val CONTENT_WORKSPACE_PREFIX = Regex("(?iu)^\\s*crie\\s+material\\s+no\\s+formato\\b")
    private val DISCUSSION_PREFIX = Regex(
        "(?iu)^\\s*(?:como\\b|vamos\\s+conversar|quero\\s+conversar|me\\s+explique|" +
            "explica\\b|explique\\b|o\\s+que\\s+acha|analise\\b|analisar\\b|" +
            "revise\\b|revisar\\b|corrija\\b|corrigir\\b)",
    )
    private val NON_CREATION_ACTION = Regex(
        "(?iu)\\b(?:analise|análise|analisar|revise|revisar|corrija|corrigir|conserte|consertar|" +
            "otimize|otimizar|melhore|melhorar|modifique|modificar|refatore|refatorar|" +
            "depure|depurar|debugue|debugar|audite|auditar|avalie|avaliar|explique|" +
            "review|fix|repair|improve|optimize|refactor)\\b",
    )
    private val PROJECT_TARGET = Regex(
        "(?iu)\\b(?:apk|app|aplicativo|aplicação|site|website|web|landing\\s+page|" +
            "página|pagina|projeto|project|programa|software|sistema|jogo|game|bot|" +
            "ferramenta|tool|api|cli)\\b",
    )
    private val CONTENT_TARGET = Regex(
        "(?iu)\\b(?:roteiro|post|publicação|publicacao|live|ideias?\\s+de\\s+conteúdo|" +
            "ideias?\\s+de\\s+conteudo|calendário|calendario|prompt\\s+visual|legenda|texto)\\b",
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
        if (request.isBlank() || CONTENT_WORKSPACE_PREFIX.containsMatchIn(request) || DISCUSSION_PREFIX.containsMatchIn(request)) return null
        val action = CREATION_ACTION.find(request) ?: return null
        val target = PROJECT_TARGET.find(request, action.range.last + 1) ?: return null
        val contentTarget = CONTENT_TARGET.find(request, action.range.last + 1)
        if (contentTarget != null && contentTarget.range.first < target.range.first) return null
        val nonCreationAction = NON_CREATION_ACTION.find(request, action.range.last + 1)
        if (nonCreationAction != null && nonCreationAction.range.first < target.range.first) return null

        val profile = when {
            WEB_TARGET.containsMatchIn(request) -> ProjectProfile.WEB_APP
            PYTHON_TARGET.containsMatchIn(request) -> ProjectProfile.PYTHON
            ANDROID_TARGET.containsMatchIn(request) -> ProjectProfile.ANDROID_APP
            else -> ProjectProfile.GENERIC
        }
        return ProjectCreationIntent(profile)
    }
}
