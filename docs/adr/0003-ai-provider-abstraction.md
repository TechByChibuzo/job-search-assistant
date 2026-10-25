# 3. AI provider behind an interface

Status: proposed (phase 3)

## Context

AI scores jobs against the resume and drafts messages. A small hosted model gives better drafts for a few dollars a month. A local model through Ollama costs nothing and keeps the resume on the machine, at lower quality. The owner hasn't chosen yet, and may change their mind.

## Decision

Put AI calls behind one interface (`match(resume, job)`, `draft(request)`), with one implementation per provider, selected by config. Calls happen only on the filtered shortlist, and results are stored so each job is scored once.

## Consequences

- Switching provider is a config change.
- Costs stay bounded: about 30 scored jobs a day.
- Prompts and output parsing are shared, so both providers are tested the same way.
