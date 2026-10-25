# 5. Local today, deployable by design

Status: accepted (2026-10-25)

## Context

The app runs on the owner's machine (ADR 0001), but the owner may host it later. Retrofitting deployment concerns into a codebase built only for localhost tends to mean rewrites: hard-coded paths, state in memory everywhere, settings in code, long requests that time out behind a proxy.

## Decision

Build for one machine, but keep every choice compatible with hosting:

- **Configuration from the environment**, with local defaults: database, port, bind address, sync schedule, rules.
- **Containers for everything**, with multi-stage builds, a non-root runtime user, health checks, and readiness and liveness probes.
- **Flyway migrations** applied on start; the schema is never changed by Hibernate.
- **No long requests.** Work that takes minutes (a full sync) runs in the background, and the UI polls for progress.
- **Same-origin web app.** nginx serves the static build and proxies `/api`, with a Content Security Policy and hardening headers.
- **A stateless API**, except for the sync run state and lock, which are kept in memory deliberately and documented as the first thing to move to the database.

Authentication is not built yet, because there is no network exposure to protect. It is the first item on the hosting checklist in docs/architecture.md, section 12.

## Consequences

- Hosting is a short checklist: add auth, move the sync lock to the database, supply secrets.
- A little more work up front (background sync, probes, headers) for a single-user app.
- The in-memory sync state limits the app to one instance until it moves.
