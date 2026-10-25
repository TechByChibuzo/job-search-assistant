# System design

This is the design of Job Search Assistant: what it does, how the parts fit, and why it is built this way. The product brief is in [BRIEF.md](BRIEF.md); decisions with real trade-offs are recorded as ADRs in [adr/](adr/).

## 1. Problem

Applying cold has a low response rate. A referral from someone inside the company is the strongest signal a candidate can send, and it works best when the role is fresh. The work around a referral is tedious: watching career pages, spotting new roles, finding the right person, writing a personal note, remembering to follow up, and filling the same application forms again and again.

The app does that work. The owner keeps every judgement call and every send.

## 2. Goals and non-goals

**Goals**
- Pull open roles every day from the hiring systems (ATS) that target companies use, and flag what is new.
- Narrow those roles with cheap filters first, then rank the shortlist against the owner's resume with AI.
- Keep a record of the people at each company and every conversation with them, with follow-up reminders.
- Draft personal referral messages and tailored application documents for the owner to review.
- Fill application forms in the browser so that applying is mostly review.

**Non-goals**
- A multi-user product. There is one user, on one machine.
- Volume. The target is 10 to 20 personal messages a day, not hundreds.

**Anti-goals** (the app has failed if it does these, even if it works)
- Automating LinkedIn in any way: scraping, sending messages or connection requests, or applying. It breaks LinkedIn's User Agreement and would put the owner's account at risk.
- Submitting anything without the owner pressing the button. Legal attestations (work authorization, sponsorship, EEO) must be the owner's own answers.
- Inventing experience in a resume or cover letter.
- Costing money to run, beyond free tiers.

## 3. System context

```mermaid
flowchart LR
    owner([Owner])
    subgraph local[Owner's machine]
        web[Web app<br/>React]
        ext[Browser extension<br/>phase 4]
        api[API<br/>Spring Boot]
        db[(Postgres)]
    end
    gh[Greenhouse, Lever, Ashby<br/>public job APIs]
    lv[Workday, Avature<br/>career sites]
    ab[amazon.jobs,<br/>Google Careers]
    llm[LLM<br/>hosted or Ollama<br/>phase 3]
    li[LinkedIn]

    owner --> web
    owner --> li
    owner --> ext
    web --> api
    ext --> api
    api --> db
    api --> gh
    api --> lv
    api --> ab
    api -.-> llm
    ext -. reads the page the owner is viewing .-> li
```

The API is the only thing that talks to the outside world, and it only reads from it. LinkedIn appears only as a page the owner is looking at: the extension reads that one page when the owner clicks, and never navigates or sends.

## 4. Components

| Component | Tech | Responsibility |
|---|---|---|
| `backend/` | Java 21, Spring Boot 3.5, JPA, Flyway, jsoup | REST API, job sources, background sync, tracker, AI calls |
| `frontend/` | React 19, Vite, Tailwind CSS 4, TanStack Query, lucide icons | Inbox, companies, tracker board |
| Postgres 16 | Docker Compose | All state |
| Browser extension | Chrome MV3, unpacked | Save a viewed profile; fill application forms (phase 4) |

The backend is organised **by feature** rather than by layer (`company`, `job`, `job.source`, `tracker`), so each feature's entity, repository, service and controller sit together. See [ADR 0002](adr/0002-package-by-feature.md).

## 5. Job ingestion

### 5.1 Sources

| Source | How it is read | Board in careers URL | Kind |
|---|---|---|---|
| Greenhouse | `GET boards-api.greenhouse.io/v1/boards/{board}/jobs?content=true` | `boards.greenhouse.io/{board}` | Official API, whole board |
| Lever | `GET api.lever.co/v0/postings/{board}?mode=json` | `jobs.lever.co/{board}` | Official API, whole board |
| Ashby | `GET api.ashbyhq.com/posting-api/job-board/{board}` | `jobs.ashbyhq.com/{board}` | Official API, whole board |
| Workday | `POST {tenant}.{wdN}.myworkdayjobs.com/wday/cxs/{tenant}/{site}/jobs` | `{tenant}.{wdN}.myworkdayjobs.com/{site}` | The JSON its own pages call; search, newest first |
| Avature | `GET {sub}.avature.net/{portal}/SearchJobs/` (HTML) | `{sub}.avature.net/{portal}` | Results pages; whole board, no dates |
| Amazon | `GET amazon.jobs/en/search.json` | `amazon.jobs` | The JSON its own page loads; search, newest first |
| Google | `GET google.com/about/careers/applications/jobs/results` (HTML with embedded JSON) | `google.com/about/careers` | Results pages; search, newest first |

The first three are documented public APIs. The rest are read the way the companies' own careers pages load them: they work, but can change without notice, so a change shows up as "Last sync failed" with a plain message rather than a silently empty board. Sites that require a login, a token or a bot check to read jobs (Microsoft, Apple, Meta at the time of writing) are not read at all; the app never works around access controls. See [ADR 0004](adr/0004-unofficial-feeds.md).

LinkedIn-only listings, and companies with no readable feed, will be added by pasting a job link (phase 2).

### 5.2 One interface, one client per source

```mermaid
classDiagram
    class JobSource {
        <<interface>>
        +AtsType type()
        +FetchResult fetch(String board, FetchQuery query)
    }
    class FetchQuery {
        <<record>>
        now, postedAfter, searches, usOnly
    }
    class FetchResult {
        <<record>>
        List~FetchedJob~ jobs
        Instant coveredSince
        +covers(postedAt) boolean
    }
    class FetchedJob {
        <<record>>
        externalId, title, team, location
        workplace, region, postedAt, url, description
    }
    JobSource <|.. GreenhouseJobSource
    JobSource <|.. LeverJobSource
    JobSource <|.. AshbyJobSource
    JobSource <|.. WorkdayJobSource
    JobSource <|.. AvatureJobSource
    JobSource <|.. AmazonJobSource
    JobSource <|.. GoogleJobSource
    JobSource ..> FetchQuery
    JobSource ..> FetchResult
    FetchResult o-- FetchedJob
    JobSyncService --> JobSource : picks by AtsType
```

Each client maps its feed into the same `FetchedJob` record, so nothing past the client knows where a job came from. Adding a source means adding one class. A board that answers 404 throws `BoardNotFoundException`, which the sync records as `BOARD_NOT_FOUND`: companies switch systems, and the UI asks the owner for a fresh careers link. A page that no longer has the expected data throws `FeedFormatException`.

Normalisation rules worth knowing:
- **Workplace** is one of `REMOTE`, `HYBRID`, `ONSITE`, `UNKNOWN`. Ashby's `workplaceType` wins over its `isRemote` flag, which is also `true` for hybrid roles. Greenhouse carries it in a "Workplace Type" custom field.
- **Region** is `US`, `NON_US` or `UNKNOWN`, from structured country fields where a feed has them (Lever `country`, Ashby addresses, Amazon `country_code`, Google location codes) and otherwise from the location text (`Region.fromLocation`). The text rules are conservative: when unsure the answer is `UNKNOWN`, so a US role is never dropped by mistake.
- **Posted date** is Greenhouse `first_published`, Lever `createdAt`, Ashby `publishedAt`, Amazon `posted_date`, Google's creation timestamp, and Workday's relative "Posted 3 Days Ago" (anything past "30+ Days" reads as 31 days). Avature has no dates.
- **Description** is stored as plain text. Greenhouse sends HTML-escaped HTML, which is unescaped and stripped. Workday and Avature lists carry no descriptions; the detail pane links to the posting.

### 5.3 What gets kept

The same rules apply to every source (`IngestRules`), configured in `app.jobs`:

| Rule | Default | Applies to |
|---|---|---|
| US only | on | every role: `NON_US` roles are skipped, `UNKNOWN` ones kept |
| Maximum age | 30 days | roles not seen before; one already stored keeps refreshing |
| Search phrases | none (every role) | per company, comma-separated; a role matches a phrase when its title and team contain every word of it |

Search-based sources (Amazon, Google, Workday) also send the phrases and the US filter to the site, so they fetch only what is wanted. Amazon posts over 200 US roles a day, so phrases are what make it usable; the add-company form asks for them.

### 5.4 Paging and coverage

Search feeds are paged newest first, and stop at the first **whole page** older than the cutoff: these sites' date sort is only roughly ordered, so one old role mid-page doesn't stop paging. Pages are spaced out (`app.http.page-delay`, 750 ms) and capped (`app.http.max-pages`, 60).

A feed that stopped early hasn't seen older roles, so their absence proves nothing. `FetchResult.coveredSince` says how far back the list is complete, and the sync only closes a missing role if the result covers its posting date:

| Result | `coveredSince` | Closes a missing role when |
|---|---|---|
| Whole board read | null | always |
| Stopped at the age cutoff | the cutoff | it was posted after the cutoff |
| Hit the page limit with no dates (Avature) | `Instant.MAX` | never |

A stored role the rules now exclude (say the search phrases changed) is closed either way.

### 5.5 Sync

```mermaid
sequenceDiagram
    participant U as UI
    participant A as SyncController
    participant R as SyncRunner
    participant Y as JobSyncService
    participant C as JobSource
    participant D as Postgres
    U->>A: POST /api/sync
    A->>R: start()
    R-->>U: 202 Accepted, progress
    R->>Y: syncAll(progress)
    loop each active company
        Y->>C: fetch(board, query)
        alt feed read
            C-->>Y: FetchResult
            Y->>D: one transaction: insert, refresh, close, record status
        else 404
            Y->>D: last_sync = BOARD_NOT_FOUND
        else other failure
            Y->>D: last_sync = FAILED, message
        end
    end
    loop every 1.5 s while running
        U->>A: GET /api/sync
        A-->>U: current company, done / total, results
    end
```

- A full sync pages politely through search feeds and takes a few minutes, so it runs in the background. The UI polls `GET /api/sync` for progress and refreshes its lists when the run ends. The daily scheduled run goes through the same runner, so it shows the same progress.
- One run at a time: a second request gets 409.
- The HTTP fetch happens outside any transaction. Each company is then reconciled in its own short transaction, so a slow board never holds a database connection and one failure never rolls back another company.
- Jobs are never deleted. A job that leaves the feed is **closed**, so the tracker keeps its history; if it reappears, it reopens.
- A job is **new** when it was first seen in the last 24 hours, except on a company's first import: then only roles *posted* in the last 24 hours count, so adding a company doesn't mark its whole board as new (`job.baseline`).
- The inbox lists roles by the date they were **listed**: the posting date, or first-seen date for feeds without dates. Undated roles from a first import have no known age and sort last, under "Date unknown".
- The schedule is a cron expression (`app.sync.cron`, default 07:00 daily). A missed day is caught by the next run or by "Sync now".

### 5.6 Adding a company

The owner pastes a careers link. `AtsDetector` recognises the host and pulls out the board (`jobs.lever.co/palantir` → Lever, `palantir`; `nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite` → Workday, `nvidia.wd5/NVIDIAExternalCareerSite`). The form previews the result as the owner types and asks for search phrases when the source is search-based. An unknown link is rejected with a message, and the owner can pick the source and board by hand.

## 6. Data model

```mermaid
erDiagram
    COMPANY ||--o{ JOB : lists
    COMPANY ||--o{ CONTACT : employs
    JOB ||--o| APPLICATION : "applied to (phase 2)"
    CONTACT ||--o{ OUTREACH : "messaged in (phase 2)"
    JOB |o--o{ OUTREACH : "about (phase 2)"

    COMPANY {
        bigint id PK
        text name
        text ats "GREENHOUSE | LEVER | ASHBY | WORKDAY | AVATURE | AMAZON | GOOGLE"
        text board
        text careers_url
        text search "comma-separated phrases"
        boolean active
        timestamptz last_synced_at
        text last_sync_status "OK | BOARD_NOT_FOUND | FAILED"
        text last_sync_error
    }
    JOB {
        bigint id PK
        bigint company_id FK
        text external_id "unique per company"
        text title
        text team
        text location
        text workplace
        text region "US | NON_US | UNKNOWN"
        timestamptz posted_at
        text url
        text description
        timestamptz first_seen_at
        timestamptz last_seen_at
        timestamptz closed_at
        boolean baseline "seen on first import, not new"
    }
    CONTACT {
        bigint id PK
        bigint company_id FK
        text name
        text title
        text kind "RECRUITER | ENGINEER | ALUMNI | OTHER"
        text linkedin_url
        text email
    }
    OUTREACH {
        bigint id PK
        bigint contact_id FK
        bigint job_id FK
        text channel "LINKEDIN | EMAIL"
        text stage
        timestamptz sent_at
        date follow_up_on
    }
    APPLICATION {
        bigint id PK
        bigint job_id FK
        date applied_on
        text resume_version
        text stage
    }
```

Schema changes go through Flyway migrations in `backend/src/main/resources/db/migration`. Hibernate only validates the schema; it never changes it.

## 7. Tracker (phase 2)

Every conversation and application moves through one pipeline:

```mermaid
stateDiagram-v2
    [*] --> Contacted
    Contacted --> Replied
    Contacted --> FollowUpDue : about 5 days of silence
    FollowUpDue --> Contacted : follow-up sent
    Replied --> Referred
    Referred --> Interviewing
    Interviewing --> Offer
    Contacted --> Closed
    Replied --> Closed
    Interviewing --> Closed
    Offer --> [*]
    Closed --> [*]
```

`follow_up_on` is set when a message is sent and cleared when a reply is logged. The board's "Due today" column is a query on that date, so no background job is needed. Reply rates are grouped by message style to show what works.

## 8. AI (phase 3)

- **Filter before calling AI.** Keyword, location, workplace and seniority filters run in SQL first; only the shortlist (about 30 jobs a day) is scored.
- **Match:** the model gets the resume and a job description and returns a score with reasons, stored with the job so it is paid for once.
- **Drafting:** referral messages, follow-ups and cover letters are drafted from the resume, the role and a real connection point. The prompt forbids claiming experience that is not in the resume, and the owner edits before anything is used.
- **Provider behind an interface:** a small hosted model (a few dollars a month) or a local model through Ollama ($0, resume stays on the machine). See [ADR 0003](adr/0003-ai-provider-abstraction.md).

## 9. Browser extension (phase 4)

- **Save profile:** on a profile page the owner is viewing, one click sends name, title, company and URL to `POST /api/contacts`. It reads only the current page, only on click.
- **Fill applications:** on Greenhouse, Lever, Ashby and Workday application pages it fills contact details, links, history, the resume upload and drafted answers from the answer bank. It never clicks submit, and it leaves attestation questions (work authorization, sponsorship, EEO, background checks) for the owner.

## 10. Cross-cutting concerns

| Concern | Approach |
|---|---|
| Security | One user on localhost. The API binds to `127.0.0.1` and has no login. The web app reaches it through the same origin (Vite proxy in development, nginx in Docker), so CORS is only a fallback. nginx sends a Content Security Policy and the usual hardening headers. Hosting it means adding auth first: see section 11. |
| Personal data | The resume and answer bank live in `private/`, which git ignores. Nothing personal is committed. |
| Errors | One `@RestControllerAdvice` returns RFC 7807 problem details. Sync failures are stored per company, not thrown at the scheduler. |
| Politeness | A descriptive `User-Agent`, timeouts, a delay between pages, a page cap, and one sync a day. No source that needs a login, token or bot check is read. |
| Testing | Unit tests for detection, region rules, paging and every source against recorded real responses; integration tests of the sync and the REST API against real Postgres via Testcontainers; component tests for the UI. CI runs them all. |
| Observability | Actuator health with liveness and readiness probes; a log line per company per sync with counts. |
| Cost | $0: public feeds, local Docker, free tiers only. |

## 11. Deployment

Today the app runs on one machine. It is built so that hosting it later is a contained change rather than a rewrite ([ADR 0005](adr/0005-deployable-by-design.md)):

**Already in place**
- Every setting comes from the environment: database, port, bind address, sync schedule, age and US rules (`application.yml` lists the variables).
- Two containers, built by multi-stage Dockerfiles: the API (a non-root JRE image) and the web app (static files on nginx, with the API proxied on the same origin).
- Schema changes are Flyway migrations, applied on start; Hibernate only validates.
- Readiness and liveness probes at `/actuator/health/readiness` and `/liveness`; graceful shutdown; `X-Forwarded-*` headers trusted from a reverse proxy.
- The API keeps no session state.

**To do before hosting it**
1. **Authentication.** Spring Security with one sign-in method (a passkey or an OAuth provider), and the API bound to all interfaces only behind TLS.
2. **Sync state in the database.** The run state and the one-run-at-a-time lock are in memory, which is right for one instance. More than one instance needs a database lock (ShedLock) and a sync-run table.
3. **Secrets and personal data.** The database password and any AI key from a secret store; the resume in encrypted storage rather than `private/`.
4. **Multi-user, if ever.** A `user_id` on companies and everything below them. Nothing in the domain model assumes a single user beyond the missing column.

## 12. Roadmap

| Phase | Scope | Status |
|---|---|---|
| 1 | Companies, link detection, Greenhouse/Lever/Ashby sync, jobs inbox with filters and "new" flag | Done |
| 1.5 | Workday, Avature, Amazon and Google sources; US-only and age rules; search phrases; background sync with progress; redesigned UI | Done |
| 2 | Contacts, outreach and application tracker, follow-up reminders, paste-a-job-link | Planned |
| 3 | AI match scoring and message drafting | Planned |
| 4 | Browser extension: save profile, fill applications | Planned |
| 5 | Tailored resumes and cover letters, answer bank, reply-rate stats | Planned |
