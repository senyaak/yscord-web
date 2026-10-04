package de.yscord.player.session

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.yscord.player.YtDlpException
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * Fans the authoritative state out to every connected client. On connect a client
 * gets the current snapshot; its commands are applied by [PlayerSessionService],
 * which publishes a [StateChangedEvent] that this handler broadcasts to everyone.
 */
@Component
class PlayerWebSocketHandler(private val service: PlayerSessionService) : TextWebSocketHandler() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()
    private val sessions = ConcurrentHashMap.newKeySet<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)
        trySend(session, mapper.writeValueAsString(service.snapshot()))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val cmd = try {
            mapper.readValue<Command>(message.payload)
        } catch (e: Exception) {
            return
        }
        // Failures go to the requester only. Only our own exception types carry
        // messages written for users; anything else (DB, bugs) is logged and
        // answered with a fixed text so internals never leak to the client.
        val error = try {
            service.handle(cmd)
            null
        } catch (e: YtDlpException) {
            e.message
        } catch (e: IllegalArgumentException) {
            e.message
        } catch (e: Exception) {
            log.error("command {} failed", cmd.type, e)
            "Something went wrong."
        }
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

    private fun trySend(session: WebSocketSession, json: String) {
        try {
            if (session.isOpen) synchronized(session) { session.sendMessage(TextMessage(json)) }
        } catch (e: Exception) {
            log.debug("drop session on send failure: {}", e.message)
            sessions.remove(session)
        }
    }
}
