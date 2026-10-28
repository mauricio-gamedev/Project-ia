# Conector GitHub do Núcleo AI

## Configuração única

A conexão usa o OAuth Device Flow do GitHub. Isso evita pedir senha da conta ou colar um Personal Access Token em cada projeto, mas exige um Client ID público de um app OAuth do GitHub.

1. Abra GitHub → Settings → Developer settings → OAuth Apps e registre um OAuth App para o Núcleo AI.
2. Use um nome e uma Homepage URL que você reconheça. A callback URL é exigida pelo cadastro do OAuth App, mas o Device Flow não usa redirecionamento de callback.
3. Habilite **Enable Device Flow** no app OAuth e salve.
4. Copie o **Client ID** (não o client secret) para Configurações → Conector GitHub no Núcleo AI.
5. Toque em Conectar GitHub, abra a página do GitHub e digite o código exibido pelo app.
6. Escolha dois alvos independentes: **repositório de contexto do chat** (leitura opcional; pode ser qualquer repositório que sua conta tenha autorização para ler) e **repositório dedicado de build** (deve ser privado e permitir escrita). Você pode usar o mesmo, mas recomendamos separá-los para não colocar o workflow de build no repositório principal do projeto.

Documentação oficial: [criação de OAuth App](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app) e [Device Flow](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps).

## Escopos e limites

O fluxo atual solicita os escopos OAuth `repo`, `workflow` e `offline_access`:

- `repo` permite acesso amplo de leitura e gravação a repositórios públicos e privados que a conta pode acessar.
- `workflow` permite que a aplicação atualize workflows do GitHub Actions, necessário para o build remoto.
- `offline_access` permite receber tokens curtos e um refresh token, quando o OAuth App suporta tokens expirantes.

Esses escopos são amplos. Embora a interface do Núcleo use seleções explícitas para contexto e build e exija consentimento separado antes do build, o token OAuth tem permissões sobre os repositórios abrangidos pelo escopo. Revise o pedido de autorização antes de aprovar. Para uma futura distribuição ampla, a opção preferível é um GitHub App com permissões granulares e instalação limitada a repositórios selecionados; isso exige registrar e manter um app GitHub próprio.

O token de acesso e o refresh token são guardados cifrados usando o Android Keystore. O app renova automaticamente tokens expirantes quando precisa acessar o GitHub. O refresh token é rotativo: o novo par substitui o anterior. Se o refresh token expirar ou for revogado, conecte novamente a conta.

Desconectar no Núcleo remove os tokens guardados neste dispositivo. Para revogar efetivamente a autorização do OAuth App, acesse GitHub → Settings → Applications → Authorized OAuth Apps.

## Uso no app

- **Chat:** a conexão permite selecionar um repositório e ativar opcionalmente “Contexto de código”. Só quando essa opção está marcada o Núcleo lê a árvore e um conjunto limitado de arquivos e os envia ao provedor de IA configurado. O token GitHub nunca é incluído no prompt. Trate código privado como informação sensível.
- **Project Factory:** usa a mesma conta, mas lê a seleção separada de repositório de build. A compilação só aceita repositório privado com permissão de escrita e mantém o consentimento de envio/execução separado.
- **Build remoto:** o Núcleo pode criar ou atualizar o workflow próprio do build, enviar o ZIP do projeto para uma branch temporária e executar Gradle no GitHub Actions. Não envia a chave de API da OpenRouter para o runner. Revise o código e os scripts Gradle antes de consentir.
- **Limite atual:** o chat lê o repositório para contexto, mas não grava alterações arbitrárias nos arquivos do repositório diretamente. As respostas e alterações propostas continuam no chat/Project Factory; a função de build remoto envia fontes do projeto para o repositório privado de build.

## Tratamento de falhas

- Client ID inválido ou Device Flow desligado: corrija o OAuth App e inicie a conexão novamente.
- Repositório não listado: confirme que sua conta possui acesso a ele e toque em Atualizar.
- HTTP 401: reconecte a conta.
- HTTP 403: verifique autorização da organização, escopos e limites da API.
- Repositório público selecionado para build: escolha um repositório privado.
- Build falhou: abra o link da execução do GitHub Actions e revise o log; o app não deve tratar fontes ou um job falho como APK.
