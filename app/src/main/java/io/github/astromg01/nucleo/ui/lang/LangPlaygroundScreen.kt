package io.github.astromg01.nucleo.ui.lang

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.astromg01.nucleo.core.lang.NucleoInterpreter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val exampleProgram = """fun fibonacci(n) {
    if (n <= 1) return n;
    return fibonacci(n - 1) + fibonacci(n - 2);
}

let result = fibonacci(8);
if (result == 21) {
    print("NúcleoLang funcionando!");
    print("Fibonacci(8) = " + "21");
} else {
    print("Resultado inesperado");
}
"""

@Composable
fun LangPlaygroundScreen() {
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf(exampleProgram) }
    var output by remember { mutableStateOf("Executa o exemplo para ver o resultado.") }
    var running by remember { mutableStateOf(false) }
    var runSummary by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("NúcleoLang", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Primeiro playground da linguagem autoral. O código roda no próprio aparelho, em um interpretador restrito sem acesso embutido a arquivos, rede ou shell.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (!running) {
                                running = true
                                output = "Executando…"
                                runSummary = ""
                                val program = source
                                scope.launch {
                                    try {
                                        val result = withContext(Dispatchers.Default) {
                                            NucleoInterpreter().execute(program)
                                        }
                                        output = if (result.output.isEmpty()) "(sem saída)" else result.output.joinToString("\n")
                                        runSummary = "Concluído · ${result.stepsExecuted} etapas"
                                    } catch (error: Exception) {
                                        output = error.message ?: "Erro de execução sem detalhes."
                                        runSummary = "Execução interrompida"
                                    } finally {
                                        running = false
                                    }
                                }
                            }
                        },
                        enabled = !running && source.isNotBlank(),
                    ) {
                        if (running) CircularProgressIndicator() else Text("Executar")
                    }
                    OutlinedButton(onClick = { source = exampleProgram; output = "Exemplo restaurado."; runSummary = "" }, enabled = !running) {
                        Text("Restaurar exemplo")
                    }
                }
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("Código-fonte") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 12,
                    maxLines = 24,
                    enabled = !running,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saída", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (runSummary.isNotBlank()) {
                    Text(runSummary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
                Text(output, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(2.dp))
        Text("Recursos nesta versão: variáveis, números, textos, booleanos, if/else, while, funções, return, recursão e operadores. Os efeitos do programa se limitam à saída de texto.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Ainda não cria projetos nem APKs. O próximo passo é integrar a linguagem ao motor de engenharia e construir adaptadores seguros para gerar, validar e compilar projetos reais.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
