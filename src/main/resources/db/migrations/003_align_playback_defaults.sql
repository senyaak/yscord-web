-- Generated from PlayerTables, then reviewed:
-- 1. Dropped the generator's no-op "TYPE DOUBLE PRECISION".
-- 2. Postgres stores 002's "DEFAULT 0" as the text 0, the model expects 0.0.
-- 3. Added "playing": databases created before 001 was frozen got these columns
--    from 001 without any default, and 002 skipped them (IF NOT EXISTS). The
--    generator compares against a fresh database, so it can't see that drift.
ALTER TABLE player_state ALTER COLUMN position_sec SET DEFAULT 0.0;
ALTER TABLE player_state ALTER COLUMN playing SET DEFAULT FALSE;
