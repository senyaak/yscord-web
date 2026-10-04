package de.yscord.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class YtDlpServiceTest {

    private val service = YtDlpService("yt-dlp")

    @Test
    fun `free text becomes a YouTube search`() {
        assertEquals("ytsearch1:lofi beats", service.toTarget("  lofi beats "))
    }

    @Test
    fun `YouTube links pass through`() {
        listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            "HTTPS://WWW.YOUTUBE.COM/watch?v=dQw4w9WgXcQ",
        ).forEach { assertEquals(it, service.toTarget(it)) }
    }

    @Test
    fun `non-YouTube links are rejected`() {
        listOf(
            "http://192.168.178.1/",
            "http://postgres:5432/",
            "http://localhost:8080/actuator",
            "https://youtube.com.evil.example/watch?v=x",
            "https://evil.example/?u=https://youtube.com",
            "file:///etc/passwd",
            "ftp://youtube.com/x",
        ).forEach { url ->
            val ex = assertThrows<IllegalArgumentException>(url) { service.toTarget(url) }
            assertEquals(YtDlpService.ONLY_YOUTUBE, ex.message)
        }
    }

    @Test
    fun `unmapped yt-dlp errors never echo stderr`() {
        val raw = "ERROR: [generic] Unable to connect: 192.168.178.50:8080 Connection refused"
        assertEquals(YtDlpService.GENERIC_ERROR, service.friendlyError(raw))
    }
}
