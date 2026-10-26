-- When the app last tried to fetch a description for a role whose feed left it out, so it asks a site once
-- rather than every time the role is opened
ALTER TABLE job ADD COLUMN description_checked_at TIMESTAMPTZ;
