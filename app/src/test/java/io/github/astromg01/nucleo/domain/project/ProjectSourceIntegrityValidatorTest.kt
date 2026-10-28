package io.github.astromg01.nucleo.domain.project

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ProjectSourceIntegrityValidatorTest {
    @Test
    fun reportsUnclosedBlockInGeneratedKotlin() {
        val issues = ProjectSourceIntegrityValidator.inspect(
            ProjectDraft(
                name = "Broken",
                profile = ProjectProfile.ANDROID_APP,
                files = listOf(GeneratedProjectFile("app/src/main/java/example/MainActivity.kt", "class MainActivity { fun run() {")),
            ),
        )

        assertTrue(issues.any { it.contains("MainActivity.kt") && it.contains("não foi fechado") })
    }

    @Test
    fun ignoresDelimitersInsideStringsCommentsAndKotlinRawStrings() {
        val issues = ProjectSourceIntegrityValidator.inspect(
            ProjectDraft(
                name = "Valid",
                profile = ProjectProfile.ANDROID_APP,
                files = listOf(
                    GeneratedProjectFile(
                        "app/src/main/java/example/MainActivity.kt",
                        """
                            class MainActivity {
                                val text = " } ] ) "
                                val raw = """ { this is text, not a block """
                                // } ] )
                                /* outer { /* nested } */ still comment */
                                fun run() { println(text) }
                            }
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun reportsMismatchedClosingDelimiterAndIncompleteString() {
        val mismatch = ProjectSourceIntegrityValidator.inspect(
            ProjectDraft(
                name = "Mismatch",
                profile = ProjectProfile.GENERIC,
                files = listOf(GeneratedProjectFile("src/main.kt", "fun main() ]")),
            ),
        )
        val incomplete = ProjectSourceIntegrityValidator.inspect(
            ProjectDraft(
                name = "String",
                profile = ProjectProfile.GENERIC,
                files = listOf(GeneratedProjectFile("src/main.kt", "val value = \"unfinished")),
            ),
        )

        assertTrue(mismatch.any { it.contains("sem abertura correspondente") })
        assertTrue(incomplete.any { it.contains("string literal incompleta") })
    }

    @Test
    fun ignoresNonSourceTextAndFullyBalancedCode() {
        val issues = ProjectSourceIntegrityValidator.inspect(
            ProjectDraft(
                name = "Okay",
                profile = ProjectProfile.GENERIC,
                files = listOf(
                    GeneratedProjectFile("README.md", "text { ( ["),
                    GeneratedProjectFile("src/main.kt", "fun main() { println(\"ok\") }"),
                ),
            ),
        )

        assertFalse(issues.isNotEmpty())
    }
}