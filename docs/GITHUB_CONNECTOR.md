# GitHub Connector

## One-time setup

The app uses GitHub OAuth Device Flow. It does not require the user to paste a personal access token (PAT) or GitHub account password into Núcleo AI, but a public OAuth App Client ID must be configured first.

1. Open GitHub **Settings → Developer settings → OAuth Apps** and create an OAuth App for Núcleo AI.
2. Set an application name and homepage URL. GitHub's OAuth App registration form may ask for a callback URL; Device Flow itself uses a user code rather than a browser redirect.
3. Enable **Device Flow** for the OAuth App and save the settings.
4. Copy the public **Client ID**—not a client secret—into **Settings → GitHub Connector** in Núcleo AI.
5. Start the connection, open GitHub's verification page, enter the one-time code shown in the app, and approve the access request.
6. Select the repository for optional chat context and, separately, the private repository for remote Android builds.

Official references: [Create an OAuth App](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app) and [Authorizing OAuth Apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps).

## Requested scopes and permission implications

The current flow requests:

- \`repo\`: broad repository access to repositories the account can access.
- \`workflow\`: access required by the remote build workflow to update/run GitHub Actions workflow files.
- \`offline_access\`: support for expiring access tokens and refresh-token renewal where supported by the OAuth App.

These scopes are broad. A repository selection in the Núcleo AI UI limits the operations the UI initiates; it does not narrow the authorization token's underlying scope. Review the GitHub consent page carefully. Revoke the OAuth App from GitHub Settings when you no longer trust or need the connection.

## Session persistence and recovery

Access and refresh tokens are encrypted using Android Keystore-backed storage. When the user returns from the browser, the app checks the stored session and refreshes the repository list. Pending Device Flow state is also stored encrypted so the app can resume polling if Android backgrounds or recreates the app. Transient polling errors can be retried; an expired or rejected code requires a new authorization attempt.

The connector now verifies the actual authenticated account through GitHub's /user endpoint before describing the session as validated. A saved token alone is shown as an unverified credential, not proof that repository access works. If GitHub rejects the session, the app clears only the rejected access/refresh credentials while preserving the user's chat/build repository choices, allowing a new Device Flow to start without stale-token interference. Temporary network or repository-listing failures are reported separately from an expired/revoked session.

If GitHub shows the OAuth App as authorized but Núcleo AI is not connected, open the connector settings and start a new Device Flow authorization. A browser approval alone cannot restore a device code that has already expired.

## Repository roles in the app

- **Chat-context repository:** read-only source context for a particular message, only when the chat context option is enabled.
- **Build repository:** a separate private repository with write and Actions/workflow permissions. It stores the project ZIP/build workflow used for remote compilation.

Using separate repositories is recommended to keep generated build files and workflows away from the main source repository.

## Privacy notes

The chat never includes the GitHub access token in the prompt. When context is enabled, limited source-tree and source-file contents are sent to the configured AI provider together with the request. That provider's data-handling terms apply.

The connector is designed for personal alpha use. For wider distribution, migrate to a GitHub App with granular, explicitly scoped repository permissions instead of relying on a broad OAuth token.
