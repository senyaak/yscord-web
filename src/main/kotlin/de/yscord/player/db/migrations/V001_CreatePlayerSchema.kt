package de.yscord.player.db.migrations

import de.yscord.player.db.Migration
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import org.springframework.stereotype.Component

/**
 * Creates the player's tables. The tables are a snapshot of the schema as it was
 * when this migration was written — later columns (V002) are not here.
 */
@Component
class V001CreatePlayerSchema : Migration {
    override val version = "001_create_player_schema"

    override fun up() = QueueItem.createStatement() + PlayerState.createStatement()

    override fun down() = PlayerState.dropStatement() + QueueItem.dropStatement()

    private object QueueItem : Table("queue_item") {
        val id = long("id").autoIncrement()
        val position = integer("position")
        val videoId = varchar("video_id", 32)
        val title = text("title")
        val duration = integer("duration")
        val uploader = varchar("uploader", 512)
        val thumbnail = text("thumbnail").nullable()
        val webpageUrl = text("webpage_url")

        override val primaryKey = PrimaryKey(id)
    }

    private object PlayerState : Table("player_state") {
        val id = integer("id")
        val currentIndex = integer("current_index")
        val loopMode = varchar("loop_mode", 16)
        val volume = double("volume")
        val updatedAt = timestamp("updated_at")

        override val primaryKey = PrimaryKey(id)
    }
}
