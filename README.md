# Núcleo AI — base autoral para Android

> **Codinome provisório · versão 0.5.0-alpha · catálogo de modelos + timeline sincronizada**

Núcleo AI é uma aplicação Android nativa pensada como um ambiente pessoal de engenharia de software: memória persistente, conversas com modelos configuráveis, tarefas que podem ser retomadas e futura execução assistida de alterações em repositórios.

A base foi construída do zero para este projeto. Ela **não incorpora arquivos-fonte dos dois arquivos de referência enviados na conversa**. Bibliotecas externas são declaradas como dependências e continuam sujeitas às respectivas licenças.

## O que já está implementado nesta base

- Interface Android nativa em Kotlin + Jetpack Compose.
- Persistência local com Room/SQLite para projetos, tarefas, conversas e memórias.
- Seleção de pastas de projeto por Storage Access Framework, com permissão de leitura persistente limitada à pasta escolhida.
- Chat com histórico persistente e contexto de memórias recentes.
- Adaptador para provedores que exponham endpoint compatível com `POST /chat/completions`.
- AutoRouter com perfis alternativos, credenciais cifradas individualmente, fallback em cotas/timeouts e cooldown por rota.
- Catálogo sob demanda da OpenRouter com pesquisa, filtro por preço publicado zerado, janela de contexto e estimativa de preço de entrada/saída; selecionar um modelo apenas preenche o formulário.
- Linha do tempo inicial baseada em registros Room de projetos, tarefas, memórias e conversas, ordenados por timestamps persistidos.
- Conclusão de tarefas e criação da memória do plano em uma transação única, respeitando cancelamentos que ocorram durante uma chamada remota.
- Bloqueio de salvamento quando o provedor/endpoint mudou, mas uma nova chave ainda não foi informada.
- Context handoff por orçamento aproximado: preserva instruções, pedido original e mensagens recentes ao alternar de modelo.
- Revisão estática assistida por IA e propostas de reparo em staging separado, com confirmação explícita e sem executar o código.
- Endpoints remotos precisam usar HTTPS; credenciais não são compartilhadas com o catálogo da OpenRouter quando a rota configurada é de outro host.
- Chave de API cifrada com AES-GCM; a chave de criptografia é gerenciada pelo Android Keystore.
- Tarefas de planejamento executadas em WorkManager, com estado persistido, checkpoints de conclusão e tentativas limitadas para falhas transitórias de rede.
- **NúcleoLang 0.2 alfa (fundação expansível)**: lexer, parser, AST e interpretador Kotlin autorais para variáveis, funções, condições, loops, listas mutáveis, indexação, bibliotecas iniciais e funções de host registradas explicitamente, com orçamento de execução e sem shell/rede/arquivos embutidos.
- Playground NúcleoLang dentro do app para editar e executar exemplos localmente, sem Termux.
- **Project Factory alpha**: geração assistida por modelo de manifestos JSON com solicitação de modo JSON quando suportado pelo provedor, validação de caminhos e perfis, staging privado com hashes SHA-256, prévia de conteúdo por arquivo com verificação de integridade e descarte seguro do rascunho, além da exportação dos arquivos-fonte por três rotas: acesso amplo a arquivos via permissão especial `MANAGE_EXTERNAL_STORAGE`, seletor Android “Salvar arquivo” (Storage Access Framework) e exportação opcional via Shizuku. O acesso amplo só é usado após autorização explícita nas configurações do Android. A descrição aceita até 4.000 caracteres e mostra um contador no campo.
- Leitura opcional e limitada de arquivos textuais de um projeto, somente depois de consentimento explícito na criação da tarefa.
- Testes unitários para validar configurações dos provedores.
- Workflow de CI para executar testes e gerar um APK de depuração.

## O que ainda não está implementado

É importante distinguir estrutura de funcionalidade concluída. Esta versão **não** modifica arquivos de código, não executa comandos de shell, não implementa Git, não cria pull requests e não inclui um runtime de inferência local. NúcleoLang ainda não compila APKs nem expõe ferramentas de engenharia: ela é uma linguagem interpretada básica cuja integração com o agente será desenvolvida depois. O Project Factory agora tem uma primeira implementação alfa: gera um manifesto com o modelo configurado, valida caminhos e arquivos essenciais, guarda o resultado em staging privado e permite exportar o código-fonte após ação explícita do usuário. Ainda não executa os scripts gerados nem compila os projetos criados. O núcleo de tarefas gera planos técnicos, não implementações automáticas. A interface `LocalModelRuntime` é apenas o ponto de extensão para integrar um motor local numa etapa futura.

Também ainda não há busca semântica por embeddings, indexação incremental completa, agentes paralelos ou treinamento de modelo próprio. Esses recursos não devem ser tratados como existentes só porque constam do roadmap.

## Stack inicial

| Camada | Tecnologia |
|---|---|
| App | Kotlin, Android SDK 36, Jetpack Compose |
| UI | Material 3 |
| Dados locais | Room 2.8.5 / SQLite |
| Tarefas em segundo plano | WorkManager |
| HTTP | OkHttp |
| Segredos | Android Keystore + AES/GCM |
| Build | Gradle 8.13, AGP 8.13.2, Kotlin 2.3.21, KSP 2.3.12 |

Requisitos oficiais de compatibilidade: [AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes) e [Kotlin/Gradle](https://kotlinlang.org/docs/gradle-configure-project.html). O projeto usa JDK 17 e Android SDK Platform 36.

## Como abrir e compilar

1. Abra a pasta raiz `nucleo-ai-android` no Android Studio.
2. Instale o JDK 17 e a plataforma Android SDK 36 pelo SDK Manager.
3. Sincronize o Gradle e execute a configuração `app` num dispositivo ou emulador.
4. Para validar por terminal, execute:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

No Windows:

```bat
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Os scripts incluídos baixam a distribuição Gradle 8.13 do endereço HTTPS oficial quando ela ainda não está em cache e verificam o SHA-256 oficial fixado no projeto. O primeiro build também precisa baixar as dependências do Maven. O build de referência da versão anterior passou no GitHub Actions. Esta versão precisa passar novamente pelo workflow após as alterações; compilar o próprio Núcleo AI não significa que os projetos gerados pela Project Factory sejam compiláveis.

O APK de depuração, quando o build terminar com sucesso, deve aparecer em:

`app/build/outputs/apk/debug/app-debug.apk`

## Conectar um modelo remoto

Em **Configurações**, preencha:

- Nome do provedor (rótulo local livre).
- URL base HTTPS, por exemplo `https://api.openai.com/v1` quando o serviço for compatível com o formato de chat-completions.
- Identificador do modelo aceito pelo provedor.
- Chave de API.

Toque em **Salvar e testar conexão**. A configuração genérica não garante compatibilidade com toda API: o serviço precisa aceitar Bearer authentication e o formato de `POST /chat/completions`.

Nunca inclua a chave na URL, em prompts, arquivos de projeto ou commits. O app rejeita URLs com HTTP puro, credenciais embutidas, query string ou fragmento. Modelos locais nativos ainda não são executados nesta versão.

## Privacidade do projeto

- Selecionar uma pasta não faz upload imediato dos arquivos.
- Ao vincular uma pasta a uma tarefa, o app exige confirmação antes de enviar um snapshot limitado ao provedor configurado.
- O snapshot ignora alguns arquivos e diretórios comuns de segredo/build e tenta redigir padrões reconhecíveis. **Nenhum filtro consegue garantir a detecção de todos os segredos**; revise o conteúdo e não vincule repositórios confidenciais a serviços externos sem autorização.
- O chat pode enviar mensagens recentes e algumas memórias persistentes ao provedor, conforme informado na interface.
- O conteúdo armazenado pelo provedor segue os termos e políticas desse próprio provedor.

Veja [docs/AI_ROUTING_AND_CODE_REVIEW.md](docs/AI_ROUTING_AND_CODE_REVIEW.md) para configurar o AutoRouter, conectar um gateway FreeLLMAPI local, entender a transferência de contexto e usar a revisão/reparo de código. Também consulte [docs/SECURITY_MODEL.md](docs/SECURITY_MODEL.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), a [especificação NúcleoLang](docs/NUCLEOLANG_SPEC.md), a [arquitetura de expansão da linguagem](docs/NUCLEOLANG_PLATFORM_ARCHITECTURE.md) e a [especificação Project Factory](docs/PROJECT_FACTORY_SPEC.md).

## Autoria e licença

O código de aplicação nesta base foi escrito de forma independente para o Núcleo AI, sem reutilizar arquivos-fonte dos arquivos de referência. Dependências de terceiros não são propriedade deste projeto e suas licenças continuam válidas. **A licença de distribuição do Núcleo AI ainda não foi escolhida**; defina-a antes de publicar ou redistribuir o repositório.

## Roadmap resumido

1. **Concluído nesta alpha:** perfis de provedor alternativos, failover/cooldowns, seleção por tipo de tarefa, transferência de contexto com limites e revisão estática por IA com propostas de reparo isoladas.
2. **Próximo:** validação de ponta a ponta no dispositivo, testes de compatibilidade dos provedores e visibilidade de cotas/cooldowns na interface.
3. **Depois:** executor de lint/test/build em ambiente isolado, com limites de recursos, cancelamento, rede restrita, diff e rollback.
4. **Em andamento nesta alpha:** ampliar descoberta de modelos e provedores compatíveis sem assumir que rótulos de capacidade ou cotas gratuitas são garantidos.
5. Integrar runtime local após medir memória, velocidade e compatibilidade reais no Android.
