-- Generated from PlayerTables, then reviewed. The draft had no default: fine on
-- the empty test database, failed in production because player_state holds a
-- row (column "muted" contains null values). Edited rather than superseded: it
-- never got applied anywhere, the failed run rolled back.
ALTER TABLE player_state ADD muted BOOLEAN DEFAULT FALSE NOT NULL;
