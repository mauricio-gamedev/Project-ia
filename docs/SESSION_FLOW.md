# Session Flow — Núcleo AI Stability Candidate

**Purpose:** Continue the current stability pass from the owner's real-device results. Do not restart the project plan, repeat completed code changes, or infer user-visible behavior from CI alone.

## 1. Live state to refresh at resume

- Repository: [ma Mauricio-gamedev/Project-ia](https://github.com/mauricio-gamedev/Project-ia)
- Work branch: `fix/project-factory-generation-github-sync`
- Active PR: [#8 — Stabilize Project Factory, AI review, provider routing, and chat](https://github.com/mauricio-gamedev/Project-ia/pull/8)
- Default branch: `main`; keep it untouched.
- Last verified `main` SHA: `773ee754c88d9555e3f9f63b6527d10ae1c8e2c4`
- Last fully validated candidate commit: `5f5f05b68fad5f9ab43147dd5fe4dcf491eb7cb4` (a later checkpoint-only commit may advance the branch without changing app/workflow sources).
- Last complete CI on that tested candidate: [run #40792610317](https://github.com/mauricio-gamedev/Project-ia/actions/runs/40792610317), successful.
- Candidate: `0.8.3-alpha`, `versionCode 15`, application ID `io.github.astromg01.nucleo`.
- Verified artifact: `nucleo-ai-debug-apk`, artifact ID `12862402251`; APK SHA-256 `1490758cd2ada09dea77cf41407bf77fab7bdfa6e382269160f63b1d88a99eba`. The configured stable signing certificate was verified. Artifact archive digest: `sha256:c9c3564545bfeee4b66274a2483d70d1bc8d44296a8dc021c05d938dc269de72`; it expires 2026-11-12 UTC.
- Refresh these values from GitHub before acting. If the branch head or run changed, the live values win.

## 2. Start with the owner's test report

The owner is testing the APK and will report back. Use the report as the source of truth. Do not ask them to repeat details already given, and do not implement speculative fixes for flows that worked.

For each reported issue, capture:
- Feature and exact steps where it occurred.
- Expected outcome vs actual outcome.
- Error text, screenshot, or log if supplied.
- Whether it reproduces every time or only after switching models, navigating, or retrying.

Then inspect the relevant call path, reproduce the failure, implement a focused root-cause fix, add a regression test, and rerun relevant tests plus full Android CI. A green CI is not device-level proof.

## 3. Suggested on-device smoke-test matrix

| Area | Test | Expected result | Evidence if it fails |
|---|---|---|---|
| Model switch / chat | Change to another configured model, return to chat, send a short message; repeat after a failed request | A valid configured route responds or the app shows a useful provider-specific error; no stale connection failure after a model switch | Selected model/provider, exact error, whether connection testing succeeds |
| AI code review | Review a small project with a few Kotlin files | Summary/findings are returned for authorized staged paths; malformed format gets one bounded retry and then a clear error | Project shape, selected model, review error and whether retry happened |
| New conversation | Run `/new` or tap new chat; switch between old/new sessions | Messages stay attached to the right conversation; histories do not mix | Steps and screenshot of both sessions |
| Clear chat | Run `/clear` on a test conversation | Active session messages and its summary are cleared; other sessions and general memories remain | Before/after conversation list |
| Delete chat | Run `/delete` or delete from menu after confirmation | Target session is cleared and a fresh empty conversation becomes active; others are preserved | Command/menu used, dialog/result, remaining session titles |
| Commands/help | Try `/help`, `/chats`, and aliases `/novo`, `/limpar`, `/excluir`, `/ajuda` | Commands run locally instead of reaching the model; unknown commands show help guidance | Command and visible response |
| Scrolling / responsiveness | Open longer history, re-enter a conversation, use the manual latest-message button | No automatic long scroll on entry or every message update; manual jump reaches the latest message; no obvious new stutter | Approximate message count and visible behavior |
| Project generation / build | Generate a small Android app using Compose/Navigation/Lifecycle imports, then use cloud build | Scaffold and Gradle dependencies sync; unresolved libraries/files are diagnosed clearly | Generated file list, build job and relevant Gradle errors |
| GitHub sync | Select a test repository you own, respecting existing consent prompts | Account/session and repo selection remain consistent; permissions/errors are explicit | Repo visibility/permission state and sanitized error text |

Test only the areas relevant to the owner's report if time is short. The whole matrix is not required in one sitting.

## 4. Repository implementation workflow

1. Refresh `main`, PR #8, branch SHA, ruleset state and latest Actions run.
2. Read `AGENTS.md`, `SESSION_CHECKPOINT.md`, this flow, the relevant source/tests and latest CI logs.
3. Map the report to one failing feature and trace the cause before editing.
4. Make a small commit on the existing PR #8 branch; keep `main` untouched.
5. Add/update a regression test; run relevant tests and then `:app:testDebugUnitTest :app:assembleDebug` via CI.
6. Confirm stable signing, checksum, artifact upload and artifact-to-head SHA match.
7. Update changelog/checkpoint and PR description as appropriate. Re-check exact SHA and PR state.
8. Do not merge or bypass the active `protect main` ruleset. It has no bypass actors and the connected user cannot bypass it.

## 5. Known scope and limits

- Chat sessions use the existing local Room table; summaries are scoped per session.
- The import/dependency synchronizer handles selected common libraries, not every arbitrary third-party dependency.
- AI review is a static assistant review, not proof of correctness or security.
- CI validates the app and its tests, not every generated project.
- Preserve package ID, stable signing identity, versionCode sequence, proprietary licensing, explicit consent, OAuth secrecy, and the no-Termux app workflow.
