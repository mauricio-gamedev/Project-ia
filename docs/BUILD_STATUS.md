# Estado da validação do build

Data da tentativa: 2026-10-21.

## Resultado neste ambiente

O comando `./gradlew --no-daemon assembleDebug testDebugUnitTest` não chegou à configuração ou compilação do projeto porque o ambiente não conseguiu resolver `services.gradle.org` para baixar Gradle 8.13. Não foi encontrado um Gradle pré-instalado nem um Android SDK local. Portanto, **não existe um APK gerado ou validado nesta sessão**.

## Caminho de validação disponível

O workflow `.github/workflows/android-ci.yml` executa testes unitários, compila o APK de depuração, verifica que o arquivo existe e não está vazio, calcula SHA-256 e publica o APK e o checksum como artefato do GitHub Actions. O workflow roda em `push`, `pull_request` e `workflow_dispatch`, em um runner Linux com acesso às dependências.

Artefato esperado: `app/build/outputs/apk/debug/app-debug.apk`. O `applicationId` de depuração contém o sufixo `.debug`.

Um build bem-sucedido no CI ainda não substitui um teste de instalação e abertura no Galaxy A06.
