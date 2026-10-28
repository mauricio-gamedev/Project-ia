# Remote Android APK Builds

## How the current flow works

An Android phone does not provide a complete build toolchain for every arbitrary Android project. Project Factory creates or completes a supported Android scaffold, validates generated files, stores them in private staging, and—after separate user consent—uploads the project ZIP to a user-selected **private GitHub repository**. GitHub Actions runs Gradle using JDK 17 and Android SDK 36, builds a debug APK, verifies the artifact, calculates SHA-256, and uploads the build artifact. The app can download the artifact, check its checksum, and let the user save it through Android's file picker.

Having Kotlin files, a manifest, or a Gradle script is not proof that an APK exists. The app must wait for a successful workflow and artifact verification before reporting a completed build.

## Initial configuration

1. Create a dedicated private repository for builds, initialized with a README and a default branch.
2. Configure GitHub OAuth Device Flow in **Settings → GitHub Connector**; see [GITHUB_CONNECTOR.md](GITHUB_CONNECTOR.md). Do not enter a GitHub password or PAT into the app.
3. Select the private build repository separately from the repository used for optional chat context.
4. In Project Factory, generate the project and review the staged files.
5. Enable the build consent option and start **Build APK in the cloud**.

The connector currently uses the broad OAuth scopes \`repo\`, \`workflow\`, and \`offline_access\`. Review them carefully. The UI's repository selection is not a token-level permission boundary.

## Workflow behavior

The Núcleo AI build adapter installs its dedicated workflow file in the selected build repository, then uploads the project to a build branch and triggers the workflow. It refuses to overwrite an existing workflow file that appears unrelated to Núcleo AI. The build logs and generated branch remain reviewable in the user's repository.

Generated source and Gradle scripts are untrusted. Review the source before authorizing a build. A successful build means the configured workflow completed and its post-build artifact checks passed; it does not guarantee application correctness, runtime stability, performance, or Play Store readiness.

## Outputs and signing

The output is a **debug APK**, not a production Play Store release. The artifact includes a SHA-256 checksum; the client validates it before offering the file. Signing behavior depends on the selected build workflow and configured signing key. Read [ANDROID_SIGNING.md](ANDROID_SIGNING.md) before relying on in-place updates between installations.

## Security and costs

- The project ZIP and build workflow are uploaded only to the selected private repository, after consent.
- Anyone with repository access can potentially read the source and artifacts.
- GitHub Actions usage and limits follow the repository owner's GitHub plan and current GitHub policies; remote builds are not guaranteed to be unlimited or free.
- Do not include credentials or signing keystores in generated project files.
- Revoke OAuth access when it is no longer needed.
