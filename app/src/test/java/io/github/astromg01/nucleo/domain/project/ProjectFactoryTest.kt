package io.github.astromg01.nucleo.domain.project

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFactoryTest {
    private val factory = ProjectFactory()

    @Test
    fun `stages generic project into private directory and records sha256 evidence`() {
        withTempDirectory { temp ->
            val draft = ProjectDraft(
                name = "My Demo",
                profile = ProjectProfile.GENERIC,
                files = listOf(
                    GeneratedProjectFile("README.md", "# Demo\n"),
                    GeneratedProjectFile("src/main.nuc", "print(\"hello\");\n"),
                ),
            )

            val staged = factory.stage(draft, File(temp, "staging"))

            assertEquals(ProjectStageStatus.REVIEW_REQUIRED, staged.status)
            assertEquals(2, staged.files.size)
            assertEquals("# Demo\n", File(staged.directory, "README.md").readText())
            assertTrue(staged.files.all { it.sha256.matches(Regex("[a-f0-9]{64}")) })
            assertEquals(
                staged.files.first { it.relativePath == "README.md" }.sizeBytes,
                File(staged.directory, "README.md").length(),
            )
        }
    }

    @Test
    fun `rejects parent traversal before creating any staged project`() {
        withTempDirectory { temp ->
            val root = File(temp, "staging")
            val draft = ProjectDraft(
                name = "Safe",
                profile = ProjectProfile.GENERIC,
                files = listOf(GeneratedProjectFile("../outside.txt", "not allowed")),
            )

            val error = assertThrows(ProjectDraftValidationException::class.java) {
                factory.stage(draft, root)
            }

            assertTrue(error.message!!.contains("traversal"))
            assertFalse(root.exists())
            assertFalse(File(temp, "outside.txt").exists())
        }
    }

    @Test
    fun `rejects absolute and Windows drive paths`() {
        val absolute = assertThrows(ProjectDraftValidationException::class.java) {
            factory.validate(draftOf("/tmp/out.txt"))
        }
        val drive = assertThrows(ProjectDraftValidationException::class.java) {
            factory.validate(draftOf("C:/out.txt"))
        }
        assertTrue(absolute.message!!.contains("relativo"))
        assertTrue(drive.message!!.contains("relativo"))
    }

    @Test
    fun `rejects case insensitive duplicate paths`() {
        val error = assertThrows(ProjectDraftValidationException::class.java) {
            factory.validate(
                ProjectDraft(
                    name = "Duplicate",
                    profile = ProjectProfile.GENERIC,
                    files = listOf(
                        GeneratedProjectFile("README.md", "one"),
                        GeneratedProjectFile("readme.md", "two"),
                    ),
                ),
            )
        }
        assertTrue(error.message!!.contains("duplicados"))
    }

    @Test
    fun `rejects file and directory path collisions`() {
        val error = assertThrows(ProjectDraftValidationException::class.java) {
            factory.validate(
                ProjectDraft(
                    name = "Collision",
                    profile = ProjectProfile.GENERIC,
                    files = listOf(
                        GeneratedProjectFile("src", "file"),
                        GeneratedProjectFile("src/main.nuc", "child"),
                    ),
                ),
            )
        }
        assertTrue(error.message!!.contains("conflito"))
    }

    @Test
    fun `blocks secret material and common secret files`() {
        val error = assertThrows(ProjectDraftValidationException::class.java) {
            factory.validate(
                ProjectDraft(
                    name = "Secrets",
                    profile = ProjectProfile.GENERIC,
                    files = listOf(GeneratedProjectFile("config/.env", "API_KEY=secret")),
                ),
            )
        }
        assertTrue(error.message!!.contains("segredos"))
    }

    @Test
    fun `rejects an oversized individual file`() {
        val error = assertThrows(ProjectDraftValidationException::class.java) {
            factory.validate(
                draftOf(
                    "large.txt",
                    "x".repeat(ProjectFactory.MAX_FILE_BYTES + 1),
                ),
            )
        }
        assertTrue(error.message!!.contains("excede o limite"))
    }

    @Test
    fun `requires core files for Android profile`() {
        val error = assertThrows(ProjectDraftValidationException::class.java) {
            factory.validate(
                ProjectDraft(
                    name = "Android",
                    profile = ProjectProfile.ANDROID_APP,
                    files = listOf(GeneratedProjectFile("README.md", "# Missing skeleton")),
                ),
            )
        }
        assertTrue(error.message!!.contains("settings.gradle"))
        assertTrue(error.message!!.contains("AndroidManifest.xml"))
    }

    @Test
    fun `accepts a minimal Android source manifest`() {
        factory.validate(
            ProjectDraft(
                name = "Minimal Android",
                profile = ProjectProfile.ANDROID_APP,
                files = listOf(
                    GeneratedProjectFile("settings.gradle.kts", "rootProject.name = \"Demo\""),
                    GeneratedProjectFile("build.gradle.kts", "plugins {}"),
                    GeneratedProjectFile("app/build.gradle.kts", "plugins {}"),
                    GeneratedProjectFile("app/src/main/AndroidManifest.xml", "<manifest />"),
                    GeneratedProjectFile("app/src/main/java/example/MainActivity.kt", "package example"),
                ),
            ),
        )
    }

    @Test
    fun `requires entry points for web and Python profiles`() {
        val webError = assertThrows(ProjectDraftValidationException::class.java) {
            factory.validate(
                ProjectDraft(
                    name = "Web",
                    profile = ProjectProfile.WEB_APP,
                    files = listOf(GeneratedProjectFile("README.md", "# Missing web entry points")),
                ),
            )
        }
        assertTrue(webError.message!!.contains("index.html"))

        val pythonError = assertThrows(ProjectDraftValidationException::class.java) {
            factory.validate(
                ProjectDraft(
                    name = "Python",
                    profile = ProjectProfile.PYTHON,
                    files = listOf(GeneratedProjectFile("main.py", "print('hi')")),
                ),
            )
        }
        assertTrue(pythonError.message!!.contains("pyproject.toml"))
    }

    private fun draftOf(path: String, content: String = "text") = ProjectDraft(
        name = "Validation",
        profile = ProjectProfile.GENERIC,
        files = listOf(GeneratedProjectFile(path, content)),
    )

    private fun withTempDirectory(block: (File) -> Unit) {
        val temp = Files.createTempDirectory("nucleo-project-factory-test").toFile()
        try {
            block(temp)
        } finally {
            temp.deleteRecursively()
        }
    }
}
