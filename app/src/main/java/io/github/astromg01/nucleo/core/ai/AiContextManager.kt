package io.github.astromg01.nucleo.core.ai

/**
 * Fits a request to a target model's configured input budget without losing the original task
 * or the newest messages. Older middle messages may be omitted; this is deterministic and does
 * not claim to summarize information it has not seen.
 *
 * The budget is measured in characters, not provider tokens. A conservative caller should set
 * it below the model's advertised token context. When the mandatory task itself cannot fit, the
 * caller should route to a model with a larger budget instead of silently cutting the task.
 */
class AiContextManager {
    fun fit(request: AiRequest, maxInputChars: Int): AiRequest {
        require(maxInputChars >= MIN_CONTEXT_CHARS) {
            "O orçamento de contexto do modelo é pequeno demais para uma solicitação segura."
        }
        if (request.messages.isEmpty()) return request
        if (estimatedChars(request.messages) <= maxInputChars) return request

        val messages = request.messages
        val firstSystemIndex = messages.indexOfFirst { it.role.equals("system", ignoreCase = true) }
        val firstUserIndex = messages.indexOfFirst { it.role.equals("user", ignoreCase = true) }
        val lastNonSystemIndex = messages.indexOfLast { !it.role.equals("system", ignoreCase = true) }
        val mandatory = linkedSetOf<Int>()
        if (firstSystemIndex >= 0) mandatory += firstSystemIndex
        if (firstUserIndex >= 0) mandatory += firstUserIndex
        if (lastNonSystemIndex >= 0) mandatory += lastNonSystemIndex

        val handoff = AiMessage(
            role = "system",
            content = "Context handoff: mensagens antigas intermediárias foram omitidas para caber no limite deste modelo. " +
                "Preserve o pedido original e as mensagens recentes disponíveis. Não invente detalhes ausentes; peça esclarecimento se necessário.",
        )
        val reserveForHandoff = handoff.content.length + ROLE_OVERHEAD
        val selected = linkedSetOf<Int>().apply { addAll(mandatory) }
        var used = mandatory.sumOf { index -> messageCost(messages[index]) }
        if (used + reserveForHandoff > maxInputChars) {
            throw ContextBudgetExceededException(
                "O pedido original e a mensagem mais recente não cabem no contexto configurado deste modelo."
            )
        }

        for (index in messages.indices.reversed()) {
            if (index in selected) continue
            val cost = messageCost(messages[index])
            if (used + cost + reserveForHandoff <= maxInputChars) {
                selected += index
                used += cost
            }
        }

        val retained = selected.sorted().map { messages[it] }
        val withHandoff = buildList {
            val systemPosition = retained.indexOfFirst { it.role.equals("system", ignoreCase = true) }
            if (systemPosition >= 0) {
                addAll(retained.take(systemPosition + 1))
                add(handoff)
                addAll(retained.drop(systemPosition + 1))
            } else {
                add(handoff)
                addAll(retained)
            }
        }
        if (estimatedChars(withHandoff) > maxInputChars) {
            throw ContextBudgetExceededException(
                "Não foi possível preservar o pedido e o contexto recente dentro do orçamento deste modelo."
            )
        }
        return request.copy(messages = withHandoff)
    }

    private fun estimatedChars(messages: List<AiMessage>): Int =
        messages.sumOf(::messageCost)

    private fun messageCost(message: AiMessage): Int =
        message.role.length + message.content.length + ROLE_OVERHEAD

    class ContextBudgetExceededException(message: String) : IllegalArgumentException(message)

    private companion object {
        const val ROLE_OVERHEAD = 8
        const val MIN_CONTEXT_CHARS = 2_000
    }
}
