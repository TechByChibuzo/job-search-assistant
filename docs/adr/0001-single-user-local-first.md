# 1. Single user, local first

Status: accepted (2026-10-25)

## Context

The app serves one person's job search. It holds a resume, contact details for real people, and private notes on conversations. It must cost nothing to run.

## Decision

Run everything on the owner's machine with Docker Compose: Postgres, the API and the web app. The API binds to `127.0.0.1` and has no login. CORS allows only the web app's origin.

## Consequences

- No hosting bill, and personal data never leaves the machine (except to an AI provider, if a hosted one is chosen; see ADR 0003).
- No auth code to write or secure in the early phases.
- The daily sync only runs while the machine is on. That is acceptable: a missed run is caught by the next one or by "Sync now", and job freshness is measured in days.
- Hosting it later means adding authentication first. The API is stateless, so that is a contained change.
