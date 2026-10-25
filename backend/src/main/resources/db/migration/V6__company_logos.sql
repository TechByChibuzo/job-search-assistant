-- The company's own website, for its logo; guessed when a company is added and editable
ALTER TABLE company ADD COLUMN domain TEXT;

-- Logos fetched once and served by the app itself, so the browser never asks a third party
CREATE TABLE company_logo (
    company_id   BIGINT      PRIMARY KEY REFERENCES company (id) ON DELETE CASCADE,
    domain       TEXT        NOT NULL,
    found        BOOLEAN     NOT NULL,
    content_type TEXT,
    image        BYTEA,
    fetched_at   TIMESTAMPTZ NOT NULL
);
