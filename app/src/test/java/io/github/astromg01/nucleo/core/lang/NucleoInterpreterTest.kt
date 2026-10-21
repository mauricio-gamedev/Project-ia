package io.github.astromg01.nucleo.core.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NucleoInterpreterTest {
    @Test
    fun `evaluates arithmetic and prints deterministic results`() {
        val result = NucleoInterpreter().execute("let total = 2 + 3 * 4; print(total); print((total - 2) / 2);")
        assertEquals(listOf("14", "6"), result.output)
        assertTrue(result.stepsExecuted > 0)
    }

    @Test
    fun `supports while conditionals and assignment`() {
        val source = """
            let count = 0;
            let sum = 0;
            while (count < 5) {
                sum = sum + count;
                count = count + 1;
            }
            if (sum == 10 && count == 5) { print("ok"); } else { print("bad"); }
        """.trimIndent()
        assertEquals(listOf("ok"), NucleoInterpreter().execute(source).output)
    }

    @Test
    fun `supports functions recursion and return values`() {
        val source = """
            fun fib(n) {
                if (n <= 1) return n;
                return fib(n - 1) + fib(n - 2);
            }
            print(fib(8));
        """.trimIndent()
        assertEquals(listOf("21"), NucleoInterpreter().execute(source).output)
    }

    @Test
    fun `logical operators short circuit`() {
        val source = "print(false && missing); print(true || missing);"
        assertEquals(listOf("false", "true"), NucleoInterpreter().execute(source).output)
    }

    @Test
    fun `supports mutable list literals indexing and collection builtins`() {
        val source = """
            let values = [3, 5, 8];
            values[1] = 7;
            push(values, 13);
            print(len(values));
            print(values[1]);
            print(contains(values, 13));
            print(pop(values));
            print(values);
        """.trimIndent()
        assertEquals(
            listOf("4", "7", "true", "13", "[3, 7, 8]"),
            NucleoInterpreter().execute(source).output,
        )
    }

    @Test
    fun `supports nested lists and chained indexing`() {
        val source = "let grid = [[1, 2], [3, 4]]; print(grid[1][0]); grid[0][1] = 9; print(grid);"
        assertEquals(listOf("3", "[[1, 9], [3, 4]]"), NucleoInterpreter().execute(source).output)
    }

    @Test
    fun `rejects invalid indexes and reads out of bounds`() {
        val nonInteger = assertThrows(NucleoLangException::class.java) {
            NucleoInterpreter().execute("let values = [1]; print(values[0.5]);")
        }
        assertTrue(nonInteger.message!!.contains("número inteiro"))
        val outOfBounds = assertThrows(NucleoLangException::class.java) {
            NucleoInterpreter().execute("let values = [1]; print(values[1]);")
        }
        assertTrue(outOfBounds.message!!.contains("fora dos limites"))
    }

    @Test
    fun `limits collection growth per interpreter`() {
        val interpreter = NucleoInterpreter(maxCollectionSize = 2)
        val error = assertThrows(NucleoLangException::class.java) {
            interpreter.execute("let values = [1, 2]; push(values, 3);")
        }
        assertTrue(error.message!!.contains("limite configurado"))
    }

    @Test
    fun `exposes explicitly registered host functions as capabilities`() {
        val interpreter = NucleoInterpreter(
            hostFunctions = listOf(
                NucleoHostFunction("text_upper", 1, 1) { args ->
                    (args[0] as? String)?.uppercase()
                        ?: throw NucleoLangException("text_upper exige texto")
                },
            ),
        )
        assertEquals(listOf("NUCLEOLANG"), interpreter.execute("print(text_upper(\"nucleolang\"));").output)
    }

    @Test
    fun `rejects host functions that return unsupported objects`() {
        val interpreter = NucleoInterpreter(
            hostFunctions = listOf(NucleoHostFunction("bad_capability", 0, 0) { Any() }),
        )
        val error = assertThrows(NucleoLangException::class.java) {
            interpreter.execute("bad_capability();")
        }
        assertTrue(error.message!!.contains("objeto externo não suportado"))
    }

    @Test
    fun `prevents host capabilities from replacing builtins`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NucleoInterpreter(hostFunctions = listOf(NucleoHostFunction("print", 0, 0) { null }))
        }
        assertTrue(error.message!!.contains("reservada"))
    }

    @Test
    fun `rejects duplicate declaration in one scope`() {
        val error = assertThrows(NucleoLangException::class.java) {
            NucleoInterpreter().execute("let value = 1; let value = 2;")
        }
        assertTrue(error.message!!.contains("já foi declarado"))
    }

    @Test
    fun `rejects invalid operations with readable error`() {
        val error = assertThrows(NucleoLangException::class.java) {
            NucleoInterpreter().execute("print(10 / 0);")
        }
        assertTrue(error.message!!.contains("divisão por zero"))
    }

    @Test
    fun `stops non terminating loops at configured budget`() {
        val interpreter = NucleoInterpreter(stepLimit = 100)
        val error = assertThrows(NucleoLangException::class.java) {
            interpreter.execute("while (true) { let ignored = 1; }")
        }
        assertTrue(error.message!!.contains("orçamento de execução excedido"))
    }

    @Test
    fun `rejects excessive syntactic nesting without crashing the process`() {
        val source = "print(" + "!".repeat(1_000) + "true);"
        val error = assertThrows(NucleoLangException::class.java) {
            NucleoInterpreter().execute(source)
        }
        assertTrue(error.message!!.contains("profundidade máxima"))
    }

    @Test
    fun `enforces function call depth`() {
        val source = "fun recurse() { return recurse(); } recurse();"
        val error = assertThrows(NucleoLangException::class.java) {
            NucleoInterpreter(maxCallDepth = 8).execute(source)
        }
        assertTrue(error.message!!.contains("profundidade máxima"))
    }
}
