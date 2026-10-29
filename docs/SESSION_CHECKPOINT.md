# Session Checkpoint — Núcleo AI

**Last verified:** 2026-10-28  
**Current main release:** `0.7.1-alpha` (`versionCode 11`)  
**Consolidated candidate in PR #8:** `0.8.3-alpha` (`versionCode 15`)  
**Default branch:** `main`  
**Handoff status:** PR #8 contains Project Factory validation/repair, AI review JSON recovery, provider response/fallback fixes, chat sessions/commands, modest chat rendering optimization, and common Android dependency synchronization. Device testing confirmed a generated test project's APK compiled, installed, and launched after the original regex fix. CI run [40790680996](https://github.com/mauricio-gamedev/Project-ia/actions/runs/40790680996) passed on code SHA `2f1af2f9e48ee2b3cc0bf8d00fccfcab319e90ba`, including unit tests, APK build, stable signing verification, checksum generation, and artifact upload. Its log exposed a separate shell-quoting bug in the run summary; the workflow is corrected in the current update and needs a fresh CI run before the next APK is distributed. PR #8 remains open; protected `main` and disabled auto-merge block integration through the connected GitHub path.

> This file is a resume aid, not a substitute for refreshing GitHub. On return, follow [AGENTS.md](../AGENTS.md) and re-check the live main SHA, open PRs, Actions, repository metadata, and protection state before making changes.

## Current confirmed state

- Repository: [mauricio-gamedev/Project-ia](https://github.com/mauricio-gamedev/Project-ia)
- Current live `main` HEAD: `773ee754c88d9555e3f9f63b6527d10ae1c8e2c4`.
- Latest merged consolidation: [PR #4](https://github.com/mauricio-gamedev/Project-ia/pull/4), merged into `main`.
- PRs #1 and #2 were closed as superseded. Their intended feature work is included through the consolidation commit; do not reopen or repeat them without checking the live diff.
- Open PRs currently include [#8](https://github.com/mauricio-gamedev/Project-ia/pull/8) (consolidated fix) and [#6](https://github.com/mauricio-gamedev/Project-ia/pull/6) (unrelated copyright update). PR #7 was closed as superseded by PR #8.
- Android application ID remains `io.github.astromg01.nucleo`. `main` is at version `0.7.1-alpha`, `versionCode 11`; the PR #8 candidate is `0.8.3-alpha`, `versionCode 15` so it can upgrade earlier alpha installs.
- The owner confirmed a generated test project's APK compiled, installed, and launched on-device after the Android regex correction. The session/command/provider/dependency changes passed CI at SHA `2f1af2f9e48ee2b3cc0bf8d00fccfcab319e90ba`; new flows still need device smoke tests.
- The original Android Gradle regex fix and regression test are in commit `2cb10abf3567c65011d993dedb2932f3c983a42d`; run [40777424598](https://github.com/mauricio-gamedev/Project-ia/actions/runs/40777424598) passed on that SHA. The latest app/source CI pass is run [40790680996](https://github.com/mauricio-gamedev/Project-ia/actions/runs/40790680996) on SHA `2f1af2f9e48ee2b3cc0bf8d00fccfcab319e90ba`. The artifact `nucleo-ai-debug-apk` ID `12861058360` was uploaded and its configured stable keystore signer verified; the APK file SHA-256 in the log was `1490758cd2ada09dea77cf41407bf77fab7bdfa6e382269160f63b1d88a99eba`. This artifact predates the CI-summary quoting fix, so use a new artifact from the post-fix run for installation.
- Stable signing was configured for that build. Preserve the same keystore, alias, application ID, and increasing versionCode for future install-over updates.
- The owner wants autonomous implementation wherever the connected tools allow it and does not want Termux to be a requirement for this app workflow.

## Current consolidated implementation — PR #8

- PR: [#8 Fix Project Factory generation and GitHub build synchronization](https://github.com/mauricio-gamedev/Project-ia/pull/8).
- Source candidate: `0.8.3-alpha` / `versionCode 15`; application ID and stable signing configuration remain unchanged.
- AI generation now retries malformed/incomplete JSON manifests once using the parser's rejection reason. It rejects missing/blank/duplicate/oversized file payloads.
- Project preflight checks source delimiters, JSON/XML validity (rejecting DTD/entities), Android scaffold files, placeholder launchers, and Manifest-to-Activity consistency. It performs a bounded structural repair, preserves the previous draft, revalidates the candidate, and blocks cloud builds when unresolved structural issues remain.
- GitHub UI synchronizes the stored OAuth session on resume, verifies the actual account through GitHub `/user`, keeps account-authentication state separate from repository selection/permissions, and clears rejected credentials without erasing saved repository selections.
- Cloud build diagnostics capture the relevant Gradle job logs, retain final error lines, redact common credential-like values, and only send diagnostics to AI after explicit consent. A repair remains a proposal and must be built again before it counts as verified.
- Consolidated chat updates include wider/readable assistant messages, message copy/selection, safe Markdown styling, persisted OLED Black, and cooldown invalidation when the primary model/endpoint/key changes.
- Tests cover manifest parsing/retry, structural validation, Android launcher matching, common import/dependency synchronization, diagnostic sanitization, code-review JSON extraction/retry/path allowlisting, provider-failure classification, chat commands, cooldown invalidation, and Markdown formatting.
- Owner device testing on 2026-10-28 first exposed `Syntax error in regexp pattern near index 21` during Android generation. The cause was unescaped literal closing braces in Gradle block regexes; those are escaped and the owner subsequently confirmed a generated test APK compiled, installed, and launched without that failure.
- The same device session exposed `A resposta de revisão não contém JSON válido.` during AI code review. The candidate retries malformed/schema-invalid review responses once, accepts balanced JSON surrounded by Markdown/text, validates required report fields, and does not reissue provider/network failures. It also rejects reports containing only paths outside the staged allowlist.
- Intermediate CI exposed two regressions and they were fixed: a misplaced `@Composable` annotation in `MainActivity.kt`, and over-escaped regex literals in the generated-import detector (`AndroidProjectScaffoldTest`). Run [40790680996](https://github.com/mauricio-gamedev/Project-ia/actions/runs/40790680996) passed all configured tests/build/artifact checks on SHA `2f1af2f9e48ee2b3cc0bf8d00fccfcab319e90ba`. Its logs exposed incorrect shell substitution in the summary lines; the current workflow update replaces that with `printf` and requires a fresh validation run.
- The latest known repository state includes active ruleset [protect main](https://github.com/mauricio-gamedev/Project-ia/rules/26013092). It has an `update` rule, no bypass actors, and `current_user_can_bypass = never`. The connected integration's merge attempt on the prior PR returned HTTP 405; auto-merge is disabled. Do not bypass/disable this rule without an authorized owner/admin action.

## Repository presentation already landed

The following changes are in `main` through PR #4:

- Professional English README with overview, highlights, stack, setup, limitations, privacy/security details, and documentation links.
- `LICENSE` and `COPYRIGHT.md`: proprietary / All Rights Reserved notice, attribution to `mauricio-gamedev (@astromg01)`, and clarification that third-party licenses remain in force.
- `SECURITY.md`, `CONTRIBUTING.md`, `SUPPORT.md`, and `CODE_OF_CONDUCT.md`.
- `.github/CODEOWNERS`, pull request template, structured bug report and feature-request forms, issue-template configuration.
- English technical documents for architecture, security, OAuth connector, cloud builds, signing, Project Factory, NúcleoLang, roadmap, and build validation.
- Changelog entry for 0.7.1-alpha OAuth resilience and persistent chat-memory updates.

## Known unfinished repository settings

### 1. Description and topics

At the last live metadata read:
- Repository description / About: empty.
- Topics: empty.
- License metadata: GitHub reports a custom/other license, which is expected for the proprietary notice and is not an SPDX open-source license.

The available GitHub integration exposed no repository-metadata write operation. Do not claim description or topics have been set. On resume, rediscover available GitHub actions and attempt the metadata update if an authorized administrative write path is available. Suggested About text:

> A proprietary Android AI workspace for context-aware chat, persistent memory, configurable model routing, project generation, and consent-based remote APK builds.

Suggested topics (use only if the owner approves/current conventions allow): `android`, `kotlin`, `jetpack-compose`, `ai-assistant`, `llm-routing`, `github-actions`, `project-factory`, `nucleolang`.

### 2. Protect `main`

Current live state (2026-10-28):
- `main` reports `protected: true`.
- Active repository ruleset [protect main](https://github.com/mauricio-gamedev/Project-ia/rules/26013092), ID `26013092`, targets `~DEFAULT_BRANCH`.
- Rules include deletion, non-fast-forward, update restriction, an empty required-status-check list, and a code-coverage rule without a numeric threshold.
- `current_user_can_bypass` is `never` and `bypass_actors` is empty.
- A connected-identity merge attempt on PR #7 returned HTTP 405 (`Cannot update this protected ref`). Repository auto-merge is disabled. Do not use force pushes or attempt to bypass the rule.
- An authorized owner/admin must adjust the ruleset or repository merge configuration if normal PR merges should be allowed; re-query server state before reporting any change.



## Next-action queue

1. Verify Android CI on the latest PR #8 head SHA after the CI-summary quoting fix; inspect unit tests, app compilation, stable-signing verification, checksum, and upload on that exact commit.
2. Download only the artifact from the post-fix successful run, matching its exact head SHA; verify signing/checksum and do not use the pre-workflow-fix artifact.
3. On-device, test `/new`, `/clear`, `/delete`, conversation switching, manual jump-to-latest, AI code review, changing models then returning to chat, and a project using Navigation/Lifecycle dependencies. Then smoke-test private build repository validation and remote APK delivery.
4. If PR #8 is green but cannot merge, keep it open and report the active `protect main` update rule/disabled auto-merge blocker; an authorized owner/admin must adjust it.
5. Repository description/topics remain unconfigured because no authorized metadata-write path was exposed. Do not work around that with unrelated code.
6. For future work, start from fresh `main`, inspect source/tests, implement, validate on the exact SHA, review, open PR, and update this checkpoint.



## Safety and accuracy notes

- GitHub OAuth in the app requests the broad `repo`, `workflow`, and `offline_access` scopes. UI repository selection does not narrow token permissions.
- Chat context is sent to the configured remote provider only when the user enables it; remote build upload needs separate consent.
- The chat does not directly edit repositories or execute arbitrary shell commands. AI code-review output is a proposal, not proof that code is safe.
- Source generation, successful compilation, artifact verification, and on-device smoke tests are distinct milestones.
- Never store tokens, passwords, signing material, private user data, or unredacted secret-bearing logs in this checkpoint.
