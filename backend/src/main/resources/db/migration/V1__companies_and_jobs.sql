CREATE TABLE company (
    id               BIGSERIAL PRIMARY KEY,
    name             TEXT        NOT NULL,
    ats              TEXT        NOT NULL,
    board            TEXT        NOT NULL,
    careers_url      TEXT,
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_synced_at   TIMESTAMPTZ,
    last_sync_status TEXT,
    last_sync_error  TEXT,
    CONSTRAINT company_ats_board_unique UNIQUE (ats, board)
);

CREATE TABLE job (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT      NOT NULL REFERENCES company (id) ON DELETE CASCADE,
    external_id   TEXT        NOT NULL,
    title         TEXT        NOT NULL,
    team          TEXT,
    location      TEXT,
    workplace     TEXT        NOT NULL,
    posted_at     TIMESTAMPTZ,
    url           TEXT        NOT NULL,
    description   TEXT,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at  TIMESTAMPTZ NOT NULL,
    closed_at     TIMESTAMPTZ,
    CONSTRAINT job_company_external_unique UNIQUE (company_id, external_id)
);

-- The inbox lists open jobs, newest first
CREATE INDEX job_open_first_seen_idx ON job (first_seen_at DESC) WHERE closed_at IS NULL;
