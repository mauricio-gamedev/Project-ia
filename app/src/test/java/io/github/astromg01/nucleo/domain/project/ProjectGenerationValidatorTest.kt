package io.github.astromg01.nucleo.domain.project

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectGenerationValidatorTest {
    private fun genericFile(path: String, source: String) = ProjectDraft(
        name = "Validator test",
        profile = ProjectProfile.GENERIC,
        description = "Static validation test",
        files = listOf(GeneratedProjectFile(path, source)),
    )

    @Test
    fun reportsUnclosedDelimiterInSourceFile() {
        val issues = ProjectGenerationValidator.validate(
            genericFile("src/app.js", "function start() { const item = [1, 2; return item; }"),
        )

        assertTrue(issues.any { it.contains("delimitadores incompatíveis") || it.contains("sem fechamento") })
    }

    @Test
    fun ignoresDelimitersInsideStringsAndComments() {
        val issues = ProjectGenerationValidator.validate(
            genericFile(
                "src/app.js",
                """
                // this comment has unmatched [
                const message = "not a brace: {";
                function start() { return [1, 2]; }
                """.trimIndent(),
            ),
        )

        assertFalse(issues.any { it.contains("delimitador", ignoreCase = true) })
    }

    @Test
    fun rejectsMalformedJson() {
        val issues = ProjectGenerationValidator.validate(genericFile("config.json", """{"items":[1, 2]"""))

        assertTrue(issues.any { it.contains("JSON válido") })
    }

    @Test
    fun rejectsMalformedXml() {
        val issues = ProjectGenerationValidator.validate(
            genericFile("layout.xml", """<root><item></root>"""),
        )

        assertTrue(issues.any { it.contains("XML") && it.contains("malformado") })
    }

    @Test
    fun acceptsValidXmlWithoutRequiringUnsupportedXercesFlags() {
        val issues = ProjectGenerationValidator.validate(
            genericFile("layout.xml", """<root><item android:enabled="true" /></root>"""),
        )

        assertFalse(issues.any { it.contains("XML") && it.contains("malformado") })
    }

    @Test
    fun rejectsDtdAndEntityDeclarationsInGeneratedXml() {
        val issues = ProjectGenerationValidator.validate(
            genericFile(
                "layout.xml",
                """<!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><root>&xxe;</root>""",
            ),
        )

        assertTrue(issues.any { it.contains("XML") && it.contains("malformado") })
    }

    @Test
    fun detectsFallbackLauncherPlaceholder() {
        val draft = AndroidProjectScaffold.complete(
            ProjectDraft(
                name = "Demo",
                profile = ProjectProfile.ANDROID_APP,
                description = "A requested mobile app.",
                files = emptyList(),
            ),
        ).draft

        val issues = ProjectGenerationValidator.validate(draft)

        assertTrue(issues.any { it.contains("tela Android de contingência") })
    }

    @Test
    fun detectsManifestLauncherThatDoesNotExistInSource() {
        val scaffold = AndroidProjectScaffold.complete(
            ProjectDraft(
                name = "Demo",
                profile = ProjectProfile.ANDROID_APP,
                description = "A requested mobile app.",
                files = emptyList(),
            ),
        ).draft
        val files = scaffold.files.map { file ->
            if (file.path == "app/src/main/AndroidManifest.xml") {
                file.copy(content = file.content.replace(
                    "io.github.nucleo.generated.NucleoLauncherActivity",
                    "io.github.nucleo.generated.MissingActivity",
                ))
            } else {
                file
            }
        }
        val issues = ProjectGenerationValidator.validate(scaffold.copy(files = files))

        assertTrue(issues.any { it.contains("não foi encontrada uma Activity compatível") })
    }
}