# Roadmap orientado a resultados

## M0 — Fundação (esta entrega)

- [x] Projeto Android independente
- [x] Interface inicial Compose
- [x] Room com projetos, tarefas, mensagens e memórias
- [x] Configuração remota genérica + chave cifrada
- [x] Chat com memória persistente recente
- [x] Tarefas duráveis para gerar plano técnico
- [x] Snapshot textual de projeto limitado e consentido
- [x] Testes unitários da validação de configuração
- [x] Workflow Android CI
- [x] Build Android validado em runner real no commit de referência 0.2.0-alpha
- [ ] Instalação e smoke test no Galaxy A06

## M1 — NúcleoLang autoral e validação

- [x] Lexer e parser iniciais
- [x] AST e interpretador Kotlin independente
- [x] Aritmética, textos, booleanos, escopos, funções e recursão
- [x] Orçamento de execução, limite de chamadas e sem efeitos de sistema embutidos
- [x] Testes de regressão para recursos básicos e limites
- [x] Proteção contra nesting sintático excessivo
- [x] Playground da linguagem acessível na tela inicial do app
- [ ] Cancelamento cooperativo integrado à UI
- [x] Build Android real e execução dos testes no CI (commit de referência 0.2.0-alpha)

## M2 — Validação e qualidade

- Build e testes no CI, correção de avisos/erros reais
- Testes de persistência Room, reabertura de processo e recuperação de tarefa
- Teste de ciclo de vida e permissão de pasta revogada
- Teste de provedor com respostas de sucesso, HTTP 401/429/500, timeout e resposta inválida
- Melhorar mensagens de erro sem expor segredo ou resposta sensível

## M3 — Project Factory (primeira alfa)

- [x] Pedir consentimento antes de enviar a descrição ao modelo remoto
- [x] Interpretar manifesto JSON de arquivos textuais gerados pelo modelo
- [x] Validar caminhos relativos, traversal, duplicatas, colisões arquivo/pasta e nomes de arquivos sensíveis
- [x] Aplicar limites de 300 arquivos, 512 KiB por arquivo e 4 MiB por projeto
- [x] Validar arquivos essenciais nos perfis Android, web e Python
- [x] Gerar staging privado e evidências SHA-256 por arquivo
- [x] Exportar para uma nova pasta escolhida pelo usuário via Storage Access Framework, sem sobrescrever arquivos existentes
- [ ] Validar código gerado com diagnósticos estáticos e revisão de dependências
- [ ] Implementar adaptador de build para projetos gerados e validar um APK externo
- [ ] Acrescentar diff revisável, histórico e remoção/limpeza de staging

- Scanner de repositório incremental
- Identificador de linguagens/build systems e símbolos básicos
- Ferramenta de leitura de arquivo com limites e exclusões
- Busca lexical contextual em índice local
- Relatório de análise com path/linha e separação entre fatos e hipóteses

## M4 — Patch e validação controlados

- Geração de patch em staging, sem gravar direto
- Diff e aprovação explícita
- Aplicação restrita à pasta autorizada
- Testes e build sob limites definidos
- Registro de artefatos, logs redigidos e rollback

## M5 — Inferência híbrida

- Benchmark de runtimes locais possíveis no Android ARM64
- Importação e remoção de modelos, checagem de licença e armazenamento
- Avaliação de RAM, latência, temperatura, consumo e compatibilidade real no A06
- Política de roteamento local/remoto, fallback e visibilidade dos custos

## M6 — DSL de engenharia e autonomia avançada

- Definir operações declarativas NúcleoLang para projeto, análise, patch, testes e relatórios
- Integrar tools por capacidades registradas e validadas; não permitir chamadas implícitas
- Checkpoints, revisão de diff e permissões por tarefa
- Agentes especializados e roteamento híbrido depois da validação das ferramentas

