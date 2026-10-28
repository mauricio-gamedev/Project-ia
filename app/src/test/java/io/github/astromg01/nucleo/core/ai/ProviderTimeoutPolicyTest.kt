package io.github.astromg01.nucleo.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderTimeoutPolicyTest {
    @Test
    fun routeStartsWithFortySecondBudget() {
        assertEquals(
            ProviderTimeoutPolicy.MAX_ROUTE_BUDGET_MILLIS,
            ProviderTimeoutPolicy.remainingBudgetMillis(1_000L, 1_000L, 1_000L),
        )
    }

    @Test
    fun routeBudgetUsesSmallerRemainingRequestDeadline() {
        assertEquals(
            12_000L,
            ProviderTimeoutPolicy.remainingBudgetMillis(
                requestStartedAtMillis = 1_000L,
                routeStartedAtMillis = 70_000L,
                nowMillis = 79_000L,
            ),
        )
    }

    @Test
    fun skipsAnotherRouteWhenGlobalDeadlineIsNearlyExpired() {
        assertNull(
            ProviderTimeoutPolicy.remainingBudgetMillis(
                requestStartedAtMillis = 1_000L,
                routeStartedAtMillis = 80_000L,
                nowMillis = 89_500L,
            ),
        )
    }

    @Test
    fun clockMovingBackwardsDoesNotCreateNegativeElapsedTime() {
        assertEquals(
            ProviderTimeoutPolicy.MAX_ROUTE_BUDGET_MILLIS,
            ProviderTimeoutPolicy.remainingBudgetMillis(
                requestStartedAtMillis = 1_000L,
                routeStartedAtMillis = 1_000L,
                nowMillis = 500L,
            ),
        )
    }
}