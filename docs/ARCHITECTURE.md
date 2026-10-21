# Arquitetura técnica

## Princípios

1. **Núcleo independente de provedores:** mensagens e respostas usam contratos próprios (`AiRequest`, `AiMessage`, `AiResponse`, `AiProvider`).
2. **Offline-first para estado:** histórico, tarefas, projetos e memórias são persistidos em Room.
3. **Retomada, não contexto infinito:** o estado de uma tarefa fica no banco; WorkManager executa o plano e aplica retentativas limitadas para erros de rede transitórios.
4. **Dados não são instruções:** conteúdo de arquivo é contexto não confiável e não deve alterar as regras do agente.
5. **Consentimento de envio:** o snapshot do projeto só é construído para uma tarefa vinculada cujo campo `shareProjectContext` esteja habilitado após confirmação explícita.
6. **Menor privilégio:** a seleção de pasta guarda permissão de leitura; escrita e shell não estão implementados.
7. **Evidência sobre alegações:** a versão atual não afirma que editou arquivos, executou comandos ou testou builds.

## Componentes

- `MainActivity` / `ui`: telas de início, conversa, projetos, tarefas, memória e configuração.
- `NucleoApplication` / `AppContainer`: composição manual das dependências sem framework de injeção.
- `data/local`: entidades Room e DAOs.
- `data/settings`: metadados de provedor e validação de URL.
- `core/security/SecretStore`: cifragem autenticada AES-GCM com chave Android Keystore.
- `core/ai/ModelRouter`: seleciona runtime local quando disponível e faz fallback para provedor remoto.
- `core/ai/OpenAiCompatibleProvider`: adaptador HTTP sem depender de SDK de um fornecedor.
- `domain/task/TaskCoordinator`: persiste e agenda o trabalho.
- `domain/task/ProjectSnapshotReader`: percorre árvore e trechos textuais sob limites de profundidade, tamanho e contagem.
- `worker/PlanningWorker`: produz plano, persiste resultado e memória dentro de uma transação.
- `core/lang`: lexer, parser, AST e interpretador da NúcleoLang 0.2 alfa, sem acesso direto ao SO.
- `ui/lang/LangPlaygroundScreen`: playground no aparelho para editar e executar programas NúcleoLang dentro de orçamento.
- `docs/PROJECT_FACTORY_SPEC.md`: especifica a futura geração de projetos em staging, validação e build adapters; não é código executável ainda.

## Fluxo de tarefa atual

1. Usuário descreve objetivo.
2. Opcionalmente escolhe projeto e aceita o envio limitado do snapshot.
3. `TaskCoordinator` grava a tarefa antes de enfileirá-la.
4. WorkManager executa `PlanningWorker` quando houver rede.
5. O snapshot local é limitado e arquivos secretos conhecidos são ignorados/redigidos.
6. O roteador usa runtime local apenas se um estiver integrado e pronto; caso contrário, usa o provedor HTTPS configurado.
7. Resultado e memória são salvos em transação Room.
8. Falhas de rede recebem retentativas limitadas; outros erros deixam a tarefa bloqueada com mensagem visível.

## Limites atuais

Não há runtime de modelo local concreto, escrita de código em projetos externos, shell, compilador Android embutido, integração Git ou agente que execute ciclos de alteração/teste. NúcleoLang executa programas próprios de escopo limitado, mas não gera ou compila projetos por si só. O chat usa contexto recente de conversa e algumas memórias globais; ainda não existe retrieval semântico nem contexto de chat selecionado por projeto.

## Evolução proposta

- Integrar NúcleoLang ao motor de tarefas depois de consolidar a semântica e o cancelamento.
- Implementar Project Factory com staging privado, diff e perfis determinísticos de projeto.
- Provar um build APK real por build adapter, com evidência e hash do artefato.

- Formalizar estados e eventos por tarefa; gravar checkpoints de cada etapa.
- Separar ferramenta de leitura, ferramenta de patch e executor de testes.
- Construir patches em staging, apresentar diff e exigir autorização antes de aplicar.
- Executar testes num ambiente isolado; detectar arquivos fora da raiz autorizada.
- Adicionar índice incremental local e retrieval lexical antes de investir em embeddings.
- Integrar um runtime Android local mediante benchmark de RAM/latência e uma licença compatível.
