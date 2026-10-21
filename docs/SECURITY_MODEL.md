# Modelo inicial de segurança e privacidade

## Dados protegidos

- Chave de API do provedor.
- Conversas e memórias gravadas localmente.
- Códigos e documentos no diretório escolhido pelo usuário.
- URIs e permissões persistentes concedidas pelo Storage Access Framework.

## Controles desta versão

- A chave de API é cifrada com AES/GCM/NoPadding; a chave AES fica no Android Keystore. A base de dados não armazena essa chave.
- Backup Android é desativado para evitar exportar acidentalmente o banco e preferências cifradas.
- HTTP puro é rejeitado na configuração dos provedores remotos.
- A pasta é escolhida explicitamente pelo usuário; a permissão persistente solicitada é de leitura.
- Nenhum snapshot é enviado para tarefa associada a projeto sem consentimento persistido.
- A leitura do projeto é limitada a 5 níveis, 300 entradas visitadas, 18 trechos, 2.500 caracteres por arquivo e 16.000 caracteres de conteúdo total.
- `.git`, `build`, `node_modules`, arquivos de ambiente, chaves, alguns arquivos de credenciais e outros artefatos são ignorados.
- Instruções encontradas em arquivos são tratadas como conteúdo não confiável.
- Os erros não registram cabeçalhos HTTP nem a chave de API.

## Limitações conhecidas

- Padrões de redação não detectam todo segredo possível. Antes de vincular qualquer projeto a uma API externa, o usuário deve confirmar que pode compartilhar o código.
- Provedores remotos recebem os prompts/memórias enviados pelo aplicativo e podem processar ou reter esses dados segundo seus próprios termos.
- Não existe sandbox para execução de código porque execução de comandos ainda não foi implementada.
- O app ainda não oferece bloqueio biométrico, exportação/remoção seletiva de todo o histórico, política avançada por provedor ou gestão de múltiplas chaves.
- O uso de Android Keystore protege a chave em repouso, mas não protege contra um dispositivo desbloqueado comprometido ou exploração dentro do próprio app.

## Requisitos antes de habilitar escrita ou shell

- Raiz de projeto explícita e verificada para toda operação.
- Preview de diff antes de alterações sensíveis.
- Bloqueio de traversal (`..`) e de destinos fora da raiz.
- Execução em ambiente isolado e sem permissões desnecessárias.
- Lista allowlist de ferramentas, timeout, limite de CPU/memória, limites de tentativas e cancelamento.
- Nenhum acesso a credenciais ou rede externa concedido automaticamente a processos de build/teste.
- Logs sem secrets, comandos com argumentos sensíveis mascarados e caminho para reverter alterações.
