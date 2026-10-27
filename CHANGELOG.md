# Changelog

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
