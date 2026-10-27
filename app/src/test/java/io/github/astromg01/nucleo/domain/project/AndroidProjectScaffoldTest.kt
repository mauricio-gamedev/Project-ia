package io.github.astromg01.nucleo.domain.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidProjectScaffoldTest {
    @Test
    fun completesAnAndroidDraftWithMissingBuildFilesAndLauncher() {
        val result = AndroidProjectScaffold.complete(
            ProjectDraft(
                name = "Lista Tarefas",
                profile = ProjectProfile.ANDROID_APP,
                description = "Uma lista de tarefas com salvamento local.",
                files = listOf(
                    GeneratedProjectFile("app/src/main/java/example/TaskLogic.kt", "package example\nclass TaskLogic"),
                ),
            ),
        )
        val files = result.draft.files.associateBy { it.path }

        assertTrue(result.addedPaths.contains("settings.gradle.kts"))
        assertTrue(result.addedPaths.contains("build.gradle.kts"))
        assertTrue(result.addedPaths.contains("app/build.gradle.kts"))
        assertTrue(result.addedPaths.contains("app/src/main/AndroidManifest.xml"))
        assertTrue(result.defaultLauncherAdded)
        assertTrue(files.containsKey("app/src/main/java/io/github/nucleo/generated/NucleoLauncherActivity.kt"))
        assertTrue(files["app/build.gradle.kts"]!!.content.contains("compileSdk = 36"))
        assertTrue(files["app/build.gradle.kts"]!!.content.contains("id(\"com.android.application\")"))
        assertTrue(files["app/src/main/AndroidManifest.xml"]!!.content.contains("io.github.nucleo.generated.NucleoLauncherActivity"))
        assertTrue(files["app/src/main/AndroidManifest.xml"]!!.content.contains("android.intent.category.LAUNCHER"))
        assertFalse(files["settings.gradle.kts"]!!.content.contains("\\n"))
        assertFalse(files["build.gradle.kts"]!!.content.contains("\\n"))
        assertFalse(files["app/build.gradle.kts"]!!.content.contains("\\n"))
        assertFalse(files["app/src/main/AndroidManifest.xml"]!!.content.contains("\\n"))
        ProjectFactory().validate(result.draft)
    }

    @Test
    fun preservesAValidGeneratedActivityAndUsesItAsTheLauncher() {
        val activity = """
            package com.example.todo

            import android.os.Bundle
            import androidx.activity.ComponentActivity

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                }
            }
        """.trimIndent()
        val result = AndroidProjectScaffold.complete(
            ProjectDraft(
                name = "Todo",
                profile = ProjectProfile.ANDROID_APP,
                files = listOf(
                    GeneratedProjectFile("app/src/main/java/com/example/todo/MainActivity.kt", activity),
                ),
            ),
        )
        val manifest = result.draft.files.first { it.path == "app/src/main/AndroidManifest.xml" }.content

        assertFalse(result.defaultLauncherAdded)
        assertFalse(result.addedPaths.contains("app/src/main/java/io/github/nucleo/generated/NucleoLauncherActivity.kt"))
        assertTrue(manifest.contains("android:name=\"com.example.todo.MainActivity\""))
        assertTrue(result.draft.files.any { it.path == "app/src/main/java/com/example/todo/MainActivity.kt" && it.content == activity })
    }

    @Test
    fun repairsInvalidBuildFilesAndPreservesLiteralDependencies() {
        val result = AndroidProjectScaffold.complete(
            ProjectDraft(
                name = "Camera Demo",
                profile = ProjectProfile.ANDROID_APP,
                files = listOf(
                    GeneratedProjectFile("settings.gradle.kts", "rootProject.name = \"oops\""),
                    GeneratedProjectFile("build.gradle.kts", "plugins {}"),
                    GeneratedProjectFile(
                        "app/build.gradle.kts",
                        """
                            plugins {}
                            dependencies {
                                implementation("androidx.camera:camera-camera2:1.4.2")
                            }
                        """.trimIndent(),
                    ),
                    GeneratedProjectFile("app/src/main/AndroidManifest.xml", "<manifest />"),
                    GeneratedProjectFile(
                        "app/src/main/java/example/MainActivity.kt",
                        "package example\nclass MainActivity : ComponentActivity() { override fun onCreate(state: Bundle?) {} }",
                    ),
                ),
            ),
        )
        val files = result.draft.files.associateBy { it.path }

        assertTrue(result.replacedPaths.contains("settings.gradle.kts"))
        assertTrue(result.replacedPaths.contains("build.gradle.kts"))
        assertTrue(result.replacedPaths.contains("app/build.gradle.kts"))
        assertTrue(result.replacedPaths.contains("app/src/main/AndroidManifest.xml"))
        assertTrue(files["app/build.gradle.kts"]!!.content.contains("androidx.camera:camera-camera2:1.4.2"))
        assertTrue(files["settings.gradle.kts"]!!.content.contains("include(\":app\")"))
        assertTrue(files["app/src/main/AndroidManifest.xml"]!!.content.contains("android.permission.INTERNET"))
    }

    @Test
    fun preservesCustomPermissionsWhenRepairingManifest() {
        val draft = ProjectDraft(
            name = "Permissions",
            profile = ProjectProfile.ANDROID_APP,
            files = listOf(
                GeneratedProjectFile("app/src/main/AndroidManifest.xml", """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                      <uses-permission android:name="android.permission.CAMERA" />
                      <application android:label="Permissions">
                        <activity android:name="example.MainActivity">
                          <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                        <service android:name=".SyncService" />
                      </application>
                    </manifest>
                """.trimIndent()),
                GeneratedProjectFile("app/src/main/java/example/MainActivity.kt", """
                    package example
                    class MainActivity : ComponentActivity() { override fun onCreate(state: Bundle?) {} }
                """.trimIndent()),
            ),
        )
        val result = AndroidProjectScaffold.complete(draft)
        val manifest = result.draft.files.first { it.path == "app/src/main/AndroidManifest.xml" }.content

        assertTrue(manifest.contains("android.permission.CAMERA"))
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertTrue(manifest.contains("android.intent.action.MAIN"))
        assertTrue(manifest.contains("android.intent.category.LAUNCHER"))
        assertTrue(manifest.contains("android:exported=\"true\""))
        assertTrue(manifest.contains("android:name=\".SyncService\""))
    }

    @Test
    fun acceptsValidGroovyGradleFilesWithoutAddingCompetingKotlinDslFiles() {
        val result = AndroidProjectScaffold.complete(
            ProjectDraft(
                name = "Groovy Project",
                profile = ProjectProfile.ANDROID_APP,
                files = listOf(
                    GeneratedProjectFile("settings.gradle", """
                        pluginManagement {
                            repositories {
                                google()
                                mavenCentral()
                                gradlePluginPortal()
                            }
                        }
                        rootProject.name = 'Groovy Project'
                        include ':app'
                    """.trimIndent()),
                    GeneratedProjectFile("build.gradle", """
                        plugins {
                            id 'com.android.application' version '8.13.2' apply false
                            id 'org.jetbrains.kotlin.android' version '2.3.21' apply false
                            id 'org.jetbrains.kotlin.plugin.compose' version '2.3.21' apply false
                        }
                    """.trimIndent()),
                    GeneratedProjectFile("app/build.gradle", """
                        plugins {
                            id 'com.android.application'
                            id 'org.jetbrains.kotlin.android'
                            id 'org.jetbrains.kotlin.plugin.compose'
                        }
                        android {
                            namespace 'com.example.groovy'
                            compileSdk 36
                            defaultConfig {
                                applicationId 'com.example.groovy'
                                minSdk 26
                                targetSdk 36
                            }
                        }
                        dependencies {
                            implementation 'androidx.core:core-ktx:1.17.0'
                        }
                    """.trimIndent()),
                    GeneratedProjectFile("app/src/main/AndroidManifest.xml", """
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                          <application android:label="Groovy" android:allowBackup="false">
                            <activity android:name="com.example.groovy.MainActivity" android:exported="true">
                              <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                              </intent-filter>
                            </activity>
                          </application>
                        </manifest>
                    """.trimIndent()),
                    GeneratedProjectFile(
                        "app/src/main/java/com/example/groovy/MainActivity.kt",
                        "package com.example.groovy\nclass MainActivity : ComponentActivity() {}",
                    ),
                ),
            ),
        )
        val paths = result.draft.files.map { it.path }.toSet()

        assertTrue(paths.contains("settings.gradle"))
        assertTrue(paths.contains("build.gradle"))
        assertTrue(paths.contains("app/build.gradle"))
        assertFalse(paths.contains("settings.gradle.kts"))
        assertFalse(paths.contains("build.gradle.kts"))
        assertFalse(paths.contains("app/build.gradle.kts"))
        assertFalse(result.defaultLauncherAdded)
    }

    @Test
    fun leavesNonAndroidProfilesUnchanged() {
        val draft = ProjectDraft(
            name = "Website",
            profile = ProjectProfile.WEB_APP,
            files = listOf(GeneratedProjectFile("index.html", "<h1>Oi</h1>")),
        )
        val result = AndroidProjectScaffold.complete(draft)

        assertEquals(draft, result.draft)
        assertTrue(result.addedPaths.isEmpty())
        assertTrue(result.replacedPaths.isEmpty())
        assertFalse(result.defaultLauncherAdded)
    }
}
