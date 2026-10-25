-- How well a role fits the owner's resume, as judged by Claude
ALTER TABLE job ADD COLUMN match_score     INT;
ALTER TABLE job ADD COLUMN match_summary   TEXT;
ALTER TABLE job ADD COLUMN match_strengths TEXT;
ALTER TABLE job ADD COLUMN match_gaps      TEXT;
ALTER TABLE job ADD COLUMN match_model     TEXT;
ALTER TABLE job ADD COLUMN matched_at      TIMESTAMPTZ;

CREATE INDEX job_match_score_idx ON job (match_score DESC NULLS LAST) WHERE closed_at IS NULL;
