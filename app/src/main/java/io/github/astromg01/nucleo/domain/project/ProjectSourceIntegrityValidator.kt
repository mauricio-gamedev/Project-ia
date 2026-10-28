package io.github.astromg01.nucleo.domain.project

import java.util.Locale

/**
 * A fast preflight for obvious model truncation before generated files are staged or built.
 * This is intentionally not a compiler and never marks source as fully correct.
 */
object ProjectSourceIntegrityValidator {
    fun inspect(draft: ProjectDraft): List<String> {
        val issues = mutableListOf<String>()
        draft.files.forEach { file ->
            val extension = file.path.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (extension !in SOURCE_EXTENSIONS || file.content.isBlank()) return@forEach
            findDelimiterProblem(file.content, extension)?.let { problem ->
                issues += "\${file.path}: $problem"
            }
        }
        return issues.take(MAX_ISSUES)
    }

    private fun findDelimiterProblem(source: String, extension: String): String? {
        val stack = mutableListOf<Pair<Char, Int>>()
        var index = 0
        var line = 1
        var state = LexicalState.CODE
        var quote = '\u0000'
        var tripleQuoted = false
        var escaped = false
        var blockCommentDepth = 0

        while (index < source.length) {
            val ch = source[index]
            val next = source.getOrNull(index + 1)
            when (state) {
                LexicalState.CODE -> {
                    if (ch == '\n') line++
                    if (ch == '/' && next == '/') {
                        state = LexicalState.LINE_COMMENT
                        index += 2
                        continue
                    }
                    if (ch == '/' && next == '*') {
                        state = LexicalState.BLOCK_COMMENT
                        blockCommentDepth = 1
                        index += 2
                        continue
                    }
                    if (extension == "py" && ch == '#') {
                        state = LexicalState.LINE_COMMENT
                        index++
                        continue
                    }
                    if (ch == '"' && source.startsWith("\"\"\"", index) &&
                        extension in TRIPLE_QUOTE_EXTENSIONS
                    ) {
                        state = LexicalState.STRING
                        quote = '"'
                        tripleQuoted = true
                        escaped = false
                        index += 3
                        continue
                    }
                    if (ch == '"' || ch == '\'' || (ch.code == 96 && extension in TEMPLATE_STRING_EXTENSIONS)) {
                        state = LexicalState.STRING
                        quote = ch
                        tripleQuoted = false
                        escaped = false
                        index++
                        continue
                    }
                    when (ch) {
                        '(', '[', '{' -> stack += ch to line
                        ')', ']', '}' -> {
                            val expected = when (ch) {
                                ')' -> '('
                                ']' -> '['
                                else -> '{'
                            }
                            val opening = stack.lastOrNull()
                            if (opening == null || opening.first != expected) {
                                return "delimitador de fechamento '$ch' sem abertura correspondente (linha $line)"
                            }
                            stack.removeAt(stack.lastIndex)
                        }
                    }
                    index++
                }

                LexicalState.LINE_COMMENT -> {
                    if (ch == '\n') {
                        line++
                        state = LexicalState.CODE
                    }
                    index++
                }

                LexicalState.BLOCK_COMMENT -> {
                    if (ch == '/' && next == '*') {
                        blockCommentDepth++
                        index += 2
                    } else if (ch == '*' && next == '/') {
                        blockCommentDepth--
                        index += 2
                        if (blockCommentDepth == 0) state = LexicalState.CODE
                    } else {
                        if (ch == '\n') line++
                        index++
                    }
                }

                LexicalState.STRING -> {
                    if (ch == '\n') line++
                    if (tripleQuoted) {
                        if (source.startsWith("\"\"\"", index)) {
                            state = LexicalState.CODE
                            tripleQuoted = false
                            index += 3
                        } else {
                            index++
                        }
                    } else if (escaped) {
                        escaped = false
                        index++
                    } else if (ch == '\\') {
                        escaped = true
                        index++
                    } else if (ch == quote) {
                        state = LexicalState.CODE
                        index++
                    } else {
                        index++
                    }
                }
            }
        }

        if (state == LexicalState.STRING) return "string literal incompleta no fim do arquivo"
        if (state == LexicalState.BLOCK_COMMENT) return "comentário de bloco incompleto no fim do arquivo"
        val unclosed = stack.lastOrNull()
        if (unclosed != null) {
            return "delimitador '\${unclosed.first}' aberto na linha \${unclosed.second} não foi fechado"
        }
        return null
    }

    private enum class LexicalState { CODE, LINE_COMMENT, BLOCK_COMMENT, STRING }

    private const val MAX_ISSUES = 20
    private val SOURCE_EXTENSIONS = setOf("kt", "kts", "java", "gradle", "json", "js", "ts", "py")
    private val TRIPLE_QUOTE_EXTENSIONS = setOf("kt", "kts", "java", "gradle")
    private val TEMPLATE_STRING_EXTENSIONS = setOf("js", "ts")
}