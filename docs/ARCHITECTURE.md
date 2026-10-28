# Technical Architecture

## Design principles

1. **Provider-neutral AI contracts.** Requests and responses use internal data types rather than depending on a single vendor SDK.
2. **Local persistence.** Room/SQLite stores chat turns, projects, tasks, and memories so important state survives ordinary navigation and app restarts.
3. **Context is bounded.** The chat sends recent turns plus selected durable memory; model context limits are handled explicitly rather than described as infinite memory.
4. **Data is not instructions.** Repository source, generated files, and other imported content are untrusted input and must not override system rules.
5. **Explicit data sharing.** Repository context is only fetched for chat when the user enables the option. Remote project builds require separate consent.
6. **Least privilege.** Folder access is user-selected; code execution and arbitrary shell access are not part of the chat.
7. **Evidence before claims.** The app distinguishes source generation, a completed remote build, and a verified APK artifact.

## Application layers

- \`MainActivity\` and Compose UI: home, conversation, project library, tasks, memory, language playground, project factory, and settings.
- \`NucleoApplication\` / \`AppContainer\`: application-wide dependency composition.
- \`data/local\`: Room entities and DAOs for projects, task state, chat messages, and memories.
- \`data/settings\`: provider profiles, fallback-route metadata, and URL validation.
- \`core/security/SecretStore\`: authenticated AES-GCM encryption using an Android Keystore-managed AES key.
- \`core/ai/ModelRouter\`: selects configured routes, applies context budgets, and handles provider cooldowns and fallback.
- \`core/ai/OpenAiCompatibleProvider\`: HTTP adapter for compatible chat-completions endpoints.
- \`core/ai/ChatMemoryManager\`: explicit-memory policy, lexical relevance, and durable conversation-summary thresholds.
- \`domain/github\`: OAuth Device Flow, encrypted session tokens, repository selection, and limited source-context retrieval.
- \`domain/task\` and WorkManager: durable technical-planning tasks with bounded retries.
- \`ui/project\` and Project Factory domain services: validated generated-file staging, preview/export, and remote Android build coordination.
- \`core/lang\`: NúcleoLang lexer, parser, AST, and Kotlin interpreter.
- \`docs/\`: implementation notes, security boundaries, specifications, and roadmap.

## Chat request flow

1. Persist the user message in Room.
2. Extract an explicit local memory only when the user uses a supported “remember that” command; obvious credential-like values are rejected.
3. Load a bounded chronological history of user/assistant turns.
4. Add the durable chat summary and relevant user-approved facts/preferences. Task-planning memories are excluded from this prompt.
5. Fetch repository snippets only when the user explicitly enabled GitHub context for that request.
6. Route the request to a configured AI provider within its context budget.
7. Persist the response. A background task may update the durable summary after enough complete turns; summary failures do not invalidate the response.
8. If Android interrupts a request after the user turn is persisted but before an assistant response exists, the chat may retry that existing turn without duplicating the message or automatically re-sending repository context.

## GitHub authorization flow

The app uses OAuth Device Flow. Pending device authorization data is stored encrypted so a flow can resume after the browser backgrounds the app or Android recreates the activity. Access and refresh tokens are stored encrypted through Android Keystore-backed storage. The requested scopes are broad (\`repo\`, \`workflow\`, and \`offline_access\`); UI repository selection is not a token-level permission boundary.

## Project generation and remote builds

Project Factory parses AI output as a strict file manifest and retries malformed/incomplete manifests once. A shared source validator checks supported static structure and Android cross-file bindings; an AI repair is re-staged and validated before activation. Failed drafts may stay in isolated private staging for inspection, but a revision with unresolved findings cannot start a remote build. Static checks are not the compiler.

The GitHub connector verifies the actual account identity against /user rather than trusting the presence of an encrypted token alone. Expired/rejected credentials can be cleared without deleting the repository selections; repository access, permission, quota, and branch-protection errors have distinct messages. A remote Android build still requires explicit consent, a private repository, a successful Gradle workflow, and verified artifact/checksum evidence.

Project Factory validates generated paths and content before staging/export. For supported Android scaffolds, a separate remote build workflow can run Gradle with JDK 17 and Android SDK 36 in a user-selected private GitHub repository. Source generation alone is never reported as a successful APK build; the remote workflow and artifact verification must succeed.

## Security boundaries and known limitations

- Remote inference sends prompts, recent conversation context, selected memories, and—when explicitly enabled—limited repository source to the configured provider.
- No local inference engine is integrated yet; the local runtime interface is an extension point.
- The chat does not directly edit GitHub repositories or run arbitrary shell commands.
- AI code review produces proposals and does not execute the reviewed source.
- NúcleoLang is an interpreter with execution limits; it is not an Android compiler.
- Automated secret filtering and generated-file validation reduce risk but cannot guarantee detection of every secret or unsafe construct.
