# NúcleoLang — arquitetura de linguagem e expansão de plataformas

Status: proposta técnica para a etapa alfa; não implica que compiladores ou integrações descritos já existam.

## Objetivo

A NúcleoLang não deve ter um catálogo fechado de tipos de projeto. O core define a linguagem; bibliotecas, pacotes, capacidades autorizadas e backends extensíveis conectam a linguagem a novos domínios e plataformas. Não é possível prometer software arbitrário automaticamente: cada alvo depende de uma toolchain, SDK, API ou runtime real, compatível e testável.

## Camadas propostas

1. **Especificação normativa** — gramática, regras de escopo, valores, erros, módulos e compatibilidade versionada. O comportamento documentado torna-se contrato coberto por testes.
2. **Front-end** — lexer, parser, diagnósticos, AST e análise semântica. Não deve importar APIs Android nem chamar modelos diretamente.
3. **Sistema de tipos** — inferência/checagem estática progressiva, com tipos explícitos quando úteis, genéricos e diagnóstico antes da execução. Até sua implementação, a versão atual é dinamicamente tipada.
4. **Representação intermediária (IR)** — forma independente de destino, validada antes de executar ou compilar. Esta camada ainda não está implementada.
5. **Runtime** — valores, coleções, funções, orçamento, cancelamento cooperativo e rastreamento de erros. O runtime não concede acesso ao sistema por padrão.
6. **Biblioteca padrão** — coleções, texto, matemática, caminhos abstratos, JSON, data/hora e utilitários com contratos determinísticos.
7. **Capacidades do host** — integração explicitamente injetada, granular e com permissões. Exemplo: `project.readText` limitado à pasta autorizada; não uma função genérica que execute qualquer comando.
8. **Gerenciador de pacotes** — dependências versionadas, hashes, origem verificável, metadados de licença, lockfile e instalação reproduzível. Ainda não implementado.
9. **Backends** — plugins selecionáveis de compilação/execução com contrato comum. Exemplos futuros: runtime interpretado, Android/JVM, WebAssembly e outros destinos quando houver toolchain suportada.
10. **Project Factory** — gerador de templates e arquivos que chama o backend adequado, compila, inspeciona o artefato, executa testes possíveis e guarda logs/checkpoints. Ainda não implementado.

## Contrato futuro de backend

Um backend deverá declarar, no mínimo:

- identificador e versão do backend;
- destinos e arquiteturas suportados;
- pré-requisitos e versões da toolchain;
- dependências externas e licenças relevantes;
- capacidades exigidas (arquivos, rede, subprocessos, SDK);
- entrada esperada e artefatos gerados;
- comandos executados, códigos de saída e logs;
- verificações pós-build e limitações conhecidas.

A escolha do backend será explícita ou recomendada pelo agente com justificativa. A IA não pode reportar “build aprovado” sem uma execução real registrada com status zero e sem as verificações de artefato definidas pelo alvo.

## Aplicações Android e APKs

Um APK Android completo normalmente exige ferramentas Android compatíveis, como JDK, Gradle/Android Gradle Plugin, Android SDK e dependências de build. Na visão de execução pelo telefone, o Project Factory deve avaliar cada alvo antes de anunciar suporte nativo no aparelho: armazenamento, memória, tempo de compilação e disponibilidade das toolchains podem inviabilizar certos builds localmente. Quando não existir backend local viável, o sistema deve declarar o bloqueio; nunca simular que compilou. Uma futura opção de build remoto exigiria consentimento explícito e transmissão controlada do projeto.

## Modelo de execução seguro

- Código NúcleoLang não pode carregar classes JVM ou executar plugins fornecidos por texto.
- Cada capacidade tem nome, versão, esquema de argumentos, permissões e limites próprios.
- Modelos de IA só podem propor chamadas registradas. O runtime valida nome, tipos, argumentos, orçamento e permissões antes da execução.
- Operações de escrita são preparadas em staging; a aplicação produz diff e pede a aprovação configurada antes de aplicar alterações relevantes.
- Execução cancelável, limites de memória/duração e checkpoints devem ser adicionados antes de tarefas não confiáveis de longa duração.
- Pacotes precisam ter procedência e licenças verificáveis; não se deve baixar e executar dependência de origem desconhecida automaticamente.

## Critérios de progresso

Um recurso conta como implementado apenas quando tiver código, documentação e testes executáveis. Um backend conta como suportado apenas quando build e smoke test forem validados no alvo declarado. Uma sugestão produzida por IA, um template criado ou um ZIP gerado não equivalem a um artefato compilado.
