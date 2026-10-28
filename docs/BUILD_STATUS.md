# Build and Validation Status

## Source of truth

The Android CI workflow at the repository's Android CI workflow file is the authority for test and build results. Use the live [Actions runs](https://github.com/mauricio-gamedev/Project-ia/actions) and pull-request checks to verify the exact commit you intend to install or distribute.

## What CI checks

The workflow runs on pull requests, pushes to the default branch, and manual dispatch. It configures JDK 17 and Android SDK 36, downloads the pinned Gradle distribution and verifies its SHA-256, runs unit tests, builds the debug APK, verifies the artifact, calculates its checksum, and uploads the APK as a workflow artifact.

Expected local output after a successful build:

    app/build/outputs/apk/debug/app-debug.apk

The app package ID is io.github.astromg01.nucleo. The current development version is 0.8.1-alpha with versionCode 13.

## Reference validation

The main release line remains 0.7.1-alpha / versionCode 11. PR #8 contains the current Project Factory generation, repair, GitHub-session, and build-diagnostics hardening. Check the live [PR #8](https://github.com/mauricio-gamedev/Project-ia/pull/8) and its newest check on the exact head SHA before treating these changes as released. CI verifies app compilation/tests and the app APK; it does not compile every source project created by Project Factory.

## What a green build means

A successful CI run demonstrates that the tests and configured build/artifact checks passed on the runner. It does not prove every UI flow has been smoke-tested on every Android device, that generated projects are correct, or that the app is production-ready.

Before distributing a build, install the exact APK on a device, verify the signing certificate is compatible with the installed app, open the app, and test the changed user flows.
