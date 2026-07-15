package de.yscord.player

import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.nio.file.Files
import java.nio.file.Path

/**
 * HTTP surface of the web player.
 *
 *  - GET /api/resolve?q=<link|search>  → track metadata (fast, no download)
 *  - GET /api/stream/{id}              → audio bytes, Range-enabled for seeking
 *
 * The queue itself lives client-side in the Angular SignalStore; the backend is a
 * stateless yt-dlp proxy. Range handling is done by hand (rather than leaning on
 * Spring's ResourceRegion converter) so a 206 Partial Content is fully explicit.
 */
@RestController
@RequestMapping("/api")
class PlayerController(
    private val ytDlp: YtDlpService,
    private val audioCache: AudioCacheService,
) {

    private companion object {
        const val CHUNK = 64 * 1024
    }

    @GetMapping("/resolve")
    fun resolve(@RequestParam("q") query: String): TrackInfo = ytDlp.resolve(query)

    @GetMapping("/stream/{id}")
    fun stream(
        @PathVariable id: String,
        @RequestHeader headers: HttpHeaders,
    ): ResponseEntity<StreamingResponseBody> {
        val file = audioCache.prepare(id)
        val length = Files.size(file)
        val mediaType = MediaTypeFactory.getMediaType(FileSystemResource(file))
            .orElse(MediaType.APPLICATION_OCTET_STREAM)

        val range = headers.range.firstOrNull()
        if (range == null) {
            // Whole file — advertise Range so the browser knows it may seek later.
            return ResponseEntity.ok()
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(mediaType)
                .contentLength(length)
                .body(bodyFor(file, 0, length - 1))
        }

        val start = range.getRangeStart(length)
        val end = range.getRangeEnd(length)
        val count = end - start + 1
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(HttpHeaders.CONTENT_RANGE, "bytes $start-$end/$length")
            .contentType(mediaType)
            .contentLength(count)
            .body(bodyFor(file, start, end))
    }

    /** Streams bytes [start]..[end] of [file] to the response without buffering it all. */
    private fun bodyFor(file: Path, start: Long, end: Long) = StreamingResponseBody { out ->
        Files.newInputStream(file).use { input ->
            input.skip(start)
            var remaining = end - start + 1
            val buffer = ByteArray(CHUNK)
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read == -1) break
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
    }
}
