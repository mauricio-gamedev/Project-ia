# Stable Android APK Signing

## Required GitHub Actions secrets

In the repository, open **Settings → Secrets and variables → Actions** and configure the four repository secrets expected by the workflow:

- \`ANDROID_SIGNING_KEYSTORE_BASE64\`: Base64-encoded contents of the private PKCS12 keystore.
- \`ANDROID_SIGNING_STORE_PASSWORD\`: keystore password.
- \`ANDROID_SIGNING_KEY_ALIAS\`: expected alias, currently \`nucleo-ai-release\`.
- \`ANDROID_SIGNING_KEY_PASSWORD\`: password for the selected key.

Keep the keystore and passwords outside the Git repository. Never commit them, attach them to an Issue/PR, or write them to logs. Keep private backups of the keystore; losing the signing key can prevent future updates from using the same Android signing identity.

## CI signing behavior

When all four secrets are available and the keystore can be opened, Gradle uses the configured stable key. The workflow verifies the APK certificate with the Android SDK's \`apksigner\` tool and records the signing mode in the job summary. If the secrets are missing or invalid, the build may fall back to a temporary runner debug certificate; such an APK must not be assumed to update an already installed app.

Check the summary of the specific workflow run rather than relying on a version label alone. The version, package ID, and signing certificate must all be compatible for Android to update an existing installation.

## One-time migration considerations

Android only permits an in-place update when the application ID and signing certificate are compatible. Some earlier alpha builds used temporary runner/debug certificates. If the installer reports a signature conflict, the installed APK was signed differently from the new artifact. Resolve that migration before installing; uninstalling an old alpha can erase local app data, so preserve any needed provider settings or keys first.

Once a stable key is used consistently, retain it for every subsequent update and increment the app's versionCode. Never generate a replacement keystore casually.

## Verification checklist

- Confirm the workflow says the stable signing key was configured and the APK certificate was verified.
- Keep the keystore and all passwords private and backed up.
- Check the package/application ID and versionCode for every build.
- Install and smoke-test the exact artifact on a device before distributing it.
