package io.github.astromg01.nucleo.core.lang

/**
 * Small, deterministic interpreter for the first NúcleoLang release.
 *
 * It intentionally exposes no filesystem, shell, reflection, network, or dynamic class loading.
 * Side effects are limited to the injected output sink until explicit capability APIs are added.
 */
class NucleoInterpreter(
    private val stepLimit: Int = DEFAULT_STEP_LIMIT,
    private val maxCallDepth: Int = DEFAULT_MAX_CALL_DEPTH,
    private val outputSink: (String) -> Unit = {},
    hostFunctions: List<NucleoHostFunction> = emptyList(),
    private val maxCollectionSize: Int = DEFAULT_MAX_COLLECTION_SIZE,
) {
    private var steps = 0
    private var callDepth = 0
    private val globals = Environment()
    private var environment = globals
    private val capturedOutput = mutableListOf<String>()

    init {
        require(stepLimit in 1..HARD_MAX_STEP_LIMIT) { "stepLimit deve ficar entre 1 e $HARD_MAX_STEP_LIMIT" }
        require(maxCallDepth in 1..HARD_MAX_CALL_DEPTH) { "maxCallDepth deve ficar entre 1 e $HARD_MAX_CALL_DEPTH" }
        require(maxCollectionSize in 1..HARD_MAX_COLLECTION_SIZE) {
            "maxCollectionSize deve ficar entre 1 e $HARD_MAX_COLLECTION_SIZE"
        }
        registerBuiltin("print", 1, 1) { args ->
            val line = stringify(args[0])
            capturedOutput += line
            outputSink(line)
            null
        }
        registerBuiltin("len", 1, 1) { args ->
            val value = args[0]
            val size = when (value) {
                is String -> value.length
                is List<*> -> value.size
                else -> throw NucleoLangException("len exige texto ou lista; recebido ${typeName(value)}")
            }
            size.toDouble()
        }
        registerBuiltin("push", 2, 2) { args ->
            val list = requireMutableList(args[0], "push")
            if (list.size >= maxCollectionSize) {
                throw NucleoLangException("lista atingiu o limite configurado de $maxCollectionSize elementos")
            }
            try {
                list.add(args[1])
            } catch (_: UnsupportedOperationException) {
                throw NucleoLangException("push não pode alterar uma lista imutável retornada por função externa")
            }
            list.size.toDouble()
        }
        registerBuiltin("pop", 1, 1) { args ->
            val list = requireMutableList(args[0], "pop")
            if (list.isEmpty()) throw NucleoLangException("pop não pode remover de uma lista vazia")
            try {
                list.removeAt(list.lastIndex)
            } catch (_: UnsupportedOperationException) {
                throw NucleoLangException("pop não pode alterar uma lista imutável retornada por função externa")
            }
        }
        registerBuiltin("contains", 2, 2) { args ->
            val collection = args[0]
            when (collection) {
                is List<*> -> collection.contains(args[1])
                is String -> (args[1] as? String)?.let(collection::contains)
                    ?: throw NucleoLangException("contains em texto exige um segundo argumento de texto")
                else -> throw NucleoLangException("contains exige lista ou texto; recebido ${typeName(collection)}")
            }
        }
        val builtinNames = setOf("print", "len", "push", "pop", "contains")
        val seenNames = builtinNames.toMutableSet()
        hostFunctions.forEach { hostFunction ->
            if (!seenNames.add(hostFunction.name)) {
                throw IllegalArgumentException("Função nativa duplicada ou reservada: '${hostFunction.name}'")
            }
            registerBuiltin(hostFunction.name, hostFunction.minArity, hostFunction.maxArity) { args ->
                val result = try {
                    hostFunction.implementation(args)
                } catch (error: NucleoLangException) {
                    throw error
                } catch (error: Exception) {
                    throw NucleoLangException("falha na capacidade '${hostFunction.name}': ${error.message ?: "erro interno"}")
                }
                validateHostResult(result)
                result
            }
        }
    }

    /** Execute one independent source program. Each interpreter instance retains its global scope. */
    fun execute(source: String): NucleoExecutionResult {
        steps = 0
        capturedOutput.clear()
        val statements = NucleoParser(NucleoLexer(source).scanTokens()).parse()
        try {
            statements.forEach(::executeStatement)
        } catch (_: ReturnSignal) {
            throw NucleoLangException("'return' só pode ser usado dentro de uma função")
        }
        return NucleoExecutionResult(capturedOutput.toList(), steps)
    }

    private fun executeStatement(statement: NucleoStmt) {
        tick()
        when (statement) {
            is NucleoStmt.Let -> environment.define(statement.name, evaluate(statement.initializer))
            is NucleoStmt.Expression -> evaluate(statement.expression)
            is NucleoStmt.Block -> executeBlock(statement.statements, Environment(environment))
            is NucleoStmt.If -> {
                if (requireBoolean(evaluate(statement.condition), "condição de if")) executeStatement(statement.thenBranch)
                else statement.elseBranch?.let(::executeStatement)
            }
            is NucleoStmt.While -> while (requireBoolean(evaluate(statement.condition), "condição de while")) executeStatement(statement.body)
            is NucleoStmt.Function -> environment.define(statement.name, UserFunction(statement.parameters, statement.body, environment))
            is NucleoStmt.Return -> throw ReturnSignal(statement.value?.let(::evaluate))
        }
    }

    private fun executeBlock(statements: List<NucleoStmt>, blockEnvironment: Environment) {
        val previous = environment
        try {
            environment = blockEnvironment
            statements.forEach(::executeStatement)
        } finally {
            environment = previous
        }
    }

    private fun evaluate(expr: NucleoExpr): Any? {
        tick()
        return when (expr) {
            is NucleoExpr.Literal -> expr.value
            is NucleoExpr.Variable -> environment.get(expr.name)
            is NucleoExpr.Assign -> evaluate(expr.value).also { environment.assign(expr.name, it) }
            is NucleoExpr.Unary -> {
                val right = evaluate(expr.right)
                when (expr.operator.type) {
                    TokenType.BANG -> !requireBoolean(right, "operador '!'")
                    TokenType.MINUS -> -requireNumber(right, "operador unário '-'")
                    else -> throw NucleoLangException("operador unário inesperado '${expr.operator.lexeme}'", expr.operator.line, expr.operator.column)
                }
            }
            is NucleoExpr.Binary -> evaluateBinary(expr)
            is NucleoExpr.Logical -> {
                val left = evaluate(expr.left)
                when (expr.operator.type) {
                    TokenType.AND_AND -> if (!requireBoolean(left, "operador '&&'")) false else requireBoolean(evaluate(expr.right), "operador '&&'")
                    TokenType.OR_OR -> if (requireBoolean(left, "operador '||'")) true else requireBoolean(evaluate(expr.right), "operador '||'")
                    else -> throw NucleoLangException("operador lógico inválido", expr.operator.line, expr.operator.column)
                }
            }
            is NucleoExpr.Call -> {
                val callee = evaluate(expr.callee)
                val arguments = expr.arguments.map(::evaluate)
                invoke(callee, arguments, expr.closingParen)
            }
            is NucleoExpr.ListLiteral -> {
                if (expr.elements.size > maxCollectionSize) {
                    throw NucleoLangException("lista literal excede o limite configurado de $maxCollectionSize elementos")
                }
                expr.elements.mapTo(mutableListOf(), ::evaluate)
            }
            is NucleoExpr.IndexGet -> {
                val target = evaluate(expr.target)
                val index = requireIndex(evaluate(expr.index), expr.closingBracket)
                val list = target as? List<*> ?: runtimeError(expr.closingBracket, "indexação exige uma lista")
                if (index !in list.indices) runtimeError(expr.closingBracket, "índice $index fora dos limites da lista (${list.size} elementos)")
                list[index]
            }
            is NucleoExpr.IndexSet -> {
                val target = evaluate(expr.target)
                val index = requireIndex(evaluate(expr.index), expr.closingBracket)
                val candidate = target as? MutableList<*> ?: runtimeError(expr.closingBracket, "atribuição por índice exige uma lista mutável")
                @Suppress("UNCHECKED_CAST")
                val list = candidate as MutableList<Any?>
                if (index !in list.indices) runtimeError(expr.closingBracket, "índice $index fora dos limites da lista (${list.size} elementos)")
                val value = evaluate(expr.value)
                try {
                    list[index] = value
                } catch (error: UnsupportedOperationException) {
                    runtimeError(expr.closingBracket, "a lista retornada por uma função externa não permite alteração")
                }
                value
            }
        }
    }

    private fun evaluateBinary(expr: NucleoExpr.Binary): Any? {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)
        return when (expr.operator.type) {
            TokenType.EQUAL_EQUAL -> left == right
            TokenType.BANG_EQUAL -> left != right
            TokenType.GREATER -> requireNumber(left, "'>'") > requireNumber(right, "'>'")
            TokenType.GREATER_EQUAL -> requireNumber(left, "'>='") >= requireNumber(right, "'>='")
            TokenType.LESS -> requireNumber(left, "'<'") < requireNumber(right, "'<'")
            TokenType.LESS_EQUAL -> requireNumber(left, "'<='") <= requireNumber(right, "'<='")
            TokenType.MINUS -> checkedNumber(requireNumber(left, "'-'") - requireNumber(right, "'-'"), expr.operator)
            TokenType.STAR -> checkedNumber(requireNumber(left, "'*'") * requireNumber(right, "'*'"), expr.operator)
            TokenType.SLASH -> {
                val divisor = requireNumber(right, "'/'")
                if (divisor == 0.0) runtimeError(expr.operator, "divisão por zero")
                checkedNumber(requireNumber(left, "'/'") / divisor, expr.operator)
            }
            TokenType.PERCENT -> {
                val divisor = requireNumber(right, "'%'")
                if (divisor == 0.0) runtimeError(expr.operator, "módulo por zero")
                checkedNumber(requireNumber(left, "'%'") % divisor, expr.operator)
            }
            TokenType.PLUS -> when {
                left is Double && right is Double -> checkedNumber(left + right, expr.operator)
                left is String && right is String -> left + right
                else -> runtimeError(expr.operator, "'+' aceita dois números ou dois textos, sem misturar tipos")
            }
            else -> runtimeError(expr.operator, "operador binário desconhecido '${expr.operator.lexeme}'")
        }
    }

    private fun invoke(callee: Any?, args: List<Any?>, closing: Token): Any? {
        if (callee !is NucleoCallable) runtimeError(closing, "valor não é uma função")
        if (args.size !in callee.minArity..callee.maxArity) {
            runtimeError(closing, "a função espera ${arityDescription(callee)} argumento(s), recebeu ${args.size}")
        }
        if (callDepth >= maxCallDepth) runtimeError(closing, "profundidade máxima de chamadas ($maxCallDepth) atingida")
        callDepth++
        try {
            return callee.call(args)
        } finally {
            callDepth--
        }
    }

    private fun tick() {
        steps++
        if (steps > stepLimit) throw NucleoLangException("orçamento de execução excedido ($stepLimit etapas); possível loop ou tarefa longa")
    }

    private fun requireIndex(value: Any?, token: Token): Int {
        val number = value as? Double ?: runtimeError(token, "índice de lista precisa ser um número inteiro")
        if (!number.isFinite() || number % 1.0 != 0.0 || number < 0.0 || number > Int.MAX_VALUE.toDouble()) {
            runtimeError(token, "índice de lista precisa ser um número inteiro não negativo válido")
        }
        return number.toInt()
    }

    private fun requireMutableList(value: Any?, functionName: String): MutableList<Any?> {
        if (value !is MutableList<*>) {
            throw NucleoLangException("$functionName exige uma lista mutável")
        }
        @Suppress("UNCHECKED_CAST")
        return value as MutableList<Any?>
    }

    private fun registerBuiltin(name: String, minArity: Int, maxArity: Int, implementation: (List<Any?>) -> Any?) {
        globals.define(Token(TokenType.IDENTIFIER, name, line = 1, column = 1), NativeFunction(minArity, maxArity, implementation))
    }

    private fun validateHostResult(value: Any?, depth: Int = 0, seen: MutableSet<Any> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())) {
        if (depth > MAX_HOST_VALUE_DEPTH) throw NucleoLangException("resultado de função nativa é profundo demais")
        when (value) {
            null, is Boolean, is String -> if (value is String && value.length > MAX_HOST_STRING_CHARS) {
                throw NucleoLangException("texto retornado por função nativa excede $MAX_HOST_STRING_CHARS caracteres")
            }
            is Double -> if (!value.isFinite()) throw NucleoLangException("função nativa retornou um número não finito")
            is List<*> -> {
                if (value.size > maxCollectionSize) throw NucleoLangException("lista retornada por função nativa excede $maxCollectionSize elementos")
                if (!seen.add(value)) return
                value.forEach { validateHostResult(it, depth + 1, seen) }
            }
            else -> throw NucleoLangException("função nativa retornou objeto externo não suportado pela linguagem")
        }
    }

    private fun checkedNumber(value: Double, token: Token): Double {
        if (!value.isFinite()) runtimeError(token, "resultado numérico fora do intervalo permitido")
        return value
    }

    private fun requireNumber(value: Any?, operator: String): Double =
        value as? Double ?: throw NucleoLangException("$operator exige números; recebido ${typeName(value)}")

    private fun requireBoolean(value: Any?, context: String): Boolean =
        value as? Boolean ?: throw NucleoLangException("$context exige booleano; recebido ${typeName(value)}")

    private fun stringify(value: Any?, depth: Int = 0): String = when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        is String -> value
        is List<*> -> if (depth >= MAX_STRINGIFY_DEPTH) "[…]" else value.joinToString(prefix = "[", postfix = "]") { stringify(it, depth + 1) }
        is NucleoCallable -> "<função>"
        else -> "<valor não exibível>"
    }

    private fun typeName(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> "booleano"
        is Double -> "número"
        is String -> "texto"
        is NucleoCallable -> "função"
        is List<*> -> "lista"
        else -> "desconhecido"
    }

    private fun arityDescription(callable: NucleoCallable): String =
        if (callable.minArity == callable.maxArity) callable.minArity.toString() else "${callable.minArity}–${callable.maxArity}"

    private fun runtimeError(token: Token, message: String): Nothing =
        throw NucleoLangException(message, token.line, token.column)

    private class Environment(private val enclosing: Environment? = null) {
        private val values = linkedMapOf<String, Any?>()

        fun define(name: Token, value: Any?) {
            if (values.containsKey(name.lexeme)) {
                throw NucleoLangException("'${name.lexeme}' já foi declarado neste escopo", name.line, name.column)
            }
            values[name.lexeme] = value
        }

        fun get(name: Token): Any? {
            if (values.containsKey(name.lexeme)) return values[name.lexeme]
            if (enclosing != null) return enclosing.get(name)
            throw NucleoLangException("nome '${name.lexeme}' não declarado", name.line, name.column)
        }

        fun assign(name: Token, value: Any?) {
            if (values.containsKey(name.lexeme)) {
                values[name.lexeme] = value
                return
            }
            if (enclosing != null) {
                enclosing.assign(name, value)
                return
            }
            throw NucleoLangException("nome '${name.lexeme}' não declarado", name.line, name.column)
        }
    }

    private interface NucleoCallable {
        val minArity: Int
        val maxArity: Int
        fun call(arguments: List<Any?>): Any?
    }

    private inner class NativeFunction(
        override val minArity: Int,
        override val maxArity: Int,
        private val implementation: (List<Any?>) -> Any?,
    ) : NucleoCallable {
        override fun call(arguments: List<Any?>): Any? = implementation(arguments)
    }

    private inner class UserFunction(
        private val parameters: List<Token>,
        private val body: List<NucleoStmt>,
        private val closure: Environment,
    ) : NucleoCallable {
        override val minArity: Int get() = parameters.size
        override val maxArity: Int get() = parameters.size

        override fun call(arguments: List<Any?>): Any? {
            val functionEnvironment = Environment(closure)
            parameters.forEachIndexed { index, token -> functionEnvironment.define(token, arguments[index]) }
            return try {
                executeBlock(body, functionEnvironment)
                null
            } catch (signal: ReturnSignal) {
                signal.value
            }
        }
    }

    private class ReturnSignal(val value: Any?) : RuntimeException(null, null, false, false)

    companion object {
        const val DEFAULT_STEP_LIMIT = 100_000
        const val HARD_MAX_STEP_LIMIT = 1_000_000
        const val DEFAULT_MAX_CALL_DEPTH = 64
        const val HARD_MAX_CALL_DEPTH = 64
        const val DEFAULT_MAX_COLLECTION_SIZE = 10_000
        const val HARD_MAX_COLLECTION_SIZE = 100_000
        const val MAX_HOST_VALUE_DEPTH = 64
        const val MAX_HOST_STRING_CHARS = 1_000_000
        const val MAX_STRINGIFY_DEPTH = 32
    }
}

data class NucleoExecutionResult(
    val output: List<String>,
    val stepsExecuted: Int,
)
