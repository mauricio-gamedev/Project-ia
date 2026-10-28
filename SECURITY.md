# Security Policy

## Scope

This policy covers the Núcleo AI Android application and the source code maintained in this repository. The project is an alpha and must not be assumed to be suitable for handling high-risk secrets or critical production workloads.

## Reporting a vulnerability

Please do **not** disclose exploitable details in a public Issue, pull request, discussion, screenshot, or commit.

Use GitHub's private vulnerability reporting for this repository if it is enabled. If that option is unavailable, contact the maintainer through the GitHub profile linked to this repository and ask for a private reporting channel. Share only the minimum details needed to reproduce the issue.

A useful report includes:
- the affected app version, commit, or workflow;
- the Android version/device or relevant runtime environment;
- clear reproduction steps and expected security impact;
- a proof of concept that does not expose another person's data;
- any safe mitigation you have identified.

Do not include live API keys, OAuth access/refresh tokens, signing keystores, passwords, or private user data. If a credential was exposed, revoke or rotate it immediately.

## Current security design

- Provider credentials and GitHub OAuth tokens are encrypted using Android Keystore-backed storage.
- GitHub OAuth currently requests the broad `repo`, `workflow`, and `offline_access` scopes. Review the authorization screen carefully and revoke access when it is no longer needed.
- Chat history and memories are stored locally, but selected prompts, memories, and source context may be sent to a configured remote AI provider.
- Repository-source content and model output are untrusted data. AI-assisted code review does not mean code has been safely executed.
- The Project Factory validates generated paths and content before staging/export, but validation and secret-pattern filtering are not guarantees that every unsafe file or secret will be caught.

## Response

The maintainer will make a best-effort assessment, investigate reproducible reports, and decide whether a fix, workaround, or advisory is appropriate. No fixed response or remediation time is guaranteed for this alpha project.
