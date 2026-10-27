# Compilação de APK Android pelo Núcleo AI

## O que o fluxo faz

O Android do celular não contém uma toolchain completa capaz de compilar qualquer projeto Android arbitrário. O Núcleo AI completa um conjunto mínimo de arquivos Gradle/Manifest quando necessário, exporta o fonte verificado para um ZIP e, após consentimento, pode enviar esse ZIP a um repositório GitHub **privado** do usuário. O GitHub Actions executa Gradle com JDK 17 e Android SDK 36, verifica o APK resultante, calcula SHA-256 e publica um artefato temporário. O aplicativo baixa o artefato, valida o checksum e permite salvar o APK no armazenamento Android.

Um arquivo-fonte presente não é evidência de compilação. O status de APK só aparece depois de o workflow terminar com sucesso e o artefato passar na verificação.

## Configuração única

1. Crie um repositório **privado** dedicado aos builds do Núcleo AI, inicializado com um README e uma branch padrão (main, por exemplo). Não use um repositório público.
2. Crie um fine-grained personal access token (PAT) restrito somente a esse repositório.
3. Conceda as permissões de repositório necessárias para a automação: **Contents: Read and write**, **Actions: Read and write** e **Workflows: Read and write**.
4. Na Project Factory, informe o proprietário, nome do repositório e token. Toque em **Verificar e salvar repositório privado**. O token é cifrado com a chave do Android Keystore; ele não é enviado ao modelo de IA.
5. Gere e revise um projeto Android. Marque o consentimento de build remoto e toque em **Compilar APK na nuvem**.

O Núcleo instala no repositório um workflow fixo chamado nucleo-android-build.yml. Se já existir um arquivo com esse nome que não foi criado pelo Núcleo AI, a automação recusa substituí-lo. O código-fonte do projeto é enviado como nucleo-source.zip em uma branch de build separada, dentro do repositório privado.

## Segurança e privacidade

- O consentimento de compilação é separado do consentimento para enviar a descrição ao modelo de IA.
- O ZIP com o código gerado é enviado ao repositório privado selecionado; qualquer pessoa com acesso a esse repositório pode ler o código.
- Gradle e plugins são executáveis: compilar um projeto gerado por IA pode executar código contido nos scripts de build. Revise os arquivos antes de autorizar a compilação. O workflow não recebe a chave da OpenRouter nem outros secrets da aplicação, e usa permissão contents: read para o seu próprio GITHUB_TOKEN.
- O Núcleo não envia o projeto para um repositório público, não executa Gradle dentro do aplicativo Android e não instala automaticamente o APK.
- A automação mantém a branch de build e o histórico de execução no repositório escolhido para diagnóstico. Você pode revisar e apagar essas branches depois.
- O APK produzido é um **APK de depuração**, assinado para teste. Não é um build de publicação da Play Store nem deve conter segredos privados.
- A chave de depuração fica em cache do GitHub Actions para tentar manter a assinatura entre builds do mesmo repositório. O cache pode expirar; por isso a assinatura de depuração não é uma estratégia de assinatura de release nem uma garantia permanente de atualização por cima. Para distribuição estável, será necessário configurar uma chave de release privada e persistente.

## Limites atuais

- Projeto Android com módulo Gradle :app, estrutura compatível com Gradle 8.13 / AGP 8.13.2 / Kotlin 2.3.21, Android SDK 36 e minSdk 26.
- O ZIP do fonte é limitado a 8 MiB; cada arquivo individual tem o limite validado pelo Project Factory.
- O adaptador aguarda até vinte minutos pelo resultado. Um timeout de espera não cancela necessariamente a execução no GitHub Actions; o link do workflow é mostrado para consulta.
- Se o build falhar, o Núcleo mostra o link do log; ele não deve afirmar que houve APK se o job falhar ou não publicar o artefato.
- Compilar não prova que todas as funcionalidades do aplicativo funcionam. Execute testes no dispositivo e inspecione permissões, comportamento e código antes de instalar.
