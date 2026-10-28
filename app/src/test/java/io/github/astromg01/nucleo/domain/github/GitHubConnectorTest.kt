package io.github.astromg01.nucleo.domain.github

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubConnectorTest {
    @Test
    fun repositoryContextContainsSelectedRepositoryBranchTreeAndSource() {
        val context = GitHubRepositoryContext(
            repository = GitHubRepository(
                owner = "mauricio-gamedev",
                name = "Project-ia",
                isPrivate = true,
                defaultBranch = "main",
                htmlUrl = "https://github.com/mauricio-gamedev/Project-ia",
                description = "Núcleo AI",
                canPush = true,
            ),
            branch = "main",
            filePaths = listOf("README.md", "app/src/main/MainActivity.kt"),
            selectedFiles = mapOf("README.md" to "# Project-ia\nLocal project notes"),
        )

        val prompt = context.toPromptContext()

        assertTrue(prompt.contains("mauricio-gamedev/Project-ia"))
        assertTrue(prompt.contains("Visibility: private"))
        assertTrue(prompt.contains("Default branch: main"))
        assertTrue(prompt.contains("app/src/main/MainActivity.kt"))
        assertTrue(prompt.contains("Local project notes"))
    }

    @Test
    fun repositoryContextRespectsCharacterBudget() {
        val context = GitHubRepositoryContext(
            repository = GitHubRepository(
                owner = "owner",
                name = "repo",
                isPrivate = false,
                defaultBranch = "main",
                htmlUrl = "https://github.com/owner/repo",
                description = "",
                canPush = false,
            ),
            branch = "main",
            filePaths = (1..100).map { "src/File$it.kt" },
            selectedFiles = mapOf("README.md" to "x".repeat(10_000)),
        )

        val prompt = context.toPromptContext(maxChars = 2_500)

        assertTrue(prompt.length <= 2_500)
        assertFalse(prompt.contains("x".repeat(3_000)))
    }
}
