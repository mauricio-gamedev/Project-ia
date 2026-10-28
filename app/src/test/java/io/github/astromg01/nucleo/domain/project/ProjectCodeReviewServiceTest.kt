package io.github.astromg01.nucleo.domain.project

import io.github.astromg01.nucleo.core.ai.AiTaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectCodeReviewServiceTest {
    private val service = ProjectCodeReviewService()

    @Test
    fun reviewParserOnlyAcceptsFindingsForStagedPaths() {
        val response = """
            {
              "summary": "Uma referência de arquivo está incorreta.",
              "findings": [
                {"severity":"error","path":"script.js","line":12,"problem":"Função não definida","recommendation":"Definir ou corrigir a chamada"},
                {"severity":"critical","path":"../../secrets.txt","line":1,"problem":"Tentar sair do staging","recommendation":"Ignorar"},
                {"severity":"warning","path":"index.html","line":0,"problem":"Referência ausente","recommendation":"Adicionar o arquivo correto"}
              ]
            }
        """.trimIndent()

        val parsed = service.parseReviewResponse(response, setOf("index.html", "script.js"), partial = false)

        assertEquals(2, parsed.findings.size)
        assertEquals("error", parsed.findings[0].severity)
        assertEquals("script.js", parsed.findings[0].path)
        assertEquals("warning", parsed.findings[1].severity)
        assertEquals(null, parsed.findings[1].line)
        assertFalse(parsed.partial)
    }

    @Test
    fun reviewParserExtractsBalancedJsonFromMarkdownAndQuotedBraces() {
        val fence = "```"
        val response = "Aqui está o relatório:\n" + fence + "json\n" +
            """{"summary":"Brace { and } inside a string","findings":[]}""" +
            "\n" + fence

        val parsed = service.parseReviewResponse(response, setOf("index.html"), partial = false)

        assertEquals("Brace { and } inside a string", parsed.summary)
        assertTrue(parsed.findings.isEmpty())
    }

    @Test
    fun reviewParserRejectsMissingFindingsArrayForBoundedRetry() {
        try {
            service.parseReviewResponse("""{"summary":"Incomplete contract"}""", setOf("index.html"), partial = false)
            throw AssertionError("Expected schema rejection.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("findings"))
        }
    }

    @Test
    fun repairParserRejectsAnyPathNotInTheStagingManifest() {
        val response = """{"summary":"bad","files":[{"path":"../../outside.txt","content":"bad"}]}"""

        try {
            service.parseRepairProposal(response, setOf("index.html"))
            throw AssertionError("Expected path rejection.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("arquivo não incluído"))
        }
    }

    @Test
    fun repairParserRejectsDuplicatePaths() {
        val response = """{"summary":"duplicates","files":[{"path":"index.html","content":"a"},{"path":"index.html","content":"b"}]}"""

        try {
            service.parseRepairProposal(response, setOf("index.html", "script.js"))
            throw AssertionError("Expected duplicate path rejection.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("repetido"))
        }
    }

    @Test
    fun reviewRequestIsTaggedForTheCodeReviewRoute() {
        val staged = StagedProject(
            id = "stage-id",
            name = "sample",
            profile = ProjectProfile.WEB_APP,
            directory = java.io.File("/tmp/stage-id"),
            files = listOf(
                StagedFileEvidence("index.html", 13, "sha"),
            ),
            createdAtMillis = 1L,
        )
        val snapshot = ProjectSourceSnapshot(
            files = listOf(GeneratedProjectFile("index.html", "<h1>Hi</h1>")),
            truncatedPaths = emptySet(),
            omittedFileCount = 0,
        )

        val request = service.buildReviewRequest(staged, "simple web page", snapshot)

        assertEquals(AiTaskType.CODE_REVIEW, request.taskType)
        assertTrue(request.jsonObjectMode)
        assertTrue(request.messages.last().content.contains("BEGIN SOURCE: index.html"))
    }

    @Test
    fun truncatedSnapshotCannotBeUsedForACompleteRepair() {
        val staged = StagedProject(
            id = "stage-id",
            name = "sample",
            profile = ProjectProfile.WEB_APP,
            directory = java.io.File("/tmp/stage-id"),
            files = listOf(StagedFileEvidence("index.html", 20, "sha")),
            createdAtMillis = 1L,
        )
        val snapshot = ProjectSourceSnapshot(
            files = listOf(GeneratedProjectFile("index.html", "partial")),
            truncatedPaths = setOf("index.html"),
            omittedFileCount = 0,
        )
        val report = ProjectCodeReviewReport(
            summary = "Bug",
            findings = listOf(CodeReviewFinding("error", "index.html", 1, "bad", "fix")),
            reviewedPaths = listOf("index.html"),
            partial = true,
        )

        try {
            service.buildRepairRequest(staged, "", snapshot, report)
            throw AssertionError("Expected partial-source repair rejection.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("sem truncamento"))
        }
    }
}
