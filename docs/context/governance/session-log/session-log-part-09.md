# Session Log Part 09

## 2026-03-15 01:28

- Context: The user clarified a standing operational constraint: there is currently no Apple Developer Program account and no Google Play developer account available for the project, and this must be preserved as persistent project context rather than treated as a one-off chat note.
- Changes: Added this constraint to `docs/context/product/product-brief.md` as an always-on delivery limitation, recorded a dedicated active risk in `docs/context/product/risk-log.md`, split the overflowing session-log/task-request-template indexes to new parts per the context protocol, and formalized the documentation request so future chats inherit the constraint explicitly.
- Decisions: Treat absence of Apple/Google developer accounts as a default planning constraint for all future work; any task depending on App Store Connect, Play Console, paid Apple entitlements, or platform-console-only provider setup/validation must call out the dependency or remain intentionally blocked/unvalidated.
- Next: Keep this constraint visible during auth, release, payments, and mobile rollout planning, and only relax it after the user explicitly confirms that the required platform developer accounts have been provisioned.

## 2026-03-15 01:28

- Context: The user clarified an Android auth UX requirement: VK authorization must attempt to open through the installed VK application first, and only fall back to the browser when VK is not installed or cannot handle the auth URL.
- Changes: Implemented Android-only external-auth launch planning that prefers a package-targeted VK `ACTION_VIEW` intent and keeps a browser fallback, added focused unit tests for `VK app first / browser fallback`, and synchronized architecture/readme context to describe the new Android launch policy without changing the shared backend callback contract.
- Decisions: Keep the current shared backend VK start/verify flow and public callback URI, but make Android launch behavior explicitly app-preferred for VK instead of always browser-first. Treat the package-targeted app launch as a client-side policy layered on top of the existing VK auth URL returned by the backend.
- Next: Run Android unit tests and smoke the VK flow on a device with and without the VK app installed to confirm that package-targeted launch resolves into the VK app before falling back to the browser.
