package io.github.astromg01.nucleo.core.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiCompatibleResponseParserTest {
    @Test
    fun acceptsStringContent() {
        assertEquals("conectado", OpenAiCompatibleResponseParser.extractTextContent(" conectado "))
    }

    @Test
    fun extractsTextFromStructuredContentBlocksAndIgnoresImages() {
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "Primeiro trecho"))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "https://example.invalid/image")))
            .put(JSONObject().put("type", "output_text").put("text", "Segundo trecho"))

        assertEquals(
            "Primeiro trecho\nSegundo trecho",
            OpenAiCompatibleResponseParser.extractTextContent(content),
        )
    }

    @Test
    fun rejectsContentWithoutTextInsteadOfReturningSerializedJson() {
        val content = JSONArray().put(JSONObject().put("type", "image_url"))

        assertEquals("", OpenAiCompatibleResponseParser.extractTextContent(content))
    }
}
