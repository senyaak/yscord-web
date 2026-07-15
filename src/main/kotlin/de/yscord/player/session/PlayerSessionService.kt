package de.yscord.player.session

import de.yscord.player.PlayerSnapshot
import de.yscord.player.PlayerStateStore
import de.yscord.player.TrackInfo
import de.yscord.player.YtDlpService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Service

/**
 * The single authoritative player shared by every client — the web equivalent of
 * the Discord bot owning one queue for a whole channel. Clients send [Command]s;
 * this service mutates state under a lock, recomputes the timing anchor, persists
 * the queue, and publishes a [StateChangedEvent] the WS layer broadcasts to all.
 *
 * Position is anchored: `positionSec` is the current track's position as of
 * `anchorEpochMs`. While playing, the live position is positionSec + elapsed since
 * the anchor — every client derives the same value, so they stay in sync.
 */
@Service
@DependsOn("migrationRunner")
class PlayerSessionService(
    private val ytDlp: YtDlpService,
    private val store: PlayerStateStore,
    private val events: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val lock = Any()

    private val tracks = mutableListOf<TrackInfo>()
    private var index = -1
    private var playing = false
    private var positionSec = 0.0
    private var anchorMs = now()
    private var loop = "off"

    @PostConstruct
    fun load() {
        val snap = store.load()
        synchronized(lock) {
            tracks.clear()
            tracks.addAll(snap.tracks)
            index = snap.index
            loop = snap.loop
            // Resume from where we crashed: position + playing come from the last
            // 5-second persist, and the anchor restarts from now.
            positionSec = snap.positionSec
            playing = snap.playing
            anchorMs = now()
        }
        log.info("session resumed: {} tracks, index {}, pos {}s, playing {}", tracks.size, index, positionSec, playing)
    }

    /** Current authoritative snapshot (anchor-based). */
    fun snapshot(): SessionState = synchronized(lock) { build() }

    /** Applies a client command; resolves tracks for `add`. Broadcasts on change. */
    fun handle(cmd: Command) {
        when (cmd.type) {
            "play" -> mutate { if (index >= 0 && !playing) { anchorMs = now(); playing = true } }
            "pause" -> mutate { if (playing) { positionSec = livePosition(); playing = false; anchorMs = now() } }
            "seek" -> mutate { positionSec = (cmd.position ?: 0.0).coerceAtLeast(0.0); anchorMs = now() }
            "stop" -> mutate { playing = false; positionSec = 0.0; anchorMs = now() }
            "next" -> mutate { advance() }
            "prev" -> mutate { if (index > 0) select(index - 1) else restart() }
            "playAt" -> mutate { cmd.index?.let { if (it in tracks.indices) select(it) } }
            "ended" -> mutate { onEnded() }
            "cycleLoop" -> mutate { loop = nextLoop(loop) }
            "shuffle" -> mutate { shuffle() }
            "remove" -> mutate { cmd.index?.let { removeAt(it) } }
            "move" -> mutate { if (cmd.index != null && cmd.delta != null) move(cmd.index, cmd.delta) }
            "add" -> add(cmd.query.orEmpty()) // resolve outside the lock (slow), then mutate
            else -> log.warn("unknown command: {}", cmd.type)
        }
    }

    /** Server-driven progression: when the current track runs out, advance for everyone. */
    fun tick() {
        val changed = synchronized(lock) {
            val track = current() ?: return
            if (playing && track.duration > 0 && livePosition() >= track.duration) {
                onEnded()
                true
            } else false
        }
        if (changed) publish()
    }

    // ---- internals (call under lock) ----

    private fun current(): TrackInfo? = tracks.getOrNull(index)

    private fun livePosition(): Double =
        if (playing) positionSec + (now() - anchorMs) / 1000.0 else positionSec

    private fun select(i: Int) {
        index = i; positionSec = 0.0; anchorMs = now(); playing = true
    }

    private fun restart() {
        positionSec = 0.0; anchorMs = now()
    }

    private fun advance() {
        when {
            index < tracks.size - 1 -> select(index + 1)
            loop == "queue" && tracks.isNotEmpty() -> select(0)
            else -> { playing = false; positionSec = 0.0; anchorMs = now() }
        }
    }

    private fun onEnded() {
        if (loop == "track") { positionSec = 0.0; anchorMs = now(); playing = true } else advance()
    }

    private fun removeAt(i: Int) {
        if (i !in tracks.indices) return
        tracks.removeAt(i)
        if (i < index) index--
        else if (i == index) { index = if (tracks.isEmpty()) -1 else index.coerceAtMost(tracks.size - 1); positionSec = 0.0; anchorMs = now() }
    }

    private fun move(i: Int, delta: Int) {
        val j = i + delta
        if (i !in tracks.indices || j !in tracks.indices) return
        tracks.add(j, tracks.removeAt(i))
        when (index) { i -> index = j; j -> index = i }
    }

    private fun shuffle() {
        val cur = current()
        for (k in tracks.indices.reversed()) {
            val r = (0..k).random()
            tracks[k] = tracks.set(r, tracks[k])
        }
        index = if (cur != null) tracks.indexOf(cur) else -1
    }

    private fun add(query: String) {
        if (query.isBlank()) return
        val track = ytDlp.resolve(query) // network; deliberately outside the lock
        mutate {
            val wasEmpty = tracks.isEmpty()
            tracks.add(track)
            if (wasEmpty) select(0)
        }
    }

    /** Runs [block] under the lock, persists the queue, and broadcasts. */
    private fun mutate(block: () -> Unit) {
        synchronized(lock) { block() }
        persist()
        publish()
    }

    private fun build(): SessionState {
        val t = now()
        return SessionState(tracks.toList(), index, playing, positionSec, anchorMs, t, loop)
    }

    private fun publish() = events.publishEvent(StateChangedEvent(snapshot()))

    /** Called every 5s by the scheduler to snapshot the moving position into the DB. */
    fun persistPeriodic() = persist()

    private fun persist() = synchronized(lock) {
        store.save(PlayerSnapshot(tracks.toList(), index, loop, DEFAULT_VOLUME, livePosition(), playing))
    }

    private fun nextLoop(mode: String) = when (mode) { "off" -> "queue"; "queue" -> "track"; else -> "off" }

    private companion object {
        const val DEFAULT_VOLUME = 0.5
        fun now() = System.currentTimeMillis()
    }
}
