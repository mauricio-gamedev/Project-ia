# Product Roadmap

This roadmap distinguishes implemented capabilities from proposals. Check current source and CI results before treating any item as shipped.

## M0 — Android foundation (implemented)

- [x] Native Android app using Kotlin and Jetpack Compose.
- [x] Room/SQLite persistence for projects, tasks, messages, and memories.
- [x] Configurable remote AI provider and encrypted provider credentials.
- [x] Durable planning tasks with bounded retries.
- [x] Consent-controlled, bounded project snapshots.
- [x] Unit-test and Android debug-build workflow in GitHub Actions.
- [x] In-app NúcleoLang interpreter playground with execution budgets.

## M1 — Model routing and chat continuity (implemented in alpha)

- [x] Primary provider and configurable alternative routes.
- [x] Quota/network cooldowns and context handoff within configured model budgets.
- [x] Persistent conversation history and a durable chat summary.
- [x] Explicit local memory commands for supported facts/preferences; obvious credential-like values are rejected.
- [x] Lexical relevance selection for chat memories; unrelated task-planning memories are excluded.
- [x] Recovery for interrupted chat turns and app navigation.
- [x] GitHub OAuth Device Flow with encrypted tokens and resumable pending authorization.
- [x] Optional per-message repository source context and separate private build repository selection.

## M2 — Project Factory and remote builds (implemented alpha slice)

- [x] Consent before sending a project description to the remote model.
- [x] JSON file-manifest parsing and path validation.
- [x] Limits for file count, per-file size, total project size, and path length.
- [x] Private staging, per-file SHA-256, content preview, and source export.
- [x] Baseline validation for Android, web, Python, and generic profiles.
- [x] Android scaffold completion for common missing Gradle/manifest/launcher pieces.
- [x] Optional GitHub Actions build for supported Android scaffolds, with artifact/checksum verification.
- [ ] Broader static diagnostics and dependency/license analysis for generated source.
- [ ] More comprehensive remote-build failure, cancellation, and artifact-recovery tests.

## M3 — Validation and robustness (next priorities)

- [ ] Broaden Room persistence, activity recreation, revoked-permission, and interrupted-request regression tests.
- [ ] Test provider success, invalid responses, HTTP 401/403/429/500, and timeouts.
- [ ] Improve per-route diagnostics and quota/cooldown visibility without exposing provider data.
- [ ] Add safer controls for viewing, exporting, and deleting local chat memories/history.
- [ ] Test OAuth revocation, expiry, refresh failure, and repository permission loss on-device.

## M4 — Reviewable repository changes (planned)

- [ ] Incremental repository scanner and local lexical index.
- [ ] File/line evidence in project-analysis reports.
- [ ] Patch generation in staging, reviewable diffs, explicit approval, and restricted application.
- [ ] Bounded lint/test/build execution after a supported execution environment and isolation policy exist.
- [ ] Audit logs, safe cancellation, rollback, and artifact provenance.

## M5 — Local inference and advanced automation (planned)

- [ ] Benchmark Android ARM64 inference runtimes on representative devices.
- [ ] Evaluate model size, licenses, RAM, latency, temperature, battery impact, and compatibility.
- [ ] Integrate a real local-inference adapter only after feasibility and safety testing.
- [ ] Improve hybrid local/remote routing and cost visibility.
- [ ] Add capability-registered tools and specialized agents only after permissions and execution have been validated.

## Explicit non-goals for the current alpha

The app does not train its own model, provide unlimited/free inference, run arbitrary shell commands, execute AI-generated code in a sandbox, or compile APKs through NúcleoLang. LocalModelRuntime is an extension point; local inference is not yet available. Remote-build success does not guarantee runtime correctness or production readiness.
