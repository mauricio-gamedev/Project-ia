# AutoRouter, Context Handoff & Code Review

This alpha adds a practical first slice of multi-model routing and staged source repair. It is intentionally smaller than a full model gateway and does not promise unlimited free inference.

## Add a fallback route

The provider configured in **Configurações → Provedor remoto** remains the primary route for chat, planning, and initial code generation. In **Configurações → AutoRouter · modelos alternativos**, add one or more compatible routes with its own endpoint, model ID, API key, priority, capability tier, and approximate context budget.

- Alternative credentials are encrypted separately in Android Keystore-backed storage and are not written into the provider profile JSON.
- Lower priority numbers win for ordinary chat/planning fallback ordering.
- Capability tiers are user-assigned metadata: 1 = basic, 2 = balanced, 3 = stronger. They are not automatically benchmarked.
- For code review and repair, higher capability tiers are attempted first. Initial code generation starts with the selected primary and then escalates on failure.
- A route is attempted only when it is enabled, has a valid HTTPS or allowed loopback URL, and has a saved API key.

## Routing and cooldown policy

The router tries configured alternatives after failures instead of retrying the same route indefinitely. Numeric `Retry-After` headers on HTTP 429 are honored up to one hour; otherwise a quota response receives a five-minute default cooldown. Network/timeouts and server errors receive a shorter cooldown; invalid credentials and incompatible route configuration receive longer cooldowns.

Cooldowns persist locally across app restarts. If every usable route is in cooldown or lacks a key, the app reports that it cannot serve the request and asks the user to try later or configure another route. This is not a quota bypass and cannot guarantee that a free provider will always be available.

## Connect FreeLLMAPI in Termux (optional)

FreeLLMAPI is a separate Node.js gateway. Start it locally in Termux and keep its listener bound to loopback. For the usual local endpoint, add an alternative profile like this:

- Name: `FreeLLMAPI local`
- Base URL: `http://127.0.0.1:3001/v1`
- Model ID: `auto:smart` (or a route/profile supported by your gateway)
- API key: the gateway's configured client/API token, if authentication is enabled
- Capability tier: choose the tier you want this route to represent

The app permits cleartext HTTP only for `localhost` and `127.0.0.1`; remote hosts still require HTTPS. Do not expose the gateway to your LAN or the internet. Whether a model is actually available still depends on the provider credentials and current upstream quotas configured in FreeLLMAPI.

## Context handoff

Every request keeps its original system instructions and original user task. If the current model's configured character budget cannot fit all history, the context manager removes older middle messages first and keeps the newest messages, inserting a notice that context was omitted. It never silently truncates the mandatory system/task/latest content: if those cannot fit, that route is skipped and a route with a larger configured budget may be tried.

The configured budget is in characters, not an exact provider token count. Set it conservatively based on the model's actual context limit. When switching model families, prior tool state, hidden reasoning, or provider-specific attachments cannot be transferred unless represented in the text context.

## Review and repair workflow

1. Generate and validate a source-only project into app-private staging.
2. Explicitly check the code-review consent checkbox. A separate request sends the staged source snippets and project description to the currently selected provider route.
3. The model returns JSON findings. The app accepts only findings tied to paths in the staged manifest and displays the summary, severity, path, and recommendation.
4. If the snapshot is complete (at most eight files, no truncation/omissions), the user can request a repair proposal. The parser rejects new paths, duplicate paths, blank content, and oversized files.
5. The proposal is staged as a separate revision; the original staged project is untouched until the user reviews the before/proposed contents and explicitly applies the new revision.

This is static, model-assisted review. It does not run tests, lint, compilation, or generated code. Findings may be wrong or incomplete; absence of findings is not proof of correctness. Source must be reviewed before execution or use.

## Known limits

- The user must add route endpoints, model IDs, and API keys. The app does not scrape or automatically create third-party accounts/keys.
- Some providers do not support the same JSON response mode, content limits, or timeout behavior. Compatibility fallback is best effort.
- Capability tiers and context sizes are user-provided estimates; no live benchmark/catalog sync is included in this slice.
- No code execution sandbox is included. Test/build execution remains a separate future milestone requiring explicit roots, resource limits, process isolation, and cancellation.
