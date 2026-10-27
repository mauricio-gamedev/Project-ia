package io.github.astromg01.nucleo.ui.project

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import io.github.astromg01.nucleo.AppContainer
import io.github.astromg01.nucleo.core.ai.AiMessage
import io.github.astromg01.nucleo.core.ai.AiRequest
import io.github.astromg01.nucleo.domain.project.GeneratedProjectFile
import io.github.astromg01.nucleo.domain.project.ProjectDraft
import io.github.astromg01.nucleo.domain.project.ProjectFactory
import io.github.astromg01.nucleo.domain.project.StagedFileEvidence
import io.github.astromg01.nucleo.domain.project.StagedFilePreview
import io.github.astromg01.nucleo.domain.project.ProjectProfile
import io.github.astromg01.nucleo.domain.project.StagedProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

@Composable
fun ProjectFactoryScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stagingRoot = remember(context) { File(context.cacheDir, "nucleo-project-factory") }

    var projectName by remember { mutableStateOf("Meu projeto") }
    var description by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(ProjectProfile.ANDROID_APP) }
    var remoteConsent by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var stagedProject by remember { mutableStateOf<StagedProject?>(null) }
    var visibleFileLimit by remember { mutableStateOf(MAX_VISIBLE_FILES) }
    var previewFile by remember { mutableStateOf<StagedFileEvidence?>(null) }
    var preview by remember { mutableStateOf<StagedFilePreview?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Descreve o projeto e gera um primeiro rascunho revisável.") }
    var statusIsError by remember { mutableStateOf(false) }

    fun discardCurrentDraft(showStatus: Boolean = false) {
        val previous = stagedProject ?: return
        stagedProject = null
        visibleFileLimit = MAX_VISIBLE_FILES
        previewFile = null
        preview = null
        previewError = null
        previewLoading = false
        if (showStatus) {
            status = "Limpando o rascunho temporário…"
            statusIsError = false
        }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ProjectFactory().discard(previous, stagingRoot)
                }
                if (showStatus) {
                    status = "Rascunho descartado e arquivos temporários removidos."
                    statusIsError = false
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = error.message ?: "Não foi possível limpar o rascunho temporário."
                statusIsError = true
            }
        }
    }

    val zipExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        val staged = stagedProject
        if (uri == null) {
            if (staged != null) {
                status = "Exportação cancelada."
                statusIsError = false
            }
        } else if (staged != null && !exporting) {
            exporting = true
            status = "Preparando ZIP e verificando a integridade dos arquivos…"
            statusIsError = false
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        exportStagedProjectZip(context, uri, staged)
                    }
                    status = result
                    statusIsError = false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    status = error.message ?: "Não foi possível exportar o ZIP."
                    statusIsError = true
                } finally {
                    exporting = false
                }
            }
        }
    }

    LaunchedEffect(stagedProject?.id, previewFile?.relativePath) {
        val staged = stagedProject
        val selected = previewFile
        if (staged == null || selected == null) {
            previewLoading = false
            preview = null
            previewError = null
        } else {
            previewLoading = true
            preview = null
            previewError = null
            try {
                preview = withContext(Dispatchers.IO) {
                    ProjectFactory().readPreview(staged, selected)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                previewError = error.message ?: "Não foi possível abrir a prévia."
            } finally {
                previewLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Criar projeto com IA", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "A IA gera arquivos de texto. O Núcleo verifica os caminhos e arquivos essenciais e guarda o rascunho numa área privada antes de exportar. Nenhum script é executado e nenhum build é declarado como concluído.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = projectName,
            onValueChange = {
                projectName = it
                discardCurrentDraft()
            },
            label = { Text("Nome do projeto") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Text("Perfil inicial", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileChip("Android / APK", profile == ProjectProfile.ANDROID_APP) {
                    profile = ProjectProfile.ANDROID_APP
                    discardCurrentDraft()
                }
                ProfileChip("Web", profile == ProjectProfile.WEB_APP) {
                    profile = ProjectProfile.WEB_APP
                    discardCurrentDraft()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileChip("Python", profile == ProjectProfile.PYTHON) {
                    profile = ProjectProfile.PYTHON
                    discardCurrentDraft()
                }
                ProfileChip("Genérico", profile == ProjectProfile.GENERIC) {
                    profile = ProjectProfile.GENERIC
                    discardCurrentDraft()
                }
            }
        }

        OutlinedTextField(
            value = description,
            onValueChange = {
                description = it.take(ProjectFactory.MAX_DESCRIPTION_CHARS)
                discardCurrentDraft()
            },
            label = { Text("O que a IA deve criar?") },
            placeholder = { Text("Ex.: um app de lista de tarefas com salvamento local, pesquisa e tema escuro…") },
            supportingText = {
                Text("${description.length}/${ProjectFactory.MAX_DESCRIPTION_CHARS} caracteres")
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            maxLines = 10,
        )

        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = remoteConsent, onCheckedChange = { remoteConsent = it })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Autorizo enviar o pedido ao modelo configurado", fontWeight = FontWeight.SemiBold)
                    Text(
                        "O texto da descrição será enviado ao provedor remoto configurado para gerar o código. Não inclua senhas, tokens nem dados privados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Button(
            onClick = {
                if (projectName.isBlank() || description.isBlank()) {
                    status = "Preenche o nome e descreve o que queres criar."
                    statusIsError = true
                } else if (!remoteConsent) {
                    status = "Confirma o envio do pedido ao provedor antes de gerar o projeto."
                    statusIsError = true
                } else if (!generating && !exporting) {
                    discardCurrentDraft()
                    generating = true
                    status = "A IA está preparando o manifesto de arquivos…"
                    statusIsError = false
                    val requestedName = projectName.trim()
                    val requestedDescription = description.trim()
                    val requestedProfile = profile
                    scope.launch {
                        try {
                            val request = AiRequest(
                                messages = listOf(
                                    AiMessage(
                                        "system",
                                        buildString {
                                            append("Você é o gerador de projetos do Núcleo AI. Gere o menor projeto útil que satisfaça os requisitos do usuário. ")
                                            append("A saída precisa ser um único objeto JSON válido, começando em { e terminando em }, sem Markdown, cercas de código, comentários ou texto antes/depois. ")
                                            append("Formato obrigatório: {\"description\":\"resumo curto\",\"files\":[{\"path\":\"index.html\",\"content\":\"conteúdo completo do arquivo\"}]}. ")
                                            append("REGRA CRÍTICA: cada valor content é uma string JSON. Escape quebras de linha como \\n, aspas internas como \\\" e barras invertidas como \\\\. Nunca coloque quebras de linha literais dentro de uma string JSON. ")
                                            append("Não use vírgulas finais. Não invente campos. Cada entrada deve ter somente path e content, ambos strings. ")
                                            append("Para WEB_APP prefira somente index.html, styles.css, script.js e package.json, sem dependências de runtime externas se não forem necessárias. Mantenha o código simples para caber na resposta. ")
                                            append("Para ANDROID_APP inclua settings.gradle.kts, build.gradle.kts, app/build.gradle.kts, app/src/main/AndroidManifest.xml e ao menos uma Activity Kotlin em app/src/main/java/. Use SDK 36, minSdk 26 e versões compatíveis de Gradle/AGP/Kotlin. ")
                                            append("Para PYTHON inclua pyproject.toml ou requirements.txt e pelo menos um arquivo .py. Para GENÉRICO use a estrutura apropriada ao pedido. ")
                                            append("Não gere binários, .git, .gradle, build, node_modules, credenciais, chaves, .env, local.properties ou google-services.json. ")
                                            append("Nunca afirme que executou testes ou compilou. A saída é apenas fonte ainda não executada. Caminhos devem ser relativos e únicos.")
                                        },
                                    ),
                                    AiMessage(
                                        "user",
                                        "Nome: $requestedName\nPerfil: ${requestedProfile.name}\nRequisitos do projeto:\n$requestedDescription\n\nGere os arquivos necessários para uma primeira versão, priorizando funcionalidade e simplicidade.",
                                    ),
                                ),
                                temperature = 0.0,
                                maxOutputTokens = 12_000,
                                jsonObjectMode = true,
                            )
                            val response = try {
                                container.modelRouter.generate(request)
                            } catch (error: io.github.astromg01.nucleo.core.ai.ProviderHttpException) {
                                // Some OpenAI-compatible endpoints do not implement JSON mode.
                                // Retry once without the optional format parameter on HTTP 400 only.
                                if (error.statusCode == 400 && request.jsonObjectMode) {
                                    container.modelRouter.generate(request.copy(jsonObjectMode = false))
                                } else {
                                    throw error
                                }
                            }
                            val draft = parseProjectDraft(
                                responseText = response.text,
                                name = requestedName,
                                description = requestedDescription,
                                profile = requestedProfile,
                            )
                            val factory = ProjectFactory()
                            val staged = withContext(Dispatchers.IO) {
                                factory.stage(draft, stagingRoot)
                            }
                            visibleFileLimit = MAX_VISIBLE_FILES
                            stagedProject = staged
                            status = "Rascunho preparado por ${response.providerName}/${response.modelName}. ${staged.files.size} arquivos em staging privado. Ainda não houve compilação."
                            statusIsError = false
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            status = error.message ?: "Não foi possível gerar o projeto."
                            statusIsError = true
                        } finally {
                            generating = false
                        }
                    }
                }
            },
            enabled = !generating && !exporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (generating) {
                CircularProgressIndicator()
            } else {
                Text("Gerar e validar arquivos")
            }
        }

        Text(
            status,
            style = MaterialTheme.typography.bodyMedium,
            color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        stagedProject?.let { staged ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(staged.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${staged.profile.name} · ${staged.files.size} arquivos · aguardando revisão",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    staged.files.take(visibleFileLimit).forEach { file ->
                        OutlinedButton(
                            onClick = {
                                previewFile = file
                                preview = null
                                previewError = null
                            },
                            enabled = !exporting && !generating,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    file.relativePath,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    "${file.sizeBytes} bytes · SHA-256 ${file.sha256.take(12)}… · tocar para revisar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (staged.files.size > visibleFileLimit) {
                        OutlinedButton(
                            onClick = {
                                visibleFileLimit = (visibleFileLimit + MAX_VISIBLE_FILES)
                                    .coerceAtMost(staged.files.size)
                            },
                            enabled = !exporting && !generating,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Mostrar mais arquivos (${staged.files.size - visibleFileLimit} restantes)")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val name = staged.name
                            zipExporter.launch(safeZipFilename(name))
                        },
                        enabled = !exporting && !generating,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (exporting) "Exportando ZIP…" else "Exportar projeto como ZIP")
                    }
                    OutlinedButton(
                        onClick = { discardCurrentDraft(showStatus = true) },
                        enabled = !exporting && !generating,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Descartar rascunho e limpar staging")
                    }
                    Text(
                        "A exportação não executa scripts, não compila APKs e não assina um release. Revise o código antes de usá-lo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    previewFile?.let { selectedFile ->
        AlertDialog(
            onDismissRequest = {
                previewFile = null
                preview = null
                previewError = null
            },
            title = {
                Text(
                    selectedFile.relativePath,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${selectedFile.sizeBytes} bytes · SHA-256 ${selectedFile.sha256}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when {
                        previewLoading -> CircularProgressIndicator()
                        previewError != null -> Text(
                            previewError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        preview != null -> {
                            val loadedPreview = preview!!
                            if (loadedPreview.truncated) {
                                Text(
                                    "Prévia limitada aos primeiros ${ProjectFactory.MAX_PREVIEW_CHARS} caracteres. O arquivo completo permanece intacto no staging e será verificado pelo SHA-256 antes da exportação.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            SelectionContainer {
                                Text(
                                    loadedPreview.content,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    previewFile = null
                    preview = null
                    previewError = null
                }) {
                    Text("Fechar")
                }
            },
        )
    }
}

@Composable
private fun ProfileChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

private fun parseProjectDraft(
    responseText: String,
    name: String,
    description: String,
    profile: ProjectProfile,
): ProjectDraft {
    val trimmed = responseText.trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    if (start < 0 || end <= start) {
        val reason = if (trimmed.isNotEmpty() && trimmed.last() != '}') {
            "A resposta parece ter sido cortada antes do fim."
        } else {
            "A resposta não contém um objeto JSON completo."
        }
        throw IllegalArgumentException(
            reason + " Tente gerar novamente. Se persistir, escolha um modelo com suporte a JSON."
        )
    }

    val jsonText = trimmed.substring(start, end + 1)
    val json = try {
        JSONObject(jsonText)
    } catch (error: Exception) {
        val detail = error.message
            ?.replace(Regex("[\\r\\n]+"), " ")
            ?.take(140)
            ?.let { " Detalhe do parser: " + it + "." }
            .orEmpty()
        val hint = if (trimmed.lastOrNull() != '}') {
            " A resposta pode ter sido interrompida pelo limite de saída."
        } else {
            " O modelo pode ter produzido aspas ou quebras de linha sem escape dentro do conteúdo de um arquivo."
        }
        throw IllegalArgumentException(
            "A IA devolveu um manifesto JSON inválido." + hint + detail +
                " Tente novamente; o Núcleo agora solicita modo JSON quando o provedor suporta esse recurso.",
            error,
        )
    }

    val filesJson = json.optJSONArray("files")
        ?: throw IllegalArgumentException("O manifesto JSON não contém uma lista 'files' válida.")
    require(filesJson.length() in 1..ProjectFactory.MAX_FILES) {
        "O manifesto precisa conter entre 1 e " + ProjectFactory.MAX_FILES + " arquivos."
    }
    val files = (0 until filesJson.length()).map { index ->
        val entry = filesJson.optJSONObject(index)
            ?: throw IllegalArgumentException("A entrada de arquivo " + (index + 1) + " não é um objeto JSON.")
        val path = entry.optString("path", "")
        val content = entry.optString("content", "")
        require(path.isNotBlank()) { "A entrada de arquivo " + (index + 1) + " não contém caminho." }
        GeneratedProjectFile(path = path, content = content)
    }
    return ProjectDraft(
        name = name,
        profile = profile,
        description = description,
        files = files,
    )
}

private fun exportStagedProjectZip(
    context: Context,
    destinationUri: Uri,
    staged: StagedProject,
): String {
    try {
        val output = context.contentResolver.openOutputStream(destinationUri, "w")
            ?: throw IOException("O Android não conseguiu abrir o arquivo ZIP para gravação.")
        val written = ProjectFactory().writeZip(staged, output)
        return "Projeto exportado como ZIP com $written arquivos. Ele ainda precisa ser extraído e testado; o Núcleo não executou o projeto."
    } catch (cancelled: CancellationException) {
        try {
            DocumentFile.fromSingleUri(context, destinationUri)?.delete()
        } catch (_: Exception) {
            // Best-effort cleanup for an incomplete output document.
        }
        throw cancelled
    } catch (error: Exception) {
        try {
            DocumentFile.fromSingleUri(context, destinationUri)?.delete()
        } catch (_: Exception) {
            // Best-effort cleanup for an incomplete output document.
        }
        if (error is IOException) throw error
        throw IOException("A exportação ZIP falhou. O Núcleo tentou remover o arquivo incompleto.", error)
    }
}

private fun safeZipFilename(raw: String): String {
    val base = raw.map { character ->
        if (character.isLetterOrDigit() || character in listOf(' ', '-', '_', '.')) character else '-'
    }.joinToString("")
        .trim()
        .trim('.')
        .take(55)
        .ifBlank { "nucleo-project" }
    return "$base.zip"
}

private const val MAX_VISIBLE_FILES = 40
