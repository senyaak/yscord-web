package de.yscord.player.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

/**
 * Schema for the player, defined in Kotlin (Exposed) rather than annotations —
 * the Knex-style "tables as code" approach. The migration runner turns these into
 * DDL; queries are built with the Exposed DSL in [PlayerStateStore].
 */

/** One track in the persisted queue; `position` keeps the order. */
object QueueItems : Table("queue_item") {
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

/** Singleton row (id = 1) holding transport state that outlives a server restart. */
object PlayerStateTable : Table("player_state") {
    val id = integer("id")
    val currentIndex = integer("current_index")
    val loopMode = varchar("loop_mode", 16)
    val volume = double("volume")
    // Added in V002 so a crash/restart resumes from the same spot.
    val positionSec = double("position_sec")
    val playing = bool("playing")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/** Migration history — the runner's equivalent of Knex's `knex_migrations`. */
object SchemaMigrations : Table("schema_migrations") {
    val version = varchar("version", 128)
    val appliedAt = timestamp("applied_at")

    override val primaryKey = PrimaryKey(version)
}
