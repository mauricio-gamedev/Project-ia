# Assinatura estável dos APKs do Núcleo AI

## Secrets necessários

Na página do repositório GitHub, abra **Settings → Secrets and variables → Actions → New repository secret** e crie estes quatro secrets:

- `ANDROID_SIGNING_KEYSTORE_BASE64`: conteúdo completo do arquivo `nucleo-ai-signing-keystore.base64.txt` que acompanha o pacote privado de assinatura.
- `ANDROID_SIGNING_STORE_PASSWORD`: senha do keystore no arquivo privado de credenciais.
- `ANDROID_SIGNING_KEY_ALIAS`: `nucleo-ai-release`.
- `ANDROID_SIGNING_KEY_PASSWORD`: senha da chave no arquivo privado de credenciais.

A keystore privada preparada para esta atualização está no pacote de assinatura entregue separadamente ao usuário. Ela usa o alias `nucleo-ai-release` e o formato PKCS12, configurado explicitamente no Gradle. A keystore e as senhas **não devem ser commitadas no repositório, issue, PR ou log**. Mantenha cópias privadas e um backup offline. Perder a chave de assinatura impede publicar atualizações com a mesma identidade Android usando outra chave.

## Como o CI se comporta

Quando os quatro secrets existem e a keystore pode ser aberta, Gradle assina a variante de depuração com essa chave persistente e o workflow compara o certificado do APK com o certificado da keystore. Se os secrets estiverem ausentes, o CI ainda executa testes e build, mas emite um aviso: o APK usa a chave temporária do runner e não deve ser tratado como atualização segura por cima de uma instalação anterior.

## Transição única da alpha anterior

Android exige que o application ID e o certificado de assinatura sejam compatíveis com o aplicativo instalado para substituir um APK. O artefato 0.4.0-alpha e o artefato 0.5.0-alpha gerado sem secrets têm impressões SHA-256 diferentes; a chave privada temporária usada nos artefatos antigos não está disponível no repositório. Portanto, não é possível assinar uma nova versão com o certificado antigo.

Depois de configurar os secrets e gerar um APK assinado pela keystore estável, será necessário **remover as instalações alpha antigas uma única vez** e instalar a nova versão estável. Os builds continuados com `versionCode` crescente e a mesma keystore permitirão atualizar por cima sem repetir essa etapa. Os APKs alpha anteriores usavam application IDs separados ou um certificado temporário. Isso pode apagar dados privados locais, inclusive histórico e chave de API salva; guarde a chave de API para cadastrá-la novamente. A identidade consolidada do app passa a ser `io.github.astromg01.nucleo`, sem sufixo de debug, para que debug/release compartilhem o mesmo application ID. A partir da instalação estável, mantenha o mesmo application ID, a mesma keystore e um versionCode crescente nas atualizações futuras para atualizar por cima, sem manter duas variantes instaladas.

## Verificação

O workflow confirma o certificado do APK com o comando oficial `apksigner verify --print-certs` e compara a impressão digital SHA-256 com a keystore configurada. O resumo do job informa se o build foi assinado pela chave estável ou pelo certificado temporário.
