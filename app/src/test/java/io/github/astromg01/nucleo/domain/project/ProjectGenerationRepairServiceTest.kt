package io.github.astromg01.nucleo.domain.project

import io.github.astromg01.nucleo.core.ai.AiResponse
import io.github.astromg01.nucleo.core.ai.AiTaskType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectGenerationRepairServiceTest {
    @Test
    fun repairChangesExistingFileAndRunsValidationAgain() = runBlocking {
        val draft = ProjectDraft(
            name = "Broken web project",
            profile = ProjectProfile.GENERIC,
            description = "Simple utility",
            files = listOf(GeneratedProjectFile("app.js", "function run() { return [1, 2; }")),
        )
        val before = ProjectGenerationValidator.validate(draft)
        assertTrue(before.isNotEmpty())
        var routeRequestType: AiTaskType? = null

        val result = ProjectGenerationRepairService().repairOnce(draft, before) { request ->
            routeRequestType = request.taskType
            assertTrue(request.jsonObjectMode)
            assertTrue(request.messages.last().content.contains("BEGIN FILE: app.js"))
            AiResponse(
                text = """{"summary":"Closed the array and kept the same file.","files":[{"path":"app.js","content":"function run() { return [1, 2]; }"}]}""",
                providerName = "Test provider",
                modelName = "test-model",
            )
        }

        assertEquals(AiTaskType.CODE_REPAIR, routeRequestType)
        assertTrue(result.attempted)
        assertTrue(result.issuesBeforeRepair.isNotEmpty())
        assertTrue(result.issuesAfterRepair.isEmpty())
        assertEquals("function run() { return [1, 2]; }", result.draft.files.single().content)
    }

    @Test
    fun repairNeverDeletesFilesOmittedFromItsProposal() = runBlocking {
        val draft = ProjectDraft(
            name = "Two files",
            profile = ProjectProfile.GENERIC,
            description = "Keep both files",
            files = listOf(
                GeneratedProjectFile("main.js", "function run() { return [1; }"),
                GeneratedProjectFile("notes.txt", "keep this note"),
            ),
        )
        val issues = ProjectGenerationValidator.validate(draft)
        val result = ProjectGenerationRepairService().repairOnce(draft, issues) {
            AiResponse(
                text = """{"summary":"Fixed main.js only.","files":[{"path":"main.js","content":"function run() { return [1]; }"}]}""",
                providerName = "Test provider",
                modelName = "test-model",
            )
        }

        assertEquals(setOf("main.js", "notes.txt"), result.draft.files.map { it.path }.toSet())
        assertEquals("keep this note", result.draft.files.first { it.path == "notes.txt" }.content)
        assertFalse(result.issuesAfterRepair.isNotEmpty())
    }
}