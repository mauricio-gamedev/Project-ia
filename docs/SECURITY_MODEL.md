# Security and Privacy Model

## Protected data

The application may handle provider API keys, GitHub OAuth access/refresh tokens, pending device authorization codes, local conversation history and memories, project metadata, user-selected folder URIs, generated source files, and remote-build artifacts.

## Controls implemented

- Provider credentials and GitHub OAuth tokens are encrypted with AES-GCM using a key held by Android Keystore.
- Android backup is disabled for the app to reduce accidental export of local databases and preferences.
- Remote provider URLs are validated; cleartext HTTP is not allowed for remote endpoints. Any explicitly supported loopback use is separate from remote-service configuration.
- Users explicitly select a folder or repository context. Folder permissions are scoped through Android's document-selection APIs.
- A local project snapshot is not sent to a task's AI provider unless the user enabled project-context sharing for that task.
- Chat source context is optional and requires an explicit choice for the message being sent.
- Project Factory validates relative paths, detects traversal/conflicts, applies project/file limits, stages output privately, and calculates SHA-256 hashes for integrity checks.
- Repository files and generated/model output are treated as untrusted data. The application does not use such data as system instructions.
- HTTP errors are sanitized to avoid placing provider response bodies, authorization headers, or credentials in UI messages/logs.
- Remote build operations require consent and use a separately selected private repository.

## GitHub OAuth scope warning

The OAuth Device Flow currently requests \`repo\`, \`workflow\`, and \`offline_access\`. These are broad account-level scopes for repositories and workflows accessible to the user. Selecting one repository in the UI limits what this particular action attempts to read/write; it does **not** restrict the OAuth token to that repository. Review the GitHub consent screen and revoke the application from GitHub Settings when access is no longer needed. A future broadly distributed version should prefer a GitHub App with granular repository permissions.

## Data sent to remote services

When remote inference is used, the configured AI provider receives the prompt and messages included in that request. This may include the durable conversation summary and selected local memories. If the user enables repository context, a limited source tree and selected source snippets may also be included. Remote provider retention and processing are governed by that provider's terms and settings.

The GitHub connector communicates with GitHub's API to authorize the session, list repositories, and read selected files. A remote build sends the generated project ZIP and build-workflow files to the selected private build repository and runs the authorized workflow there.

## Limitations

- Secret-pattern filtering cannot detect every credential, private key, or sensitive datum. Review source before sharing it with an external service.
- Android Keystore protects stored secrets, but it does not make a compromised device or a maliciously modified app safe.
- Project validation is not a general-purpose security audit and does not prove generated code is correct.
- Remote build success proves the configured build pipeline completed and the artifact passed its checks; it does not prove the app is bug-free, safe, or production-ready.
- The application is an alpha. Biometric lock, fine-grained token permissions, a complete data-export/deletion interface, and an isolated arbitrary-code execution environment are not currently implemented.

## Reporting issues

Do not disclose vulnerabilities or credentials in public Issues. Follow [SECURITY.md](../SECURITY.md) for responsible reporting.
