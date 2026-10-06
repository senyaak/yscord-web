package de.yscord.player.db.migrations

import de.yscord.player.db.Migration
import org.springframework.stereotype.Component

/**
 * Adds live-position columns to player_state so a crash/restart resumes playback
 * from the same spot (the scheduler persists them every 5s). IF [NOT] EXISTS keeps
 * it idempotent: databases created before V001 was frozen already have them.
 */
@Component
class V002AddPlaybackPosition : Migration {
    override val version = "002_add_playback_position"

    override fun up() = listOf(
        "ALTER TABLE player_state ADD COLUMN IF NOT EXISTS position_sec DOUBLE PRECISION DEFAULT 0 NOT NULL",
        "ALTER TABLE player_state ADD COLUMN IF NOT EXISTS playing BOOLEAN DEFAULT FALSE NOT NULL",
    )

    override fun down() = listOf(
        "ALTER TABLE player_state DROP COLUMN IF EXISTS position_sec",
        "ALTER TABLE player_state DROP COLUMN IF EXISTS playing",
    )
}
