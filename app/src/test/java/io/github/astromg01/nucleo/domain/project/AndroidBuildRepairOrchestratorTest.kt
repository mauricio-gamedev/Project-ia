package io.github.astromg01.nucleo.domain.project

import io.github.astromg01.nucleo.core.ai.AiResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class AndroidBuildRepairOrchestratorTest {
    private val sourcePath = "app/src/main/java/example/BuildInfo.kt"

    private fun draft(): ProjectDraft = AndroidProjectScaffold.complete(
        ProjectDraft(
            name = "Repair sample",
            profile = ProjectProfile.ANDROID_APP,
            description = "A small generated Android project.",
            files = listOf(
                GeneratedProjectFile(
                    "app/src/main/java/example/MainActivity.kt",
                    "package example\nimport androidx.activity.ComponentActivity\nclass MainActivity : ComponentActivity()",
                ),
                GeneratedProjectFile(
                    sourcePath,
                    "package example\nobject BuildInfo { const val READY = true }",
                ),
            ),
        ),
    ).draft

    private fun staged(id: String) = StagedProject(
        id = id,
        name = "Repair sample",
        profile = ProjectProfile.ANDROID_APP,
        directory = File(System.getProperty("java.io.tmpdir"), "nucleo-test-" + id),
        files = emptyList(),
        createdAtMillis = 1L,
    )

    private fun repairResponse() = AiResponse(
        text = """{"summary":"Adjusted the generated source.","files":[{"path":"$sourcePath","content":"package example\\nobject BuildInfo { const val READY = false }"}]}""",
        providerName = "Test provider",
        modelName = "test-model",
    )

    private fun artifact(): BuiltAndroidArtifact {
        val file = File.createTempFile("nucleo-auto-repair", ".apk")
        file.writeBytes(byteArrayOf(1, 2, 3))
        return BuiltAndroidArtifact(
            file = file,
            sha256 = "0".repeat(64),
            repositoryUrl = "https://github.com/example/build",
            branch = "nucleo-test",
            runUrl = "https://github.com/example/build/actions/runs/1",
        )
    }

    @Test
    fun repairsStagesAndRebuildsThenReplacesOriginalOnlyAfterSuccess() = runBlocking {
        val original = draft()
        val originalContent = original.files.first { it.path == sourcePath }.content
        var buildCalls = 0
        var stageCalls = 0
        var repairCalls = 0
        val discarded = mutableListOf<String>()

        val result = AndroidBuildRepairOrchestrator().build(
            initialDraft = original,
            initialStagedProject = staged("original"),
            maxAutoRepairAttempts = 2,
            generateRepair = {
                repairCalls++
                repairResponse()
            },
            stageDraft = {
                stageCalls++
                staged("candidate-" + stageCalls)
            },
            buildStagedProject = {
                buildCalls++
                if (buildCalls == 1) {
                    throw AndroidBuildFailureException("https://github.com/example/run/1", "failure", "Unexpected Gradle error")
                }
                artifact()
            },
            discardStagedProject = { discarded += it.id },
        )

        assertTrue(result is AndroidBuildRepairOutcome.Success)
        val success = result as AndroidBuildRepairOutcome.Success
        assertEquals(1, success.repairAttempts)
        assertEquals(1, repairCalls)
        assertEquals(2, buildCalls)
        assertEquals("package example\nobject BuildInfo { const val READY = false }",
            success.draft.files.first { it.path == sourcePath }.content)
        assertEquals(originalContent, original.files.first { it.path == sourcePath }.content)
        assertTrue(discarded.contains("original"))
        success.artifact.file.delete()
    }

    @Test
    fun stopsAfterTwoRepairAttemptsAndKeepsTheOriginalStage() = runBlocking {
        var buildCalls = 0
        var stageCalls = 0
        val discarded = mutableListOf<String>()
        val result = AndroidBuildRepairOrchestrator().build(
            initialDraft = draft(),
            initialStagedProject = staged("original"),
            maxAutoRepairAttempts = 2,
            generateRepair = { repairResponse() },
            stageDraft = {
                stageCalls++
                staged("candidate-" + stageCalls)
            },
            buildStagedProject = {
                buildCalls++
                throw AndroidBuildFailureException("https://github.com/example/run/" + buildCalls, "failure", "Gradle error")
            },
            discardStagedProject = { discarded += it.id },
        )

        assertTrue(result is AndroidBuildRepairOutcome.Failed)
        val failure = result as AndroidBuildRepairOutcome.Failed
        assertEquals(2, failure.repairAttempts)
        assertEquals(3, buildCalls)
        assertEquals(2, stageCalls)
        assertEquals("candidate-2", failure.stagedProject.id)
        assertFalse(discarded.contains("original"))
        assertTrue(discarded.contains("candidate-1"))
    }

    @Test
    fun doesNotRetryNetworkOrAuthenticationFailures() = runBlocking {
        var repairCalls = 0
        try {
            AndroidBuildRepairOrchestrator().build(
                initialDraft = draft(),
                initialStagedProject = staged("original"),
                maxAutoRepairAttempts = 2,
                generateRepair = {
                    repairCalls++
                    repairResponse()
                },
                stageDraft = { staged("candidate") },
                buildStagedProject = { throw IOException("network unavailable") },
                discardStagedProject = {},
            )
            org.junit.Assert.fail("Network failures must not trigger AI source repair.")
        } catch (error: IOException) {
            assertEquals("network unavailable", error.message)
        }
        assertEquals(0, repairCalls)
    }
}
