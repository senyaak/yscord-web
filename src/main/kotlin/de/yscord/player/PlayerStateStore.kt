package de.yscord.player

import de.yscord.player.db.PlayerStateTable
import de.yscord.player.db.QueueItems
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Service
import java.time.Instant

/** The persisted player: the queue plus transport state (index, loop, volume, position). */
data class PlayerSnapshot(
    val tracks: List<TrackInfo>,
    val index: Int,
    val loop: String,
    val volume: Double,
    val positionSec: Double = 0.0,
    val playing: Boolean = false,
)

/**
 * Reads/writes the player snapshot with the Exposed DSL. Persistence is a simple
 * full snapshot: the frontend store is the source of truth and pushes its whole
 * state; here we replace the queue rows and upsert the singleton state row.
 */
@Service
class PlayerStateStore {

    fun load(): PlayerSnapshot = transaction {
        val tracks = QueueItems.selectAll()
            .orderBy(QueueItems.position)
            .map {
                TrackInfo(
                    id = it[QueueItems.videoId],
                    title = it[QueueItems.title],
                    duration = it[QueueItems.duration],
                    uploader = it[QueueItems.uploader],
                    thumbnail = it[QueueItems.thumbnail],
                    webpageUrl = it[QueueItems.webpageUrl],
                )
            }
        val state = PlayerStateTable.selectAll().firstOrNull()
        PlayerSnapshot(
            tracks = tracks,
            index = state?.get(PlayerStateTable.currentIndex) ?: -1,
            loop = state?.get(PlayerStateTable.loopMode) ?: "off",
            volume = state?.get(PlayerStateTable.volume) ?: 0.5,
            positionSec = state?.get(PlayerStateTable.positionSec) ?: 0.0,
            playing = state?.get(PlayerStateTable.playing) ?: false,
        )
    }

    fun save(snapshot: PlayerSnapshot): Unit = transaction {
        QueueItems.deleteAll()
        snapshot.tracks.forEachIndexed { i, track ->
            QueueItems.insert {
                it[position] = i
                it[videoId] = track.id
                it[title] = track.title
                it[duration] = track.duration
                it[uploader] = track.uploader
                it[thumbnail] = track.thumbnail
                it[webpageUrl] = track.webpageUrl
            }
        }
        PlayerStateTable.deleteAll()
        PlayerStateTable.insert {
            it[id] = 1
            it[currentIndex] = snapshot.index
            it[loopMode] = snapshot.loop
            it[volume] = snapshot.volume
            it[positionSec] = snapshot.positionSec
            it[playing] = snapshot.playing
            it[updatedAt] = Instant.now()
        }
    }
}
