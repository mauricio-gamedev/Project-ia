package io.github.astromg01.nucleo.core.ai

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFailurePolicyTest {
    @Test
    fun treatsModelConfigurationErrorsAsNonRetryableAndShortCooldown() {
        val result = ProviderFailurePolicy.classify(ProviderHttpException(400))
        assertEquals("HTTP 400", result.reason)
        assertFalse(result.retryable)
        assertEquals(120_000L, result.cooldownMillis)
    }

    @Test
    fun usesRetryAfterForQuotaErrorsWithinTheConfiguredBounds() {
        assertEquals(7_000L, ProviderFailurePolicy.classify(ProviderHttpException(429, 7L)).cooldownMillis)
        assertEquals(3_600_000L, ProviderFailurePolicy.classify(ProviderHttpException(429, 90_000L)).cooldownMillis)
    }

    @Test
    fun authenticationAndNetworkFailuresUseTheirOwnCategories() {
        assertEquals(900_000L, ProviderFailurePolicy.classify(ProviderHttpException(401)).cooldownMillis)
        val network = ProviderFailurePolicy.classify(IOException("offline"))
        assertTrue(network.retryable)
        assertEquals(45_000L, network.cooldownMillis)
    }
}
