-- What the extension fills into application forms: the owner's own details, one row.
-- Legal attestations (work authorization, sponsorship, EEO) are deliberately not stored.
CREATE TABLE applicant_profile (
    id              BIGINT      PRIMARY KEY,
    first_name      TEXT,
    last_name       TEXT,
    email           TEXT,
    phone           TEXT,
    city            TEXT,
    state           TEXT,
    country         TEXT,
    linkedin_url    TEXT,
    github_url      TEXT,
    website_url     TEXT,
    current_company TEXT,
    current_title   TEXT,
    school          TEXT,
    degree          TEXT,
    graduation_year TEXT,
    updated_at      TIMESTAMPTZ NOT NULL
);
