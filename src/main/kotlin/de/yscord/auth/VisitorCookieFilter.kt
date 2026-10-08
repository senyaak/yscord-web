package de.yscord.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.UUID

/**
 * Gives every browser a long-lived visitor_id cookie on its first request and
 * exposes the id to the rest of the request as an attribute.
 *
 * Runs before Spring Security, so the id is there when a login completes
 * (the OAuth callback is answered by Security and never reaches later filters).
 */
@Component
// Spring Security's filter chain is registered at order -100; run just before it.
@Order(-110)
class VisitorCookieFilter(private val visitors: VisitorService) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val fromCookie = request.cookies?.firstOrNull { it.name == COOKIE }?.value
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val id = visitors.touch(fromCookie)
        if (id != fromCookie) setCookie(request, response, id)
        request.setAttribute(ATTRIBUTE, id)
        chain.doFilter(request, response)
    }

    companion object {
        const val COOKIE = "visitor_id"
        const val ATTRIBUTE = "yscord.visitorId"

        fun setCookie(request: HttpServletRequest, response: HttpServletResponse, id: UUID) {
            val cookie = ResponseCookie.from(COOKIE, id.toString())
                .httpOnly(true)
                .secure(request.isSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(365))
                .build()
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
        }

        fun visitorId(request: HttpServletRequest): UUID? = request.getAttribute(ATTRIBUTE) as UUID?
    }
}
