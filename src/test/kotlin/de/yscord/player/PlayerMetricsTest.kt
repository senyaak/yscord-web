package de.yscord.player

import de.yscord.player.PlayerMetrics.Outcome
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PlayerMetricsTest {
    private val registry = SimpleMeterRegistry()
    private val metrics = PlayerMetrics(registry)

    @Test
    fun `command types a client invents all land in one series`() {
        repeat(100) { metrics.command("evil-$it", Outcome.REJECTED) }
        metrics.command("play", Outcome.OK)

        val types = registry.find("yscord.commands").counters().map { it.id.getTag("type") }.toSet()
        assertEquals(setOf("unknown", "play"), types)
        assertEquals(100.0, registry.get("yscord.commands").tag("type", "unknown").counter().count())
    }

    @Test
    fun `a failing yt-dlp call is timed as an error and still throws`() {
        assertThrows<YtDlpException> {
            metrics.ytDlp(PlayerMetrics.YtDlpOperation.RESOLVE) { throw YtDlpException("boom") }
        }
        val timer = registry.get("yscord.ytdlp").tag("operation", "resolve").tag("outcome", "error").timer()
        assertEquals(1, timer.count())
    }
}
