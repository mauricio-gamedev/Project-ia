package io.github.astromg01.nucleo.core.ai

import io.github.astromg01.nucleo.data.settings.ProviderConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelRoutePlannerTest {
    private fun route(
        id: String,
        tier: Int = 1,
        priority: Int = 100,
        enabled: Boolean = true,
    ) = ProviderConfiguration(
        id = id,
        providerName = id,
        baseUrl = "https://$id.example.test/v1",
        modelName = "model-$id",
        enabled = enabled,
        priority = priority,
        qualityTier = tier,
    )

    @Test
    fun codingTasksEscalateToHigherCapabilityFallbacksAfterPrimary() {
        val primary = route("primary", tier = 1)
        val fallbacks = listOf(
            route("low-priority-high", tier = 3, priority = 90),
            route("first-priority-low", tier = 1, priority = 1),
            route("mid", tier = 2, priority = 2),
            route("disabled", tier = 3, priority = 0, enabled = false),
            route("primary", tier = 3, priority = 0),
        )

        val ordered = ModelRoutePlanner.order(primary, fallbacks, AiTaskType.CODE_REVIEW)

        assertEquals(listOf("low-priority-high", "mid", "primary", "first-priority-low"), ordered.map { it.id })
    }

    @Test
    fun ordinaryChatUsesConfiguredPriorityBeforeCapabilityTier() {
        val primary = route("primary")
        val fallbacks = listOf(
            route("late-high", tier = 3, priority = 50),
            route("first-low", tier = 1, priority = 1),
            route("first-high", tier = 3, priority = 1),
        )

        val ordered = ModelRoutePlanner.order(primary, fallbacks, AiTaskType.CHAT)

        assertEquals(listOf("primary", "first-high", "first-low", "late-high"), ordered.map { it.id })
    }

    @Test
    fun disabledPrimaryDoesNotBlockEnabledFallbacks() {
        val ordered = ModelRoutePlanner.order(
            route("primary", enabled = false),
            listOf(route("fallback", priority = 10)),
            AiTaskType.CHAT,
        )

        assertEquals(listOf("fallback"), ordered.map { it.id })
    }
}
