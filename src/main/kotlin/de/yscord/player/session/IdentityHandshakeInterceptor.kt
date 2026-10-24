package de.yscord.player.session

import de.yscord.auth.VisitorCookieFilter
import de.yscord.player.PlayerMetrics
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.server.HandshakeInterceptor
import java.util.UUID

/**
 * Who opened the socket, read once from the HTTP handshake (the only request of
 * the connection that carries cookies): the visitor id set by
 * [VisitorCookieFilter], and whether the browser is logged in. Kept in the
 * session's attributes for the connection's lifetime; logging in or out shows
 * up on the next connect (the SPA reconnects after a login redirect anyway).
 */
class IdentityHandshakeInterceptor : HandshakeInterceptor {

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val servlet = (request as? ServletServerHttpRequest)?.servletRequest
        servlet?.let { VisitorCookieFilter.visitorId(it) }?.let { attributes[VISITOR] = it }
        // Spring Security puts the login on the request; anonymous visitors have none.
        attributes[IDENTITY] = if (request.principal != null) PlayerMetrics.USER else PlayerMetrics.ANONYMOUS
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) = Unit

    companion object {
        private const val VISITOR = "yscord.visitorId"
        private const val IDENTITY = "yscord.identity"

        fun visitorId(session: WebSocketSession): UUID? = session.attributes[VISITOR] as UUID?
        fun identity(session: WebSocketSession): String =
            session.attributes[IDENTITY] as String? ?: PlayerMetrics.ANONYMOUS
    }
}
