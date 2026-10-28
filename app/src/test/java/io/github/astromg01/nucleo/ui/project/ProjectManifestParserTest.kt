package io.github.astromg01.nucleo.ui.project

import io.github.astromg01.nucleo.domain.project.ProjectProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectManifestParserTest {
    @Test
    fun acceptsCompleteNonEmptyManifestFiles() {
        val draft = ProjectManifestParser.parse(
            """{"description":"ok","files":[{"path":"index.html","content":"<h1>Oi</h1>"}]}""",
            name = "Website",
            description = "Página inicial",
            profile = ProjectProfile.GENERIC,
        )

        assertEquals("index.html", draft.files.single().path)
        assertEquals("<h1>Oi</h1>", draft.files.single().content)
    }

    @Test
    fun rejectsEmptyFileContentInsteadOfSilentlyStagingIt() {
        val error = runCatching {
            ProjectManifestParser.parse(
                """{"files":[{"path":"app/src/main/java/MainActivity.kt","content":"  "}]}""",
                name = "App",
                description = "Android",
                profile = ProjectProfile.ANDROID_APP,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("veio vazia"))
    }

    @Test
    fun rejectsMissingContentField() {
        val error = runCatching {
            ProjectManifestParser.parse(
                """{"files":[{"path":"main.py"}]}""",
                name = "Script",
                description = "Python",
                profile = ProjectProfile.PYTHON,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("conteúdo completo"))
    }

    @Test
    fun rejectsDuplicatePathsCaseInsensitively() {
        val error = runCatching {
            ProjectManifestParser.parse(
                """{"files":[{"path":"Main.kt","content":"class Main"},{"path":"main.KT","content":"class Other"}]}""",
                name = "App",
                description = "Android",
                profile = ProjectProfile.GENERIC,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("duplicado"))
    }

    @Test
    fun reportsLikelyTruncatedManifest() {
        val error = runCatching {
            ProjectManifestParser.parse(
                """{"files":[{"path":"Main.kt","content":"class Main" """,
                name = "App",
                description = "Android",
                profile = ProjectProfile.GENERIC,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("cortada"))
    }
}