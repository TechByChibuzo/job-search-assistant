-- A second degree for forms that ask for each (a master's and the bachelor's before it)
ALTER TABLE applicant_profile ADD COLUMN previous_school TEXT;
ALTER TABLE applicant_profile ADD COLUMN previous_degree TEXT;
