package io.github.astromg01.nucleo.domain.project

import java.io.StringReader
import java.util.ArrayDeque
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.InputSource

/**
 * Static checks for generated files before remote compilation. A clean report is not proof that
 * source compiles; the remote Gradle build remains the authoritative compiler check.
 */
object ProjectGenerationValidator {
    fun validate(draft: ProjectDraft): List<String> {
        val issues = linkedSetOf<String>()
        try {
            ProjectFactory().validate(draft)
        } catch (error: ProjectDraftValidationException) {
            issues += error.problems
        } catch (error: Exception) {
            issues += (error.message ?: "O projeto não passou na validação estrutural.")
        }
        if (issues.isNotEmpty()) return issues.toList()

        draft.files.forEach { file ->
            val extension = file.path.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (file.content.isBlank()) {
                issues += "O arquivo " + file.path + " está vazio; a geração não retornou o conteúdo completo."
            }
            if (extension in SOURCE_EXTENSIONS) {
                balancedDelimiterIssue(file.path, file.content, extension)?.let(issues::add)
                if (file.content.contains(PLACEHOLDER_IMPLEMENTATION_MARKER, ignoreCase = true)) {
                    issues += "O arquivo " + file.path +
                        " ainda contém a tela Android de contingência; a funcionalidade solicitada não está ligada à interface principal."
                }
            }
            when (extension) {
                "json" -> if (!isValidJson(file.content)) {
                    issues += "O arquivo " + file.path + " não contém JSON válido."
                }
                "xml" -> if (!isValidXml(file.content)) {
                    issues += "O XML " + file.path + " está malformado ou incompleto."
                }
            }
        }

        if (draft.profile == ProjectProfile.ANDROID_APP) {
            validateAndroidLauncher(draft)?.let(issues::add)
        }
        return issues.toList()
    }

    private fun validateAndroidLauncher(draft: ProjectDraft): String? {
        val manifest = draft.files.firstOrNull {
            it.path.equals("app/src/main/AndroidManifest.xml", ignoreCase = true)
        }?.content ?: return "O manifesto Android está ausente."

        val launcherBlock = Regex("""(?is)<activity\b[^>]*>.*?</activity\s*>""")
            .findAll(manifest)
            .firstOrNull { block ->
                block.value.contains("android.intent.action.MAIN") &&
                    block.value.contains("android.intent.category.LAUNCHER")
            }?.value ?: return "O Manifest não declara uma Activity com MAIN/LAUNCHER."

        val declaredName = Regex("""(?is)android:name\s*=\s*["']([^"']+)["']""")
            .find(launcherBlock.substringBefore(">"))?.groupValues?.get(1)
            ?: return "A Activity principal do Manifest não tem android:name."
        val manifestPackage = Regex("""(?is)<manifest\b[^>]*\bpackage\s*=\s*["']([^"']+)["']""")
            .find(manifest)?.groupValues?.get(1).orEmpty()
        // AGP 8 projects commonly declare namespace in Gradle rather than the Manifest.
        // Use it to resolve relative launcher names before comparing against source packages.
        val gradleNamespace = draft.files.firstOrNull {
            it.path.equals("app/build.gradle.kts", ignoreCase = true) ||
                it.path.equals("app/build.gradle", ignoreCase = true)
        }?.content?.let { buildFile ->
            Regex("""(?m)\bnamespace\s*(?:=\s*)?["']([^"']+)["']""")
                .find(buildFile)?.groupValues?.get(1)
        }.orEmpty()
        val packageForResolution = manifestPackage.ifBlank { gradleNamespace }
        val qualifiedName = when {
            declaredName.startsWith(".") && packageForResolution.isNotBlank() -> packageForResolution + declaredName
            '.' !in declaredName && packageForResolution.isNotBlank() -> packageForResolution + "." + declaredName
            else -> declaredName
        }

        val kotlinPackage = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)""")
        val javaPackage = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)\s*;""")
        val kotlinActivity = Regex(
            """\bclass\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?:\([^)]*\))?\s*:\s*(?:androidx\.activity\.ComponentActivity|androidx\.appcompat\.app\.AppCompatActivity|android\.app\.Activity|ComponentActivity|AppCompatActivity|Activity)\s*\(""",
        )
        val javaActivity = Regex(
            """\bclass\s+([A-Za-z_][A-Za-z0-9_]*)\s+extends\s+(?:[A-Za-z_][A-Za-z0-9_]*\.)*(?:Activity|AppCompatActivity|ComponentActivity)\b""",
        )
        val activities = draft.files.mapNotNull { file ->
            val lower = file.path.lowercase(Locale.ROOT)
            if (!lower.startsWith("app/src/main/") || !(lower.endsWith(".kt") || lower.endsWith(".java"))) {
                return@mapNotNull null
            }
            val packageMatch = if (lower.endsWith(".kt")) kotlinPackage.find(file.content) else javaPackage.find(file.content)
            val activityMatch = if (lower.endsWith(".kt")) kotlinActivity.find(file.content) else javaActivity.find(file.content)
            if (packageMatch == null || activityMatch == null) null
            else packageMatch.groupValues[1] + "." + activityMatch.groupValues[1]
        }.toSet()

        val matchesActivity = qualifiedName in activities || activities.any { activity ->
            activity == declaredName ||
                (declaredName.startsWith(".") && activity.endsWith(declaredName)) ||
                (!declaredName.contains('.') && activity.substringAfterLast('.') == declaredName)
        }
        return if (matchesActivity) null else {
            "O Manifest aponta para " + declaredName +
                ", mas não foi encontrada uma Activity compatível em app/src/main/. Corrija o nome/package ou complete a Activity."
        }
    }

    private fun balancedDelimiterIssue(path: String, content: String, extension: String): String? {
        // Python has '#' comments and indentation rules that need a Python-aware parser; avoid false
        // positives from a language-agnostic bracket scanner and validate its structure separately.
        if (extension == "py") return null

        val stack = ArrayDeque<Pair<Char, Int>>()
        var state = ScanState.NORMAL
        var escaped = false
        var index = 0
        val hasTemplateLiteral = extension in TEMPLATE_STRING_EXTENSIONS

        while (index < content.length) {
            val current = content[index]
            val next = content.getOrNull(index + 1)
            when (state) {
                ScanState.NORMAL -> {
                    if (current == '/' && next == '/' && extension != "css") {
                        state = ScanState.LINE_COMMENT
                        index += 2
                        continue
                    }
                    if (current == '/' && next == '*') {
                        state = ScanState.BLOCK_COMMENT
                        index += 2
                        continue
                    }
                    if (content.startsWith("\"\"\"", index)) {
                        state = ScanState.TRIPLE_DOUBLE
                        index += 3
                        continue
                    }
                    if (content.startsWith("'''", index)) {
                        state = ScanState.TRIPLE_SINGLE
                        index += 3
                        continue
                    }
                    when (current) {
                        '"' -> state = ScanState.DOUBLE_STRING
                        '\'' -> state = ScanState.SINGLE_STRING
                        '\u0060' -> if (hasTemplateLiteral) state = ScanState.TEMPLATE_STRING
                        '(', '[', '{' -> stack.addLast(current to index)
                        ')', ']', '}' -> {
                            val expectedOpen = when (current) {
                                ')' -> '('
                                ']' -> '['
                                else -> '{'
                            }
                            val open = stack.pollLast()
                            if (open == null || open.first != expectedOpen) {
                                return "O arquivo " + path + " tem delimitadores incompatíveis perto do caractere " + (index + 1) + "."
                            }
                        }
                    }
                }
                ScanState.LINE_COMMENT -> if (current == '\n') state = ScanState.NORMAL
                ScanState.BLOCK_COMMENT -> if (current == '*' && next == '/') {
                    state = ScanState.NORMAL
                    index += 2
                    continue
                }
                ScanState.DOUBLE_STRING, ScanState.SINGLE_STRING, ScanState.TEMPLATE_STRING -> {
                    if (escaped) {
                        escaped = false
                    } else if (current == '\\') {
                        escaped = true
                    } else if (
                        (state == ScanState.DOUBLE_STRING && current == '"') ||
                        (state == ScanState.SINGLE_STRING && current == '\'') ||
                        (state == ScanState.TEMPLATE_STRING && current == '\u0060')
                    ) {
                        state = ScanState.NORMAL
                    }
                }
                ScanState.TRIPLE_DOUBLE -> if (content.startsWith("\"\"\"", index)) {
                    state = ScanState.NORMAL
                    index += 3
                    continue
                }
                ScanState.TRIPLE_SINGLE -> if (content.startsWith("'''", index)) {
                    state = ScanState.NORMAL
                    index += 3
                    continue
                }
            }
            index++
        }

        if (state == ScanState.BLOCK_COMMENT) return "O comentário de bloco em " + path + " não foi fechado."
        if (state in INCOMPLETE_STRING_STATES) return "Uma string em " + path + " não foi fechada."
        if (stack.isNotEmpty()) return "O arquivo " + path + " tem um delimitador sem fechamento."
        return null
    }

    private fun isValidJson(content: String): Boolean = runCatching {
        JSONObject(content)
    }.isSuccess || runCatching {
        JSONArray(content)
    }.isSuccess

    private fun isValidXml(content: String): Boolean {
        // Generated XML does not need DTDs/entities. Reject them before parsing so that a
        // platform XML parser that lacks a particular hardening flag cannot enable XXE.
        if (Regex("""(?is)<!\s*(DOCTYPE|ENTITY)\b""").containsMatchIn(content)) return false
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // Android and JVM XML parser implementations differ. Apply each hardening
                // option independently; one unsupported Xerces feature must not invalidate
                // every otherwise-valid AndroidManifest.xml on a device.
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { isXIncludeAware = false }
                runCatching { isExpandEntityReferences = false }
            }
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            builder.parse(InputSource(StringReader(content)))
            true
        }.getOrDefault(false)
    }

    private enum class ScanState {
        NORMAL,
        LINE_COMMENT,
        BLOCK_COMMENT,
        DOUBLE_STRING,
        SINGLE_STRING,
        TRIPLE_DOUBLE,
        TRIPLE_SINGLE,
        TEMPLATE_STRING,
    }

    private const val PLACEHOLDER_IMPLEMENTATION_MARKER = "A funcionalidade específica ainda precisa de implementação"
    private val SOURCE_EXTENSIONS = setOf("kt", "java", "kts", "gradle", "js", "jsx", "ts", "tsx", "py", "css")
    private val TEMPLATE_STRING_EXTENSIONS = setOf("js", "jsx", "ts", "tsx")
    private val INCOMPLETE_STRING_STATES = setOf(
        ScanState.DOUBLE_STRING,
        ScanState.SINGLE_STRING,
        ScanState.TRIPLE_DOUBLE,
        ScanState.TRIPLE_SINGLE,
        ScanState.TEMPLATE_STRING,
    )
}