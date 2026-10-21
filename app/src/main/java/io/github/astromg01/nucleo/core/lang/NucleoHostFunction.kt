package io.github.astromg01.nucleo.core.lang

/**
 * A trusted, explicitly registered function exposed by the host application to NúcleoLang.
 *
 * Implementations are application code, not code loaded from a NúcleoLang program. They should
 * expose one narrowly scoped capability at a time (for example, project_read_text) and must not
 * rely on the language sandbox to constrain their own Kotlin implementation.
 *
 * Supported argument/result values are null, Boolean, finite Double, String, and lists containing
 * those values. A host function can receive a mutable list created by the language. Returning
 * arbitrary JVM objects is rejected by the interpreter.
 */
data class NucleoHostFunction(
    val name: String,
    val minArity: Int,
    val maxArity: Int,
    val implementation: (List<Any?>) -> Any?,
) {
    init {
        require(NAME_PATTERN.matches(name)) { "Nome de função nativa inválido: '$name'" }
        require(minArity >= 0 && maxArity >= minArity) { "Aridade inválida para '$name'" }
    }

    companion object {
        private val NAME_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
