# Project Factory — Design and Implementation Specification

## Purpose

Project Factory turns a user goal into a reviewable project package: select a profile, prepare a predictable structure, generate text files, validate the manifest and paths, export source, and optionally invoke a supported remote build adapter. Android is the control surface; the location of compilation must be explicit rather than assumed.

## Implemented in the current alpha

The screen accepts a project name, profile, and requirements. The description is limited to 4,000 characters and displays a counter. After explicit consent, the app sends the description to the configured AI provider and requests a JSON manifest of text files. When supported, JSON response mode is requested; if an OpenAI-compatible endpoint rejects that optional parameter with HTTP 400, the app retries once without it.

ProjectFactory validates the manifest and stages accepted files in app-private storage. It calculates file sizes and SHA-256 hashes. Users can review files and export a ZIP through Android's Storage Access Framework; the exporter checks size and hashes again and attempts cleanup when export fails. Alternative export paths can use a user-authorized Shizuku UserService or, after the user explicitly grants Android's special all-files permission, write the generated ZIP under Download/NucleoAIProjects. The privileged route transfers only generated ZIP bytes and does not execute shell commands.

Current limits: 300 files, 512 KiB per file, 4 MiB per project, and 240 characters per path. Profiles include Android, web, Python, and generic. The Android/web/Python profiles enforce minimum expected files. For Android, the scaffold can complete common Gradle/manifest boilerplate and add a fallback launcher Activity when no suitable launcher is detected. This improves the starting structure but does not prove model-generated application code compiles.

An optional remote GitHub Actions adapter is available for supported Android scaffolds. It requires OAuth configuration, a separately selected private build repository, write/Actions permissions, and explicit consent. No complete Android build toolchain runs inside the app on the phone.

## Generation retry, validation, and repair boundaries
- Static AI code review requires a valid root JSON report with a non-empty `summary` and a `findings` array. It can extract a balanced object from surrounding Markdown/text and respects braces inside JSON strings.
- A malformed or schema-invalid review response gets one bounded retry with the parser's rejection reason. Provider/network errors are propagated without an extra review call; review findings remain restricted to the authorized staged paths.

- The file-manifest parser rejects malformed JSON, non-string paths/content, empty payloads, duplicate paths, and oversized files before staging.
- A malformed/incomplete manifest gets one bounded regeneration attempt with the parser's diagnostic. This retry does not repeat provider/network failures.
- Before build, static preflight checks project path/size rules, source delimiters, JSON/XML syntax, Android Gradle configuration, launcher placeholders, and whether the manifest launcher resolves to an Activity present in the source tree.
- One AI-assisted repair pass may propose changes to a structural draft or failed build. The result is revalidated, and an AI repair that was not attempted because of size limits is not offered as a new candidate.
- A draft with unresolved structural findings may remain in private staging so the user can inspect and repair it, but remote APK compilation stays disabled until the current revision passes preflight.
- Static checks do not compile or prove application behavior. Only a successful remote Gradle job plus artifact/signature/checksum verification counts as a built APK.

## User flow

1. **Clarify the goal:** identify platform, required behavior, constraints, expected artifacts, and acceptance criteria.
2. **Select a profile:** prefer deterministic templates for common structure and AI for task-specific source.
3. **Generate in private staging:** never overwrite a user folder directly while generating.
4. **Validate structure:** check required files, relative paths, traversal, duplicate paths, file/folder conflicts, and likely secret files.
5. **Review changes:** preview files and require authorization before export or remote build.
6. **Run supported checks:** use local static checks where implemented and a designated build adapter for compilation.
7. **Record evidence:** preserve adapter status, toolchain versions, sanitized logs, duration, artifact metadata, and hashes.
8. **Deliver accurately:** source generation, successful compilation, and verified artifact delivery are distinct statuses.

## Initial project profiles

- **Native Android:** Kotlin, Jetpack Compose, Gradle, manifest, tests, and build settings.
- **Web applications:** TypeScript/JavaScript structure, build scripts, and tests.
- **Python:** modules, declared dependencies/environment, and tests.
- **Games:** engine-specific profiles only when tools and licensing permit.
- **Generic:** arbitrary text-file manifests inside validated staging.

This list is a development order, not a guarantee of universal compatibility. Each profile needs tests and an explicit definition of what it can validate.

## APK build adapters

Generating Android source and producing an installable APK are different operations. An APK build requires compatible JDK/Gradle, Android SDK/build-tools, dependencies, network access, and sufficient resources.

- **Local:** not available as a general-purpose Android build executor in this alpha. Do not assume invoking Gradle inside the app is viable.
- **Remote GitHub Actions:** implemented for supported Android scaffolds, using a user-selected private build repository and separate consent to upload code and run Gradle scripts.
- **Source export:** available without a build adapter.

No project source should be uploaded to a build service without explicit consent. Secret files must be excluded where possible and reviewed by the user. An APK is marked as built only when the remote job and its configured artifact checks succeed.

## Build adapter contract

A future or alternative adapter should expose operations equivalent to:

- inspect(projectSpec): report available toolchains, versions, and limitations.
- validate(stagingProject): check prerequisites before compiling.
- build(stagingProject, profile, cancellation): create an identifiable, resumable job.
- getStatus(jobId): return status and validation evidence.
- collectArtifacts(jobId): list artifacts with path, size, hash, and metadata.
- cancel(jobId): request safe cancellation.

Typed result states are SUCCESS, FAILED, CANCELLED, BLOCKED, and UNSUPPORTED. SUCCESS must reference verifiable execution evidence; it cannot be inferred from model-generated text.

## Security and acceptance criteria

- Use explicit project roots and prevent path traversal.
- Generate and validate in staging before writing externally.
- Apply file-count, per-file, total-size, time, and resource limits.
- Keep long-running work off the UI thread and support cancellation where possible.
- Make build dependencies and plugins inspectable; do not execute arbitrary downloaded tools blindly.
- Require explicit approval for publishing, release signing, and credential use.
- Preserve previous project data if export/build fails; do not overwrite user work.
- For Android, validate workflow completion, APK signature information, checksum, test status, and toolchain versions. A green build is not proof of runtime correctness.
