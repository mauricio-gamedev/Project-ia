package io.github.astromg01.nucleo.domain.project

import io.github.astromg01.nucleo.core.ai.AiMessage
import io.github.astromg01.nucleo.core.ai.AiRequest
import io.github.astromg01.nucleo.core.ai.AiResponse
import io.github.astromg01.nucleo.core.ai.AiTaskType
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectGenerationCoordinatorTest {
    @Test
    fun retriesAutomaticallyWhenFirstSourceHasUnclosedBlock() = runBlocking {
        var calls = 0
        val coordinator = ProjectGenerationCoordinator()
        val result = coordinator.generate(
            request = AiRequest(
                messages = listOf(AiMessage("system", "Generate one JSON project manifest.")),
                taskType = AiTaskType.CODE_GENERATION,
            ),
            name = "Demo",
            description = "A simple utility",
            profile = ProjectProfile.GENERIC,
        ) { request ->
            calls++
            if (calls == 1) {
                AiResponse(
                    projectJson("src/main.kt", "fun main() {"),
                    "TestProvider",
                    "test-model",
                )
            } else {
                assertTrue(request.messages.last().content.contains("não foi fechado"))
                AiResponse(
                    projectJson("src/main.kt", "fun main() {}"),
                    "TestProvider",
                    "test-model",
                )
            }
        }

        assertEquals(2, calls)
        assertEquals(2, result.attempts)
        assertTrue(result.readinessIssues.isEmpty())
    }

    @Test
    fun reportsIncompleteAndroidLauncherAfterRetryInsteadOfCallingItReady() = runBlocking {
        var calls = 0
        val files = listOf(
            "settings.gradle.kts" to "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }; rootProject.name = \"Demo\"; include(\":app\")",
            "build.gradle.kts" to "plugins { id(\"com.android.application\") version \"8.13.2\" apply false; id(\"org.jetbrains.kotlin.android\") version \"2.3.21\" apply false; id(\"org.jetbrains.kotlin.plugin.compose\") version \"2.3.21\" apply false }",
            "app/build.gradle.kts" to "plugins { id(\"com.android.application\"); id(\"org.jetbrains.kotlin.android\"); id(\"org.jetbrains.kotlin.plugin.compose\") }; android { namespace = \"com.example.demo\"; compileSdk = 36; defaultConfig { applicationId = \"com.example.demo\"; minSdk = 26; targetSdk = 36 }; buildFeatures { compose = true } }; dependencies { implementation(\"androidx.activity:activity-compose:1.11.0\") }",
            "app/src/main/AndroidManifest.xml" to "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application android:label=\"Demo\"></application></manifest>",
            "app/src/main/java/com/example/demo/Logic.kt" to "package com.example.demo\nclass Logic",
        )
        val responseText = JSONObject()
            .put("files", JSONArray().apply {
                files.forEach { (path, content) ->
                    put(JSONObject().put("path", path).put("content", content))
                }
            })
            .toString()
        val result = ProjectGenerationCoordinator().generate(
            request = AiRequest(
                messages = listOf(AiMessage("system", "Generate a complete Android project.")),
                taskType = AiTaskType.CODE_GENERATION,
            ),
            name = "Demo",
            description = "A complete Android app",
            profile = ProjectProfile.ANDROID_APP,
        ) {
            calls++
            AiResponse(responseText, "TestProvider", "test-model")
        }

        assertEquals(2, calls)
        assertTrue(result.readinessIssues.any { it.contains("Activity de entrada") })
        assertTrue(result.scaffold.defaultLauncherAdded)
    }

    private fun projectJson(path: String, content: String): String =
        JSONObject()
            .put("files", JSONArray().put(JSONObject().put("path", path).put("content", content)))
            .toString()
}