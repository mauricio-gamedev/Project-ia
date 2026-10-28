package io.github.astromg01.nucleo.domain.project

import java.io.IOException
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
}
