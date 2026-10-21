package io.github.astromg01.nucleo.core.lang

/** Token types for the first, deliberately small NúcleoLang grammar. */
internal enum class TokenType {
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE, LEFT_BRACKET, RIGHT_BRACKET, COMMA, SEMICOLON,
    PLUS, MINUS, STAR, SLASH, PERCENT,
    BANG, BANG_EQUAL, EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,
    AND_AND, OR_OR,
    IDENTIFIER, STRING, NUMBER,
    LET, FUN, RETURN, IF, ELSE, WHILE, TRUE, FALSE, NULL,
    EOF,
}

internal data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any? = null,
    val line: Int,
    val column: Int,
)

class NucleoLangException(message: String, val line: Int? = null, val column: Int? = null) :
    IllegalArgumentException(buildString {
        append("NúcleoLang: ")
        if (line != null) {
            append("linha ").append(line)
            if (column != null) append(", coluna ").append(column)
            append(" — ")
        }
        append(message)
    })

internal class NucleoLexer(private val source: String) {
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var line = 1
    private var column = 1
    private var tokenLine = 1
    private var tokenColumn = 1
    private var nestingDepth = 0

    fun scanTokens(): List<Token> {
        if (source.length > MAX_SOURCE_CHARS) {
            throw NucleoLangException("fonte excede o limite de $MAX_SOURCE_CHARS caracteres")
        }
        while (!isAtEnd()) {
            start = current
            tokenLine = line
            tokenColumn = column
            scanToken()
            if (tokens.size > MAX_TOKENS) {
                throw NucleoLangException("programa excede o limite de $MAX_TOKENS tokens", line, column)
            }
        }
        tokens += Token(TokenType.EOF, "", line = line, column = column)
        return tokens
    }

    private fun scanToken() {
        when (val c = advance()) {
            '(' -> openNesting(TokenType.LEFT_PAREN)
            ')' -> closeNesting(TokenType.RIGHT_PAREN)
            '{' -> openNesting(TokenType.LEFT_BRACE)
            '}' -> closeNesting(TokenType.RIGHT_BRACE)
            '[' -> openNesting(TokenType.LEFT_BRACKET)
            ']' -> closeNesting(TokenType.RIGHT_BRACKET)
            ',' -> add(TokenType.COMMA)
            ';' -> add(TokenType.SEMICOLON)
            '+' -> add(TokenType.PLUS)
            '-' -> add(TokenType.MINUS)
            '*' -> add(TokenType.STAR)
            '%' -> add(TokenType.PERCENT)
            '!' -> add(if (match('=')) TokenType.BANG_EQUAL else TokenType.BANG)
            '=' -> add(if (match('=')) TokenType.EQUAL_EQUAL else TokenType.EQUAL)
            '<' -> add(if (match('=')) TokenType.LESS_EQUAL else TokenType.LESS)
            '>' -> add(if (match('=')) TokenType.GREATER_EQUAL else TokenType.GREATER)
            '&' -> if (match('&')) add(TokenType.AND_AND) else fail("use && para conjunção")
            '|' -> if (match('|')) add(TokenType.OR_OR) else fail("use || para disjunção")
            '/' -> if (match('/')) {
                while (peek() != '\n' && !isAtEnd()) advance()
            } else add(TokenType.SLASH)
            ' ', '\r', '\t' -> Unit
            '\n' -> { line++; column = 1 }
            '"' -> string()
            else -> when {
                c.isDigit() -> number()
                c.isLetter() || c == '_' -> identifier()
                else -> fail("caractere inesperado '$c'")
            }
        }
    }

    private fun string() {
        val value = StringBuilder()
        while (!isAtEnd() && peek() != '"') {
            val c = advance()
            if (c == '\n') {
                line++
                column = 1
                value.append('\n')
                continue
            }
            if (c == '\\') {
                if (isAtEnd()) fail("escape incompleto em texto")
                value.append(when (val escaped = advance()) {
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    '"' -> '"'
                    '\\' -> '\\'
                    else -> throw NucleoLangException("escape não suportado '\\$escaped'", tokenLine, tokenColumn)
                })
            } else value.append(c)
        }
        if (isAtEnd()) fail("texto não terminado")
        advance() // closing quote
        add(TokenType.STRING, value.toString())
    }

    private fun number() {
        while (peek().isDigit()) advance()
        if (peek() == '.' && peekNext().isDigit()) {
            advance()
            while (peek().isDigit()) advance()
        }
        val value = source.substring(start, current).toDoubleOrNull()
            ?: fail("número inválido")
        if (!value.isFinite()) fail("número fora do intervalo permitido")
        add(TokenType.NUMBER, value)
    }

    private fun identifier() {
        while (peek().isLetterOrDigit() || peek() == '_') advance()
        val text = source.substring(start, current)
        val type = when (text) {
            "let" -> TokenType.LET
            "fun" -> TokenType.FUN
            "return" -> TokenType.RETURN
            "if" -> TokenType.IF
            "else" -> TokenType.ELSE
            "while" -> TokenType.WHILE
            "true" -> TokenType.TRUE
            "false" -> TokenType.FALSE
            "null" -> TokenType.NULL
            else -> TokenType.IDENTIFIER
        }
        add(type)
    }

    private fun openNesting(type: TokenType) {
        nestingDepth++
        if (nestingDepth > MAX_NESTING_DEPTH) fail("aninhamento sintático excede $MAX_NESTING_DEPTH níveis")
        add(type)
    }

    private fun closeNesting(type: TokenType) {
        if (nestingDepth > 0) nestingDepth--
        add(type)
    }

    private fun add(type: TokenType, literal: Any? = null) {
        tokens += Token(type, source.substring(start, current), literal, tokenLine, tokenColumn)
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd() || source[current] != expected) return false
        advance()
        return true
    }

    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[current]
    private fun peekNext(): Char = if (current + 1 >= source.length) '\u0000' else source[current + 1]
    private fun isAtEnd(): Boolean = current >= source.length

    private fun advance(): Char {
        val c = source[current++]
        column++
        return c
    }

    private fun fail(message: String): Nothing = throw NucleoLangException(message, tokenLine, tokenColumn)

    companion object {
        const val MAX_SOURCE_CHARS = 256_000
        const val MAX_TOKENS = 50_000
        const val MAX_NESTING_DEPTH = 128
    }
}
