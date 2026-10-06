-- Live position, so a crash/restart resumes from the same spot (persisted every 5s).
-- IF NOT EXISTS: databases created before V001 was frozen already have these columns.
ALTER TABLE player_state ADD COLUMN IF NOT EXISTS position_sec DOUBLE PRECISION DEFAULT 0 NOT NULL;
ALTER TABLE player_state ADD COLUMN IF NOT EXISTS playing BOOLEAN DEFAULT FALSE NOT NULL;
