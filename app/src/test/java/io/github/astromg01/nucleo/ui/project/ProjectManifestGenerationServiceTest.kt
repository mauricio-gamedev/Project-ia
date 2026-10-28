package io.github.astromg01.nucleo.ui.project

import io.github.astromg01.nucleo.core.ai.AiMessage
import io.github.astromg01.nucleo.core.ai.AiRequest
import io.github.astromg01.nucleo.core.ai.AiResponse
import io.github.astromg01.nucleo.core.ai.AiTaskType
import io.github.astromg01.nucleo.domain.project.ProjectProfile
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectManifestGenerationServiceTest {
    @Test
    fun retriesOnceWhenTheFirstManifestContainsAnEmptyFile() = runBlocking {
        var calls = 0
        val result = ProjectManifestGenerationService().generate(
            request = AiRequest(
                messages = listOf(AiMessage("system", "Return a complete project manifest.")),
                taskType = AiTaskType.CODE_GENERATION,
            ),
            name = "Demo",
            description = "A small utility",
            profile = ProjectProfile.GENERIC,
        ) { request ->
            calls++
            if (calls == 1) {
                AiResponse(
                    manifest(path = "src/main.kt", source = ""),
                    "TestProvider",
                    "test-model",
                )
            } else {
                assertTrue(request.messages.last().content.contains("vazio"))
                AiResponse(
                    manifest(path = "src/main.kt", source = "fun main() {}"),
                    "TestProvider",
                    "test-model",
                )
            }
        }

        assertEquals(2, calls)
        assertEquals(2, result.attempts)
        assertEquals("fun main() {}", result.draft.files.single().content)
    }

    @Test
    fun doesNotRepeatAValidManifest() = runBlocking {
        var calls = 0
        val result = ProjectManifestGenerationService().generate(
            request = AiRequest(
                messages = listOf(AiMessage("system", "Return a complete project manifest.")),
                taskType = AiTaskType.CODE_GENERATION,
            ),
            name = "Demo",
            description = "",
            profile = ProjectProfile.GENERIC,
        ) {
            calls++
            AiResponse(
                manifest(path = "README.md", source = "Complete"),
                "TestProvider",
                "test-model",
            )
        }

        assertEquals(1, calls)
        assertEquals(1, result.attempts)
    }

    private fun manifest(path: String, source: String): String =
        JSONObject()
            .put("files", JSONArray().put(JSONObject().put("path", path).put("content", source)))
            .toString()
}