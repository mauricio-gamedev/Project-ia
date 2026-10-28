# NúcleoLang — Language and Platform Architecture

**Status:** technical proposal for the alpha roadmap. Describing a compiler or integration here does not imply it has been implemented.

## Goal

NúcleoLang should not have a closed catalogue of project types. The core defines the language; libraries, authorized capabilities, and extensible backends connect it to new domains and platforms. Arbitrary software generation cannot be promised: every target depends on a real, compatible, testable toolchain, SDK, API, or runtime.

## Proposed layers

1. **Normative specification:** grammar, scopes, values, errors, modules, and versioned compatibility. Documented behavior becomes a contract covered by tests.
2. **Front end:** lexer, parser, diagnostics, AST, and semantic analysis. It should not depend on Android APIs or call AI models directly.
3. **Type system:** gradual inference/checking, explicit types where useful, generics, and pre-execution diagnostics. The current alpha remains dynamically typed.
4. **Intermediate representation (IR):** a target-independent representation validated before execution/compilation. Not implemented yet.
5. **Runtime:** values, collections, functions, budgets, cooperative cancellation, and error tracing. The runtime does not grant system access by default.
6. **Standard library:** collections, text, math, abstract paths, JSON, date/time, and deterministic utilities.
7. **Host capabilities:** explicitly injected, granular integrations with permissions. For example, project.readText restricted to an authorized folder—not a generic function that can execute any command.
8. **Package manager:** versioned dependencies, hashes, verifiable origins, license metadata, lockfile, and reproducible installation. Not implemented yet.
9. **Backends:** selectable compilation/execution adapters with a common contract. Potential future targets include interpreted runtime, Android/JVM, WebAssembly, and other environments once supported toolchains exist.
10. **Project Factory:** template/file generation that calls an appropriate backend, compiles, inspects artifacts, runs available tests, and records logs/checkpoints. Only a subset of project-generation and remote Android build workflows exists today.

## Future backend contract

A backend should declare at least:
- backend identifier and version;
- supported targets and architectures;
- prerequisites and toolchain versions;
- external dependencies and relevant licenses;
- required capabilities (filesystem, network, subprocesses, SDKs);
- expected input and produced artifacts;
- executed commands, exit codes, and logs;
- post-build checks and known limitations.

Backend selection must be explicit or recommended by the agent with a rationale. AI cannot report a successful build without recorded execution and target-specific artifact checks.

## Android apps and APKs

A complete Android APK normally requires a compatible JDK, Gradle/Android Gradle Plugin, Android SDK/build-tools, and build dependencies. For on-device execution, Project Factory must assess target feasibility before claiming local support; storage, memory, build time, and toolchain availability may make local builds impractical. When no viable backend exists, the system must declare a blocked/unsupported state instead of simulating success. Remote builds require explicit consent and controlled project transfer.

## Safe execution model

- NúcleoLang source cannot load arbitrary JVM classes or execute plugins from text.
- Every capability declares a name, version, argument schema, permissions, and limits.
- AI models can propose registered capability calls only. The runtime validates call name, types, arguments, budget, and permissions.
- Writes are staged; the app produces a diff and requests approval before applying consequential changes.
- Cancellation, memory/time limits, and checkpoints must be in place before long-running untrusted tasks are allowed.
- Packages require verifiable provenance and license metadata; dependencies from unknown origins must not be downloaded and executed automatically.

## Progress criteria

A feature counts as implemented only when code, documentation, and executable tests exist. A backend counts as supported only when build and smoke tests pass on the declared target. An AI suggestion, generated template, or ZIP does not equal a compiled artifact.
