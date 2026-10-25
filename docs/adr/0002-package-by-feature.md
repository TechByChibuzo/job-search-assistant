# 2. Package by feature

Status: accepted (2026-10-25)

## Context

A layered layout (`controller/`, `service/`, `repository/`) spreads one feature across many folders. This app has clear features that grow independently: companies, jobs and their sources, the tracker, AI.

## Decision

Organise the backend by feature: `company`, `job`, `job.source`, `tracker`, with each feature's entity, repository, service, controller and DTOs together. Types a feature doesn't need to share are package-private.

## Consequences

- A change to one feature usually touches one package.
- Package-private types make accidental coupling a compile error.
- Shared code (`common`, `config`) stays small and is the only cross-feature dependency.
