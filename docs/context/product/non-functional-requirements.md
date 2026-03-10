# Non-Functional Requirements

## Performance

- App startup target (cold): <= 3.0s on representative mid-tier devices.
- App startup target (warm): <= 2.0s on representative mid-tier devices.
- Main feed/list interactions should remain smooth (no visible frame drops under normal load).
- P95 API latency target for core user actions: <= 400ms (excluding external provider delays).
- P95 latency target for seat-hold/order creation: <= 700ms before provider checkout handoff.
- Live stage-status and lineup updates should reach active clients within 2s under normal network conditions.

## Reliability

- Critical flows (auth, ticket purchase, check-in) require graceful retry and user-visible status.
- Refund and donation flows also require explicit user-visible status and recovery path.
- Client and server errors must be observable with structured logs and trace IDs.
- No silent failure in payment, booking, or application flows.
- Ticketing domain must prevent double-sell of the same inventory unit.
- Payment/donation webhooks must be signature-verified and processed idempotently.
- Successful payment must always end in either issued ticket/donation success or a tracked recovery incident.

## Security and Privacy

- No secrets in repository.
- Sensitive data must be protected in transit and at rest according to platform best practices.
- PII must be masked/omitted in logs and analytics.
- Mobile auth tokens must remain in secure storage only.
- RBAC is mandatory for organizer workspace and staff actions.
- Public auth/payment/webhook entrypoints require request-size limits, rate limiting, and replay protection where applicable.
- Critical organizer actions (pricing, refunds, lineup live changes, staff invites) require audit log coverage.

## Availability and Operability

- Backend should support rolling deploy without data loss.
- Health checks and basic alerting must exist for production services.
- Incident triage path must be documented for payment and ticketing failures.
- Hold expiration, refund reconciliation, and payout sync require background-job observability.

## Accessibility and UX Quality

- Core screens should support readable typography and clear error states.
- Critical actions (buy ticket, apply to event, approve lineup) must have explicit confirmation states.
- QR check-in and live stage status must remain legible and operable under venue lighting/stress conditions.

## Rule

- Update this file when introducing a new operational constraint or SLO.
