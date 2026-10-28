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
            genericFile("layout.xml", """<root xmlns:android="http://schemas.android.com/apk/res/android"><item android:enabled="true" /></root>"""),
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


    @Test
    fun reportsBlankGeneratedFilesEvenWhenPathsAreValid() {
        val issues = ProjectGenerationValidator.validate(genericFile("src/empty.js", "   "))

        assertTrue(issues.any { it.contains("está vazio") })
    }

    @Test
    fun resolvesRelativeManifestActivityUsingGradleNamespace() {
        val draft = ProjectDraft(
            name = "Namespace fallback",
            profile = ProjectProfile.ANDROID_APP,
            description = "A valid minimal app.",
            files = listOf(
                GeneratedProjectFile(
                    "settings.gradle.kts",
                    """
                    pluginManagement {
                        repositories {
                            google()
                            mavenCentral()
                            gradlePluginPortal()
                        }
                    }
                    rootProject.name = "Namespace fallback"
                    include(":app")
                    """.trimIndent(),
                ),
                GeneratedProjectFile(
                    "build.gradle.kts",
                    """
                    plugins {
                        id("com.android.application") version "8.13.2" apply false
                        id("org.jetbrains.kotlin.android") version "2.3.21" apply false
                        id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
                    }
                    """.trimIndent(),
                ),
                GeneratedProjectFile(
                    "app/build.gradle.kts",
                    """
                    plugins {
                        id("com.android.application")
                        id("org.jetbrains.kotlin.android")
                        id("org.jetbrains.kotlin.plugin.compose")
                    }
                    android {
                        namespace = "com.example.namespace"
                        compileSdk = 36
                        defaultConfig {
                            applicationId = "com.example.namespace"
                            minSdk = 26
                            targetSdk = 36
                        }
                    }
                    dependencies {}
                    """.trimIndent(),
                ),
                GeneratedProjectFile(
                    "app/src/main/AndroidManifest.xml",
                    """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                        <application android:label="Namespace fallback">
                            <activity android:name=".MainActivity" android:exported="true">
                                <intent-filter>
                                    <action android:name="android.intent.action.MAIN" />
                                    <category android:name="android.intent.category.LAUNCHER" />
                                </intent-filter>
                            </activity>
                        </application>
                    </manifest>
                    """.trimIndent(),
                ),
                GeneratedProjectFile(
                    "app/src/main/java/com/example/namespace/MainActivity.kt",
                    """
                    package com.example.namespace
                    import androidx.activity.ComponentActivity
                    class MainActivity : ComponentActivity()
                    """.trimIndent(),
                ),
            ),
        )

        val issues = ProjectGenerationValidator.validate(draft)

        assertFalse(issues.any { it.contains("não foi encontrada uma Activity compatível") })
    }

}
