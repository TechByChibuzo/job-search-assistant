# Job Search Assistant: brief

Notes from the first conversation about this project (2026-10-25), so work can continue in a new session. Nothing is designed or built yet. The next step is a design session, then a written plan.

## What it's for

A **personal** tool for the owner's own job search, not a product. The focus is **getting referrals**:
- find open roles at target companies
- find the right people (recruiters, engineers on the team, alumni)
- message them with personal, specific notes
- track every conversation and follow up

## Ground rules

- **Never automate LinkedIn.** No scraping, no automated messages or connection requests, no auto-applying. LinkedIn's User Agreement bans bots, it detects them, and a restricted account would hurt the search. The tool does the work *around* LinkedIn, and the owner does every click and send there.
- A browser extension may save the profile the owner **is already viewing** (name, title, company, URL) with one click. That replaces copy-paste; it doesn't browse on its own.
- Messages are drafted by AI and **reviewed and sent by the owner**. Quality over volume: roughly 10 to 20 personal messages a day.
- **Free to run.** Everything runs locally; paid services only on free tiers. See "Cost".
- **Commits:** no AI attribution of any kind in commit messages or PR descriptions (no co-author trailers, no "generated with" lines).

## Where jobs come from (verified working on 2026-10-25)

Most LinkedIn listings originally come from companies' hiring systems (ATS), and several publish public, read-only job APIs with no key or login:

| ATS | Endpoint | Tested with |
|---|---|---|
| Greenhouse | `https://boards-api.greenhouse.io/v1/boards/{board}/jobs` (add `?content=true` for descriptions) | `airbnb`: 191 jobs |
| Lever | `https://api.lever.co/v0/postings/{company}?mode=json` | `palantir`: 309, `spotify`: 90 |
| Ashby | `https://api.ashbyhq.com/posting-api/job-board/{board}` | `openai`: 793 |
| SmartRecruiters | public postings API (not tested yet) | |

**What we learned:**
- The board name usually shows in the careers-page URL: `boards.greenhouse.io/airbnb`, `jobs.lever.co/palantir`, `jobs.ashbyhq.com/openai`. The tool can detect the ATS and board from a pasted careers link.
- **Companies switch systems.** `plaid` returned "not found" on Lever, so a board that stops answering should trigger re-detection.
- **Workday** (many large companies) has no official public API. Its careers pages load jobs from an undocumented JSON endpoint that could be called politely, but treat that as best-effort.
- **LinkedIn-only listings** can't be pulled. Support pasting a job link instead.

## Rough shape (to be designed properly)

1. **Companies:** a list of target companies, each with its ATS and board.
2. **Daily fetch:** pull open roles, normalize them (title, team, location, remote, posted date, link, description), store them, and flag what's **new** since yesterday. Fresh roles are the best time to ask for a referral.
3. **Filter, then AI match:** keyword, location and seniority filters first; then AI scores the shortlist against the owner's resume and explains each fit.
4. **People:** contacts per company and role, saved through the browser extension or added by hand. Optional work-email lookup on a free tier (Hunter.io or Apollo).
5. **Outreach:** an AI-drafted referral message that names the exact role and a real connection point. The owner reviews and sends it on LinkedIn or by email.
6. **Tracking:** contacted → replied → referred → interviewing → offer or closed. Follow-up reminders after about 5 days of silence, with a drafted follow-up. Stats on which message styles get replies.

## Cost

- **Job APIs, local hosting (Docker), Gmail API, an unpacked Chrome extension:** free.
- **Email lookup:** free tiers (limited lookups a month), so look up only people the owner will actually contact.
- **AI:**
  - filter before calling AI, so only about 30 jobs a day get scored
  - a small, cheap model comes to an estimated few dollars a month
  - or a local model through Ollama, for $0 and a private resume, at somewhat lower quality

## Owner's background, for defaults

- Builds with **Java 21 / Spring Boot 3**, **React (Vite, Tailwind)**, **Postgres**, **Docker Compose**, GitHub Actions and Testcontainers; see `~/Developer/ai-finance-coach`.
- Has used Claude through AWS Bedrock, Stripe, Plaid and SendGrid.

## Open questions for the design session

- **Target roles and seniority**, and which locations or remote, so the first filters are right.
- **Who to reach first:** recruiters, engineers on the target team, or alumni (which school?).
- **Channels:** LinkedIn messages only, or email too?
- **Model:** a hosted model (best drafts, a few dollars a month) or a local one (free and private)?
- **Starting point:** a web app first, or the jobs engine plus tracker first, with the browser extension later?
