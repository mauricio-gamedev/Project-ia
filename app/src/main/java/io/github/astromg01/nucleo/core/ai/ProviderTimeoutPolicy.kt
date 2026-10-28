package io.github.astromg01.nucleo.core.ai

/**
 * Bounds time spent waiting on remote model routes so a stalled provider cannot hold chat for
 * several minutes before AutoRouter tries another configured route.
 */
internal object ProviderTimeoutPolicy {
    const val MAX_ROUTE_BUDGET_MILLIS = 40_000L
    const val MAX_TOTAL_BUDGET_MILLIS = 90_000L
    const val CONNECT_TIMEOUT_MILLIS = 8_000L
    const val WRITE_TIMEOUT_MILLIS = 15_000L
    const val MIN_USEFUL_REMAINING_MILLIS = 2_000L

    /**
     * Returns the time remaining for this route, bounded by both its own budget and the whole
     * request budget. [requestStartedAtMillis], [routeStartedAtMillis], and [nowMillis] must use
     * the same monotonic time source.
     */
    fun remainingBudgetMillis(
        requestStartedAtMillis: Long,
        routeStartedAtMillis: Long,
        nowMillis: Long,
    ): Long? {
        val requestElapsed = (nowMillis - requestStartedAtMillis).coerceAtLeast(0L)
        val routeElapsed = (nowMillis - routeStartedAtMillis).coerceAtLeast(0L)
        val requestRemaining = MAX_TOTAL_BUDGET_MILLIS - requestElapsed
        val routeRemaining = MAX_ROUTE_BUDGET_MILLIS - routeElapsed
        val remaining = minOf(requestRemaining, routeRemaining)
        return remaining.takeIf { it >= MIN_USEFUL_REMAINING_MILLIS }
    }
}