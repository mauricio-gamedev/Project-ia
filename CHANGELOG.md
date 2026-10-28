# Changelog

All notable project changes are documented here. Dates use ISO-style calendar dates.


## 0.8.1-alpha — 2026-10-28

### Chat readability and provider recovery
- Expanded assistant responses, adjusted typography and spacing, and compacted the chat status banner.
- Added selectable message text, one-tap copy controls, and safe Markdown formatting for headings, lists, emphasis, and code.
- Added a persisted OLED Black theme with high-contrast accents and matching Android system bars.
- Restored the established provider request timeout behavior and clear stale primary-route cooldowns when model, endpoint, provider settings, or API key changes.
- Added a manual AutoRouter action to clear temporary cooldowns across configured routes.

### Project Factory generation and validation
- Escape closing braces in the Android Gradle `plugins`, `android`, and `dependencies` validation regexes to prevent an Android-runtime pattern syntax error; add a Kotlin DSL retention regression test.
- Retry a malformed or incomplete AI file manifest once with the parser's exact rejection reason; provider/network failures are not blindly repeated.
- Validate non-empty file payloads, unique paths, source delimiters, JSON/XML structure, Android Gradle configuration, launcher placeholders, and Manifest-to-Activity consistency before a project can be built.
- Make XML validation compatible with Android's XML parser API surface while rejecting DTD/entity declarations and resolving external entities safely.
- Re-run the Android scaffold and structural checks after AI-assisted repair. Do not create a repair candidate when the size limits prevent the repair request from running.
- Keep structural errors visible in the Project Factory UI and disable remote builds until the current staged source passes preflight. A clean preflight is not a substitute for successful Gradle compilation.

### GitHub connector and cloud-build recovery
- Verify the authenticated GitHub account through the /user endpoint rather than equating a stored token with a connected session.
- On expired/revoked OAuth credentials, clear only the rejected credentials and preserve selected chat/build repositories so a new Device Flow authorization can resume.
- Distinguish authentication failures from private-repository access, write/workflow permission, API quota, and branch-protection failures; do not bypass repository protections.
- Capture bounded Gradle/compiler diagnostics, retain the end of the logs where build errors usually appear, and redact common credentials before presenting or sending diagnostics for explicitly consented AI repair.
- Synchronize staging, repair candidates, validation results, prior APK artifacts, build diagnostics, and GitHub status so a new source revision does not inherit stale build evidence.

## 0.7.1-alpha — 2026-10-28

### GitHub OAuth reliability
- Persisted the pending OAuth Device Flow code encrypted with Android Keystore-backed storage so authorization can resume after Android backgrounds or recreates the app.
- Restored the GitHub connection state on screen resume and refreshed repository lists when a stored session is present.
- Added recovery for transient authorization-polling and repository-listing failures without treating an already-saved token as disconnected.
- Added a manual “Resume verification” action when polling needs another attempt.

### Chat continuity and memory
- Improved prompt instructions to resolve short follow-up messages against recent conversation context instead of treating them as unrelated topics.
- Filtered the chat transcript to actual user/assistant turns and expanded the recent history window.
- Added a compact, durable summary of the main conversation, refreshed in the background after enough complete turns.
- Added explicit local memory commands such as “Remember that …” for supported facts and preferences. Obvious credential-like content is rejected.
- Ranked saved chat facts/preferences by lexical relevance and excluded unrelated task-planning memories from chat context.
- Kept active chat requests alive when navigating between app screens.
- Added recovery for an interrupted user turn when the message was persisted but its assistant response was not. Recovery reuses the existing message and does not silently resend optional GitHub repository context.

### Release and validation
- Updated the Android app to version 0.7.1-alpha (versionCode 11).
- Added unit tests for the explicit-memory policy, credential rejection, relevance filtering, and summary thresholds.
- Validated unit tests, APK assembly, artifact checks, checksum generation, and stable signing configuration through GitHub Actions. A successful CI run is not a substitute for a device-level smoke test.

## 0.7.0-alpha — 2026-10-28



### Creator workspace
- Added a creation mode inside Chat with formats for video scripts, posts, live descriptions, content ideas, editorial calendars and visual prompts.
- Added controls for topic, destination platform and tone, plus copy and Markdown export of the generated result.
- Keeps created material in the persistent chat history; no new AI provider or additional token is required.

### GitHub connector
- Added OAuth Device Flow authorization, repository selection and encrypted token storage using Android Keystore-backed SecretStore.
- Requests expiring OAuth tokens with `offline_access` and rotates refresh tokens automatically when an access token expires.
- Shares the OAuth account/session across Settings, chat context and Project Factory while keeping the chat-context repository and dedicated private build repository as separate selections. Owner/name/PAT are no longer re-entered for each build.
- Adds an explicit chat checkbox before sending a limited repository tree/source context to the selected AI provider; the OAuth token is never included in model prompts.
- Adds documentation for the OAuth registration prerequisite, broad OAuth scopes, privacy implications and recovery from revoked/expired access.

### Signature continuity
- Bumps the app to `0.7.0-alpha` / `versionCode 10` and explicitly configures the persistent keystore type as PKCS12.
- A private stable-signing package is prepared outside the repository; the GitHub Actions secrets still must be configured before CI artifacts can use that certificate.
- The first install using the new persistent certificate can require one final uninstall because old alpha builds used a temporary debug certificate. Preserve the new keystore for all later updates.

### Validation
- Added unit tests for selected-repository prompt context and length limiting, in addition to the existing checksum and identifier tests.
- The OAuth callback flow cannot be completed until an OAuth App Client ID has been registered and Device Flow enabled by the account owner. An end-to-end login and repository build must still be tested on-device.

## 0.6.0-alpha — 2026-10-27

### Android Project Factory
- Added deterministic completion of missing Android Gradle/Manifest metadata using a tested Kotlin/Compose base, rather than relying on the model to remember every boilerplate file.
- Repairs invalid root/app Gradle scaffolding, retains literal declared dependencies where possible, adds a launcher activity when no Android Activity is detected, and preserves common requested permissions when repairing the manifest.
- Increases Android generation output budget and strengthens the prompt to request a coherent, functional app with a launcher activity linked to the manifest.
- Adds tests for generated scaffolding, manifest permissions, dependency retention, activity discovery, and non-Android profile behavior.

### Cloud APK builds
- Added an opt-in adapter for building Android projects in a user-owned private GitHub repository using GitHub Actions, JDK 17, Android SDK 36, and a pinned Gradle 8.13 distribution.
- Adds OAuth-backed repository selection shared with Settings, separate chat/build repository targets, separate per-build consent, remote build status, verified APK artifact download, and Save APK through Android's document picker.
- The token is saved encrypted in Android Keystore-backed storage and never sent to the configured AI model. The remote builder refuses public repositories and does not overwrite an unrelated workflow.
- The build runs with read-only GitHub repository permissions, executes the generated Gradle scripts only after explicit consent, validates the APK and checksum, and reports the run link on failure. APK output is debug-signed and is not a Play Store release.

### Chat capability accuracy
- Updates the chat system instructions and home-screen feature list to distinguish source generation from actual APK compilation and to stop claiming APK generation is generally impossible.

### Validation
- New unit tests cover Android project scaffolding and its main structural cases. The current source revision must pass Android unit tests, build and artifact verification before use.
- Historical 0.6.0-alpha used a manually configured fine-grained token. The 0.7.0-alpha flow uses OAuth Device Flow and a dedicated private build-repository selection; OAuth scope permissions are broader and must be reviewed carefully. GitHub Actions quota/network availability and generated code quality remain external constraints.

## 0.5.0-alpha — 2026-10-27

### Descoberta de modelos
- Added an on-demand OpenRouter text-model catalog with search, a published-zero-price filter, context-window details, and published input/output price estimates.
- Selecting a catalog entry only fills the primary-provider form; it does not save settings or silently reuse a credential after the provider/endpoint changes.
- Catalog requests never send credentials saved for unrelated provider hosts. Models marked at zero price are not advertised as guaranteed-free or unlimited.

### Consistência da interface e dos dados
- Added a home-screen activity timeline sourced from persisted project, task, memory, and chat records, sorted by their stored update/creation timestamps.
- Guarded task completion with a database transaction and preserved cancellation if a model response arrives after the task is cancelled; plan memory and completion state commit together.
- Provider settings now require a new key before saving a changed provider identity/endpoint, preventing silent reuse of an unrelated stored credential.

### Validation
- Unit tests cover catalog JSON parsing, duplicate/malformed entries, and the distinction between explicitly zero pricing and unknown pricing.
- Added optional stable-signing support in Gradle and CI using GitHub Actions Secrets. If the secrets are absent, CI warns that its debug APK uses a temporary signer and must not be assumed to update an existing installation.
- The older 0.4.0-alpha artifact used the separate .debugautorouter application ID, while the stable app identity is now io.github.astromg01.nucleo. The old artifact and temporary 0.5.0-alpha artifact also have different certificate fingerprints. A one-time clean install is unavoidable; uninstalling an old build erases app-private history and saved credentials. After stable signing is configured and the final app is installed, future updates can replace it in place.

## 0.4.0-alpha — 2026-10-27

### AutoRouter e continuidade
- Added multiple OpenAI-compatible fallback profiles with separately encrypted API keys.
- Initial code generation starts with the chosen primary and escalates to higher capability tiers after a failure; code review/repair prioritize stronger user-assigned tiers up front. Ordinary chat follows explicit priority order.
- Persist per-route cooldowns for rate limits, authentication errors, timeouts, server failures, and provider compatibility errors; honor numeric `Retry-After` for HTTP 429.
- Added per-route input-context budgets. The context handoff preserves system instructions, the original user task, and recent messages; it omits old middle messages only when necessary and refuses to cut the mandatory task.

### Code review and repair
- Added AI-assisted static review with path-validated findings and explicit consent before sending staged source to a remote model.
- Added bounded repair proposals restricted to existing staged paths. Proposed code is placed in a separate staging revision, diff previews are shown, and changes are applied only after user confirmation.
- Refuses complete repair when the review was partial, files were omitted, or source contents were truncated. No generated source is executed or compiled.

### FreeLLMAPI local gateway
- Permit plain HTTP only for loopback endpoints (`localhost`, `127.0.0.1`, `::1`) to support a gateway such as FreeLLMAPI running locally in Termux; external HTTP hosts remain rejected.
- Added an Android network security configuration with cleartext disabled by default and loopback-only exceptions.
- Version 0.4.0-alpha / versionCode 7; debug app ID suffix `.debugautorouter` and launcher label “Núcleo AI • AutoRouter” keep this build separate from prior test installs.

### Validation status
- The current commit must pass Android unit tests and debug APK verification before its artifact is used. Capability-tier values are user-assigned preferences, not automatic model benchmarks.

## 0.3.3-alpha — 2026-10-27

### Armazenamento e rede
- Declare `MANAGE_EXTERNAL_STORAGE` e adicione um fluxo explícito para abrir as configurações especiais de “Gerenciar todos os arquivos”.
- Adicione exportação direta para `Download/NucleoAIProjects` após o usuário conceder acesso, sem usar o seletor de árvore de pastas.
- Preserve o salvamento por arquivo via Storage Access Framework e a alternativa Shizuku.
- Aumente o tempo limite da API compatível com OpenAI para permitir modelos gratuitos mais lentos e forneça uma mensagem específica quando a geração expirar.
- Use `versionCode 6`, `versionName 0.3.3-alpha` e um identificador de debug separado (`.debugfiles`) para manter a instalação atual intacta.

### Observações
- `MANAGE_EXTERNAL_STORAGE` é uma permissão especial concedida pelo usuário nas configurações do Android; não existe concessão silenciosa.
- Validação no GitHub Actions ainda precisa confirmar o build desta revisão.

## 0.3.2-alpha — 2026-10-27

### Exportação e armazenamento
- Added an optional Shizuku UserService path to export verified project ZIPs directly to `Download/NucleoAIProjects`.
- The privileged service receives only ZIP bytes in bounded chunks, checks file-name/path safety, never reads the app-private staging directory, and never invokes a shell command.
- Kept Android CreateDocument as a normal-user fallback; it intentionally uses a per-file URI grant rather than requesting broad storage permission.
- Bumped Android versionCode to 5 and versionName to 0.3.2-alpha.

### Validation status
- Shizuku installation/authorization and direct write to Downloads still require device-level testing on a phone where Shizuku is started.
- Generated source is exported but not automatically executed or compiled.

## 0.3.0-alpha — 2026-10-21

### Project Factory
- Added an AI-assisted project drafting screen with explicit remote-prompt consent.
- Added profile-aware manifest validation for Android, web, Python, and generic source projects.
- Added private staging with path traversal, collision, secret-file, per-file size, file-count, and total-size checks.
- Added per-file SHA-256 evidence and explicit source export through Android Storage Access Framework, without executing generated scripts.
- Added a bounded, selectable source preview for each staged file; previews re-check path safety, UTF-8 decoding, file size, and SHA-256 integrity.
- Added guarded staging cleanup and regression tests proving that discard refuses directories outside the authorized staging root.
- Requested provider JSON-object response mode for generation and added an HTTP 400 compatibility retry for OpenAI-style endpoints that reject the optional parameter.
- Improved malformed-manifest diagnostics to distinguish incomplete output from likely unescaped quotes or line breaks.
- Increased the project-description limit from 1,000 to 4,000 characters and added a visible counter to prevent avoidable validation failures.
- Replaced folder-tree export with a ZIP archive saved through Android's CreateDocument flow, avoiding the need to grant access to a whole directory.
- Added an optional, explicitly authorized Shizuku UserService export path that streams the verified ZIP to `Download/NucleoAIProjects`, without requesting broad storage permission or executing shell commands.
- Added verified ZIP-writing tests for archive contents and detection of staged-file tampering.
- Added regression tests for staging, unsafe paths, collisions, secret files, size limits, and profile requirements.

### Status
- The previous 0.2.0-alpha baseline build and APK artifact were verified by GitHub Actions. This 0.3.0-alpha branch still requires a fresh CI build and test run.
- Generated projects are source-only; generated-project static analysis, build adapters, and APK generation remain pending.


## 0.2.0-alpha — 2026-10-21

### NúcleoLang
- Added mutable list literals (`[1, 2]`), nested lists, chained indexing, and indexed assignment.
- Added the `len`, `push`, `pop`, and `contains` collection/string built-ins.
- Added explicit host-function registration through `NucleoHostFunction` for trusted, narrowly-scoped application capabilities.
- Validated host-function return values; arbitrary JVM objects and non-finite numbers are rejected.
- Added configurable collection growth limits and safe errors for bad indexes, empty `pop`, and immutable host-returned collections.
- Added regression cases for collections, capability registration, invalid indexes, limit enforcement, and host return validation.

### Architecture and documentation
- Defined NúcleoLang as a general-purpose, extensible language goal rather than a closed list of supported project types.
- Added a proposed language/platform architecture covering semantic specification, front-end, future IR, runtime, packages, capabilities, and pluggable build backends.
- Added a collections example and updated the language specification and version metadata.

### Validation
- Kotlin compiler check: NúcleoLang core compiled successfully.
- NúcleoLang unit methods: 16/16 passed using a local test harness compatible with the test assertions.
- Static project preflight: 34/34 checks passed.
- Full Android Gradle build was not completed in this environment because Gradle distribution download failed at DNS/network resolution; no APK is claimed as built.
