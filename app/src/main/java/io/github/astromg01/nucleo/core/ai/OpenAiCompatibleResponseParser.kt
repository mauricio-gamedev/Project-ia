package io.github.astromg01.nucleo.core.ai

import org.json.JSONArray
import org.json.JSONObject

/** Extracts ordinary text from common OpenAI-compatible string and content-block responses. */
internal object OpenAiCompatibleResponseParser {
    fun extractTextContent(content: Any?): String = when (content) {
        is String -> content.trim()
        is JSONArray -> buildList {
            for (index in 0 until content.length()) {
                val block = content.optJSONObject(index) ?: continue
                val type = block.optString("type", "text")
                if (type !in TEXT_BLOCK_TYPES) continue
                val text = block.opt("text") as? String ?: continue
                if (text.isNotBlank()) add(text)
            }
        }.joinToString("\n").trim()
        is JSONObject -> (content.opt("text") as? String).orEmpty().trim()
        else -> ""
    }

    private val TEXT_BLOCK_TYPES = setOf("text", "output_text")
}
