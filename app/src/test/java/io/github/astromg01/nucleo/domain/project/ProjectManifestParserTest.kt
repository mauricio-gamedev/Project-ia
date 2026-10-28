package io.github.astromg01.nucleo.domain.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectManifestParserTest {
    @Test
    fun parsesACompleteGenericProjectManifest() {
        val draft = ProjectManifestParser.parse(
            responseText = """{"description":"demo","files":[{"path":"src/main.kt","content":"fun main() {}"}]}""",
            name = "Demo",
            description = "Small demo",
            profile = ProjectProfile.GENERIC,
        )

        assertEquals("src/main.kt", draft.files.single().path)
        assertEquals("fun main() {}", draft.files.single().content)
    }

    @Test
    fun rejectsNonStringFileContentsInsteadOfCoercingThem() {
        assertThrows(IllegalArgumentException::class.java) {
            ProjectManifestParser.parse(
                responseText = """{"files":[{"path":"src/main.kt","content":123}]}""",
                name = "Demo",
                description = "",
                profile = ProjectProfile.GENERIC,
            )
        }
    }

    @Test
    fun rejectsEmptyContentsAndTruncatedJson() {
        assertThrows(IllegalArgumentException::class.java) {
            ProjectManifestParser.parse(
                responseText = """{"files":[{"path":"src/main.kt","content":" "}]}""",
                name = "Demo",
                description = "",
                profile = ProjectProfile.GENERIC,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProjectManifestParser.parse(
                responseText = """{"files":[{"path":"src/main.kt","content":"unfinished""",
                name = "Demo",
                description = "",
                profile = ProjectProfile.GENERIC,
            )
        }
    }
}