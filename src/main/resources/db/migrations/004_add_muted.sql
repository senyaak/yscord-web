-- Generated from PlayerTables. Review before committing: a rename shows up
-- as DROP + ADD (data loss), type changes may need USING, data moves are manual.
ALTER TABLE player_state ADD muted BOOLEAN NOT NULL;
