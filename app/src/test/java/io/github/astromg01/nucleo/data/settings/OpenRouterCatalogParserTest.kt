package io.github.astromg01.nucleo.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterCatalogParserTest {
    @Test
    fun parsesIdsContextAndPricesInUsdPerMillionTokens() {
        val models = OpenRouterCatalogParser.parse(
            """
            {
              "data": [
                {
                  "id": "vendor/model-a",
                  "name": "Model A",
                  "context_length": 32000,
                  "pricing": {"prompt": "0.000002", "completion": "0.000004"},
                  "description": "Test model"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, models.size)
        assertEquals("vendor/model-a", models.single().id)
        assertEquals("Model A", models.single().name)
        assertEquals(32000, models.single().contextLengthTokens)
        assertEquals(2.0, models.single().promptPricePerMillionUsd!!, 0.000001)
        assertEquals(4.0, models.single().completionPricePerMillionUsd!!, 0.000001)
        assertFalse(models.single().isListedFree)
    }

    @Test
    fun onlyMarksModelsFreeWhenBothPublishedPricesAreExplicitlyZero() {
        val models = OpenRouterCatalogParser.parse(
            """
            {
              "data": [
                {"id":"free/model","pricing":{"prompt":"0","completion":"0"}},
                {"id":"partial-zero/model","pricing":{"prompt":"0","completion":"0.000001"}},
                {"id":"unknown/model","pricing":{}}
              ]
            }
            """.trimIndent(),
        )

        assertTrue(models.first { it.id == "free/model" }.isListedFree)
        assertFalse(models.first { it.id == "partial-zero/model" }.isListedFree)
        assertFalse(models.first { it.id == "unknown/model" }.isListedFree)
    }

    @Test
    fun skipsMalformedRowsAndDuplicateIdsWithoutDroppingValidModels() {
        val models = OpenRouterCatalogParser.parse(
            """
            {
              "data": [
                null,
                {"name":"missing id"},
                {"id":"vendor/model","name":"First"},
                {"id":"vendor/model","name":"Duplicate"},
                {"id":"vendor/model-2"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("vendor/model", "vendor/model-2"), models.map { it.id })
        assertEquals("First", models.first().name)
        assertEquals("vendor/model-2", models.last().name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsResponseWithoutDataArray() {
        OpenRouterCatalogParser.parse("""{"models": []}""")
    }
}
