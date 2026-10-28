# Session Checkpoint — Núcleo AI

**Last verified:** 2026-10-28  
**Current release line:** `0.7.1-alpha` (`versionCode 11`)  
**Default branch:** `main`  
**Handoff status:** repository documentation consolidated; application branch state is stable; administrative metadata and branch protection remain unfinished.

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

At the last live branch read, `main` was **not protected** (`protected: false`, protection disabled, required checks empty). The integration's GET request for branch protection returned HTTP 403 (`Resource not accessible by integration`), and the repository's rulesets endpoint returned an empty list. No write operation for branch protection/rulesets was exposed by the available GitHub connector.

The presence of `.github/CODEOWNERS` does not enforce review by itself. Do not call the branch protected until live GitHub metadata confirms the rule.

On resume:
1. Re-discover current connector actions; check whether an admin-authorized branch-protection/ruleset operation is now available.
2. If available, create a conservative `main` ruleset/protection policy: block force pushes and deletion; require PRs; require the Android CI check on the latest PR head; require code-owner review only if review workflow and ownership expectations are actually workable for this solo-maintained project.
3. Be careful not to create a rule that locks the owner out or makes all merges impossible. Since this repository appears to be solo-maintained, do not require approval from another human unless a second reviewer/team exists. A required CI check and PR-only merge policy may be the more workable minimum.
4. Re-fetch the branch/ruleset endpoint after the attempted change. Report success only if server-side state confirms it.
5. If no authorized write path exists, keep the limitation explicit and provide the owner with the exact minimum remaining admin step only then.

## Next-action queue

1. **Resume from this checkpoint.** Refresh live `main`, PRs, branch protection, repository About/topics, and Actions state.
2. Finish repository metadata (description/topics) via an authorized GitHub integration operation if available.
3. Finish `main` protection via an authorized ruleset/branch-protection operation if available. Server-side verification is mandatory.
4. If those admin operations remain unavailable, avoid changing unrelated app code merely to work around permissions. Record the actual blocker and leave the documentation/CI state intact.
5. Once repository settings are handled, ask what next app feature the owner wants to build; do not restart the completed documentation task.
6. For any new engineering task: branch from fresh `main`, inspect existing code/tests, implement, validate, review, open PR, wait for CI on exact head SHA, merge only after checks pass, and update this checkpoint.

## Safety and accuracy notes

- GitHub OAuth in the app requests the broad `repo`, `workflow`, and `offline_access` scopes. UI repository selection does not narrow token permissions.
- Chat context is sent to the configured remote provider only when the user enables it; remote build upload needs separate consent.
- The chat does not directly edit repositories or execute arbitrary shell commands. AI code-review output is a proposal, not proof that code is safe.
- Source generation, successful compilation, artifact verification, and on-device smoke tests are distinct milestones.
- Never store tokens, passwords, signing material, private user data, or unredacted secret-bearing logs in this checkpoint.
