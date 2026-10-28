package io.github.astromg01.nucleo.domain.project

import java.io.IOException
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubAndroidBuildAdapterTest {
    @Test
    fun rejectsMissingPublishedChecksum() {
        expectIOException("SHA-256") {
            verifyPublishedApkChecksum(expectedChecksum = null, actualChecksum = "a".repeat(64))
        }
    }

    @Test
    fun rejectsMalformedPublishedChecksum() {
        expectIOException("SHA-256") {
            verifyPublishedApkChecksum(expectedChecksum = "not-a-checksum", actualChecksum = "a".repeat(64))
        }
    }

    @Test
    fun rejectsChecksumThatDoesNotMatchApkBytes() {
        expectIOException("não corresponde") {
            verifyPublishedApkChecksum(expectedChecksum = "b".repeat(64), actualChecksum = "a".repeat(64))
        }
    }

    @Test
    fun acceptsMatchingSha256CaseInsensitively() {
        val digest = "abcdef0123456789".repeat(4)
        verifyPublishedApkChecksum(expectedChecksum = digest.uppercase(), actualChecksum = digest)
    }

    @Test
    fun sanitizesBuildLogsBeforeTheyCanBeSentToTheRepairModel() {
        val githubToken = "gho_" + "A".repeat(32)
        val raw = "e: app/src/main/java/MainActivity.kt:12: unresolved reference; token=" + githubToken

        val safe = sanitizeBuildDiagnostics(raw)

        assertTrue(safe.contains("unresolved reference"))
        assertTrue(safe.contains("[REDACTED]"))
        assertTrue(!safe.contains(githubToken))
    }

    @Test
    fun reportsProtectedBranchPolicySeparatelyFromAuthenticationFailure() {
        val protected = classifyGitHubBuildFailure(
            403,
            "Repository rule violations found: Cannot update this protected ref",
        )
        val unauthorized = classifyGitHubBuildFailure(401, "Bad credentials")

        assertTrue(protected.contains("regra de proteção"))
        assertTrue(protected.contains("não vai contornar"))
        assertTrue(unauthorized.contains("sessão OAuth"))
        assertTrue(unauthorized.contains("expirado"))
    }

    @Test
    fun distinguishesMissingRepositoryAccessFromRateLimit() {
        val permissions = classifyGitHubBuildFailure(403, "Resource not accessible by integration")
        val inaccessible = classifyGitHubBuildFailure(404, "Not Found")
        val rateLimit = classifyGitHubBuildFailure(403, "API rate limit exceeded")

        assertTrue(permissions.contains("repo/workflow"))
        assertTrue(inaccessible.contains("repositórios privados"))
        assertTrue(rateLimit.contains("cota"))
    }

    @Test
    fun redactsFineGrainedGithubTokensAndUnlabelledTokenFields() {
        val token = "github_pat_" + "B".repeat(35)
        val raw = "token=$token api-key=sk-" + "C".repeat(24)

        val safe = sanitizeBuildDiagnostics(raw)

        assertTrue(!safe.contains(token))
        assertTrue(!safe.contains("sk-" + "C".repeat(24)))
        assertTrue(safe.contains("[REDACTED]"))
    }

    @Test
    fun retainsTailDiagnosticsWhenApplyingTheOutputSizeLimit() {
        val raw = (1..100).joinToString("\n") { index ->
            "diagnostic-$index " + "x".repeat(220)
        }

        val safe = sanitizeBuildDiagnostics(raw)

        assertTrue(safe.contains("diagnostic-100"))
        assertTrue(!safe.contains("diagnostic-1 "))
    }

    private fun expectIOException(expectedMessage: String, action: () -> Unit) {
        try {
            action()
        } catch (error: IOException) {
            assertTrue(
                "Expected an IOException mentioning \"$expectedMessage\", got: ${error.message}",
                error.message.orEmpty().contains(expectedMessage, ignoreCase = true),
            )
            return
        }
        throw AssertionError("Expected IOException mentioning \"$expectedMessage\".")
    }


    @Test
    fun readsPlainTextLogFallbackAndKeepsTheFinalCompilerError() {
        val log = (1..40).joinToString("\n") { "progress-$it" } + "\nBUILD FAILED: unresolved reference"
        val decoded = readableRawBuildLog(log.toByteArray(Charsets.UTF_8), maxChars = 160)

        assertTrue(decoded.orEmpty().contains("BUILD FAILED"))
        assertTrue(decoded.orEmpty().contains("unresolved reference"))
    }

    @Test
    fun doesNotExposeBinaryZipBytesAsPlainTextDiagnostics() {
        val binary = byteArrayOf(0, 1, 2, 3, 4, 5, 0, 7)

        assertNull(readableRawBuildLog(binary, maxChars = 200))
    }

}
