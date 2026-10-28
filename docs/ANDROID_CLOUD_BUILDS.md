# Compilação de APK Android pelo Núcleo AI

## Fluxo atual

O Android do celular não contém uma toolchain completa capaz de compilar qualquer projeto Android arbitrário. A Project Factory completa a estrutura Android básica, guarda o projeto em staging privado e, após consentimento separado, envia o ZIP para um repositório GitHub **privado**. O GitHub Actions executa Gradle com JDK 17 e Android SDK 36, verifica a assinatura do APK, publica o APK e o checksum SHA-256. O app baixa o artefato, exige o checksum válido e permite salvar o APK pelo seletor do Android.

Ter arquivos Kotlin, um Manifest ou uma build.gradle não comprova que há APK. A interface só declara compilação concluída depois que o workflow remoto termina com sucesso e o artefato passa pela verificação.

## Configuração inicial — uma vez

1. Crie um repositório privado dedicado aos builds, inicializado com README e uma branch padrão.
2. Configure o Conector GitHub em **Configurações → Conector GitHub**. O Núcleo usa OAuth Device Flow: registre um OAuth App na sua conta GitHub, habilite **Device Flow** e informe o Client ID público no app. Não informe senha da conta nem cole um PAT.
3. Toque em **Conectar GitHub**, abra a página oficial do GitHub e autorize o aplicativo com o código mostrado.
4. Em Configurações → Conector GitHub, escolha o **repositório privado de build** separadamente do repositório que o chat usa para contexto. Recomendamos um repositório dedicado para que o workflow e os ZIPs de build não sejam escritos no seu projeto principal.
5. Na Project Factory, gere e revise o projeto Android, marque o consentimento de build remoto e toque em **Compilar APK na nuvem**.

Detalhes do OAuth e escopos: [GITHUB_CONNECTOR.md](GITHUB_CONNECTOR.md). A autorização atual usa os escopos OAuth `repo`, `workflow` e `offline_access`; eles são amplos. Revise cuidadosamente a tela de autorização. Para distribuição do app a mais usuários, prefira evoluir para um GitHub App com permissões granulares e instalação limitada aos repositórios desejados.

O Núcleo instala `nucleo-android-build.yml` no repositório selecionado. Se já existir um workflow com esse nome que não pertença ao Núcleo AI, ele recusa sobrescrevê-lo. O ZIP do projeto é enviado para uma branch de build separada; a branch e o histórico de execução podem ser revisados ou apagados pelo dono do repositório.

## Segurança e privacidade

- O código-fonte é enviado somente ao repositório privado selecionado. Qualquer pessoa com acesso a esse repositório poderá ler os arquivos.
- O chat só lê e envia um conjunto limitado de arquivos do repositório ao provedor de IA quando o usuário ativa expressamente **Contexto de código**. O token GitHub nunca é colocado no prompt.
- O build remoto tem consentimento separado, pois scripts Gradle e plugins são executáveis e podem rodar código no runner. Revise o fonte antes de consentir.
- O workflow do build usa permissão `contents: read` para seu próprio `GITHUB_TOKEN`; não recebe a chave da OpenRouter nem outros segredos configurados no app.
- Desconectar o GitHub remove os tokens guardados neste dispositivo, mas a revogação da autorização OAuth deve ser feita em GitHub → Settings → Applications → Authorized OAuth Apps.
- O build produz um **APK de depuração**, não um release de publicação na Play Store. Não instala o APK automaticamente.

## Assinatura do APK gerado

A compilação remota de projetos gera um APK de depuração para teste. Ela não usa a chave de assinatura de release do próprio Núcleo AI. A assinatura desses projetos é independente da assinatura de atualização do app Núcleo.

## Limites atuais

- Estrutura esperada: projeto Android com módulo Gradle `:app`, Gradle 8.13, AGP 8.13.2, Kotlin 2.3.21, Android SDK 36 e minSdk 26.
- ZIP do fonte limitado a 8 MiB.
- O app aguarda até vinte minutos. Um timeout da interface não cancela necessariamente o runner; use o link da execução para consultar o status.
- Se Gradle falhar ou o artefato/checksum estiver ausente ou incorreto, o app não deve declarar APK pronto.
- Build bem-sucedido comprova compilação e assinatura/checksum do APK, não a correção de todas as funções solicitadas. Teste o aplicativo gerado no dispositivo.
