package io.github.astromg01.nucleo.ui.project

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import io.github.astromg01.nucleo.AppContainer
import io.github.astromg01.nucleo.export.IShizukuExportService
import io.github.astromg01.nucleo.export.ShizukuExportService
import io.github.astromg01.nucleo.core.ai.AiMessage
import io.github.astromg01.nucleo.core.ai.AiRequest
import io.github.astromg01.nucleo.core.ai.AiTaskType
import io.github.astromg01.nucleo.domain.project.GeneratedProjectFile
import io.github.astromg01.nucleo.domain.project.ProjectDraft
import io.github.astromg01.nucleo.domain.project.ProjectCodeReviewService
import io.github.astromg01.nucleo.domain.project.ProjectCodeReviewReport
import io.github.astromg01.nucleo.domain.project.ProjectSourceSnapshot
import io.github.astromg01.nucleo.domain.project.ProjectFactory
import io.github.astromg01.nucleo.domain.project.ProjectGenerationValidator
import io.github.astromg01.nucleo.domain.project.ProjectGenerationRepairService
import io.github.astromg01.nucleo.domain.project.AndroidProjectScaffold
import io.github.astromg01.nucleo.domain.project.GitHubAndroidBuildAdapter
import io.github.astromg01.nucleo.domain.project.BuiltAndroidArtifact
import io.github.astromg01.nucleo.domain.project.StagedFileEvidence
import io.github.astromg01.nucleo.domain.project.StagedFilePreview
import io.github.astromg01.nucleo.domain.project.ProjectProfile
import io.github.astromg01.nucleo.domain.project.StagedProject
import io.github.astromg01.nucleo.domain.github.GITHUB_OAUTH_TOKEN_ID
import io.github.astromg01.nucleo.domain.github.GitHubConnectorPreferences
import io.github.astromg01.nucleo.domain.github.GitHubRepository
import io.github.astromg01.nucleo.domain.github.GitHubOAuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import rikka.shizuku.Shizuku

private data class ProjectRepairDiffEntry(
    val path: String,
    val before: String,
    val after: String,
)

@Composable
fun ProjectFactoryScreen(container: AppContainer, onOpenGitHubSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val stagingRoot = remember(context) { File(context.cacheDir, "nucleo-project-factory") }
    val githubConnectorPreferences = remember(context) { GitHubConnectorPreferences(context) }
    val githubOAuthSession = remember(context) { GitHubOAuthSession(context, container.secretStore) }
    var selectedBuildRepository by remember { mutableStateOf(githubConnectorPreferences.selectedBuildRepository()) }
    var githubBuildTokenAvailable by remember { mutableStateOf(!container.secretStore.readApiKey(GITHUB_OAUTH_TOKEN_ID).isNullOrBlank()) }
    var githubAccountLogin by remember { mutableStateOf<String?>(null) }
    var githubSyncTick by remember { mutableStateOf(0) }
    var syncingGitHub by remember { mutableStateOf(false) }

    var projectName by remember { mutableStateOf("Meu projeto") }
    var description by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(ProjectProfile.ANDROID_APP) }
    var remoteConsent by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var stagedProject by remember { mutableStateOf<StagedProject?>(null) }
    var currentDraft by remember { mutableStateOf<ProjectDraft?>(null) }
    var generationValidationIssues by remember { mutableStateOf<List<String>>(emptyList()) }
    var verifyingBuildTarget by remember { mutableStateOf(false) }
    var buildingApk by remember { mutableStateOf(false) }
    var buildConsent by remember { mutableStateOf(false) }
    var buildStatus by remember { mutableStateOf("") }
    var githubStatus by remember { mutableStateOf("") }
    var buildFailureDiagnostics by remember { mutableStateOf("") }
    var buildFailureRunUrl by remember { mutableStateOf("") }
    var repairingBuild by remember { mutableStateOf(false) }
    var builtApk by remember { mutableStateOf<BuiltAndroidArtifact?>(null) }
    var visibleFileLimit by remember { mutableStateOf(MAX_VISIBLE_FILES) }
    var previewFile by remember { mutableStateOf<StagedFileEvidence?>(null) }
    var preview by remember { mutableStateOf<StagedFilePreview?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Descreve o projeto e gera um primeiro rascunho revisável.") }
    var statusIsError by remember { mutableStateOf(false) }
    var hasAllFilesAccess by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager())
    }
    var reviewConsent by remember { mutableStateOf(false) }
    var reviewingCode by remember { mutableStateOf(false) }
    var proposingRepair by remember { mutableStateOf(false) }
    var reviewSnapshot by remember { mutableStateOf<ProjectSourceSnapshot?>(null) }
    var reviewReport by remember { mutableStateOf<ProjectCodeReviewReport?>(null) }
    var repairCandidate by remember { mutableStateOf<StagedProject?>(null) }
    var repairCandidateDraft by remember { mutableStateOf<ProjectDraft?>(null) }
    var repairCandidateValidationIssues by remember { mutableStateOf<List<String>>(emptyList()) }
    var repairSummary by remember { mutableStateOf("") }
    var repairDiffs by remember { mutableStateOf<List<ProjectRepairDiffEntry>>(emptyList()) }

    val projectInteractionLocked = generating || exporting || buildingApk || verifyingBuildTarget ||
        reviewingCode || proposingRepair || repairingBuild

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) githubSyncTick += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(githubSyncTick, generating, buildingApk, verifyingBuildTarget, repairingBuild) {
        if (generating || buildingApk || verifyingBuildTarget || repairingBuild) return@LaunchedEffect

        val storedToken = !container.secretStore.readApiKey(GITHUB_OAUTH_TOKEN_ID).isNullOrBlank()
        githubBuildTokenAvailable = storedToken
        selectedBuildRepository = githubConnectorPreferences.selectedBuildRepository()
        if (!storedToken) {
            githubAccountLogin = null
            githubStatus = "Nenhuma sessão GitHub salva neste dispositivo. Abra o Conector GitHub e autorize a conta."
            return@LaunchedEffect
        }

        syncingGitHub = true
        githubStatus = "Validando a sessão GitHub e sincronizando o repositório de build…"
        try {
            val token = githubOAuthSession.validAccessToken()
                ?: throw IOException("Não há token de acesso válido salvo. Reconecte a conta GitHub.")
            val login = io.github.astromg01.nucleo.domain.github.GitHubConnector()
                .authenticatedUserLogin(token)
            githubAccountLogin = login
            githubBuildTokenAvailable = true

            val repository = githubConnectorPreferences.selectedBuildRepository()
            selectedBuildRepository = repository
            if (repository == null) {
                githubStatus = "Conta GitHub confirmada como @" + login +
                    ", mas falta escolher um repositório privado dedicado à compilação. A seleção do chat é separada."
            } else {
                try {
                    val target = GitHubAndroidBuildAdapter(context).verifyRepository(
                        repository.owner,
                        repository.name,
                        token,
                    )
                    githubStatus = "Conta @" + login + " confirmada · repositório de build " +
                        target.owner + "/" + target.name + " validado (privado e com escrita)."
                } catch (repositoryError: CancellationException) {
                    throw repositoryError
                } catch (repositoryError: Exception) {
                    githubStatus = "Conta @" + login + " autenticada, mas o repositório de build não está pronto: " +
                        (repositoryError.message ?: "não foi possível validar privacidade e permissão de escrita.")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = error.message ?: "Não foi possível confirmar a conta no GitHub agora."
            val authFailure = when (error) {
                is io.github.astromg01.nucleo.domain.github.GitHubConnector.GitHubConnectorException ->
                    error.statusCode == 401
                is io.github.astromg01.nucleo.domain.project.GitHubAndroidBuildAdapter.GitHubBuildException ->
                    error.statusCode == 401
                else -> message.contains("reconecte", ignoreCase = true) ||
                    message.contains("sessão GitHub expirou", ignoreCase = true) ||
                    message.contains("bad_refresh_token", ignoreCase = true) ||
                    message.contains("não há token de acesso válido", ignoreCase = true)
            }
            githubAccountLogin = null
            githubStatus = if (authFailure) {
                "A sessão GitHub foi encontrada no armazenamento, mas precisa de nova autorização: " + message
            } else {
                "O token está salvo, mas não consegui confirmar a conta no GitHub agora. Isso não prova que a conexão foi perdida: " + message
            }
        } finally {
            syncingGitHub = false
        }
    }

    fun clearReviewState() {
        val candidate = repairCandidate
        repairCandidate = null
        repairCandidateDraft = null
        repairCandidateValidationIssues = emptyList()
        reviewSnapshot = null
        reviewReport = null
        repairSummary = ""
        repairDiffs = emptyList()
        reviewingCode = false
        proposingRepair = false
        if (candidate != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ProjectFactory().discard(candidate, stagingRoot)
                    }
                } catch (_: Exception) {
                    // Best-effort cleanup of a private draft created only for an unaccepted repair.
                }
            }
        }
    }

    fun discardCurrentDraft(showStatus: Boolean = false) {
        val previous = stagedProject
        stagedProject = null
        currentDraft = null
        generationValidationIssues = emptyList()
        visibleFileLimit = MAX_VISIBLE_FILES
        previewFile = null
        preview = null
        previewError = null
        previewLoading = false
        buildFailureDiagnostics = ""
        buildFailureRunUrl = ""
        buildStatus = ""
        builtApk?.file?.delete()
        builtApk = null
        buildConsent = false

        if (previous == null) {
            if (showStatus) {
                status = "Rascunho descartado; não havia arquivos temporários ativos."
                statusIsError = false
            }
            return
        }
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

    val apkSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive"),
    ) { uri: Uri? ->
        val artifact = builtApk
        if (uri == null) {
            buildStatus = "Salvamento do APK cancelado."
        } else if (artifact == null) {
            buildStatus = "O APK não está disponível; execute a compilação novamente."
        } else {
            buildingApk = true
            buildStatus = "Salvando e verificando o APK no dispositivo…"
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        copyBuiltApkToUri(context, uri, artifact)
                    }
                    buildStatus = "APK salvo no destino escolhido. SHA-256 verificado: ${artifact.sha256}."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    buildStatus = error.message ?: "Não foi possível salvar o APK."
                } finally {
                    buildingApk = false
                }
            }
        }
    }

    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
        hasAllFilesAccess = allowed
        val staged = stagedProject
        if (allowed && staged != null && !exporting && !generating) {
            exporting = true
            status = "Permissão concedida. Exportando ZIP diretamente para Downloads…"
            statusIsError = false
            scope.launch {
                try {
                    status = withContext(Dispatchers.IO) {
                        exportStagedProjectWithAllFilesAccess(context, staged)
                    }
                    statusIsError = false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    status = error.message ?: "Não foi possível exportar para Downloads."
                    statusIsError = true
                } finally {
                    exporting = false
                }
            }
        } else if (!allowed) {
            status = "O Android ainda não concedeu acesso a todos os arquivos. Ative a permissão e volte ao Núcleo."
            statusIsError = true
        } else {
            status = "Acesso aos arquivos autorizado. Toque em Exportar direto para Downloads para continuar."
            statusIsError = false
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
        OutlinedButton(
            onClick = {
                when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> {
                        status = "O acesso total por configurações especiais está disponível no Android 11 ou superior."
                        statusIsError = true
                    }
                    Environment.isExternalStorageManager() -> {
                        hasAllFilesAccess = true
                        status = "O acesso total aos arquivos já está autorizado. Quando houver um rascunho, use Exportar direto para Downloads."
                        statusIsError = false
                    }
                    else -> {
                        try {
                            status = "Na tela do Android, ative ‘Permitir gerenciamento de todos os arquivos’ para Núcleo AI • Arquivos."
                            statusIsError = false
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            allFilesAccessLauncher.launch(intent)
                        } catch (error: Exception) {
                            status = "Não foi possível abrir a tela de permissão de arquivos: ${error.message ?: "erro desconhecido"}"
                            statusIsError = true
                        }
                    }
                }
            },
            enabled = !exporting && !generating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (hasAllFilesAccess) "Acesso total aos arquivos: autorizado"
                else "Conceder permissão total aos arquivos",
            )
        }

        OutlinedTextField(
            value = projectName,
            onValueChange = {
                projectName = it
                clearReviewState()
                discardCurrentDraft()
            },
            label = { Text("Nome do projeto") },
            enabled = !projectInteractionLocked,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Text("Perfil inicial", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileChip("Android / APK", profile == ProjectProfile.ANDROID_APP, enabled = !projectInteractionLocked) {
                    profile = ProjectProfile.ANDROID_APP
                    clearReviewState()
                    discardCurrentDraft()
                }
                ProfileChip("Web", profile == ProjectProfile.WEB_APP, enabled = !projectInteractionLocked) {
                    profile = ProjectProfile.WEB_APP
                    clearReviewState()
                    discardCurrentDraft()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileChip("Python", profile == ProjectProfile.PYTHON, enabled = !projectInteractionLocked) {
                    profile = ProjectProfile.PYTHON
                    clearReviewState()
                    discardCurrentDraft()
                }
                ProfileChip("Genérico", profile == ProjectProfile.GENERIC, enabled = !projectInteractionLocked) {
                    profile = ProjectProfile.GENERIC
                    clearReviewState()
                    discardCurrentDraft()
                }
            }
        }

        OutlinedTextField(
            value = description,
            onValueChange = {
                description = it.take(ProjectFactory.MAX_DESCRIPTION_CHARS)
                clearReviewState()
                discardCurrentDraft()
            },
            label = { Text("O que a IA deve criar?") },
            placeholder = { Text("Ex.: um app de lista de tarefas com salvamento local, pesquisa e tema escuro…") },
            supportingText = {
                Text("${description.length}/${ProjectFactory.MAX_DESCRIPTION_CHARS} caracteres")
            },
            enabled = !projectInteractionLocked,
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            maxLines = 10,
        )

        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = remoteConsent, onCheckedChange = { remoteConsent = it }, enabled = !projectInteractionLocked)
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
                } else if (!generating && !exporting && !buildingApk && !verifyingBuildTarget) {
                    clearReviewState()
                    discardCurrentDraft()
                    currentDraft = null
                    repairCandidateDraft = null
                    repairCandidateValidationIssues = emptyList()
                    generationValidationIssues = emptyList()
                    buildFailureDiagnostics = ""
                    buildFailureRunUrl = ""
                    buildStatus = ""
                    builtApk?.file?.delete()
                    builtApk = null
                    buildConsent = false
                    generating = true
                    status = "A IA está preparando os arquivos; o Núcleo vai completar e validar a estrutura base…"
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
                                            append("Para ANDROID_APP, gere um app funcional completo com Kotlin e Jetpack Compose, não apenas arquivos de exemplo ou um esqueleto vazio. Inclua uma Activity inicial real, ligada ao manifest, e todo código de UI/estado necessário para a funcionalidade pedida. Inclua settings.gradle.kts, build.gradle.kts, app/build.gradle.kts e app/src/main/AndroidManifest.xml consistentes entre si, com plugin Android, Kotlin/Compose, namespace, applicationId, compileSdk 36, minSdk 26, targetSdk 36, dependências explícitas e repositórios Google/Maven Central. Mantenha a mesma package name entre o código e a Activity declarada no manifest. Não omita imports, métodos chamados, recursos ou dependências usados pelo código. ")
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
                                maxOutputTokens = if (requestedProfile == ProjectProfile.ANDROID_APP) 20_000 else 12_000,
                                jsonObjectMode = true,
                                taskType = AiTaskType.CODE_GENERATION,
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
                            val parsedDraft = parseProjectDraft(
                                responseText = response.text,
                                name = requestedName,
                                description = requestedDescription,
                                profile = requestedProfile,
                            )
                            val initialScaffold = AndroidProjectScaffold.complete(parsedDraft)
                            val initialIssues = ProjectGenerationValidator.validate(initialScaffold.draft)
                            var finalDraft = initialScaffold.draft
                            var repairNote = ""

                            if (initialIssues.isNotEmpty()) {
                                status = "Detectei ${initialIssues.size} problema(s) estrutural(is). Tentando uma correção automática isolada antes de preparar o projeto…"
                                try {
                                    val repair = ProjectGenerationRepairService().repairOnce(
                                        draft = initialScaffold.draft,
                                        issues = initialIssues,
                                    ) { repairRequest ->
                                        try {
                                            container.modelRouter.generate(repairRequest)
                                        } catch (error: io.github.astromg01.nucleo.core.ai.ProviderHttpException) {
                                            if (error.statusCode == 400 && repairRequest.jsonObjectMode) {
                                                container.modelRouter.generate(repairRequest.copy(jsonObjectMode = false))
                                            } else {
                                                throw error
                                            }
                                        }
                                    }
                                    finalDraft = AndroidProjectScaffold.complete(repair.draft).draft
                                    repairNote = if (repair.attempted) {
                                        " Correção automática: ${repair.summary}"
                                    } else {
                                        " Correção automática não executada: ${repair.summary}"
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (repairError: Exception) {
                                    repairNote = " A correção automática não conseguiu terminar: ${repairError.message ?: "erro não detalhado"}."
                                }
                            }

                            val finalIssues = ProjectGenerationValidator.validate(finalDraft)
                            val factory = ProjectFactory()
                            val staged = withContext(Dispatchers.IO) {
                                factory.stage(finalDraft, stagingRoot)
                            }
                            generationValidationIssues = finalIssues
                            currentDraft = finalDraft
                            visibleFileLimit = MAX_VISIBLE_FILES
                            stagedProject = staged
                            builtApk?.file?.delete()
                            builtApk = null
                            buildConsent = false
                            val scaffoldNote = if (requestedProfile == ProjectProfile.ANDROID_APP) {
                                buildString {
                                    val added = initialScaffold.addedPaths.size
                                    val replaced = initialScaffold.replacedPaths.size
                                    if (added > 0 || replaced > 0) {
                                        append(" Base Android completada: $added arquivo(s) adicionados, $replaced configuração(ões) corrigida(s).")
                                    }
                                    if (initialScaffold.defaultLauncherAdded) {
                                        append(" Foi criada uma Activity de contingência; a validação bloqueia build até sua funcionalidade estar implementada.")
                                    }
                                }
                            } else {
                                ""
                            }
                            status = if (finalIssues.isEmpty()) {
                                "Rascunho preparado por ${response.providerName}/${response.modelName}. ${staged.files.size} arquivos verificados em staging privado.$scaffoldNote$repairNote A validação estática passou; ainda falta executar o Gradle para confirmar que compila."
                            } else {
                                "Rascunho salvo em staging privado com ${staged.files.size} arquivos, mas ${finalIssues.size} problema(s) permanecem. A compilação está bloqueada até corrigir os itens listados.$scaffoldNote$repairNote"
                            }
                            statusIsError = finalIssues.isNotEmpty()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            status = if (
                                error is java.net.SocketTimeoutException ||
                                (error is java.io.IOException && error.message?.contains("timeout", ignoreCase = true) == true)
                            ) {
                                "O modelo demorou demais para responder. Aumentei o limite de espera para 210 segundos; tenta novamente ou seleciona outro modelo gratuito no OpenRouter."
                            } else {
                                error.message ?: "Não foi possível gerar o projeto."
                            }
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

        if (generationValidationIssues.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Validação estrutural: ${generationValidationIssues.size} problema(s)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    generationValidationIssues.take(12).forEachIndexed { index, issue ->
                        Text(
                            "${index + 1}. $issue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (generationValidationIssues.size > 12) {
                        Text(
                            "Mais ${generationValidationIssues.size - 12} problema(s). Exporte o fonte para revisar todos os arquivos.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        "O Núcleo não vai marcar nem iniciar um APK enquanto esses problemas permanecerem.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        if (profile == ProjectProfile.ANDROID_APP) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Compilação de APK na nuvem", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "O Núcleo usa sua conta GitHub autorizada. Escolha um repositório privado dedicado ao build em Configurações → Conector GitHub. Essa seleção é separada do repositório que o chat lê como contexto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    selectedBuildRepository?.let { repository ->
                        Text(
                            "Repositório de build ativo: ${repository.fullName} · ${if (repository.isPrivate) "privado" else "público"} · branch ${repository.defaultBranch.ifBlank { "não definida" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    } ?: Text(
                        "Nenhum repositório de build selecionado. Abra o Conector GitHub nas Configurações.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        when {
                            githubAccountLogin != null -> "Conta GitHub confirmada: @" + githubAccountLogin + "."
                            githubBuildTokenAvailable -> "Existe uma sessão salva; a conta ainda precisa ser validada com o GitHub."
                            else -> "Nenhuma sessão GitHub salva. Conecte a conta em Configurações."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (syncingGitHub) {
                        androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedButton(onClick = onOpenGitHubSettings, enabled = !buildingApk && !verifyingBuildTarget, modifier = Modifier.fillMaxWidth()) {
                        Text("Conectar GitHub / escolher repositório de build")
                    }
                    OutlinedButton(
                        onClick = {
                            selectedBuildRepository = githubConnectorPreferences.selectedBuildRepository()
                            githubBuildTokenAvailable = !container.secretStore.readApiKey(GITHUB_OAUTH_TOKEN_ID).isNullOrBlank()
                            githubSyncTick += 1
                            githubStatus = if (selectedBuildRepository == null) {
                                "Nenhum repositório de build dedicado selecionado. Vou sincronizar a sessão e validar o estado no GitHub."
                            } else {
                                "Atualizando a seleção salva e verificando novamente a conta e o repositório no GitHub…"
                            }
                        },
                        enabled = !buildingApk && !verifyingBuildTarget && !syncingGitHub,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Sincronizar conta e repositório GitHub") }
                    Button(
                        onClick = {
                            val repository = githubConnectorPreferences.selectedBuildRepository()
                            val hasStoredToken = !container.secretStore.readApiKey(GITHUB_OAUTH_TOKEN_ID).isNullOrBlank()
                            if (repository == null) {
                                githubStatus = "Escolha um repositório privado de build no Conector GitHub em Configurações."
                            } else if (!hasStoredToken) {
                                githubBuildTokenAvailable = false
                                githubStatus = "A conexão GitHub não está disponível. Reconecte a conta em Configurações."
                            } else if (verifyingBuildTarget || buildingApk) {
                                Unit
                            } else {
                                verifyingBuildTarget = true
                                githubStatus = "Validando acesso e privacidade do repositório de build…"
                                scope.launch {
                                    try {
                                        val token = githubOAuthSession.validAccessToken()
                                            ?: throw java.io.IOException("Não há uma sessão OAuth válida salva. Reconecte a conta GitHub.")
                                        val login = io.github.astromg01.nucleo.domain.github.GitHubConnector()
                                            .authenticatedUserLogin(token)
                                        githubAccountLogin = login
                                        githubBuildTokenAvailable = true
                                        val target = GitHubAndroidBuildAdapter(context)
                                            .verifyRepository(repository.owner, repository.name, token)
                                        selectedBuildRepository = repository
                                        githubStatus = "Repositório privado de build confirmado: ${target.owner}/${target.name}."
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        githubStatus = error.message ?: "Não foi possível validar o repositório."
                                    } finally {
                                        verifyingBuildTarget = false
                                    }
                                }
                            }
                        },
                        enabled = !verifyingBuildTarget && !buildingApk && !syncingGitHub && githubAccountLogin != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (verifyingBuildTarget) CircularProgressIndicator(modifier = Modifier.height(18.dp))
                        else Text("Verificar repositório privado")
                    }
                    if (githubStatus.isNotBlank()) Text(
                        githubStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (githubStatus.startsWith("O GitHub") || githubStatus.startsWith("O token") ||
                            githubStatus.startsWith("A compilação falhou") || githubStatus.startsWith("Não foi") ||
                            githubStatus.startsWith("Falha") || githubStatus.startsWith("Nenhum") ||
                            githubStatus.startsWith("Conecte")) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        stagedProject?.let { staged ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(staged.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${staged.profile.name} · ${staged.files.size} arquivos · aguardando revisão",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (staged.profile == ProjectProfile.ANDROID_APP) {
                        HorizontalDivider()
                        Text("Gerar APK instalável", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Checkbox(
                                checked = buildConsent,
                                onCheckedChange = { buildConsent = it },
                                enabled = !buildingApk && !exporting,
                            )
                            Text(
                                "Autorizo enviar este projeto ao repositório GitHub privado selecionado e executar os scripts Gradle no runner do GitHub Actions. Revise o fonte primeiro; scripts Gradle podem executar código durante a compilação.",
                                modifier = Modifier.padding(top = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = {
                                val hasStoredToken = !container.secretStore.readApiKey(GITHUB_OAUTH_TOKEN_ID).isNullOrBlank()
                                val repository = githubConnectorPreferences.selectedBuildRepository()
                                when {
                                    !buildConsent -> buildStatus = "Confirme o envio do fonte ao repositório privado e a execução do build remoto."
                                    repository == null -> buildStatus = "Escolha um repositório no Conector GitHub em Configurações."
                                    !hasStoredToken -> {
                                        githubBuildTokenAvailable = false
                                        buildStatus = "Conecte sua conta GitHub em Configurações antes de compilar."
                                    }
                                    !repository.isPrivate -> buildStatus = "Por segurança, a compilação aceita somente um repositório privado."
                                    githubAccountLogin == null || syncingGitHub -> buildStatus = "Sincronize a sessão GitHub e confirme a conta antes de iniciar a compilação."
                                    buildingApk || verifyingBuildTarget -> Unit
                                    else -> {
                                        buildingApk = true
                                        builtApk = null
                                        buildFailureDiagnostics = ""
                                        buildFailureRunUrl = ""
                                        buildStatus = "Preparando build remoto…"
                                        scope.launch {
                                            try {
                                                val token = githubOAuthSession.validAccessToken()
                                                    ?: throw java.io.IOException("Não há uma sessão OAuth válida salva. Reconecte sua conta em Configurações.")
                                                val login = io.github.astromg01.nucleo.domain.github.GitHubConnector()
                                                    .authenticatedUserLogin(token)
                                                githubAccountLogin = login
                                                githubBuildTokenAvailable = true
                                                val artifact = GitHubAndroidBuildAdapter(context).build(
                                                    stagedProject = staged,
                                                    stagingRoot = stagingRoot,
                                                    owner = repository.owner,
                                                    repository = repository.name,
                                                    token = token,
                                                    onStatus = { update -> scope.launch { buildStatus = update } },
                                                )
                                                builtApk = artifact
                                                buildStatus = "APK compilado e verificado · SHA-256 ${artifact.sha256}."
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (error: Exception) {
                                                if (error is io.github.astromg01.nucleo.domain.project.AndroidBuildFailureException) {
                                                    buildFailureDiagnostics = error.diagnostics
                                                    buildFailureRunUrl = error.runUrl
                                                } else {
                                                    buildFailureDiagnostics = ""
                                                    buildFailureRunUrl = ""
                                                }
                                                buildStatus = error.message ?: "Não foi possível compilar o APK."
                                            } finally {
                                                buildingApk = false
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !buildingApk && !verifyingBuildTarget && !exporting && !generating &&
                                !syncingGitHub && githubAccountLogin != null && generationValidationIssues.isEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (buildingApk) CircularProgressIndicator(modifier = Modifier.height(18.dp))
                            else Text(if (generationValidationIssues.isEmpty()) "Compilar APK na nuvem" else "Corrija a validação antes de compilar")
                        }
                        if (buildingApk) {
                            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        builtApk?.let { artifact ->
                            Text(
                                "O build remoto terminou. Este é um APK de depuração, não uma versão de publicação para a Play Store.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                "SHA-256: ${artifact.sha256}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                            )
                            OutlinedButton(
                                onClick = {
                                    apkSaveLauncher.launch(safeApkFilename(staged.name))
                                },
                                enabled = !buildingApk,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Salvar APK no dispositivo") }
                            TextButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(artifact.runUrl)))
                                    } catch (_: Exception) {
                                        buildStatus = "Não foi possível abrir o log do GitHub Actions."
                                    }
                                },
                                modifier = Modifier.align(Alignment.End),
                            ) { Text("Abrir log do build") }
                        }
                        if (buildStatus.isNotBlank()) {
                            Text(
                                buildStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (buildStatus.startsWith("O GitHub") || buildStatus.startsWith("O token") ||
                                    buildStatus.startsWith("A compilação falhou") || buildStatus.startsWith("O Gradle") ||
                                    buildStatus.startsWith("A sessão") || buildStatus.startsWith("Não foi") ||
                                    buildStatus.startsWith("Falha") || buildStatus.startsWith("Nenhuma sessão"))
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.secondary,
                            )
                        }
                        if (buildFailureRunUrl.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(buildFailureRunUrl)))
                                    }.onFailure {
                                        buildStatus = "Não foi possível abrir a execução do GitHub Actions."
                                    }
                                },
                                modifier = Modifier.align(Alignment.End),
                            ) { Text("Abrir log completo do Gradle") }
                        }
                        if (buildFailureDiagnostics.isNotBlank()) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Erros principais da compilação", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    SelectionContainer {
                                        Text(
                                            buildFailureDiagnostics.take(2_400),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                    Text(
                                        "A correção assistida vai enviar o fonte atual e esses diagnósticos ao provedor de IA; ela não executa os arquivos.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Button(
                                        enabled = !repairingBuild && !buildingApk && !generating && repairCandidate == null,
                                        onClick = {
                                            if (!reviewConsent) {
                                                status = "Ative o consentimento de análise/correção por IA antes de enviar o fonte e os erros do Gradle."
                                                statusIsError = true
                                            } else {
                                                val originalDraft = currentDraft
                                                if (originalDraft == null) {
                                                    status = "O fonte completo desse rascunho não está disponível no estado atual. Gere novamente ou abra o rascunho original antes de corrigir."
                                                    statusIsError = true
                                                } else {
                                                    repairingBuild = true
                                                    status = "Preparando uma proposta de correção a partir dos erros do Gradle…"
                                                    statusIsError = false
                                                    val diagnostics = buildFailureDiagnostics
                                                    val runUrl = buildFailureRunUrl
                                                    scope.launch {
                                                        try {
                                                            val repairIssues = listOf(
                                                                "A compilação Android falhou no GitHub Actions. URL da execução: " + runUrl,
                                                                diagnostics,
                                                            ).filter { it.isNotBlank() }
                                                            val repair = ProjectGenerationRepairService().repairOnce(
                                                                originalDraft,
                                                                repairIssues,
                                                            ) { repairRequest ->
                                                                try {
                                                                    container.modelRouter.generate(repairRequest)
                                                                } catch (error: io.github.astromg01.nucleo.core.ai.ProviderHttpException) {
                                                                    if (error.statusCode == 400 && repairRequest.jsonObjectMode) {
                                                                        container.modelRouter.generate(repairRequest.copy(jsonObjectMode = false))
                                                                    } else {
                                                                        throw error
                                                                    }
                                                                }
                                                            }
                                                            if (!repair.attempted) {
                                                                status = "A correção automática não foi executada; nenhum rascunho de correção será criado. " + repair.summary
                                                                statusIsError = true
                                                                return@launch
                                                            }
                                                            val completedDraft = AndroidProjectScaffold.complete(repair.draft).draft
                                                            val candidateIssues = ProjectGenerationValidator.validate(completedDraft)
                                                            val originalByPath = originalDraft.files.associateBy { it.path }
                                                            val candidateDiffs = completedDraft.files.mapNotNull { changed ->
                                                                val original = originalByPath[changed.path]
                                                                if (original == null || original.content != changed.content) {
                                                                    ProjectRepairDiffEntry(changed.path, original?.content.orEmpty(), changed.content)
                                                                } else {
                                                                    null
                                                                }
                                                            }
                                                            val candidate = withContext(Dispatchers.IO) {
                                                                ProjectFactory().stage(completedDraft, stagingRoot)
                                                            }
                                                            repairCandidate = candidate
                                                            repairCandidateDraft = completedDraft
                                                            repairCandidateValidationIssues = candidateIssues
                                                            repairSummary = buildString {
                                                                append("Correção proposta por IA com base nos erros do Gradle. ")
                                                                append(repair.summary)
                                                                if (candidateIssues.isEmpty()) {
                                                                    append(" A validação estática passou; é obrigatório executar o Gradle novamente para confirmar.")
                                                                } else {
                                                                    append(" Ainda restam ")
                                                                    append(candidateIssues.size)
                                                                    append(" problema(s) estruturais; o build seguirá bloqueado.")
                                                                }
                                                            }
                                                            repairDiffs = candidateDiffs
                                                            status = "Proposta de correção criada em staging separado; o fonte ativo não foi alterado. Revise o diff antes de aplicar."
                                                            statusIsError = candidateIssues.isNotEmpty()
                                                        } catch (cancelled: CancellationException) {
                                                            throw cancelled
                                                        } catch (error: Exception) {
                                                            status = error.message ?: "A IA não conseguiu propor uma correção para os erros do Gradle."
                                                            statusIsError = true
                                                        } finally {
                                                            repairingBuild = false
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        if (repairingBuild) CircularProgressIndicator(modifier = Modifier.height(18.dp))
                                        else Text("Propor correção com IA a partir do Gradle")
                                    }
                                }
                            }
                        }
                    }
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
                        Text(if (exporting) "Exportando ZIP…" else "Salvar ZIP pelo seletor do Android")
                    }
                    OutlinedButton(
                        onClick = {
                            when {
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> {
                                    status = "A exportação direta requer Android 11 ou superior. Use o botão de salvar ZIP pelo seletor."
                                    statusIsError = true
                                }
                                !Environment.isExternalStorageManager() -> {
                                    try {
                                        status = "Ative ‘Permitir gerenciamento de todos os arquivos’ para o Núcleo AI na tela do Android que vai abrir."
                                        statusIsError = false
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        allFilesAccessLauncher.launch(intent)
                                    } catch (error: Exception) {
                                        status = "Não foi possível abrir a tela de permissão de arquivos: ${error.message ?: "erro desconhecido"}"
                                        statusIsError = true
                                    }
                                }
                                !exporting && !generating -> {
                                    exporting = true
                                    status = "Exportando ZIP diretamente para Download/NucleoAIProjects…"
                                    statusIsError = false
                                    val stagedNow = staged
                                    scope.launch {
                                        try {
                                            status = withContext(Dispatchers.IO) {
                                                exportStagedProjectWithAllFilesAccess(context, stagedNow)
                                            }
                                            statusIsError = false
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            status = error.message ?: "Não foi possível exportar para Downloads."
                                            statusIsError = true
                                        } finally {
                                            exporting = false
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !exporting && !generating,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (exporting) "Exportando ZIP…" else if (hasAllFilesAccess)
                                "Exportar direto para Downloads (acesso total)"
                            else "Permitir acesso total aos arquivos e exportar",
                        )
                    }
                    Text(
                        "Esta opção abre a permissão especial do Android; ela não é uma permissão de diálogo normal. Quando autorizada, o ZIP é salvo diretamente em Download/NucleoAIProjects. O seletor ZIP e o Shizuku continuam disponíveis como alternativas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            try {
                                when {
                                    !Shizuku.pingBinder() -> {
                                        status = "Shizuku não está iniciado. Abre o Shizuku, inicia o serviço e tenta novamente."
                                        statusIsError = true
                                    }
                                    Shizuku.isPreV11() -> {
                                        status = "Esta integração exige Shizuku v11 ou superior."
                                        statusIsError = true
                                    }
                                    Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> {
                                        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                                        status = "Autoriza o Núcleo AI no diálogo do Shizuku. Depois toca novamente neste botão."
                                        statusIsError = false
                                    }
                                    !exporting && !generating -> {
                                        exporting = true
                                        status = "Preparando ZIP e enviando ao serviço autorizado do Shizuku…"
                                        statusIsError = false
                                        scope.launch {
                                            try {
                                                status = withContext(Dispatchers.IO) {
                                                    exportStagedProjectViaShizuku(context, staged)
                                                }
                                                statusIsError = false
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (error: Exception) {
                                                status = error.message ?: "A exportação via Shizuku falhou."
                                                statusIsError = true
                                            } finally {
                                                exporting = false
                                            }
                                        }
                                    }
                                }
                            } catch (error: Exception) {
                                status = error.message ?: "Não foi possível consultar a autorização do Shizuku."
                                statusIsError = true
                            }
                        },
                        enabled = !exporting && !generating,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (exporting) "Exportando via Shizuku…" else "Exportar direto para Download (Shizuku)")
                    }
                    Text(
                        "A opção Shizuku grava em Download/NucleoAIProjects e exige o serviço Shizuku iniciado e autorizado. A opção do seletor Android continua disponível como alternativa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { discardCurrentDraft(showStatus = true) },
                        enabled = !projectInteractionLocked,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Descartar rascunho e limpar staging")
                    }
                    HorizontalDivider()
                    Text("Análise de código e reparo controlado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = reviewConsent, onCheckedChange = { reviewConsent = it })
                        Text(
                            "Autorizo enviar o conteúdo dos arquivos do rascunho e, se eu pedir correção de build, os diagnósticos sanitizados do Gradle ao provedor remoto.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "A revisão é estática e feita por IA: não executa o código. Para propor uma correção completa, o Núcleo exige que todos os arquivos estejam disponíveis sem truncamento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            if (!reviewConsent) {
                                status = "Autoriza explicitamente o envio do código antes de iniciar a revisão."
                                statusIsError = true
                            } else {
                                reviewingCode = true
                                status = "Lendo arquivos íntegros e pedindo uma revisão estática à IA…"
                                statusIsError = false
                                val stagedForReview = staged
                                val descriptionForReview = description.trim()
                                scope.launch {
                                    try {
                                        val reviewer = ProjectCodeReviewService()
                                        val snapshot = withContext(Dispatchers.IO) {
                                            reviewer.collectSources(
                                                stagedForReview,
                                                maxFiles = 8,
                                                maxCharsPerFile = ProjectFactory.MAX_PREVIEW_CHARS,
                                                totalChars = 30_000,
                                            )
                                        }
                                        val request = reviewer.buildReviewRequest(stagedForReview, descriptionForReview, snapshot)
                                        val response = container.modelRouter.generate(request)
                                        val report = reviewer.parseReviewResponse(
                                            responseText = response.text,
                                            allowedPaths = snapshot.files.map { it.path }.toSet(),
                                            partial = snapshot.truncatedPaths.isNotEmpty() || snapshot.omittedFileCount > 0,
                                        )
                                        reviewSnapshot = snapshot
                                        reviewReport = report
                                        status = "Revisão concluída por ${response.providerName}/${response.modelName}: ${report.findings.size} achado(s). Nenhum código foi executado."
                                        statusIsError = false
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        status = error.message ?: "Não foi possível analisar o código."
                                        statusIsError = true
                                    } finally {
                                        reviewingCode = false
                                    }
                                }
                            }
                        },
                        enabled = !exporting && !generating && !reviewingCode && !proposingRepair && repairCandidate == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (reviewingCode) CircularProgressIndicator(modifier = Modifier.height(18.dp))
                        else Text("Analisar código com IA")
                    }
                    reviewReport?.let { report ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Relatório de revisão", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(report.summary, style = MaterialTheme.typography.bodySmall)
                                if (report.partial) {
                                    Text(
                                        "Revisão parcial: alguns arquivos ou trechos foram omitidos. O Núcleo não vai propor uma correção automática a partir desse relatório.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (report.findings.isEmpty()) {
                                    Text("A IA não retornou achados válidos para os arquivos analisados.", style = MaterialTheme.typography.bodySmall)
                                }
                                report.findings.take(12).forEach { finding ->
                                    HorizontalDivider()
                                    Text(
                                        "[${finding.severity.uppercase()}] ${finding.path}${finding.line?.let { ":$it" }.orEmpty()}",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    Text(finding.problem, style = MaterialTheme.typography.bodySmall)
                                    if (finding.recommendation.isNotBlank()) {
                                        Text("Sugestão: ${finding.recommendation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (report.findings.size > 12) {
                                    Text("Mais ${report.findings.size - 12} achado(s) no relatório.", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = {
                                        val snapshot = reviewSnapshot
                                        if (report.partial || snapshot == null || !snapshot.canProposeCompleteRepairs) {
                                            status = "A correção automática exige uma revisão completa, com todos os arquivos sem truncamento."
                                            statusIsError = true
                                        } else if (report.findings.isEmpty()) {
                                            status = "Não há achados da revisão para corrigir."
                                            statusIsError = false
                                        } else {
                                            proposingRepair = true
                                            status = "Solicitando uma proposta de correção isolada do rascunho original…"
                                            statusIsError = false
                                            val currentStaged = staged
                                            val currentSnapshot = snapshot
                                            scope.launch {
                                                try {
                                                    val reviewer = ProjectCodeReviewService()
                                                    val request = reviewer.buildRepairRequest(
                                                        currentStaged,
                                                        description.trim(),
                                                        currentSnapshot,
                                                        report,
                                                    )
                                                    val response = container.modelRouter.generate(request)
                                                    val proposal = reviewer.parseRepairProposal(
                                                        response.text,
                                                        currentSnapshot.files.map { it.path }.toSet(),
                                                    )
                                                    val originalByPath = currentSnapshot.files.associateBy { it.path }
                                                    val changedFiles = proposal.files.mapNotNull { changed ->
                                                        originalByPath[changed.path]?.let { original ->
                                                            ProjectRepairDiffEntry(changed.path, original.content, changed.content)
                                                        }
                                                    }
                                                    val replacements = proposal.files.associateBy { it.path }
                                                    val updatedFiles = currentSnapshot.files.map { original ->
                                                        replacements[original.path] ?: original
                                                    }
                                                    val proposedDraft = ProjectDraft(
                                                        name = currentStaged.name,
                                                        profile = currentStaged.profile,
                                                        description = description.trim(),
                                                        files = updatedFiles,
                                                    )
                                                    val completedDraft = AndroidProjectScaffold.complete(proposedDraft).draft
                                                    val candidateIssues = ProjectGenerationValidator.validate(completedDraft)
                                                    val candidate = withContext(Dispatchers.IO) {
                                                        ProjectFactory().stage(completedDraft, stagingRoot)
                                                    }
                                                    val actualDiffs = completedDraft.files.mapNotNull { changed ->
                                                        val original = originalByPath[changed.path]
                                                        if (original == null || original.content != changed.content) {
                                                            ProjectRepairDiffEntry(changed.path, original?.content.orEmpty(), changed.content)
                                                        } else {
                                                            null
                                                        }
                                                    }
                                                    repairCandidate = candidate
                                                    repairCandidateDraft = completedDraft
                                                    repairCandidateValidationIssues = candidateIssues
                                                    repairSummary = if (candidateIssues.isEmpty()) {
                                                        proposal.summary + " A validação estrutural passou; ainda não foi compilado."
                                                    } else {
                                                        proposal.summary + " Ainda restam ${candidateIssues.size} problema(s); a compilação continuará bloqueada até corrigir."
                                                    }
                                                    repairDiffs = actualDiffs.ifEmpty { changedFiles }
                                                    status = "Proposta criada por ${response.providerName}/${response.modelName} num staging separado. O rascunho original continua intacto; nenhum teste foi executado."
                                                    statusIsError = candidateIssues.isNotEmpty()
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (error: Exception) {
                                                    status = error.message ?: "Não foi possível preparar a proposta de correção."
                                                    statusIsError = true
                                                } finally {
                                                    proposingRepair = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = !report.partial && report.findings.isNotEmpty() &&
                                        reviewSnapshot?.canProposeCompleteRepairs == true &&
                                        !exporting && !generating && !reviewingCode && !proposingRepair && repairCandidate == null,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (proposingRepair) CircularProgressIndicator(modifier = Modifier.height(18.dp))
                                    else Text("Propor correções em rascunho")
                                }
                            }
                        }
                    }
                    repairCandidate?.let { candidate ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Proposta isolada de correção", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(repairSummary, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Nada foi aplicado ao rascunho atual. Confere o diff abaixo; aceitar troca o rascunho ativo pela nova cópia.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                repairDiffs.forEach { diff ->
                                    HorizontalDivider()
                                    Text(diff.path, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                    Text("ANTES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    SelectionContainer {
                                        Text(diff.before.take(1_200), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("PROPOSTA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    SelectionContainer {
                                        Text(diff.after.take(1_200), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (diff.before.length > 1_200 || diff.after.length > 1_200) {
                                        Text("Prévia do diff limitada a 1.200 caracteres por versão.", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Button(
                                    onClick = {
                                        val previous = stagedProject
                                        stagedProject = candidate
                                        currentDraft = repairCandidateDraft
                                        generationValidationIssues = repairCandidateValidationIssues
                                        repairCandidate = null
                                        repairCandidateDraft = null
                                        repairCandidateValidationIssues = emptyList()
                                        reviewSnapshot = null
                                        reviewReport = null
                                        repairSummary = ""
                                        repairDiffs = emptyList()
                                        buildFailureDiagnostics = ""
                                        buildFailureRunUrl = ""
                                        buildStatus = ""
                                        builtApk?.file?.delete()
                                        builtApk = null
                                        status = if (generationValidationIssues.isEmpty()) {
                                            "Rascunho corrigido aplicado como nova revisão. A validação estrutural passou; ainda falta executar o Gradle."
                                        } else {
                                            "Nova revisão aplicada, mas restam ${generationValidationIssues.size} problema(s). A compilação continua bloqueada."
                                        }
                                        statusIsError = generationValidationIssues.isNotEmpty()
                                        if (previous != null) {
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        ProjectFactory().discard(previous, stagingRoot)
                                                    }
                                                } catch (error: Exception) {
                                                    status = "Nova revisão ativada, mas a cópia antiga não foi limpa: ${error.message ?: "erro de limpeza"}"
                                                    statusIsError = true
                                                }
                                            }
                                        }
                                    },
                                    enabled = !proposingRepair && !reviewingCode && !exporting,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Aplicar rascunho corrigido") }
                                TextButton(
                                    onClick = {
                                        val discard = repairCandidate
                                        repairCandidate = null
                                        repairCandidateDraft = null
                                        repairCandidateValidationIssues = emptyList()
                                        repairSummary = ""
                                        repairDiffs = emptyList()
                                        if (discard != null) {
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        ProjectFactory().discard(discard, stagingRoot)
                                                    }
                                                    status = "Proposta descartada; rascunho original preservado."
                                                    statusIsError = false
                                                } catch (error: Exception) {
                                                    status = error.message ?: "Não foi possível limpar a proposta."
                                                    statusIsError = true
                                                }
                                            }
                                        }
                                    },
                                    enabled = !proposingRepair && !exporting,
                                ) { Text("Descartar proposta") }
                                Text(
                                    "Essa correção é apenas uma proposta validada pelo staging. Ainda não foi compilada nem executada.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
private fun ProfileChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilterChip(selected = selected, onClick = onClick, enabled = enabled, label = { Text(label) })
}

private fun parseProjectDraft(
    responseText: String,
    name: String,
    description: String,
    profile: ProjectProfile,
): ProjectDraft = ProjectManifestParser.parse(responseText, name, description, profile)

private fun exportStagedProjectWithAllFilesAccess(
    context: Context,
    staged: StagedProject,
): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
        throw SecurityException("O acesso a todos os arquivos ainda não foi autorizado nas configurações do Android.")
    }
    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!downloads.exists() && !downloads.mkdirs()) {
        throw IOException("O Android não conseguiu criar a pasta pública Download.")
    }
    val canonicalDownloads = downloads.canonicalFile
    if (!canonicalDownloads.isDirectory) {
        throw IOException("A pasta Download não está disponível.")
    }

    val requestedDirectory = File(canonicalDownloads, "NucleoAIProjects")
    if (Files.isSymbolicLink(requestedDirectory.toPath())) {
        throw IOException("A pasta NucleoAIProjects não pode ser um link simbólico.")
    }
    if (!requestedDirectory.exists() && !requestedDirectory.mkdirs()) {
        throw IOException("Não foi possível criar Download/NucleoAIProjects.")
    }
    val directory = requestedDirectory.canonicalFile
    if (!directory.isDirectory || directory.parentFile != canonicalDownloads) {
        throw IOException("O destino saiu da pasta Download autorizada.")
    }

    val baseName = safeZipFilename(staged.name).removeSuffix(".zip")
    var destination = File(directory, "$baseName.zip")
    var suffix = 2
    while (destination.exists()) {
        destination = File(directory, "$baseName-$suffix.zip")
        suffix++
        if (suffix > 1000) throw IOException("Não foi possível encontrar um nome de arquivo livre.")
    }
    val temporary = File.createTempFile(".nucleo-export-", ".part", directory)
    try {
        val written = ProjectFactory().writeZip(staged, FileOutputStream(temporary))
        if (written != staged.files.size || temporary.length() <= 0L) {
            throw IOException("O ZIP gerado não contém todos os arquivos esperados.")
        }
        Files.move(temporary.toPath(), destination.toPath())
        return "Projeto exportado com $written arquivos em: ${destination.absolutePath}. O código ainda precisa ser extraído e testado."
    } catch (cancelled: CancellationException) {
        temporary.delete()
        throw cancelled
    } catch (error: Exception) {
        temporary.delete()
        if (error is IOException || error is SecurityException) throw error
        throw IOException("Falha na exportação direta para Download/NucleoAIProjects.", error)
    }
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

private data class BoundShizukuExportService(
    val args: Shizuku.UserServiceArgs,
    val connection: ServiceConnection,
    val remote: IShizukuExportService,
)

private suspend fun exportStagedProjectViaShizuku(
    context: Context,
    staged: StagedProject,
): String {
    if (!Shizuku.pingBinder()) throw IOException("Shizuku não está iniciado. Inicie-o e tente novamente.")
    if (Shizuku.isPreV11()) throw IOException("A integração exige Shizuku v11 ou superior.")
    if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
        throw SecurityException("O Núcleo AI ainda não recebeu autorização do Shizuku.")
    }

    val archive = File(context.cacheDir, "nucleo-export-" + UUID.randomUUID() + ".zip")
    var bound: BoundShizukuExportService? = null
    try {
        val count = ProjectFactory().writeZip(staged, FileOutputStream(archive))
        if (count != staged.files.size || !archive.isFile || archive.length() <= 0L) {
            throw IOException("O ZIP temporário não contém todos os arquivos esperados.")
        }

        val activeConnection = bindShizukuExportService(context)
        bound = activeConnection
        val remote = activeConnection.remote
        remote.beginExport(safeZipFilename(staged.name))
        try {
            FileInputStream(archive).use { input ->
                val buffer = ByteArray(SHIZUKU_CHUNK_BYTES)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    if (size == 0) continue
                    remote.writeChunk(buffer.copyOf(size))
                }
            }
            val savedPath = remote.finishExport()
            return "ZIP salvo via Shizuku em: $savedPath. O fonte foi exportado; ainda não foi executado nem compilado."
        } catch (error: Exception) {
            try {
                remote.abortExport()
            } catch (_: Exception) {
                // The remote service also cleans up its partial file when it is destroyed.
            }
            throw IOException(
                "A cópia via Shizuku falhou. O Núcleo tentou remover o arquivo temporário; confira se o serviço segue ativo.",
                error,
            )
        }
    } finally {
        archive.delete()
        bound?.let { connection ->
            try {
                Shizuku.unbindUserService(connection.args, connection.connection, true)
            } catch (_: Exception) {
                // Best-effort shutdown of this one-shot export service.
            }
        }
    }
}

private suspend fun bindShizukuExportService(context: Context): BoundShizukuExportService {
    val args = Shizuku.UserServiceArgs(
        ComponentName(context, ShizukuExportService::class.java),
    )
        .daemon(false)
        .tag("nucleo-ai-project-export-v1")
        .version(1)

    return suspendCancellableCoroutine { continuation ->
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (!continuation.isActive) return
                if (!binder.pingBinder()) {
                    continuation.resumeWithException(IOException("O serviço do Shizuku retornou um Binder inválido."))
                    return
                }
                val remote = IShizukuExportService.Stub.asInterface(binder)
                continuation.resume(BoundShizukuExportService(args, this, remote))
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IOException("O serviço do Shizuku foi desconectado."))
                }
            }

            override fun onBindingDied(name: ComponentName) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IOException("A ligação com Shizuku morreu; reinicie o serviço e tente novamente."))
                }
            }

            override fun onNullBinding(name: ComponentName) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IOException("O Shizuku não forneceu um serviço de exportação."))
                }
            }
        }

        try {
            Shizuku.bindUserService(args, connection)
        } catch (error: Exception) {
            continuation.resumeWithException(
                IOException("Não foi possível iniciar o serviço de exportação do Shizuku.", error),
            )
            return@suspendCancellableCoroutine
        }

        continuation.invokeOnCancellation {
            try {
                Shizuku.unbindUserService(args, connection, true)
            } catch (_: Exception) {
                // Best-effort cancellation cleanup.
            }
        }
    }
}

private fun safeApkFilename(raw: String): String {
    val base = raw.map { character ->
        if (character.isLetterOrDigit() || character in listOf(' ', '-', '_', '.')) character else '-'
    }.joinToString("")
        .trim()
        .trim('.')
        .removeSuffix(".zip")
        .take(55)
        .ifBlank { "nucleo-app" }
    return "$base.apk"
}

private fun copyBuiltApkToUri(
    context: Context,
    destinationUri: Uri,
    artifact: BuiltAndroidArtifact,
) {
    if (!artifact.file.isFile || artifact.file.length() <= 0L) {
        throw IOException("O APK compilado não está disponível no cache.")
    }
    val preCopyDigest = sha256Of(artifact.file)
    if (!preCopyDigest.equals(artifact.sha256, ignoreCase = true)) {
        throw IOException("A integridade do APK mudou antes de salvar. Compile novamente.")
    }
    try {
        val output = context.contentResolver.openOutputStream(destinationUri, "w")
            ?: throw IOException("O Android não conseguiu abrir o destino escolhido.")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        output.use { destination ->
            FileInputStream(artifact.file).use { source ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 120L * 1024L * 1024L) throw IOException("O APK excedeu o limite de cópia.")
                    digest.update(buffer, 0, count)
                    destination.write(buffer, 0, count)
                }
                destination.flush()
            }
        }
        val copiedDigest = digest.digest().joinToString("") { "%02x".format(it) }
        if (!copiedDigest.equals(artifact.sha256, ignoreCase = true)) {
            throw IOException("O SHA-256 do APK salvo não corresponde ao artefato validado.")
        }
    } catch (error: Exception) {
        try {
            DocumentFile.fromSingleUri(context, destinationUri)?.delete()
        } catch (_: Exception) {
            // Best effort: a document provider may not allow deleting the partial output.
        }
        if (error is IOException) throw error
        throw IOException("Não foi possível gravar o APK no destino escolhido.", error)
    }
}

private fun sha256Of(file: File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 4107
private const val SHIZUKU_CHUNK_BYTES = 128 * 1024

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
