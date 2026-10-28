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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.astromg01.nucleo.AppContainer
import io.github.astromg01.nucleo.domain.github.GITHUB_OAUTH_TOKEN_ID
import io.github.astromg01.nucleo.domain.github.GitHubConnector
import io.github.astromg01.nucleo.domain.github.GitHubConnectorPreferences
import io.github.astromg01.nucleo.domain.github.GitHubDeviceAuthorization
import io.github.astromg01.nucleo.domain.github.GitHubRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun GitHubConnectorCard(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { GitHubConnectorPreferences(context) }
    val connector = remember { GitHubConnector() }
    var clientId by remember { mutableStateOf(preferences.clientId()) }
    var connected by remember { mutableStateOf(container.secretStore.readApiKey(GITHUB_OAUTH_TOKEN_ID) != null) }
    var repositories by remember { mutableStateOf<List<GitHubRepository>>(emptyList()) }
    var selected by remember { mutableStateOf(preferences.selectedRepository()) }
    var menuOpen by remember { mutableStateOf(false) }
    var deviceAuthorization by remember { mutableStateOf<GitHubDeviceAuthorization?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    suspend fun refresh(token: String) {
        repositories = connector.listRepositories(token)
        selected = preferences.selectedRepository()
        status = "Conexão GitHub pronta · ${repositories.size} repositórios carregados."
    }

    LaunchedEffect(connected) {
        if (connected) {
            val token = container.secretStore.readApiKey(GITHUB_OAUTH_TOKEN_ID)
            if (!token.isNullOrBlank()) {
                try { refresh(token) } catch (error: Exception) {
                    if (error !is CancellationException) status = error.message ?: "Não foi possível atualizar repositórios."
                }
            }
        } else repositories = emptyList()
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
                        enabled = !busy && clientId.isNotBlank(),
                        onClick = {
                            if (busy) return@Button
                            preferences.saveClientId(clientId)
                            busy = true
                            status = "Solicitando autorização…"
                            scope.launch {
                                try {
                                    val auth = connector.requestDeviceAuthorization(clientId)
                                    deviceAuthorization = auth
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(auth.verificationUri))) }
                                    status = "Digite o código no GitHub; o app vai detectar a autorização automaticamente."
                                    val token = connector.awaitAccessToken(clientId, auth) { update -> scope.launch { status = update } }
                                    container.secretStore.saveApiKey(GITHUB_OAUTH_TOKEN_ID, token)
                                    connected = true
                                    deviceAuthorization = null
                                    refresh(token)
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
                        }
                    }
                }
            } else {
                Text("Conta GitHub conectada", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selected?.fullName ?: "Escolher repositório", maxLines = 1)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            repositories.forEach { repository ->
                                DropdownMenuItem(
                                    text = { Text("${repository.fullName} · ${if (repository.isPrivate) "privado" else "público"}") },
                                    onClick = {
                                        preferences.saveSelectedRepository(repository)
                                        selected = repository
                                        menuOpen = false
                                        status = "Repositório ativo: ${repository.fullName}."
                                    },
                                )
                            }
                        }
                    }
                    OutlinedButton(enabled = !busy, onClick = {
                        val token = container.secretStore.readApiKey(GITHUB_OAUTH_TOKEN_ID)
                        if (token.isNullOrBlank()) connected = false else {
                            busy = true
                            scope.launch {
                                try { refresh(token) } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    status = error.message ?: "Não foi possível atualizar a lista."
                                } finally { busy = false }
                            }
                        }
                    }) { Text("Atualizar") }
                }
                selected?.let { repository ->
                    Text(
                        "${if (repository.isPrivate) "Privado" else "Público"} · branch ${repository.defaultBranch.ifBlank { "não definida" }} · ${if (repository.canPush) "escrita disponível" else "somente leitura detectada"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repository.htmlUrl))) }
                    }) { Text("Abrir repositório") }
                }
                TextButton(enabled = !busy, onClick = {
                    container.secretStore.removeApiKey(GITHUB_OAUTH_TOKEN_ID)
                    preferences.clearSelectedRepository()
                    connected = false
                    selected = null
                    repositories = emptyList()
                    status = "Token removido deste dispositivo. Para revogar a autorização, acesse GitHub → Settings → Applications."
                }) { Text("Desconectar neste dispositivo") }
            }
            Text(
                "A autorização solicita os escopos repo e workflow, que são amplos. O app usa apenas o repositório selecionado; o código só é enviado ao provedor de IA se você habilitar o contexto do GitHub no chat. O build exige consentimento separado.",
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
