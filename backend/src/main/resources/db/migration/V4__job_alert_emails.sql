-- The owner's Gmail connection: one row at most. The refresh token is read-only (gmail.readonly);
-- encrypt it before hosting the app (docs/architecture.md, section 12).
CREATE TABLE gmail_account (
    id                 BIGINT      PRIMARY KEY,
    email              TEXT        NOT NULL,
    refresh_token      TEXT        NOT NULL,
    connected_at       TIMESTAMPTZ NOT NULL,
    last_import_at     TIMESTAMPTZ,
    last_import_error  TEXT,
    last_emails_read   INT         NOT NULL DEFAULT 0,
    last_roles_added   INT         NOT NULL DEFAULT 0,
    last_unreadable    INT         NOT NULL DEFAULT 0
);

-- Alert emails already read, so each is processed once
CREATE TABLE imported_email (
    message_id  TEXT        PRIMARY KEY,
    sender      TEXT,
    subject     TEXT,
    received_at TIMESTAMPTZ,
    imported_at TIMESTAMPTZ NOT NULL,
    roles_found INT         NOT NULL
);
