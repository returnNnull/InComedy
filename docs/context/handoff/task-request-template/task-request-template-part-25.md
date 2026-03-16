# Task Request Template Part 25

## Formalized Review Follow-Up (Ticketing Diagnostics Granularity)

## Why This Step

- Review of the new ticketing foundation found that multiple `404` outcomes were collapsed into one diagnostics stage, which made sanitized server diagnostics less useful for production triage.
- Operators need to distinguish `event`, `inventory`, and `hold` failures through safe low-cardinality metadata without changing the client-facing HTTP contract.
- This follow-up should stay bounded to observability and regression coverage, not expand into checkout, sold-out automation, or persistence redesign.

## Scope

- Refine ticketing route diagnostics for:
  - missing event
  - unavailable event inventory surface
  - missing inventory unit during hold create
  - missing hold during release
- Keep the HTTP `404` response body unchanged for clients.
- Add route-level diagnostics tests that assert `requestId` correlation plus safe `resource/reason` metadata.

## Explicitly Out Of Scope

- changing ticket inventory persistence semantics or hold lifecycle behavior
- checkout/order/payment implementation
- redesigning the diagnostics endpoint or storage backend
- addressing the separate review findings about DB lock ordering or read/write reconciliation cost

## Constraints

- Diagnostics must remain sanitized and bounded, using only low-cardinality `stage` plus safe metadata.
- The change must preserve the existing protected ticketing route surface and public API responses.
- Governance memory must record this review follow-up as a separate bounded task.

## Acceptance Signals

- Distinct diagnostics stages exist for `ticketing.event.not_found`, `ticketing.event.unavailable`, `ticketing.inventory.not_found`, and `ticketing.hold.not_found`.
- Safe metadata identifies `resource` and bounded `reason` values for those events.
- Route tests fail if diagnostics regress back to a single ambiguous `ticketing.not_found` stage.

## Implementation Outcome

## Delivered

- `TicketingRoutes` now emits resource-aware diagnostics stages and safe `resource/reason` metadata for ticketing `404` outcomes, while preserving the existing client-facing `404` payload.
- `TicketingRoutesTest` now covers missing event, unavailable event, missing inventory unit, and missing hold diagnostics capture with `X-Request-ID` correlation.

## Verification

- `./gradlew :server:test --tests com.bam.incomedy.server.ticketing.TicketingRoutesTest`

## Remaining Follow-Up

- DB lock-order normalization in PostgreSQL ticketing persistence
- separating heavy inventory reconciliation from the public read path

## Formalized Review Follow-Up (Ticketing Lock Ordering And Read-Path Reconcile)

## Why This Step

- The diagnostics remediation left two material ticketing review findings unresolved: PostgreSQL deadlock risk from mixed lock ordering and a public inventory read path that still wanted a full reconcile whenever no persistent freshness marker existed.
- Both gaps affect production robustness more than product surface, so they should be fixed as bounded infrastructure follow-up before checkout or sold-out automation expands the ticketing load profile.

## Scope

- Normalize PostgreSQL ticketing mutation flows around inventory-first locking to remove the `hold -> inventory` versus `inventory -> hold` deadlock shape.
- Add a persisted inventory sync marker keyed by organizer event revision so unchanged inventory reads do not perform a full write-heavy reconcile.
- Add regression coverage proving:
  - unchanged repeated inventory reads do not trigger a new sync
  - event update invalidates the sync marker and triggers a new derive/sync pass
  - migration coverage includes the new sync-state table

## Explicitly Out Of Scope

- public catalog access or unauthenticated ticket inventory
- checkout/order capture, QR issuance, refunds, or check-in
- removing the remaining need to expire overdue holds during reads when a hold actually becomes stale

## Constraints

- Frozen `EventHallSnapshot` remains immutable and inventory still derives from snapshot plus event-local overrides.
- Diagnostics and HTTP contracts stay stable; this follow-up is persistence/orchestration only.
- New server-side logic continues to use Russian responsibility/flow comments.

## Acceptance Signals

- `releaseSeatHold` no longer takes locks in the opposite order from create/reconcile flows.
- Inventory GET performs at most a cheap freshness check and snapshot load when organizer event revision has not changed.
- Schema migration history includes a persistent ticketing inventory sync-state table.
- Route and migration tests protect the new behavior.

## Implementation Outcome

## Delivered

- Added `ticket_inventory_sync_state` with `V9__ticketing_inventory_sync_state.sql` and moved ticketing inventory sync to an organizer-event revision marker instead of blind full reconcile on every read.
- `EventTicketingService` now syncs inventory only when `StoredOrganizerEvent.updatedAt` changes or when inventory has not yet been initialized; unchanged reads now load inventory plus expire overdue holds without replaying the full upsert pass.
- `PostgresTicketingRepository` now serializes release flow through inventory-first locking (`FOR UPDATE OF i`), aligning it with create/expiry paths and removing the previous opposite lock-order pattern.
- `TicketingRoutesTest` now asserts bootstrap-only sync, no resync on unchanged repeated reads, and resync after organizer event update.

## Verification

- `./gradlew :server:test --tests com.bam.incomedy.server.db.DatabaseMigrationRunnerTest --tests com.bam.incomedy.server.ticketing.TicketingRoutesTest`

## Remaining Follow-Up

- add dedicated PostgreSQL-level concurrency regression coverage if ticketing write throughput grows beyond the current foundation slice
