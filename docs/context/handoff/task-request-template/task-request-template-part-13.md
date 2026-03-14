# Task Request Template Part 13

## Formalized Implementation Request (Finish Uncompleted Authorization Slice)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/governance/decision-traceability.md`
  - `D-059`
- Current constraints:
  - The previous chat partially implemented authorization work but did not finish the repository-wide sync needed to make the slice safely handoff-ready.
  - The codebase already contains credential-auth and VK-auth implementation pieces, but context docs and top-level READMEs still describe the superseded phone-first baseline.
  - `docs/context/*` remains the primary source of truth, so stale auth docs must be corrected in the same change as any final code/test updates.

## Goal

- What should be delivered:
  - finish the current authorization slice so credential auth and VK auth are no longer left in an ambiguous partially-documented state
  - add the missing focused automated coverage for the new auth routes/state handling
  - resynchronize architecture, API contract, traceability, and handoff memory with the implemented auth baseline

## Scope

- In scope:
  - verify and complete credential-auth + VK auth implementation readiness
  - add focused server tests for VK auth start/verify/state handling
  - update API contract and implementation-status docs from phone-first to `login + password + VK ID`
  - update governance traceability and session memory for this completion pass
- Out of scope:
  - provisioning live VK production credentials
  - live-device auth smoke tests against production/staging in the same task
  - password recovery/reset flows

## Constraints

- Tech/business constraints:
  - internal `User + AuthIdentity + session` architecture remains mandatory
  - credentials must stay first-party with secure password hashing and abuse controls
  - VK ID remains browser/backend driven and must fit the current mobile callback flow
  - no secrets, VK credentials, or diagnostics tokens may be written into repository docs

## Definition of Done

- Functional result:
  - credential auth and VK auth repository slice is covered by focused automated checks
  - `docs/context/*` and top-level READMEs no longer claim the superseded phone-first auth baseline
  - decision traceability reflects that `D-059` has implementation progress rather than docs-only planning
  - the next chat can continue auth work without rediscovering the repository state
- Required docs updates:
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/governance/decision-traceability.md`
  - `docs/context/governance/session-log.md`

## Formalized Setup Request (VK ID Runtime Configuration Checklist)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/governance/session-log.md`
  - `D-059`
- Current constraints:
  - The repository-level VK auth implementation is already present, but runtime configuration in VK ID and target env files is still incomplete.
  - Local docs list optional `VK_ID_*` vars, but `.env.example` files were missing those placeholders, which increases setup ambiguity.
  - The current app callback parser and mobile manifests expect the VK callback URL to be recognized through a stable `/auth/vk` callback shape on the app side.

## Goal

- What should be delivered:
  - produce a repo-specific checklist for configuring VK ID in the external VK cabinet and in runtime envs
  - sync setup examples so operators can see which `VK_ID_*` values must be provided

## Scope

- In scope:
  - verify current official VK ID no-SDK and business-verification requirements
  - map those requirements to the current server/mobile implementation
  - add missing `VK_ID_*` placeholders to local and deploy env examples
  - record setup conclusions in governance memory
- Out of scope:
  - creating the real VK business profile or application in someone else's VK cabinet
  - storing real VK secrets or tokens in repository files
  - changing the current VK callback architecture in the same task

## Constraints

- Tech/business constraints:
  - `docs/context/*` stays the primary source of truth
  - real VK credentials and business data must stay outside the repository
  - the configured redirect URI must match both VK cabinet settings and the repository callback handling exactly

## Definition of Done

- Functional result:
  - the repository has explicit `VK_ID_*` env placeholders for local and deploy setups
  - the user has an actionable checklist for VK business verification, app creation, callback alignment, env wiring, and smoke validation
  - session memory captures the repo-specific redirect/setup nuance for the next chat

## Formalized Implementation Request (Android Release Signing Automation)

## Context

- Related docs/decisions:
  - `docs/context/engineering/tooling-stack.md`
  - `README.md`
  - `composeApp/build.gradle.kts`
- Current constraints:
  - A release keystore was generated locally in the project root, but Android release builds are not yet wired to use it automatically.
  - Keystore secrets must not be committed to git or copied into context docs.
  - The user wants the key moved into a dedicated folder and release signing handled through Gradle.

## Goal

- What should be delivered:
  - move the generated Android release keystore into a dedicated signing folder
  - configure Gradle so release builds use that keystore automatically when local signing credentials are present
  - document the local signing workflow without storing secrets in the repository

## Scope

- In scope:
  - move the generated `.jks` file into a dedicated local folder inside the repository
  - ignore local signing materials in git
  - configure `composeApp` release signing to read credentials from local ignored properties or env/Gradle properties
  - document the release-signing workflow in source-of-truth docs
- Out of scope:
  - uploading the key to any external store
  - storing real keystore passwords in tracked files
  - configuring Play App Signing or CI secrets in the same task

## Constraints

- Tech/business constraints:
  - release signing must remain compatible with local Android Studio and Gradle CLI builds
  - the repository must not silently rely on an untracked key in the root directory anymore
  - no secrets may be added to tracked docs or config files

## Definition of Done

- Functional result:
  - the keystore lives under a dedicated signing folder
  - `:composeApp:assembleRelease` uses a Gradle release signing config automatically when local credentials are configured
  - tracked docs explain where the keystore and local signing credentials live

## Formalized Implementation Request (VK iOS Callback And Universal Link Readiness)

## Context

- Related docs/decisions:
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/governance/session-log.md`
  - `D-059`
- Current constraints:
  - VK auth repository support exists, but iOS is not rollout-ready because the project lacks universal-link plumbing and the server lacks the public VK HTTPS callback bridge route expected by the configured redirect URI.
  - Android currently relies on app deep links, while server tests already assume `https://incomedy.ru/auth/vk/callback` as the canonical redirect URI.
  - The user now needs concrete project changes first and only then the exact VK cabinet values.

## Goal

- What should be delivered:
  - make the repository ready for a single public VK callback URI that works with the current mobile auth architecture
  - add the missing iOS project wiring needed for universal-link based VK return flow
  - expose the associated-domains metadata needed for iOS universal-link verification

## Scope

- In scope:
  - add a public VK callback bridge route and static bridge page on the server
  - add iOS associated-domains entitlement and universal-link callback handling
  - expose Apple App Site Association JSON from the backend when configured
  - update docs/env examples/tests for the new callback contract
- Out of scope:
  - live VK cabinet changes done outside the repository
  - real Apple team/app identifiers committed into tracked secrets files
  - production smoke validation on a physical iPhone in the same change

## Constraints

- Tech/business constraints:
  - `docs/context/*` remains the primary source of truth
  - keep the active auth baseline as `login + password + VK ID`
  - prefer one stable public VK callback URI over divergent ad-hoc platform-specific callback contracts
  - no secrets may be written into tracked docs or config files

## Definition of Done

- Functional result:
  - the repository exposes a public VK callback bridge under the production domain
  - iOS project files are ready to receive VK return links through associated domains
  - server runtime has an explicit optional config for Apple App Site Association app ids
  - the user can fill VK cabinet fields from the updated project state without guessing
