-- The owner chose to save their voluntary self-identification answers so the extension can fill them. Each is
-- optional: empty leaves the question for the owner. Work authorization and sponsorship are still never stored.
ALTER TABLE applicant_profile ADD COLUMN eeo_gender TEXT;
ALTER TABLE applicant_profile ADD COLUMN eeo_hispanic_latino TEXT;
ALTER TABLE applicant_profile ADD COLUMN eeo_veteran TEXT;
