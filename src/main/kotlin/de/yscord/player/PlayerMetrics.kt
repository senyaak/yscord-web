package de.yscord.player

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * The player's own metrics, served on /actuator/prometheus.
 *
 * Every label value comes from a small fixed vocabulary. Each distinct label
 * combination is a separate series held in Prometheus' memory, so ids (visitor,
 * track) and anything a client sends are never used as label values: a client
 * sending random command types would otherwise create series without end.
 * Per-entity questions (who listened to what) belong to events, not metrics.
 */
@Component
class PlayerMetrics(private val registry: MeterRegistry) {

    /** Connected WebSocket clients, by [identity]; [count] is read on every scrape. */
    fun listeners(identity: String, count: () -> Int) {
        require(identity in IDENTITIES) { "unknown identity $identity" }
        Gauge.builder("yscord.listeners") { count().toDouble() }
            .description("Connected player clients")
            .tag("identity", identity)
            .register(registry)
    }

    /** One player command. [type] is client input: anything unknown becomes "unknown". */
    fun command(type: String, outcome: Outcome) {
        Counter.builder("yscord.commands")
            .description("Player commands received over the WebSocket")
            .tag("type", if (type in COMMANDS) type else "unknown")
            .tag("outcome", outcome.label)
            .register(registry)
            .increment()
    }

    /** Times one yt-dlp call; failed if [block] throws. */
    fun <T> ytDlp(operation: YtDlpOperation, block: () -> T): T {
        val sample = Timer.start(registry)
        var outcome = Outcome.ERROR
        try {
            return block().also { outcome = Outcome.OK }
        } finally {
            sample.stop(
                Timer.builder("yscord.ytdlp")
                    .description("yt-dlp calls: resolving a query, downloading audio")
                    .tag("operation", operation.label)
                    .tag("outcome", outcome.label)
                    // Explicit buckets: a handful of series instead of Micrometer's
                    // ~70 default ones, chosen around what a user would notice.
                    .serviceLevelObjectives(*YTDLP_BUCKETS)
                    .register(registry),
            )
        }
    }

    /** An audio request: served from the cache file or downloaded first. */
    fun audioCache(hit: Boolean) {
        Counter.builder("yscord.audio.cache")
            .description("Audio requests by cache result")
            .tag("result", if (hit) "hit" else "miss")
            .register(registry)
            .increment()
    }

    enum class Outcome(val label: String) { OK("ok"), REJECTED("rejected"), ERROR("error") }

    enum class YtDlpOperation(val label: String) { RESOLVE("resolve"), DOWNLOAD("download") }

    companion object {
        const val USER = "user"
        const val ANONYMOUS = "anonymous"
        private val IDENTITIES = setOf(USER, ANONYMOUS)

        /** Must list every type PlayerSessionService.handle understands. */
        val COMMANDS = setOf(
            "play", "pause", "seek", "stop", "next", "prev", "playAt",
            "ended", "cycleLoop", "shuffle", "remove", "move", "add",
        )

        private val YTDLP_BUCKETS = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0)
            .map { Duration.ofMillis((it * 1000).toLong()) }
            .toTypedArray()
    }
}
