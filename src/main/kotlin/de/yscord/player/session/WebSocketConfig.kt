package de.yscord.player.session

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/** Exposes the player socket at /ws/player. Origins are open so `ng serve` can connect in dev. */
@Configuration
@EnableWebSocket
class WebSocketConfig(private val handler: PlayerWebSocketHandler) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/ws/player").setAllowedOriginPatterns("*")
    }
}
