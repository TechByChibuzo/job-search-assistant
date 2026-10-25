-- Optional search phrases per company, comma-separated ("software engineer, backend")
ALTER TABLE company ADD COLUMN search TEXT;

-- Whether a role is in the US, as far as its feed says: US, NON_US or UNKNOWN
ALTER TABLE job ADD COLUMN region TEXT NOT NULL DEFAULT 'UNKNOWN';

-- Roles imported on a company's first sync that were already old: seen, but not new
ALTER TABLE job ADD COLUMN baseline BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE job SET baseline = TRUE;
