-- Roles added by pasting a link; a sync never closes them
ALTER TABLE job ADD COLUMN manual BOOLEAN NOT NULL DEFAULT FALSE;

-- People at a company the owner might ask for a referral
CREATE TABLE contact (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT      NOT NULL REFERENCES company (id) ON DELETE CASCADE,
    name         TEXT        NOT NULL,
    title        TEXT,
    kind         TEXT        NOT NULL,
    linkedin_url TEXT,
    email        TEXT,
    notes        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX contact_company_idx ON contact (company_id);

-- A role the owner is pursuing, and how far it has got
CREATE TABLE pursuit (
    id             BIGSERIAL PRIMARY KEY,
    job_id         BIGINT      NOT NULL UNIQUE REFERENCES job (id) ON DELETE CASCADE,
    stage          TEXT        NOT NULL,
    applied_on     DATE,
    resume_version TEXT,
    notes          TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

-- A conversation with one person, usually about one role
CREATE TABLE outreach (
    id                BIGSERIAL PRIMARY KEY,
    contact_id        BIGINT      NOT NULL REFERENCES contact (id) ON DELETE CASCADE,
    job_id            BIGINT      REFERENCES job (id) ON DELETE SET NULL,
    channel           TEXT        NOT NULL,
    status            TEXT        NOT NULL,
    message           TEXT,
    first_sent_at     TIMESTAMPTZ NOT NULL,
    last_contacted_at TIMESTAMPTZ NOT NULL,
    follow_ups        INT         NOT NULL DEFAULT 0,
    follow_up_on      DATE
);
CREATE INDEX outreach_contact_idx ON outreach (contact_id);
CREATE INDEX outreach_job_idx ON outreach (job_id);
CREATE INDEX outreach_due_idx ON outreach (follow_up_on) WHERE status = 'AWAITING_REPLY';

-- Everything that happened in a conversation, for the timeline and reply-rate stats
CREATE TABLE outreach_event (
    id          BIGSERIAL PRIMARY KEY,
    outreach_id BIGINT      NOT NULL REFERENCES outreach (id) ON DELETE CASCADE,
    type        TEXT        NOT NULL,
    at          TIMESTAMPTZ NOT NULL,
    note        TEXT
);
CREATE INDEX outreach_event_outreach_idx ON outreach_event (outreach_id);
