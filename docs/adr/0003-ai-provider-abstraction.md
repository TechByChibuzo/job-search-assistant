# 3. Claude behind an interface

Status: accepted (2026-10-25)

## Context

AI scores roles against the resume and drafts outreach. Two options were considered: a hosted model (best judgement and writing, paid per request) or a local model through Ollama (free, the resume never leaves the machine, noticeably weaker drafts). The owner chose hosted Claude for quality.

Costs need a ceiling, tests must never call a paid API, and the provider or model may change later.

## Decision

- Use Claude through the official Anthropic Java SDK, model set by `AI_MODEL` (default Claude Opus 5.5).
- Hide it behind a small `Claude` interface: one method that sends a system prompt, the resume and a request, and returns JSON matching a schema. `MatchService` and `DraftService` depend only on that.
- Ask for structured output (a JSON schema) rather than parsing prose; opt into server-side refusal fallbacks; turn every failure into a message for the owner.
- Cap spend: score only on demand and up to `AI_DAILY_LIMIT` new roles a day; cache the system prompt and resume; low effort for scoring.

## Consequences

- Switching model is a setting; switching provider (including a local model) is one new implementation of `Claude`.
- Tests use a fake `Claude`, plus one test of the real client against a local stub of the Messages API that checks the exact request sent.
- The resume and role text are sent to Anthropic's API. That is the trade made for quality; the local option remains open.
- Monthly cost depends on the model: at about 30 roles a day, roughly $15 to $20 with Claude Opus 5.5, well under $1 with Claude Haiku 5.5.
