# Non-Functional Requirements

## Performance

- App startup target (warm): <= 2.0s on representative mid-tier devices.
- Main feed/list interactions should remain smooth (no visible frame drops under normal load).
- P95 API latency target for core user actions: <= 400ms (excluding external provider delays).

## Reliability

- Critical flows (auth, ticket purchase, check-in) require graceful retry and user-visible status.
- Client and server errors must be observable with structured logs and trace IDs.
- No silent failure in payment, booking, or application flows.

## Security and Privacy

- No secrets in repository.
- Sensitive data must be protected in transit and at rest according to platform best practices.
- PII must be masked/omitted in logs and analytics.

## Availability and Operability

- Backend should support rolling deploy without data loss.
- Health checks and basic alerting must exist for production services.
- Incident triage path must be documented for payment and ticketing failures.

## Accessibility and UX Quality

- Core screens should support readable typography and clear error states.
- Critical actions (buy ticket, apply to event, approve lineup) must have explicit confirmation states.

## Rule

- Update this file when introducing a new operational constraint or SLO.
