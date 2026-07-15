package de.yscord.player.db.migrations

import de.yscord.player.db.Migration
import de.yscord.player.db.PlayerStateTable
import de.yscord.player.db.QueueItems
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.springframework.stereotype.Component

/** Creates the player's tables. `down()` drops them — the rollback path. */
@Component
class V001CreatePlayerSchema : Migration {
    override val version = "001_create_player_schema"

    override fun up() {
        SchemaUtils.create(QueueItems, PlayerStateTable)
    }

    override fun down() {
        SchemaUtils.drop(PlayerStateTable, QueueItems)
    }
}
