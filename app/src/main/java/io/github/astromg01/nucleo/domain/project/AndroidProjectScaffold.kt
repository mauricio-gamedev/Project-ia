package io.github.astromg01.nucleo.domain.project

import java.util.Locale

data class AndroidScaffoldResult(
    val draft: ProjectDraft,
    val addedPaths: List<String>,
    val replacedPaths: List<String>,
    val defaultLauncherAdded: Boolean,
)

/**
 * Completes the deterministic Android/Gradle skeleton around AI-generated application sources.
 * It does not claim the generated app is correct or buildable until a real build adapter verifies it.
 */
object AndroidProjectScaffold {
    private const val MAIN_ACTIVITY_PATH = "app/src/main/java/io/github/nucleo/generated/NucleoLauncherActivity.kt"
    private const val MANIFEST_PATH = "app/src/main/AndroidManifest.xml"

    fun complete(draft: ProjectDraft): AndroidScaffoldResult {
        if (draft.profile != ProjectProfile.ANDROID_APP) {
            return AndroidScaffoldResult(draft, emptyList(), emptyList(), false)
        }

        val files = draft.files.toMutableList()
        val added = mutableListOf<String>()
        val replaced = mutableListOf<String>()
        fun existing(path: String): GeneratedProjectFile? =
            files.firstOrNull { it.path.equals(path, ignoreCase = true) }

        fun remove(path: String) {
            val current = existing(path) ?: return
            files.remove(current)
            if (current.path !in replaced) replaced += current.path
        }

        fun set(path: String, content: String, replaceInvalid: Boolean = false) {
            val current = existing(path)
            if (current == null) {
                files += GeneratedProjectFile(path, content)
                added += path
            } else if (replaceInvalid && current.content != content) {
                val index = files.indexOf(current)
                files[index] = GeneratedProjectFile(path, content)
                if (current.path !in replaced) replaced += current.path
            }
        }

        fun ensureScriptPair(
            kotlinPath: String,
            groovyPath: String,
            isUsable: (String) -> Boolean,
            template: (List<String>) -> String,
        ) {
            val kotlinFile = existing(kotlinPath)
            val groovyFile = existing(groovyPath)
            val candidates = listOfNotNull(kotlinFile, groovyFile)
            val valid = candidates.firstOrNull { isUsable(it.content) }
            if (valid != null) {
                candidates.filter { it.path != valid.path }.forEach { remove(it.path) }
            } else {
                val retainedDependencies = candidates.flatMap { extractLiteralDependencies(it.content) }.distinct()
                candidates.forEach { remove(it.path) }
                set(kotlinPath, template(retainedDependencies))
            }
        }

        val slug = packageSlug(draft.name)
        val projectTitle = draft.name.trim().ifBlank { "Núcleo App" }

        ensureScriptPair(
            "settings.gradle.kts",
            "settings.gradle",
            { isUsableSettings(it) },
            { _ -> settingsTemplate(projectTitle) },
        )
        ensureScriptPair(
            "build.gradle.kts",
            "build.gradle",
            { isUsableRootBuild(it) },
            { _ -> rootBuildTemplate() },
        )

        set(
            "gradle.properties",
            "org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8\nandroid.useAndroidX=true\nkotlin.code.style=official\n",
        )
        ensureScriptPair(
            "app/build.gradle.kts",
            "app/build.gradle",
            { isUsableAppBuild(it) },
            { dependencies -> appBuildTemplate(slug, dependencies) },
        )

        set(
            "gradle/wrapper/gradle-wrapper.properties",
            "distributionBase=GRADLE_USER_HOME\ndistributionPath=wrapper/dists\ndistributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-bin.zip\ndistributionSha256Sum=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78\nzipStoreBase=GRADLE_USER_HOME\nzipStorePath=wrapper/dists\n",
        )

        val launcher = findLauncher(files, existing(MANIFEST_PATH)?.content)
        val defaultLauncherAdded = launcher == null
        val launcherClass = launcher ?: run {
            files += GeneratedProjectFile(
                MAIN_ACTIVITY_PATH,
                defaultLauncherTemplate(projectTitle, draft.description),
            )
            added += MAIN_ACTIVITY_PATH
            "io.github.nucleo.generated.NucleoLauncherActivity"
        }

        val manifest = existing(MANIFEST_PATH)
        if (manifest == null) {
            set(MANIFEST_PATH, manifestTemplate(projectTitle, launcherClass, emptyList()))
        } else {
            val repaired = repairManifest(manifest.content, projectTitle, launcherClass)
            if (repaired != manifest.content) {
                set(MANIFEST_PATH, repaired, replaceInvalid = true)
            }
        }

        set("README.md", readmeTemplate(projectTitle))

        val result = ProjectDraft(
            name = draft.name,
            profile = draft.profile,
            description = draft.description,
            files = files,
        )
        ProjectFactory().validate(result)
        val finalPaths = result.files.associateBy { it.path.lowercase(Locale.ROOT) }
        val settingsContent = finalPaths["settings.gradle.kts"]?.content
            ?: finalPaths["settings.gradle"]?.content.orEmpty()
        val rootBuildContent = finalPaths["build.gradle.kts"]?.content
            ?: finalPaths["build.gradle"]?.content.orEmpty()
        val appBuildContent = finalPaths["app/build.gradle.kts"]?.content
            ?: finalPaths["app/build.gradle"]?.content.orEmpty()
        require(isUsableSettings(settingsContent)) {
            "O scaffold Android não conseguiu preparar os settings do Gradle."
        }
        require(isUsableRootBuild(rootBuildContent)) {
            "O scaffold Android não conseguiu preparar o build.gradle da raiz."
        }
        require(isUsableAppBuild(appBuildContent)) {
            "O scaffold Android não conseguiu preparar app/build.gradle."
        }
        require(isUsableManifest(finalPaths[MANIFEST_PATH.lowercase(Locale.ROOT)]?.content.orEmpty())) {
            "O scaffold Android não conseguiu preparar o AndroidManifest.xml."
        }
        return AndroidScaffoldResult(
            draft = result,
            addedPaths = added.distinct(),
            replacedPaths = replaced.distinct(),
            defaultLauncherAdded = defaultLauncherAdded,
        )
    }

    private fun packageSlug(name: String): String {
        var slug = name.lowercase(Locale.ROOT)
            .filter { it in 'a'..'z' || it in '0'..'9' }
            .take(24)
        if (slug.isBlank()) slug = "app"
        if (slug.first().isDigit()) slug = "app$slug"
        return slug
    }

    private fun isUsableSettings(content: String): Boolean =
        content.contains("pluginManagement") &&
            content.contains("repositories") &&
            content.contains("google()") &&
            content.contains("mavenCentral()") &&
            content.contains("gradlePluginPortal()") &&
            content.contains("include") &&
            (content.contains(":app") || content.contains("'app'"))

    private fun isUsableRootBuild(content: String): Boolean =
        Regex("""(?s)\bplugins\s*\{.*}""").containsMatchIn(content) &&
            content.contains("com.android.application") &&
            content.contains("8.13.2") &&
            content.contains("org.jetbrains.kotlin.android") &&
            content.contains("2.3.21") &&
            content.contains("org.jetbrains.kotlin.plugin.compose")

    private fun isUsableAppBuild(content: String): Boolean =
        Regex("""(?s)\bplugins\s*\{.*}""").containsMatchIn(content) &&
            content.contains("com.android.application") &&
            content.contains("org.jetbrains.kotlin.android") &&
            content.contains("org.jetbrains.kotlin.plugin.compose") &&
            Regex("""(?s)\bandroid\s*\{.*}""").containsMatchIn(content) &&
            content.contains("namespace") &&
            Regex("""\bcompileSdk\b""").containsMatchIn(content) &&
            content.contains("applicationId") &&
            Regex("""\bminSdk\b""").containsMatchIn(content) &&
            Regex("""\btargetSdk\b""").containsMatchIn(content) &&
            Regex("""(?s)\bdependencies\s*\{.*}""").containsMatchIn(content)

    private fun isUsableManifest(content: String): Boolean =
        content.contains("<manifest") &&
            content.contains("</manifest", ignoreCase = true) &&
            content.contains("<application") &&
            content.contains("</application", ignoreCase = true) &&
            content.contains("<activity") &&
            content.contains("android.intent.action.MAIN") &&
            content.contains("android.intent.category.LAUNCHER") &&
            Regex("""android:name\s*=\s*["'][^"']+["']""").containsMatchIn(content) &&
            Regex("""android:exported\s*=\s*["']true["']""").containsMatchIn(content)

    private fun findLauncher(files: List<GeneratedProjectFile>, manifestContent: String? = null): String? {
        val kotlinPackage = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)""")
        val kotlinActivity = Regex(
            """\bclass\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?:\([^)]*\))?\s*:\s*(?:androidx\.activity\.ComponentActivity|androidx\.appcompat\.app\.AppCompatActivity|android\.app\.Activity|ComponentActivity|AppCompatActivity|Activity)\s*\(""",
        )
        val javaPackage = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)\s*;""")
        val javaActivity = Regex(
            """\bclass\s+([A-Za-z_][A-Za-z0-9_]*)\s+extends\s+(?:[A-Za-z_][A-Za-z0-9_]*\.)*(?:Activity|AppCompatActivity|ComponentActivity)\b""",
        )
        val candidates = files.mapNotNull { file ->
            val lower = file.path.lowercase(Locale.ROOT)
            if (!(lower.startsWith("app/src/main/") && (lower.endsWith(".kt") || lower.endsWith(".java")))) {
                return@mapNotNull null
            }
            val packageMatch = if (lower.endsWith(".kt")) kotlinPackage.find(file.content) else javaPackage.find(file.content)
            val classMatch = if (lower.endsWith(".kt")) kotlinActivity.find(file.content) else javaActivity.find(file.content)
            if (packageMatch == null || classMatch == null) null
            else "${packageMatch.groupValues[1]}.${classMatch.groupValues[1]}"
        }
        val declaredLauncher = manifestContent?.let { source ->
            Regex("""(?is)<activity\b[^>]*>.*?</activity\s*>""")
                .findAll(source)
                .firstOrNull { block ->
                    block.value.contains("android.intent.action.MAIN") &&
                        block.value.contains("android.intent.category.LAUNCHER")
                }?.value
        }?.let { block ->
            Regex("""(?is)android:name\s*=\s*["']([^"']+)["']""")
                .find(block.substringBefore(">"))?.groupValues?.get(1)
        }
        if (!declaredLauncher.isNullOrBlank()) {
            candidates.firstOrNull { candidate ->
                declaredLauncher == candidate ||
                    declaredLauncher == candidate.substringAfterLast('.') ||
                    declaredLauncher == ".${candidate.substringAfterLast('.')}" ||
                    candidate.endsWith(".$declaredLauncher")
            }?.let { return it }
        }
        return candidates.firstOrNull()
    }

    private fun extractLiteralDependencies(buildFile: String): List<String> {
        val configurations = "implementation|api|testImplementation|androidTestImplementation|debugImplementation|compileOnly|runtimeOnly"
        val kotlinDsl = Regex(
            """(?m)^\s*($configurations)\(\s*["']([^"']+)["']\s*\)\s*$""",
        )
        val groovyDsl = Regex(
            """(?m)^\s*($configurations)\s+["']([^"']+)["']\s*$""",
        )
        return (kotlinDsl.findAll(buildFile).toList() + groovyDsl.findAll(buildFile).toList())
            .map { "${it.groupValues[1]}(\"${it.groupValues[2]}\")" }
            .distinct()
    }

    private fun extractPermissions(manifest: String): List<String> {
        val regex = Regex("""(?m)^\s*<uses-permission\b[^>]*?/?>\s*$""")
        return regex.findAll(manifest).map { it.value.trim() }.distinct().toList()
    }

    private fun settingsTemplate(name: String): String = """
        pluginManagement {
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
        }

        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                google()
                mavenCentral()
            }
        }

        rootProject.name = "${escapeString(name)}"
        include(":app")
    """.trimIndent() + "\n"

    private fun rootBuildTemplate(): String = """
        plugins {
            id("com.android.application") version "8.13.2" apply false
            id("org.jetbrains.kotlin.android") version "2.3.21" apply false
            id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
        }
    """.trimIndent() + "\n"

    private fun appBuildTemplate(slug: String, retainedDependencies: List<String>): String {
        val dependencies = linkedSetOf(
            "implementation(\"androidx.core:core-ktx:1.17.0\")",
            "implementation(\"androidx.activity:activity-compose:1.11.0\")",
            "implementation(platform(\"androidx.compose:compose-bom:2025.08.00\"))",
            "implementation(\"androidx.compose.ui:ui\")",
            "implementation(\"androidx.compose.ui:ui-graphics\")",
            "implementation(\"androidx.compose.ui:ui-tooling-preview\")",
            "implementation(\"androidx.compose.material3:material3\")",
            "implementation(\"androidx.compose.foundation:foundation\")",
            "debugImplementation(\"androidx.compose.ui:ui-tooling\")",
            "testImplementation(\"junit:junit:4.13.2\")",
        )
        dependencies.addAll(retainedDependencies)
        return """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
                id("org.jetbrains.kotlin.plugin.compose")
            }

            android {
                namespace = "io.github.nucleo.generated.$slug"
                compileSdk = 36

                defaultConfig {
                    applicationId = "io.github.nucleo.generated.$slug"
                    minSdk = 26
                    targetSdk = 36
                    versionCode = 1
                    versionName = "1.0"
                }

                buildFeatures {
                    compose = true
                }
            }

            dependencies {
            ${dependencies.joinToString("\n") { "    $it" }}
            }
        """.trimIndent() + "\n"
    }

    private fun repairManifest(
        source: String,
        name: String,
        activityName: String,
    ): String {
        val manifestTagRegex = Regex("""(?is)<manifest\b[^>]*>""")
        val applicationTagRegex = Regex("""(?is)<application\b[^>]*>""")
        if (manifestTagRegex.find(source) == null ||
            applicationTagRegex.find(source) == null ||
            !source.contains("</application", ignoreCase = true)
        ) {
            return manifestTemplate(name, activityName, extractPermissions(source))
        }

        var content = source
        if (!content.contains("android.permission.INTERNET", ignoreCase = true)) {
            val manifestTag = manifestTagRegex.find(content)
                ?: return manifestTemplate(name, activityName, extractPermissions(source))
            val insertAt = manifestTag.range.last + 1
            content = content.substring(0, insertAt) +
                "\n    <uses-permission android:name=\"android.permission.INTERNET\" />" +
                content.substring(insertAt)
        }

        val activityBlockRegex = Regex("""(?is)<activity\b[^>]*>.*?</activity\s*>""")
        val blocks = activityBlockRegex.findAll(content).toList()
        val launcherBlock = blocks.firstOrNull {
            it.value.contains("android.intent.action.MAIN") &&
                it.value.contains("android.intent.category.LAUNCHER")
        }
        val desiredSimpleName = activityName.substringAfterLast('.')
        val desiredBlock = blocks.firstOrNull { match ->
            val opening = match.value.substringBefore(">")
            val declaredName = Regex("""(?is)android:name\s*=\s*["']([^"']+)["']""")
                .find(opening)?.groupValues?.get(1)
            declaredName == activityName ||
                declaredName == desiredSimpleName ||
                declaredName == ".$desiredSimpleName" ||
                (!declaredName.isNullOrBlank() && activityName.endsWith(".$declaredName"))
        }
        val target = launcherBlock ?: desiredBlock ?: blocks.firstOrNull()

        if (target == null) {
            val appTag = applicationTagRegex.find(content)
                ?: return manifestTemplate(name, activityName, extractPermissions(source))
            val appClose = content.indexOf("</application", appTag.range.last + 1)
            if (appClose < 0) return manifestTemplate(name, activityName, extractPermissions(source))
            val launcherMarkup = """
                <activity android:name="$activityName" android:exported="true">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            """.trimIndent()
            return content.substring(0, appClose) + "\n        " + launcherMarkup + "\n    " + content.substring(appClose)
        }

        val openingEnd = target.value.indexOf('>')
        if (openingEnd < 0) return manifestTemplate(name, activityName, extractPermissions(source))
        val opening = target.value.substring(0, openingEnd + 1)
        val exportedRegex = Regex("""(?is)android:exported\s*=\s*["'][^"']*["']""")
        val namedOpening = Regex("""(?is)android:name\s*=\s*["'][^"']*["']""")
        val withActivityName = if (namedOpening.containsMatchIn(opening)) {
            namedOpening.replace(opening, "android:name=\"$activityName\"")
        } else {
            opening.dropLast(1) + " android:name=\"$activityName\">"
        }
        val fixedOpening = if (exportedRegex.containsMatchIn(withActivityName)) {
            exportedRegex.replace(withActivityName, "android:exported=\"true\"")
        } else {
            withActivityName.dropLast(1) + " android:exported=\"true\">"
        }
        var fixedBlock = fixedOpening + target.value.substring(openingEnd + 1)
        if (launcherBlock == null) {
            val targetOpeningEnd = fixedBlock.indexOf('>')
            val filter = """
                <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.LAUNCHER" />
                </intent-filter>
            """.trimIndent()
            fixedBlock = fixedBlock.substring(0, targetOpeningEnd + 1) +
                "\n                    " + filter + "\n                    " +
                fixedBlock.substring(targetOpeningEnd + 1)
        }
        return content.replaceRange(target.range, fixedBlock)
    }

    private fun manifestTemplate(name: String, activityName: String, preservedPermissions: List<String>): String {
        val internetPermission = """<uses-permission android:name="android.permission.INTERNET" />"""
        val permissions = (preservedPermissions + internetPermission).distinct().joinToString("\n    ")
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                $permissions
                <application
                    android:allowBackup="false"
                    android:label="${escapeXml(name)}"
                    android:supportsRtl="true"
                    android:theme="@android:style/Theme.Material.Light.NoActionBar">
                    <activity
                        android:name="$activityName"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent() + "\n"
    }

    private fun defaultLauncherTemplate(name: String, description: String): String {
        val projectName = escapeString(name)
        val summary = escapeString(description.trim().take(240).ifBlank { "Aplicativo Android gerado com o Núcleo AI." })
        return """
            package io.github.nucleo.generated

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.layout.Arrangement
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Surface
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            class NucleoLauncherActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        MaterialTheme {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                GeneratedAppHome()
                            }
                        }
                    }
                }
            }

            @Composable
            private fun GeneratedAppHome() {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("$projectName", style = MaterialTheme.typography.headlineMedium)
                    Text("$summary", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Estrutura Android criada. A funcionalidade específica ainda precisa de implementação e validação.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        """.trimIndent() + "\n"
    }

    private fun readmeTemplate(name: String): String = """
        # $name

        Projeto Android nativo com Kotlin, Jetpack Compose, Gradle 8.13, Android Gradle Plugin 8.13.2 e SDK 36.

        ## Compilação

        O Núcleo AI pode compilar este projeto pelo fluxo **Compilar APK na nuvem**, usando um repositório GitHub privado. A compilação executa scripts Gradle do próprio projeto em um runner temporário; revise os arquivos-fonte antes de autorizar.

        ## Limites

        Os arquivos foram gerados e verificados estruturalmente. A aplicação só deve ser considerada compilada depois de um build real bem-sucedido; a presença destes arquivos, por si só, não comprova que as funcionalidades estão completas.
    """.trimIndent() + "\n"

    private fun escapeString(value: String): String = value
        .replace('\\', ' ')
        .replace('"', ' ')
        .replace('$', ' ')
        .replace('\r', ' ')
        .replace('\n', ' ')

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
