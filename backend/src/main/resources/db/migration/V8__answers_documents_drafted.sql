-- Reusable answers to application questions
CREATE TABLE answer (
    id         BIGSERIAL PRIMARY KEY,
    question   TEXT        NOT NULL,
    answer     TEXT        NOT NULL,
    uses       INT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Resumes and cover letters tailored to one role, kept so each is paid for once
CREATE TABLE tailored_document (
    id         BIGSERIAL PRIMARY KEY,
    job_id     BIGINT      NOT NULL REFERENCES job (id) ON DELETE CASCADE,
    kind       TEXT        NOT NULL,
    content    TEXT        NOT NULL,
    model      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX tailored_document_job_idx ON tailored_document (job_id);

-- Whether the first message was drafted by Claude, for reply-rate insights
ALTER TABLE outreach ADD COLUMN drafted BOOLEAN NOT NULL DEFAULT FALSE;
