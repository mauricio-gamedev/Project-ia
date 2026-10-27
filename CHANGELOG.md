# Changelog

## 0.4.0-alpha — 2026-10-27

### AutoRouter e continuidade
- Added multiple OpenAI-compatible fallback profiles with separately encrypted API keys.
- Route code-generation/review/repair requests toward higher user-assigned capability tiers after the primary route fails; ordinary chat follows explicit priority order.
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
