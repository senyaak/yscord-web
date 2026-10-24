package de.yscord.player.session

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.yscord.player.PlayerMetrics
import de.yscord.player.PlayerMetrics.Outcome
import de.yscord.player.YtDlpException
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Fans the authoritative state out to every connected client. On connect a client
 * gets the current snapshot; its commands are applied by [PlayerSessionService],
 * which publishes a [StateChangedEvent] that this handler broadcasts to everyone.
 */
@Component
class PlayerWebSocketHandler(
    private val service: PlayerSessionService,
    private val metrics: PlayerMetrics,
) : TextWebSocketHandler() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()
    private val sessions = ConcurrentHashMap.newKeySet<WebSocketSession>()

    init {
        for (identity in listOf(PlayerMetrics.USER, PlayerMetrics.ANONYMOUS)) {
            metrics.listeners(identity) { sessions.count { IdentityHandshakeInterceptor.identity(it) == identity } }
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)
        trySend(session, mapper.writeValueAsString(service.snapshot()))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val cmd = try {
            mapper.readValue<Command>(message.payload)
        } catch (e: Exception) {
            metrics.command("", Outcome.REJECTED)
            return
        }
        if (cmd.type !in PlayerMetrics.COMMANDS) {
            metrics.command(cmd.type, Outcome.REJECTED)
            return
        }
        // Failures go to the requester only. Only our own exception types carry
        // messages written for users; anything else (DB, bugs) is logged and
        // answered with a fixed text so internals never leak to the client.
        var outcome = Outcome.REJECTED
        val error = try {
            service.handle(cmd)
            outcome = Outcome.OK
            null
        } catch (e: YtDlpException) {
            e.message
        } catch (e: IllegalArgumentException) {
            e.message
        } catch (e: Exception) {
            log.error("command {} failed", cmd.type, e)
            outcome = Outcome.ERROR
            "Something went wrong."
        }
        metrics.command(cmd.type, outcome)
        if (error != null) trySend(session, """{"error":${mapper.writeValueAsString(error)}}""")
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session)
    }

    @EventListener
    fun onStateChanged(event: StateChangedEvent) {
        val json = mapper.writeValueAsString(event.state)
        sessions.forEach { trySend(it, json) }
    }

    /**
     * Keepalive. State is only sent on change, so a paused player's connection
     * goes silent, and proxies on the way close idle WebSockets (Cloudflare
     * after 100s): clients reconnected every ~2 minutes. A ping frame every 30s
     * is traffic for every hop; browsers answer with a pong by themselves. A
     * ping that fails also drops a dead client from the list.
     */
    @Scheduled(fixedRate = 30_000)
    fun keepAlive() {
        sessions.forEach { trySend(it, PingMessage(ByteBuffer.allocate(0))) }
    }

    private fun trySend(session: WebSocketSession, json: String) = trySend(session, TextMessage(json))

    private fun trySend(session: WebSocketSession, message: WebSocketMessage<*>) {
        try {
            if (session.isOpen) synchronized(session) { session.sendMessage(message) }
        } catch (e: Exception) {
            log.debug("drop session on send failure: {}", e.message)
            sessions.remove(session)
        }
    }
}
