package io.github.astromg01.nucleo.core.lang

internal sealed interface NucleoStmt {
    data class Let(val name: Token, val initializer: NucleoExpr) : NucleoStmt
    data class Expression(val expression: NucleoExpr) : NucleoStmt
    data class Block(val statements: List<NucleoStmt>) : NucleoStmt
    data class If(val condition: NucleoExpr, val thenBranch: NucleoStmt, val elseBranch: NucleoStmt?) : NucleoStmt
    data class While(val condition: NucleoExpr, val body: NucleoStmt) : NucleoStmt
    data class Function(val name: Token, val parameters: List<Token>, val body: List<NucleoStmt>) : NucleoStmt
    data class Return(val keyword: Token, val value: NucleoExpr?) : NucleoStmt
}

internal sealed interface NucleoExpr {
    data class Literal(val value: Any?) : NucleoExpr
    data class Variable(val name: Token) : NucleoExpr
    data class Assign(val name: Token, val value: NucleoExpr) : NucleoExpr
    data class Unary(val operator: Token, val right: NucleoExpr) : NucleoExpr
    data class Binary(val left: NucleoExpr, val operator: Token, val right: NucleoExpr) : NucleoExpr
    data class Logical(val left: NucleoExpr, val operator: Token, val right: NucleoExpr) : NucleoExpr
    data class Call(val callee: NucleoExpr, val closingParen: Token, val arguments: List<NucleoExpr>) : NucleoExpr
    data class ListLiteral(val elements: List<NucleoExpr>) : NucleoExpr
    data class IndexGet(val target: NucleoExpr, val index: NucleoExpr, val closingBracket: Token) : NucleoExpr
    data class IndexSet(
        val target: NucleoExpr,
        val index: NucleoExpr,
        val value: NucleoExpr,
        val closingBracket: Token,
    ) : NucleoExpr
}
