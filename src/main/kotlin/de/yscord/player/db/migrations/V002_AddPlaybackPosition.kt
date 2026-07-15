package de.yscord.player.db.migrations

import de.yscord.player.db.Migration
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component

/**
 * Adds live-position columns to player_state so a crash/restart resumes playback
 * from the same spot (the scheduler persists them every 5s). Raw ALTERs with
 * IF [NOT] EXISTS keep it idempotent and dialect-neutral (Postgres + H2), and
 * `down()` reverts them — a real, reversible migration.
 */
@Component
class V002AddPlaybackPosition : Migration {
    override val version = "002_add_playback_position"

    override fun up() {
        exec("ALTER TABLE player_state ADD COLUMN IF NOT EXISTS position_sec DOUBLE PRECISION DEFAULT 0 NOT NULL")
        exec("ALTER TABLE player_state ADD COLUMN IF NOT EXISTS playing BOOLEAN DEFAULT FALSE NOT NULL")
    }

    override fun down() {
        exec("ALTER TABLE player_state DROP COLUMN IF EXISTS position_sec")
        exec("ALTER TABLE player_state DROP COLUMN IF EXISTS playing")
    }

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }
}
