package io.github.astromg01.nucleo.core.lang

internal class NucleoParser(private val tokens: List<Token>) {
    private var current = 0
    private var recursiveExpressionDepth = 0

    fun parse(): List<NucleoStmt> {
        val statements = mutableListOf<NucleoStmt>()
        while (!isAtEnd()) statements += declaration()
        return statements
    }

    private fun declaration(): NucleoStmt = try {
        when {
            match(TokenType.LET) -> letDeclaration()
            match(TokenType.FUN) -> functionDeclaration()
            else -> statement()
        }
    } catch (error: NucleoLangException) {
        synchronize()
        throw error
    }

    private fun letDeclaration(): NucleoStmt {
        val name = consume(TokenType.IDENTIFIER, "esperado nome depois de 'let'")
        consume(TokenType.EQUAL, "esperado '=' depois do nome")
        val initializer = expression()
        consume(TokenType.SEMICOLON, "esperado ';' após declaração")
        return NucleoStmt.Let(name, initializer)
    }

    private fun functionDeclaration(): NucleoStmt {
        val name = consume(TokenType.IDENTIFIER, "esperado nome da função")
        consume(TokenType.LEFT_PAREN, "esperado '(' depois do nome da função")
        val parameters = mutableListOf<Token>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (parameters.size >= MAX_PARAMETERS) fail(peek(), "máximo de $MAX_PARAMETERS parâmetros por função")
                parameters += consume(TokenType.IDENTIFIER, "esperado nome do parâmetro")
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RIGHT_PAREN, "esperado ')' após os parâmetros")
        consume(TokenType.LEFT_BRACE, "esperado '{' antes do corpo da função")
        return NucleoStmt.Function(name, parameters, blockContents())
    }

    private fun statement(): NucleoStmt = when {
        match(TokenType.IF) -> ifStatement()
        match(TokenType.WHILE) -> whileStatement()
        match(TokenType.RETURN) -> returnStatement()
        match(TokenType.LEFT_BRACE) -> NucleoStmt.Block(blockContents())
        else -> expressionStatement()
    }

    private fun ifStatement(): NucleoStmt {
        consume(TokenType.LEFT_PAREN, "esperado '(' depois de 'if'")
        val condition = expression()
        consume(TokenType.RIGHT_PAREN, "esperado ')' depois da condição")
        val thenBranch = statement()
        val elseBranch = if (match(TokenType.ELSE)) statement() else null
        return NucleoStmt.If(condition, thenBranch, elseBranch)
    }

    private fun whileStatement(): NucleoStmt {
        consume(TokenType.LEFT_PAREN, "esperado '(' depois de 'while'")
        val condition = expression()
        consume(TokenType.RIGHT_PAREN, "esperado ')' depois da condição")
        return NucleoStmt.While(condition, statement())
    }

    private fun returnStatement(): NucleoStmt {
        val keyword = previous()
        val value = if (check(TokenType.SEMICOLON)) null else expression()
        consume(TokenType.SEMICOLON, "esperado ';' após return")
        return NucleoStmt.Return(keyword, value)
    }

    private fun blockContents(): List<NucleoStmt> {
        val statements = mutableListOf<NucleoStmt>()
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            statements += declaration()
        }
        consume(TokenType.RIGHT_BRACE, "esperado '}' para fechar bloco")
        return statements
    }

    private fun expressionStatement(): NucleoStmt {
        val expr = expression()
        consume(TokenType.SEMICOLON, "esperado ';' após expressão")
        return NucleoStmt.Expression(expr)
    }

    private fun expression(): NucleoExpr = assignment()

    private fun assignment(): NucleoExpr = guardedExpression { assignmentBody() }

    private fun assignmentBody(): NucleoExpr {
        val expr = or()
        if (match(TokenType.EQUAL)) {
            val equals = previous()
            val value = assignment()
            return when (expr) {
                is NucleoExpr.Variable -> NucleoExpr.Assign(expr.name, value)
                is NucleoExpr.IndexGet -> NucleoExpr.IndexSet(expr.target, expr.index, value, expr.closingBracket)
                else -> fail(equals, "destino de atribuição inválido")
            }
        }
        return expr
    }

    private fun or(): NucleoExpr {
        var expr = and()
        while (match(TokenType.OR_OR)) {
            val op = previous()
            expr = NucleoExpr.Logical(expr, op, and())
        }
        return expr
    }

    private fun and(): NucleoExpr {
        var expr = equality()
        while (match(TokenType.AND_AND)) {
            val op = previous()
            expr = NucleoExpr.Logical(expr, op, equality())
        }
        return expr
    }

    private fun equality(): NucleoExpr {
        var expr = comparison()
        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            val op = previous()
            expr = NucleoExpr.Binary(expr, op, comparison())
        }
        return expr
    }

    private fun comparison(): NucleoExpr {
        var expr = term()
        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            val op = previous()
            expr = NucleoExpr.Binary(expr, op, term())
        }
        return expr
    }

    private fun term(): NucleoExpr {
        var expr = factor()
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val op = previous()
            expr = NucleoExpr.Binary(expr, op, factor())
        }
        return expr
    }

    private fun factor(): NucleoExpr {
        var expr = unary()
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            val op = previous()
            expr = NucleoExpr.Binary(expr, op, unary())
        }
        return expr
    }

    private fun unary(): NucleoExpr = guardedExpression {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            val operator = previous()
            NucleoExpr.Unary(operator, unary())
        } else call()
    }

    private inline fun <T> guardedExpression(block: () -> T): T {
        recursiveExpressionDepth++
        try {
            if (recursiveExpressionDepth > MAX_RECURSIVE_EXPRESSION_DEPTH) {
                fail(peek(), "expressão excede a profundidade máxima de $MAX_RECURSIVE_EXPRESSION_DEPTH")
            }
            return block()
        } finally {
            recursiveExpressionDepth--
        }
    }

    private fun call(): NucleoExpr {
        var expr = primary()
        while (true) {
            when {
                match(TokenType.LEFT_PAREN) -> {
                    val arguments = mutableListOf<NucleoExpr>()
                    if (!check(TokenType.RIGHT_PAREN)) {
                        do {
                            if (arguments.size >= MAX_ARGUMENTS) fail(peek(), "máximo de $MAX_ARGUMENTS argumentos por chamada")
                            arguments += expression()
                        } while (match(TokenType.COMMA))
                    }
                    val closing = consume(TokenType.RIGHT_PAREN, "esperado ')' após argumentos")
                    expr = NucleoExpr.Call(expr, closing, arguments)
                }
                match(TokenType.LEFT_BRACKET) -> {
                    val index = expression()
                    val closing = consume(TokenType.RIGHT_BRACKET, "esperado ']' após índice")
                    expr = NucleoExpr.IndexGet(expr, index, closing)
                }
                else -> return expr
            }
        }
    }

    private fun primary(): NucleoExpr = when {
        match(TokenType.FALSE) -> NucleoExpr.Literal(false)
        match(TokenType.TRUE) -> NucleoExpr.Literal(true)
        match(TokenType.NULL) -> NucleoExpr.Literal(null)
        match(TokenType.NUMBER, TokenType.STRING) -> NucleoExpr.Literal(previous().literal)
        match(TokenType.IDENTIFIER) -> NucleoExpr.Variable(previous())
        match(TokenType.LEFT_BRACKET) -> listLiteral()
        match(TokenType.LEFT_PAREN) -> {
            val expr = expression()
            consume(TokenType.RIGHT_PAREN, "esperado ')' após expressão")
            expr
        }
        else -> fail(peek(), "esperada expressão")
    }


    private fun listLiteral(): NucleoExpr {
        val elements = mutableListOf<NucleoExpr>()
        if (!check(TokenType.RIGHT_BRACKET)) {
            do {
                if (elements.size >= MAX_LIST_LITERAL_ELEMENTS) {
                    fail(peek(), "lista literal excede $MAX_LIST_LITERAL_ELEMENTS elementos")
                }
                elements += expression()
            } while (match(TokenType.COMMA) && !check(TokenType.RIGHT_BRACKET))
        }
        consume(TokenType.RIGHT_BRACKET, "esperado ']' após elementos da lista")
        return NucleoExpr.ListLiteral(elements)
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) if (check(type)) { advance(); return true }
        return false
    }

    private fun consume(type: TokenType, message: String): Token =
        if (check(type)) advance() else fail(peek(), message)

    private fun check(type: TokenType): Boolean = if (isAtEnd()) type == TokenType.EOF else peek().type == type
    private fun advance(): Token { if (!isAtEnd()) current++; return previous() }
    private fun isAtEnd() = peek().type == TokenType.EOF
    private fun peek() = tokens[current]
    private fun previous() = tokens[current - 1]

    private fun synchronize() {
        if (isAtEnd()) return
        advance()
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) return
            if (peek().type in setOf(TokenType.LET, TokenType.FUN, TokenType.IF, TokenType.WHILE, TokenType.RETURN)) return
            advance()
        }
    }

    private fun fail(token: Token, message: String): Nothing =
        throw NucleoLangException(message, token.line, token.column)

    companion object {
        const val MAX_PARAMETERS = 32
        const val MAX_ARGUMENTS = 32
        const val MAX_LIST_LITERAL_ELEMENTS = 10_000
        const val MAX_RECURSIVE_EXPRESSION_DEPTH = 128
    }
}
