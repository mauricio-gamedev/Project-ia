# Núcleo AI

<p align="center">
  <strong>A native Android workspace for AI-assisted software engineering, persistent chat memory, and consent-based project builds.</strong>
</p>

<p align="center">
  <a href="https://github.com/mauricio-gamedev/Project-ia/actions/workflows/android-ci.yml"><img alt="Android CI" src="https://github.com/mauricio-gamedev/Project-ia/actions/workflows/android-ci.yml/badge.svg?branch=main"></a>
  <img alt="Version" src="https://img.shields.io/badge/version-0.8.2--alpha-orange">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-green">
  <img alt="Language" src="https://img.shields.io/badge/language-Kotlin-purple">
  <img alt="License" src="https://img.shields.io/badge/license-All%20rights%20reserved-lightgrey">
</p>

Núcleo AI is a source-visible, proprietary Android application designed to bring configurable AI providers, a context-aware chat, durable local state, project generation, and remote Android builds into one mobile workspace. It is built with Kotlin and Jetpack Compose and is designed to run directly on Android; a desktop PC or Termux is not a requirement for normal app use.

> **Project status: alpha.** Features and interfaces may change. A successful CI build proves that the app's test/build workflow passed; it does not guarantee that every generated project is correct or production-ready.

## Highlights

- **Context-aware chat:** persistent conversation history, recent-turn continuity, a durable conversation summary, and locally stored facts/preferences that the user explicitly asks the app to remember.
- **Creator workspace:** generate drafts for video scripts, social posts, livestream descriptions, content ideas, editorial calendars, and visual prompts; copy results or export Markdown.
- **Configurable AI providers:** OpenAI-compatible chat-completions endpoints, separately encrypted credentials, alternative routes, quota-aware fallback, cooldowns, and context-budget handoff.
- **GitHub OAuth Device Flow:** authorize without pasting a personal access token into the app; store access/refresh tokens and pending device authorization data using Android Keystore-backed encryption.
- **Repository context with consent:** select a repository for optional chat context. Source snippets are sent to the configured AI provider only when the user enables this feature.
- **Project Factory:** generate and validate project files in private staging, review content, export source files, and request a remote Android debug build after explicit consent.
- **Remote APK builds:** use a separately selected private GitHub repository and GitHub Actions to compile, verify, and retrieve a debug APK with a SHA-256 checksum.
- **AutoRouter and code review:** configure alternative model routes and request static AI-assisted review/repair proposals without executing generated code.
- **NúcleoLang 0.2 alpha:** a small Kotlin-based interpreter and in-app playground with execution limits and no built-in shell, network, or filesystem access.
- **Local persistence:** Room/SQLite stores chat messages, projects, tasks, and memories. WorkManager runs durable planning tasks with bounded retries.
- **Android CI:** GitHub Actions runs unit tests, builds a debug APK, verifies the artifact, and uploads it for inspection.

## Current capabilities and limitations

The application can create source files in staging, validate paths and content hashes, export project files, and request a GitHub Actions build for a supported Android scaffold. The cloud build requires a private build repository, a working OAuth authorization, repository write/workflow permissions, and explicit user consent.

The chat itself **does not directly edit GitHub repositories or execute arbitrary shell commands**. AI-assisted code review produces proposals; it does not run the reviewed code. NúcleoLang is interpreted and does not compile Android packages. A local inference runtime is not yet integrated: the current `LocalModelRuntime` is an extension point, so configured remote providers are used for AI inference.

The project does not claim to provide embeddings-based semantic search, an autonomous code executor, model training, or unlimited/free AI inference. Free model availability, provider quotas, pricing, and compatibility depend on the selected provider and can change independently of this app.

## Architecture and technology

| Layer | Technology |
| --- | --- |
| Language and UI | Kotlin, Jetpack Compose, Material 3 |
| Android SDK | Compile/target SDK 36; minimum SDK 26 |
| Local data | Room 2.8.5 / SQLite |
| Background tasks | WorkManager |
| HTTP | OkHttp |
| Credential protection | Android Keystore and AES-GCM |
| Build system | Gradle 8.13, Android Gradle Plugin 8.13.2 |
| Kotlin toolchain | Kotlin 2.3.21, KSP 2.3.12, JDK 17 |

Application ID: `io.github.astromg01.nucleo`.

## Getting started

### Build the app

Requirements: JDK 17, Android SDK Platform 36, Android Build Tools 36.0.0, and network access to download the pinned Gradle distribution and Maven dependencies.

Open the repository in Android Studio, sync Gradle, and select the `app` configuration. Alternatively, from the repository root run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

On Windows:

```bat
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk` after a successful build. CI artifacts are published by the Android workflow; check the workflow run for its build summary, checksum, and signing mode.

### Configure an AI provider

In **Settings**, configure a provider name, HTTPS base URL, model identifier, and API key. The service must support Bearer authentication and an OpenAI-compatible `POST /chat/completions` request. Save and test the connection before using the chat.

Do not put credentials in URLs, prompts, source files, issues, or commits. Never commit provider API keys, OAuth tokens, signing keys, keystores, or build secrets.

### Connect GitHub

1. Create an OAuth App in GitHub Developer Settings and enable **Device Flow**.
2. Enter the OAuth App's public **Client ID** in **Settings → GitHub Connector**.
3. Start the connection, enter the one-time user code on GitHub, and approve the displayed access request.
4. Select the chat-context repository and the private build repository independently.

The current OAuth request uses the broad `repo`, `workflow`, and `offline_access` scopes. Review the GitHub consent screen carefully: these scopes can grant more access than a single selected repository. Selection in the UI is not equivalent to token-level permission restriction. Revoke the OAuth App's access from GitHub Settings if the connection is no longer trusted.

See [GitHub Connector](docs/GITHUB_CONNECTOR.md) and [Android Cloud Builds](docs/ANDROID_CLOUD_BUILDS.md) for the full setup and security implications.

## Privacy and security

- Conversation history, explicit memories, project/task metadata, and credentials are stored locally; credentials are encrypted with Android Keystore-backed encryption.
- Prompts and selected context are sent to the configured AI provider when a feature requires remote inference. The provider's own terms and retention policies apply.
- Repository source context is optional and requires an explicit in-chat choice. Remote builds require separate consent and use the selected private build repository.
- Project file readers apply limits and exclude common build, VCS, and secret-file paths, but automated filtering cannot guarantee that every secret will be detected.
- Treat source files, repository content, model output, and generated code as untrusted input. Review generated changes before using them.
- Do not use a public issue to disclose a vulnerability, private source, API key, or account token. Follow [SECURITY.md](SECURITY.md) for responsible reporting.

Read the [Security Model](docs/SECURITY_MODEL.md) for a fuller description of current controls and known limitations.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [AI routing and code review](docs/AI_ROUTING_AND_CODE_REVIEW.md)
- [GitHub OAuth connector](docs/GITHUB_CONNECTOR.md)
- [Remote Android builds](docs/ANDROID_CLOUD_BUILDS.md)
- [Signing continuity](docs/ANDROID_SIGNING.md)
- [Security model](docs/SECURITY_MODEL.md)
- [Project Factory specification](docs/PROJECT_FACTORY_SPEC.md)
- [NúcleoLang specification](docs/NUCLEOLANG_SPEC.md)
- [NúcleoLang platform architecture](docs/NUCLEOLANG_PLATFORM_ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Changelog](CHANGELOG.md)
- [Contributing guidelines](CONTRIBUTING.md)
- [Security reporting](SECURITY.md)

## Project status and roadmap

The project is an actively evolving alpha. Current priorities are stronger lifecycle and persistence tests, better diagnostics for provider failures, additional validation of generated projects, clearer model-routing observability, and safe incremental project workflows. Features described only in design specifications or roadmap documents should not be interpreted as already implemented.

## Copyright and license

Copyright © 2026 mauricio-gamedev (@astromg01). All rights reserved.

Núcleo AI is a proprietary, source-visible project. **No general license is granted to use, copy, modify, redistribute, sublicense, or create derivative works from original project materials without prior written permission from the copyright holder.** Public visibility on GitHub does not make this repository open source.

Third-party libraries, tools, names, and materials remain governed by their respective owners and licenses. The project copyright notice does not claim ownership of third-party components. See [LICENSE](LICENSE) and [COPYRIGHT.md](COPYRIGHT.md).

---

Maintained by [@mauricio-gamedev](https://github.com/mauricio-gamedev), with project credit to @astromg01.

- [Support](SUPPORT.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Contribution Policy](CONTRIBUTING.md)
- [Copyright Notice](COPYRIGHT.md)
- [Security Policy](SECURITY.md)

For feature proposals and bug reports, use the repository's GitHub Issues templates.
