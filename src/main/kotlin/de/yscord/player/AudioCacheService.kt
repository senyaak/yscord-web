package de.yscord.player

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

/**
 * Downloads a track's best audio to a local cache file, once per video id, and
 * hands back its [Path]. Serving a real file (rather than a live yt-dlp pipe) is
 * the deliberate choice: a file supports HTTP Range, so the panel's progress bar
 * can seek, and a second play is instant from cache.
 */
@Service
class AudioCacheService(
    private val ytDlp: YtDlpService,
    @Value("\${player.cache-dir:#{systemProperties['java.io.tmpdir']}/yscord-audio}")
    private val cacheDirValue: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // YouTube ids are 11 chars of [A-Za-z0-9_-]. Validating guards against both
    // path traversal and shell/arg injection into the yt-dlp call.
    private val idRegex = Regex("^[A-Za-z0-9_-]{11}$")

    private val locks = ConcurrentHashMap<String, Any>()
    private lateinit var cacheDir: Path

    @PostConstruct
    fun init() {
        cacheDir = Path.of(cacheDirValue)
        Files.createDirectories(cacheDir)
        log.info("audio cache dir: {}", cacheDir.toAbsolutePath())
    }

    /**
     * Ensures the audio for [id] is on disk and returns its file. Blocks on first
     * request while yt-dlp downloads; subsequent calls return the cached file.
     */
    fun prepare(id: String): Path {
        require(idRegex.matches(id)) { "invalid video id" }

        findCached(id)?.let { return it }
        // Serialize downloads of the same id so two players don't race on one file.
        synchronized(locks.computeIfAbsent(id) { Any() }) {
            findCached(id)?.let { return it }
            download(id)
            return findCached(id)
                ?: throw YtDlpException("yt-dlp produced no audio file for $id")
        }
    }

    /** Existing cache file for [id] (any extension yt-dlp chose), or null. */
    private fun findCached(id: String): Path? {
        if (!cacheDir.exists()) return null
        return cacheDir.listDirectoryEntries("$id.*").firstOrNull { Files.size(it) > 0 }
    }

    private fun download(id: String) {
        val url = "https://www.youtube.com/watch?v=$id"
        val output = cacheDir.resolve("$id.%(ext)s").toString()
        val proc = ProcessBuilder(
            ytDlp.binary(),
            "-f", "bestaudio[ext=webm]/bestaudio/best",
            "--no-playlist",
            "--no-warnings",
            "--socket-timeout", "20",
            "-o", output,
            url,
        ).redirectErrorStream(false).start()

        val stderr = proc.errorStream.bufferedReader().readText()
        if (!proc.waitFor(120, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            throw YtDlpException("yt-dlp download timed out for $id")
        }
        if (proc.exitValue() != 0) {
            throw YtDlpException(ytDlp.friendlyError(stderr))
        }
    }
}
