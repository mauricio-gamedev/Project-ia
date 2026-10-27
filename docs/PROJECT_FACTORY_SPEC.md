# Project Factory — especificação inicial

## Objetivo

O Project Factory será o subsistema que converte um objetivo em um projeto reproduzível: escolher uma stack, preparar estrutura e dependências, gerar arquivos, executar verificações, compilar quando houver toolchain disponível e entregar artefatos com um relatório de validação. A interface e o controle de tarefas ficam no Android; o local real de compilação será um adaptador selecionável, não uma suposição.

## Estado implementado nesta alfa

A tela Project Factory solicita o nome, o perfil e os requisitos do projeto. A descrição aceita até 4.000 caracteres, com contador visível e limite aplicado na entrada. Depois de consentimento explícito, envia somente essa descrição ao provedor configurado e espera um manifesto JSON com arquivos de texto. Quando suportado, a solicitação ativa o modo JSON do provedor; se um endpoint OpenAI-compatible rejeitar esse parâmetro com HTTP 400, a tela tenta uma única vez sem o modo opcional. A validação apresenta um diagnóstico mais útil se o modelo ainda retornar JSON inválido. `ProjectFactory` valida o manifesto e grava os arquivos aceitos numa pasta privada de staging, calculando tamanho e SHA-256 de cada arquivo. O usuário pode revisar a lista e exportar o fonte como arquivo ZIP pela ação CreateDocument do Android Storage Access Framework, escolhendo um destino de arquivo em vez de autorizar uma árvore inteira de pastas. O exportador verifica tamanho e SHA-256 novamente e tenta remover o ZIP se a exportação falhar. Como alternativa, há uma rota opcional que usa um Shizuku UserService autorizado para gravar diretamente em `Download/NucleoAIProjects`; ela envia apenas os bytes do ZIP gerado, não lê o staging privado pelo processo privilegiado nem executa comandos de shell. O caminho primário alternativo em Android 11+ solicita a permissão especial `MANAGE_EXTERNAL_STORAGE` abrindo `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`; só depois da autorização grava diretamente o ZIP em `Download/NucleoAIProjects`. A permissão não é concedida automaticamente.

Limites atuais: 300 arquivos, 512 KiB por arquivo, 4 MiB por projeto e 240 caracteres por caminho. Perfis disponíveis: Android, web, Python e genérico. Os perfis Android/web/Python exigem um conjunto mínimo de arquivos. A validação não prova que o código compila, não executa código gerado, não resolve dependências e não produz APKs. A saída permanece marcada como aguardando revisão.

## Fluxo proposto

1. **Entender o objetivo:** levantar plataforma, funções, restrições, artefatos esperados e critérios de aceitação.
2. **Escolher um perfil:** preferir templates e geradores determinísticos para a estrutura inicial; usar o modelo de IA para código específico da necessidade.
3. **Criar staging privado:** gerar todos os arquivos em área temporária do app, nunca sobrescrever diretamente uma pasta do usuário.
4. **Validar estrutura:** conferir arquivos obrigatórios, caminhos, IDs, referências, dependências, segredos acidentais e conflitos.
5. **Revisar alterações:** exibir o que será criado/modificado e exigir autorização antes de exportar para a pasta selecionada.
6. **Executar checks:** testes estáticos locais primeiro; testes e compilação por um build adapter apropriado.
7. **Registrar evidências:** guardar comandos/ações, versões, logs redigidos, duração, resultado e hashes dos artefatos.
8. **Entregar:** exportar o projeto e os artefatos disponíveis, sem alegar build bem-sucedido caso o build não tenha ocorrido.

## Perfis iniciais candidatos

- Android nativo: Kotlin, Jetpack Compose, Gradle, manifest, testes e configurações de build.
- Aplicações web: TypeScript, estrutura de projeto, scripts de build e testes.
- Python: módulos, ambiente/dependências declarados e testes.
- Jogos: perfis específicos para Godot e outros motores, condicionados à disponibilidade e licença das ferramentas.
- Projeto genérico: arquivos arbitrários gerados a partir de um plano validado, sempre em staging.

A lista é uma ordem de implementação, não uma garantia de compatibilidade universal. Cada perfil deverá ter testes próprios e uma definição explícita do que pode validar.

## Construção de APKs

Gerar a fonte de um projeto Android e produzir um APK instalável são etapas diferentes. A primeira é viável localmente no aplicativo. A segunda exige JDK/Gradle, Android SDK, build-tools, dependências compatíveis e recursos suficientes.

O Android será o ambiente de controle principal. O subsistema deve expor adaptadores de build para:

- **Local**, apenas depois de escolher e validar uma toolchain compatível com Android e o dispositivo-alvo; não presumir que executar Gradle diretamente no processo Android funciona.
- **Build remoto opcional**, por exemplo CI ligada a um repositório, apenas depois de o usuário configurar credenciais e autorizar o envio do código.
- **Exportação de fonte**, sempre disponível, quando nenhum executor de build for configurado.

Nenhum código de projeto deve ser enviado para um serviço de build sem consentimento explícito. Segredos devem ser excluídos dos pacotes por padrão. Um APK só será marcado como validado quando houver resultado de build bem-sucedido e verificações pós-build configuradas.

## Contrato de build adapter (rascunho)

Cada implementação deverá expor operações equivalentes a:

- `inspect(projectSpec)` — declarar toolchains disponíveis, versões e limitações.
- `validate(stagingProject)` — validar precondições antes de compilar.
- `build(stagingProject, buildProfile, cancellation)` — produzir um job identificável e retomável.
- `getStatus(jobId)` — consultar estado e evidências.
- `collectArtifacts(jobId)` — listar artefatos com caminho, tamanho, hash e metadados.
- `cancel(jobId)` — solicitar cancelamento seguro.

Resultados possíveis são tipados: `SUCCESS`, `FAILED`, `CANCELLED`, `BLOCKED` ou `UNSUPPORTED`. `SUCCESS` precisa referenciar evidências verificáveis; não pode ser inferido de texto gerado por modelo.

## Segurança e recuperação

- Raiz do projeto autorizada explicitamente e validação contra path traversal.
- Staging antes de qualquer escrita externa.
- Limites de arquivo, tamanho total, tempo e consumo configuráveis.
- Cancelamento cooperativo e checkpoints; builds não devem bloquear a UI.
- Dependências e plugins de build auditáveis, sem baixar executáveis cegamente.
- Aprovação explícita para publicação, assinatura de release e acesso a credenciais.
- Rollback ou exportação separada em caso de falha, sem destruir o projeto anterior.

## Critérios de aceitação do primeiro perfil Android

- Gerar um projeto mínimo reproduzível com build Gradle e teste simples.
- Inspecionar todos os caminhos e manifest antes de exportar.
- Detectar ausência de JDK/SDK ou incompatibilidade de plugin como `BLOCKED`, nunca como sucesso.
- Produzir APK de debug apenas quando um adapter concluir o build e o artefato for localizado e validado.
- Registrar versão de Gradle/AGP/SDK, saída resumida, hash SHA-256 do APK e status de testes.
