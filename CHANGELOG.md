# Changelog

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
