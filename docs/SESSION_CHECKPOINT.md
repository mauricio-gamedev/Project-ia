# Session Checkpoint — Núcleo AI

**Last verified:** 2026-10-28  
**Main release line:** `0.7.1-alpha` (`versionCode 11`)  
**Proposed release in PR #7:** `0.8.0-alpha` (`versionCode 12`)  
**Default branch:** `main`  
**Handoff status:** PR #7 contains chat usability and model-switch cooldown recovery. Android CI passed on app-source SHA `24d0d699db31c08de15fdb5b3ed0f45ba81c4562` (run [#186](https://github.com/mauricio-gamedev/Project-ia/actions/runs/40749643966)); APK artifact ID `12843805758` was uploaded. Merge is blocked: the active `protect main` ruleset restricts updates and has no bypass actor; API merge returned HTTP 405. Repository auto-merge is disabled. Do not claim the PR was merged.

> This file is a resume aid, not a substitute for refreshing GitHub. On return, follow [AGENTS.md](../AGENTS.md) and re-check the live main SHA, open PRs, Actions, repository metadata, and protection state before making changes.

## Current confirmed state

- Repository: [mauricio-gamedev/Project-ia](https://github.com/mauricio-gamedev/Project-ia)
- `main` HEAD at checkpoint creation: `665c72fa2fd9a195e0a4ebfec3f0d18553ba6487`
- Latest merged consolidation: [PR #4](https://github.com/mauricio-gamedev/Project-ia/pull/4), merged into `main`.
- PRs #1 and #2 were closed as superseded. Their intended feature work is included through the consolidation commit; do not reopen or repeat them without checking the live diff.
- Open PRs at checkpoint creation: none.
- Android app: `io.github.astromg01.nucleo`, version `0.7.1-alpha`, `versionCode 11`.
- The owner reported that the 0.7.1-alpha APK installed/launched successfully and the GitHub connector worked after reauthorization.
- GitHub Actions run [#166](https://github.com/mauricio-gamedev/Project-ia/actions/runs/40636208614) passed for the current `main` commit, including tests, APK build, artifact/checksum verification, and upload.
- Stable signing was configured for that build. Preserve the same keystore, alias, application ID, and increasing versionCode for future install-over updates.
- The owner wants autonomous implementation wherever the connected tools allow it and does not want Termux to be a requirement for this app workflow.

## Latest recovery work — PR #7

- PR: [#7 Improve chat readability and fix model-switch cooldowns](https://github.com/mauricio-gamedev/Project-ia/pull/7).
- Main remains at `773ee754c88d9555e3f9f63b6527d10ae1c8e2c4`; PR #7 is open and based on that SHA. PR #6 is also open and remains unrelated.
- The app-source change restores the previously established provider request timeout behavior rather than enforcing the experimental 40-second route cap.
- `ProviderPreferences.save` now clears the primary cooldown if provider/model/endpoint/enabled state changes. Saving a new primary API key also clears cooldown. Settings adds an explicit `Limpar esperas dos modelos` action for the primary route and configured fallbacks.
- Chat UI changes include wider assistant messages, compact text/spacing, selectable text, per-message copy, safe Markdown headings/lists/emphasis/code, and persistent OLED Black mode.
- Added tests for cooldown invalidation and Markdown edge cases (fence length, escaped punctuation, underscore identifiers, and code color contrast).
- CI run [#186](https://github.com/mauricio-gamedev/Project-ia/actions/runs/40749643966) completed successfully on app-source SHA `24d0d699db31c08de15fdb5b3ed0f45ba81c4562`, including unit tests, APK build, APK-signature/checksum verification, and artifact upload. SHA-256 of APK: `947d0db9428971bf38428317deaa7d063289d7994a5fbe3aee80e4db6896354a`.
- Artifact download page: https://github.com/mauricio-gamedev/Project-ia/actions/runs/40749643966/artifacts/12843805758
- The API merge attempt returned HTTP 405, “Cannot update this protected ref.” Active ruleset: `protect main`, ID `26013092`; rules include `update`, deletion, non-fast-forward, empty required-check list, and code-coverage rule with no numeric threshold. `current_user_can_bypass` is `never`. The repository's auto-merge setting is disabled, so the connected integration cannot complete the merge without a ruleset/repository setting change by an authorized administrator.
- Do not weaken/bypass the ruleset from a tool call merely to force a merge. Keep PR #7 open until an authorized merge path is available.

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

Current live state (refreshed 2026-10-28):
- `main` reports `protected: true`.
- Active repository ruleset [protect main](https://github.com/mauricio-gamedev/Project-ia/rules/26013092), ID `26013092`, targets `~DEFAULT_BRANCH`.
- Rules include deletion, non-fast-forward, update restriction, required-status-check rule with an empty checks list, and a code-coverage rule without a numeric threshold.
- `current_user_can_bypass` is `never` and `bypass_actors` is empty.
- Branch-protection API read returns HTTP 403 to the connected integration. Attempted merge of PR #7 returned HTTP 405: `Cannot update this protected ref.`
- Enabling auto-merge through the integration failed because the repository has auto-merge disabled. Do not retry force merges or change the ruleset from an unauthorized path.

The active `update` restriction appears to block this integration's PR merge attempt. Because the user asked for repository safety and the connected identity cannot bypass the ruleset, keep the PR open and report the exact admin/repository-settings blocker. An authorized owner must adjust the ruleset/merge configuration so successful PRs can merge without bypassing the intended protections.

## Next-action queue

1. Keep PR #7 open until an authorized merge path is available; its app-source changes and APK passed CI, and the build artifact is available for installation/testing.
2. If the user installs the 0.8.0-alpha APK, open Settings → AutoRouter → `Limpar esperas dos modelos` to clear stale cooldowns without changing keys or bypassing quota.
3. Resolve the active `protect main` ruleset / disabled auto-merge setting through an authorized owner/admin path; do not bypass it.
4. Continue repository metadata (description/topics) only when an authorized metadata write operation is available.
5. For future work, branch from fresh `main`, inspect source/tests, implement, validate, review, open PR, wait for CI on exact head SHA, merge only after checks pass, and update this checkpoint.

## Safety and accuracy notes

- GitHub OAuth in the app requests the broad `repo`, `workflow`, and `offline_access` scopes. UI repository selection does not narrow token permissions.
- Chat context is sent to the configured remote provider only when the user enables it; remote build upload needs separate consent.
- The chat does not directly edit repositories or execute arbitrary shell commands. AI code-review output is a proposal, not proof that code is safe.
- Source generation, successful compilation, artifact verification, and on-device smoke tests are distinct milestones.
- Never store tokens, passwords, signing material, private user data, or unredacted secret-bearing logs in this checkpoint.
