package io.github.astromg01.nucleo.ui.github

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import io.github.astromg01.nucleo.AppContainer
import io.github.astromg01.nucleo.domain.github.GitHubConnector
import io.github.astromg01.nucleo.domain.github.GitHubConnectorPreferences
import io.github.astromg01.nucleo.domain.github.GitHubDeviceAuthorization
import io.github.astromg01.nucleo.domain.github.GitHubOAuthSession
import io.github.astromg01.nucleo.domain.github.GitHubRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun GitHubConnectorCard(container: AppContainer) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { GitHubConnectorPreferences(context) }
    val connector = remember { GitHubConnector() }
    val session = remember(context) { GitHubOAuthSession(context, container.secretStore, connector) }
    var clientId by remember { mutableStateOf(preferences.clientId()) }
    var connected by remember { mutableStateOf(session.hasStoredAccessToken()) }
    var repositories by remember { mutableStateOf<List<GitHubRepository>>(emptyList()) }
    var selected by remember { mutableStateOf(preferences.selectedRepository()) }
    var selectedBuild by remember { mutableStateOf(preferences.selectedBuildRepository()) }
    var contextMenuOpen by remember { mutableStateOf(false) }
    var buildMenuOpen by remember { mutableStateOf(false) }
    var deviceAuthorization by remember { mutableStateOf(session.pendingAuthorization()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var resumeTick by remember { mutableStateOf(0) }
    var pollRetry by remember { mutableStateOf(0) }

    suspend fun refresh() {
        val token = session.validAccessToken()
            ?: throw java.io.IOException("Conecte sua conta GitHub para carregar os repositórios.")
        repositories = connector.listRepositories(token)
        selected = preferences.selectedRepository()
        selectedBuild = preferences.selectedBuildRepository()
        status = "Conexão GitHub pronta · ${repositories.size} repositórios carregados."
    }

    suspend fun waitForDeviceAuthorization(authorization: GitHubDeviceAuthorization) {
        val id = preferences.clientId().ifBlank { clientId }
        val tokens = connector.awaitAccessToken(id, authorization) { update ->
            scope.launch { status = update }
        }
        session.save(tokens)
        connected = true
        deviceAuthorization = null
        refresh()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Kept independent from ON_RESUME so returning from the browser does not cancel polling.
    // A recreated process loads the encrypted device code and resumes this same flow.
    LaunchedEffect(deviceAuthorization?.deviceCode, pollRetry) {
        val authorization = deviceAuthorization ?: return@LaunchedEffect
        if (session.hasStoredAccessToken()) {
            connected = true
            session.clearPendingAuthorization()
            deviceAuthorization = null
            return@LaunchedEffect
        }
        busy = true
        status = "Aguardando a autorização do GitHub. Pode voltar para esta tela; a verificação continuará."
        try {
            waitForDeviceAuthorization(authorization)
            status = "GitHub conectado · ${repositories.size} repositórios carregados."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            status = error.message ?: "Não foi possível concluir a autorização do GitHub."
            val lower = status.lowercase()
            if (lower.contains("expirou") || lower.contains("recusada") || lower.contains("recusou")) {
                session.clearPendingAuthorization()
                deviceAuthorization = null
            }
        } finally {
            busy = false
        }
    }

    LaunchedEffect(resumeTick) {
        if (session.hasStoredAccessToken()) {
            connected = true
            if (!busy && repositories.isEmpty()) {
                busy = true
                try {
                    refresh()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    status = error.message ?: "Conexão detectada, mas não foi possível carregar os repositórios."
                } finally {
                    busy = false
                }
            }
        } else if (deviceAuthorization == null) {
            connected = false
            repositories = emptyList()
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Conector GitHub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Autorize no GitHub e escolha um repositório uma vez. O vínculo é compartilhado pelo chat e pela Project Factory.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!connected) {
                OutlinedTextField(
                    value = clientId, onValueChange = { clientId = it.trim().take(80) },
                    label = { Text("Client ID do OAuth App (configuração única)") },
                    placeholder = { Text("ID público fornecido pelo GitHub") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Registre um OAuth App na sua conta GitHub e habilite Device Flow. O app não pede sua senha nem um PAT manual. O Client ID é a única configuração inicial.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/developers"))) }
                                .onFailure { status = "Não foi possível abrir configurações do GitHub." }
                        }, modifier = Modifier.weight(1f),
                    ) { Text("Configurar OAuth") }
                    Button(
                        enabled = !busy && deviceAuthorization == null && clientId.isNotBlank(),
                        onClick = {
                            if (busy) return@Button
                            preferences.saveClientId(clientId)
                            busy = true
                            status = "Solicitando autorização…"
                            scope.launch {
                                try {
                                    val auth = connector.requestDeviceAuthorization(clientId)
                                    session.savePendingAuthorization(auth)
                                    deviceAuthorization = auth
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(auth.verificationUri))) }
                                    status = "Digite o código no GitHub. A verificação ficará salva se o Android pausar ou recriar o app."
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    status = error.message ?: "Não foi possível conectar ao GitHub."
                                } finally { busy = false }
                            }
                        }, modifier = Modifier.weight(1f),
                    ) { Text(if (busy) "Conectando…" else "Conectar GitHub") }
                }
                deviceAuthorization?.let { auth ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Código de autorização", style = MaterialTheme.typography.labelLarge)
                            Text(auth.userCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("Expira em aproximadamente ${auth.expiresInSeconds / 60} min.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Código GitHub", auth.userCode))
                                status = "Código copiado."
                            }) { Text("Copiar código") }
                            TextButton(enabled = !busy, onClick = { pollRetry += 1 }) {
                                Text("Retomar verificação")
                            }
                        }
                    }
                }
            } else {
                Text("Conta GitHub conectada", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
                Text("Repositório de contexto do chat", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { contextMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selected?.fullName ?: "Escolher repositório para o chat", maxLines = 1)
                }
                DropdownMenu(expanded = contextMenuOpen, onDismissRequest = { contextMenuOpen = false }) {
                    repositories.forEach { repository ->
                        DropdownMenuItem(
                            text = { Text("${repository.fullName} · ${if (repository.isPrivate) "privado" else "público"}") },
                            onClick = {
                                preferences.saveSelectedRepository(repository)
                                selected = repository
                                contextMenuOpen = false
                                status = "Repositório de contexto do chat: ${repository.fullName}."
                            },
                        )
                    }
                }
                selected?.let { repository ->
                    Text(
                        "${if (repository.isPrivate) "Privado" else "Público"} · branch ${repository.defaultBranch.ifBlank { "não definida" }} · ${if (repository.canPush) "escrita disponível" else "somente leitura detectada"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repository.htmlUrl))) }
                    }) { Text("Abrir repositório de contexto") }
                }
                Text("Repositório dedicado à compilação de APK", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { buildMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedBuild?.fullName ?: "Escolher repositório privado de build", maxLines = 1)
                }
                DropdownMenu(expanded = buildMenuOpen, onDismissRequest = { buildMenuOpen = false }) {
                    repositories.forEach { repository ->
                        DropdownMenuItem(
                            text = { Text("${repository.fullName} · ${if (repository.isPrivate) "privado" else "público"} · ${if (repository.canPush) "escrita" else "somente leitura"}") },
                            onClick = {
                                preferences.saveSelectedBuildRepository(repository)
                                selectedBuild = repository
                                buildMenuOpen = false
                                status = "Repositório de build selecionado: ${repository.fullName}. O build remoto vai validar privacidade e permissões antes de escrever."
                            },
                        )
                    }
                }
                selectedBuild?.let { repository ->
                    Text(
                        "${if (repository.isPrivate) "Privado" else "Público"} · branch ${repository.defaultBranch.ifBlank { "não definida" }} · ${if (repository.canPush) "escrita disponível" else "somente leitura detectada"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(enabled = !busy, onClick = {
                    if (!session.hasStoredAccessToken()) connected = false else {
                        busy = true
                        scope.launch {
                            try { refresh() } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                status = error.message ?: "Não foi possível atualizar a lista."
                            } finally { busy = false }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Atualizar lista de repositórios") }
                TextButton(enabled = !busy, onClick = {
                    session.disconnect()
                    connected = false
                    deviceAuthorization = null
                    selected = null
                    repositories = emptyList()
                    status = "Token removido deste dispositivo. Para revogar a autorização, acesse GitHub → Settings → Applications."
                }) { Text("Desconectar neste dispositivo") }
            }
            Text(
                "A autorização solicita os escopos repo e workflow, que são amplos. As seleções de contexto do chat e de build são separadas. O código só é enviado ao provedor de IA se você habilitar o contexto no chat; o build exige consentimento separado.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status.isNotBlank()) Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (status.contains("não foi", true) || status.contains("expirou", true) || status.contains("recusada", true))
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
