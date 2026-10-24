package de.yscord.auth

import de.yscord.player.TestcontainersConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.context.annotation.Import
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.TimeUnit

/** The login plumbing as a browser sees it, against a real server. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfig::class)
class AuthHttpTest(
    @Value("\${local.server.port}") private val port: Int,
    @Value("\${local.management.port}") private val managementPort: Int,
) {
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    private fun send(method: String, path: String, cookies: Map<String, String> = emptyMap(), vararg headers: String) =
        sendTo(port, method, path, cookies, *headers)

    private fun sendTo(port: Int, method: String, path: String, cookies: Map<String, String> = emptyMap(), vararg headers: String) =
        http.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .apply { if (cookies.isNotEmpty()) header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }) }
                .apply { if (headers.isNotEmpty()) headers(*headers) }
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun HttpResponse<*>.setCookies(): Map<String, String> =
        headers().allValues("Set-Cookie").associate { it.substringBefore('=') to it }

    private fun Map<String, String>.value(name: String) = getValue(name).substringAfter('=').substringBefore(';')

    @Test
    fun `first visit gets a visitor cookie and an XSRF token`() {
        val response = send("GET", "/api/me")
        assertEquals(200, response.statusCode())
        val cookies = response.setCookies()
        val visitor = checkNotNull(cookies["visitor_id"]) { "no visitor_id cookie: $cookies" }
        assertTrue("HttpOnly" in visitor, visitor)
        assertNotNull(cookies["XSRF-TOKEN"], "no XSRF-TOKEN cookie: $cookies")
        assertTrue(cookies.value("visitor_id") in response.body(), response.body())
    }

    @Test
    fun `logout is refused without the XSRF token and accepted with it`() {
        val cookies = send("GET", "/api/me").setCookies()
        val jar = mapOf("visitor_id" to cookies.value("visitor_id"), "XSRF-TOKEN" to cookies.value("XSRF-TOKEN"))
        assertEquals(403, send("POST", "/logout", jar).statusCode())
        assertEquals(204, send("POST", "/logout", jar, "X-XSRF-TOKEN", jar.getValue("XSRF-TOKEN")).statusCode())
    }

    @Test
    fun `login sends the browser to Google`() {
        val response = send("GET", "/oauth2/authorization/google")
        assertEquals(302, response.statusCode())
        val location = response.headers().firstValue("Location").orElseThrow()
        assertTrue(location.startsWith("https://accounts.google.com/"), location)
        // From app.public-url (test config), not from how the request reached us.
        assertTrue("redirect_uri=http://localhost/login/oauth2/code/google" in location, location)
    }

    @Test
    fun `probes and metrics live on the management port and create no visitor`() {
        val health = sendTo(managementPort, "GET", "/actuator/health/readiness")
        assertEquals(200, health.statusCode())
        assertTrue("visitor_id" !in health.setCookies(), health.headers().toString())
        val metrics = sendTo(managementPort, "GET", "/actuator/prometheus")
        assertEquals(200, metrics.statusCode())
        assertTrue("jvm_memory_used_bytes" in metrics.body())
        // Never on the public port: the route to the internet only leads there.
        // Even there /actuator creates no visitor (in case the ports are ever
        // merged again: the management server doesn't run our filters).
        val public = send("GET", "/actuator/prometheus")
        assertEquals(404, public.statusCode())
        assertTrue("visitor_id" !in public.setCookies(), public.headers().toString())
    }

    @Test
    fun `an open player socket counts as an anonymous listener`() {
        fun anonymousListeners() = sendTo(managementPort, "GET", "/actuator/prometheus").body()
            .lineSequence().first { it.startsWith("""yscord_listeners{identity="anonymous"}""") }
            .substringAfterLast(' ').toDouble()

        val before = anonymousListeners()
        val socket = http.newWebSocketBuilder()
            .buildAsync(URI("ws://localhost:$port/ws/player"), object : WebSocket.Listener {})
            .get(5, TimeUnit.SECONDS)
        try {
            assertEquals(before + 1, anonymousListeners())
        } finally {
            socket.abort()
        }
    }
}
