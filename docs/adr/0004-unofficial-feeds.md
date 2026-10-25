# 4. Read big employers' own careers pages, never around access controls

Status: accepted (2026-10-25)

## Context

The target list includes large tech companies that don't use Greenhouse, Lever or Ashby. Checked on 2026-10-25:

| Company | What their careers site does |
|---|---|
| Amazon | `amazon.jobs` loads results from a JSON endpoint that answers without a key, with a US filter and newest-first sort |
| Google | The results page embeds the listings as JSON, with country codes and timestamps |
| Workday companies (Nvidia, Salesforce, Adobe…) | Pages call `POST /wday/cxs/{tenant}/{site}/jobs`, which answers without a key; dates are relative |
| Bloomberg (Avature) | HTML results pages; the RSS feed ignores paging and has no locations |
| Goldman Sachs | higher.gs.com calls a GraphQL API that answers without a login, with a US filter, date sort and descriptions |
| Microsoft | The jobs API answers "Please try again later" to anything but a browser session |
| Apple | The search API answers 401 without a token |
| Meta | Jobs load through a GraphQL API that needs page-issued tokens |

None of the first five are documented APIs. They are what the companies' own pages use, and they can change.

## Decision

- Read the sources that answer a plain request, the same way a browser loading the careers page would, at a personal scale: one sync a day, pages spaced out, a page cap, a descriptive `User-Agent`.
- Never work around an access control: no session or token scraping, no headless browsers to pass bot checks, no rotating identities. Microsoft, Apple and Meta are not read; their roles come in through paste-a-job-link and job-alert emails the owner subscribes to.
- Treat these sources as best-effort. When a page no longer has the expected data, the source throws `FeedFormatException` and the company shows "Last sync failed" with a plain message, instead of quietly going empty.
- Test each one against a trimmed copy of a real response, so a parser change is deliberate.

## Consequences

- The big employers that matter most are covered without credentials or cost.
- These sources will break occasionally. The failure is visible, contained to that company, and fixed by updating one class.
- Some companies can't be covered automatically. That is the cost of staying on the right side of their terms.
