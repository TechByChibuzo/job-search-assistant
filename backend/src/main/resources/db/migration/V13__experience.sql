-- How senior a role is: the fewest years of experience its posting asks for (with the sentence), and whether
-- its title says senior (Principal, Staff, Senior, Lead, Manager…)
ALTER TABLE job ADD COLUMN min_years INTEGER;
ALTER TABLE job ADD COLUMN years_evidence TEXT;
ALTER TABLE job ADD COLUMN senior_title BOOLEAN NOT NULL DEFAULT FALSE;
