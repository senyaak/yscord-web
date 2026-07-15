package de.yscord.player

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/** Metadata for one resolved track — the HTTP-facing shape the frontend consumes. */
data class TrackInfo(
    val id: String,
    val title: String,
    val duration: Int,
    val uploader: String,
    val thumbnail: String?,
    val webpageUrl: String,
)

/** Thrown when yt-dlp fails; mapped to a ProblemDetail by [PlayerExceptionHandler]. */
class YtDlpException(message: String) : RuntimeException(message)

/**
 * Thin wrapper around the yt-dlp binary. This is the Kotlin port of the bot's
 * `src/ytdlp.js`: we shell out to the same tool, but instead of piping audio into
 * Discord we expose metadata over HTTP and let [AudioCacheService] handle bytes.
 */
@Service
class YtDlpService(
    @Value("\${player.yt-dlp-path:yt-dlp}") private val ytDlpPath: String,
) {

    // Only used to parse yt-dlp's JSON output — a plain mapper is enough and keeps
    // the service free of a bean that Boot 4.1 no longer exposes by default.
    private val objectMapper = ObjectMapper()
    private val urlRegex = Regex("^https?://", RegexOption.IGNORE_CASE)

    /**
     * Resolves a link OR free-text search into a single track's metadata.
     * `yt-dlp -j` prints one JSON object per line; on a search we take the first.
     */
    fun resolve(query: String): TrackInfo {
        val trimmed = query.trim()
        val target = if (urlRegex.containsMatchIn(trimmed)) trimmed else "ytsearch1:$trimmed"

        val result = run(
            "-j",
            "--no-playlist",
            "--no-warnings",
            "--default-search", "ytsearch",
            target,
        )
        val firstLine = result.stdout.lineSequence().firstOrNull { it.isNotBlank() }
            ?: throw YtDlpException(friendlyError(result.stderr))

        val info: JsonNode = try {
            objectMapper.readTree(firstLine)
        } catch (e: Exception) {
            throw YtDlpException("Cannot parse yt-dlp output: ${e.message}")
        }

        val id = info.get("id")?.asText()
            ?: throw YtDlpException("yt-dlp returned no video id")
        return TrackInfo(
            id = id,
            title = info.get("title")?.asText() ?: "Untitled",
            duration = info.get("duration")?.asInt() ?: 0,
            uploader = info.get("uploader")?.asText() ?: info.get("channel")?.asText() ?: "",
            thumbnail = info.get("thumbnail")?.asText(),
            webpageUrl = info.get("webpage_url")?.asText() ?: "https://www.youtube.com/watch?v=$id",
        )
    }

    /** Runs yt-dlp with [args], capturing stdout/stderr as text. */
    fun run(vararg args: String): ProcessResult {
        val proc = try {
            ProcessBuilder(listOf(ytDlpPath) + args).start()
        } catch (e: Exception) {
            throw YtDlpException("Cannot start yt-dlp: ${e.message}")
        }
        val stdout = proc.inputStream.bufferedReader().readText()
        val stderr = proc.errorStream.bufferedReader().readText()
        if (!proc.waitFor(60, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            throw YtDlpException("yt-dlp timed out")
        }
        return ProcessResult(proc.exitValue(), stdout, stderr)
    }

    /** Path to the yt-dlp binary, for services that spawn it directly. */
    fun binary(): String = ytDlpPath

    data class ProcessResult(val exitCode: Int, val stdout: String, val stderr: String)

    /**
     * Turns raw yt-dlp stderr into a short, actionable message. yt-dlp breaks every
     * time YouTube shifts its API, so hinting at an update saves debugging time.
     */
    fun friendlyError(raw: String): String {
        val s = raw.lowercase()
        return when {
            Regex("sign in to confirm|not a bot").containsMatchIn(s) ->
                "YouTube wants bot confirmation — yt-dlp is likely stale. Update it (yt-dlp -U)."
            "private video" in s -> "This is a private video."
            Regex("video unavailable|has been removed|not available").containsMatchIn(s) ->
                "Video is unavailable or removed."
            "unsupported url" in s -> "Unrecognized link."
            Regex("unable to (extract|download)|http error 4|http error 5|nsig|player.*signature").containsMatchIn(s) ->
                "YouTube won't serve this — yt-dlp is probably stale. Update it (yt-dlp -U)."
            else -> raw.trim().ifBlank { "unknown yt-dlp error" }
        }
    }
}
