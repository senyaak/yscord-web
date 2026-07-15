package de.yscord.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Boots the full context — which runs the Exposed [de.yscord.player.db.MigrationRunner]
 * against H2, creating the schema — then round-trips a snapshot through the store.
 */
@SpringBootTest
class PlayerStateStoreTest(@Autowired val store: PlayerStateStore) {

    private fun track(id: String, title: String) =
        TrackInfo(id, title, 200, "Uploader", null, "https://youtu.be/$id")

    @Test
    fun `saves and reloads a snapshot including position and playing`() {
        val snapshot = PlayerSnapshot(
            tracks = listOf(track("dQw4w9WgXcQ", "First"), track("aaaaaaaaaaa", "Second")),
            index = 1,
            loop = "queue",
            volume = 0.7,
            positionSec = 42.5,
            playing = true,
        )
        store.save(snapshot)

        val loaded = store.load()
        assertEquals(2, loaded.tracks.size)
        assertEquals("First", loaded.tracks[0].title)
        assertEquals("Second", loaded.tracks[1].title)
        assertEquals(1, loaded.index)
        assertEquals("queue", loaded.loop)
        assertEquals(0.7, loaded.volume)
        // crash-recovery fields
        assertEquals(42.5, loaded.positionSec)
        assertEquals(true, loaded.playing)
    }

    @Test
    fun `saving again replaces the previous snapshot`() {
        store.save(PlayerSnapshot(listOf(track("bbbbbbbbbbb", "Old")), 0, "off", 0.5))
        store.save(PlayerSnapshot(listOf(track("ccccccccccc", "New")), 0, "track", 0.3))

        val loaded = store.load()
        assertEquals(1, loaded.tracks.size)
        assertEquals("New", loaded.tracks[0].title)
        assertEquals("track", loaded.loop)
        assertEquals(0.3, loaded.volume)
    }
}
