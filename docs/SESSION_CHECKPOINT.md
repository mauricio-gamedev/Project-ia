# Session Checkpoint — Núcleo AI

**Last verified:** 2026-10-28 (Manaus local date; GitHub Actions timestamps are UTC)  
**Current main release:** `0.7.1-alpha` (`versionCode 11`)  
**Consolidated candidate in PR #8:** `0.8.3-alpha` (`versionCode 15`)  
**Default branch:** `main`  
**Handoff status:** The owner is testing candidate `0.8.3-alpha` (`versionCode 15`) on their Android phone and plans to report results next session. Start from the owner's actual observations; do not restart implementation blindly or ask them to repeat supplied context. Current PR #8 head `bbc6ce9e98aba7259e35da21b892cc2ba7a2333e` passed CI run [#40791650177](https://github.com/mauricio-gamedev/Project-ia/actions/runs/40791650177), including unit tests, APK build, stable-signing verification, checksum, and artifact upload. PR #8 remains open and `main` is untouched.

> This file is a resume aid, not a substitute for refreshing GitHub. On return, follow [AGENTS.md](../AGENTS.md) and re-check the live main SHA, open PRs, Actions, repository metadata, and protection state before making changes.

## Current confirmed state

- Repository: [mauricio-gamedev/Project-ia](https://github.com/mauricio-gamedev/Project-ia)
- Current live `main` HEAD: `773ee754c88d9555e3f9f63b6527d10ae1c8e2c4`.
- Latest merged consolidation: [PR #4](https://github.com/mauricio-gamedev/Project-ia/pull/4), merged into `main`.
- PRs #1 and #2 were closed as superseded. Their intended feature work is included through the consolidation commit; do not reopen or repeat them without checking the live diff.
- Open PRs currently include [#8](https://github.com/mauricio-gamedev/Project-ia/pull/8) (stability candidate) and [#6](https://github.com/mauricio-gamedev/Project-ia/pull/6) (unrelated copyright update). PR #7 was closed as superseded by PR #8.
- Android application ID remains `io.github.astromg01.nucleo`. `main` is at version `0.7.1-alpha`, `versionCode 11`; the PR #8 candidate is `0.8.3-alpha`, `versionCode 15` so it can upgrade earlier alpha installs.
- The owner previously confirmed a generated test project's APK compiled, installed, and launched on-device after the original Android regex correction. The owner is now testing candidate `0.8.3-alpha` on the A06; conversation/session commands, AI review recovery, provider switching, and generated dependency synchronization need device-level outcomes reported.
- The original Android Gradle regex fix and regression test are in commit `2cb10abf3567c65011d993dedb2932f3c983a42d`. Latest full CI run [#40791650177](https://github.com/mauricio-gamedev/Project-ia/actions/runs/40791650177) passed on the current PR head `bbc6ce9e98aba7259e35da21b892cc2ba7a2333e`, including `:app:testDebugUnitTest`, `:app:assembleDebug`, stable-keystore signature verification, checksum generation, and artifact upload. Artifact: `nucleo-ai-debug-apk`, ID `12862505116`, size `18,837,045` bytes, archive digest `sha256:6ff616428e590e331f637531f88eb741631be05808d2eb4716eb7ce0f53eb872`, expires `2026-11-12T00:06:27Z`. APK file SHA-256: `1490758cd2ada09dea77cf41407bf77fab7bdfa6e382269160f63b1d88a99eba`. Use the matching artifact from the Actions run page; choose a newer successful artifact if the live branch head has advanced.
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
- Intermediate CI had regressions that were corrected before the passing head: misplaced `@Composable` annotation, over-escaped import-detection regexes, and shell command substitution in the CI summary. These failures are from older commits; current head `bbc6ce9e98aba7259e35da21b892cc2ba7a2333e` passed run [#40791650177](https://github.com/mauricio-gamedev/Project-ia/actions/runs/40791650177).
- The latest known repository state includes active ruleset [protect main](https://github.com/mauricio-gamedev/Project-ia/rules/26013092). It has an `update` rule, no bypass actors, and `current_user_can_bypass = never`. The connected integration's merge attempt on the prior PR returned HTTP 405; auto-merge is disabled. Do not bypass/disable this rule without an authorized owner/admin action.

## Resume here — next session

The owner is testing the current APK and will report what happened. Start from those actual results rather than re-asking for supplied context or implementing speculative changes.

1. Refresh live GitHub state: branch `fix/project-factory-generation-github-sync`, PR #8, `main`, newest Actions run for the current head, and matching artifact/signing status. The checkpoint is a handoff, not a replacement for live state.
2. Use the owner's report as the first priority. Map each result to [SESSION_FLOW.md](SESSION_FLOW.md); do not ask them to repeat test outcomes already present in the conversation.
3. For each failure, capture expected vs actual, reproduce the exact path, trace its call sites, fix the root cause in a focused commit, and add a regression test. Do not redesign unrelated features or optimize aggressively.
4. Run relevant tests and full Android CI on the exact new head; verify stable signing, checksum and artifact upload before asking for a new install.
5. Keep changes on PR #8. Do not touch `main`, replace signing, change the package ID, or bypass the active ruleset. Device behavior remains unverified until the owner reports it.

**Current state at checkpoint:** `main` = `773ee754c88d9555e3f9f63b6527d10ae1c8e2c4`; PR #8 head = `bbc6ce9e98aba7259e35da21b892cc2ba7a2333e`; last complete successful CI = [#40791650177](https://github.com/mauricio-gamedev/Project-ia/actions/runs/40791650177); artifact ID = `12862505116`; candidate = `0.8.3-alpha` / `versionCode 15`.

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

1. Wait for the owner's test results for the exact current candidate; capture the failing feature, steps, expected vs actual, and any screenshot/error already provided.
2. Use [SESSION_FLOW.md](SESSION_FLOW.md) to choose only the relevant reproduction steps and expected outcomes.
3. Fix reported root causes with regression tests; run full Android CI on the new exact head and verify signing/checksum/artifact provenance.
4. Keep PR #8 open and `main` untouched; active `protect main` update restrictions and disabled auto-merge block the connected merge path.
5. Refresh GitHub at the start of any subsequent session and do not repeat completed tasks.

## Safety and accuracy notes

- GitHub OAuth in the app requests the broad `repo`, `workflow`, and `offline_access` scopes. UI repository selection does not narrow token permissions.
- Chat context is sent to the configured remote provider only when the user enables it; remote build upload needs separate consent.
- The chat does not directly edit repositories or execute arbitrary shell commands. AI code-review output is a proposal, not proof that code is safe.
- Source generation, successful compilation, artifact verification, and on-device smoke tests are distinct milestones.
- Never store tokens, passwords, signing material, private user data, or unredacted secret-bearing logs in this checkpoint.
