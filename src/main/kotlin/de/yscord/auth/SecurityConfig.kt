package de.yscord.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler

/**
 * Login is optional: the player stays public, logging in with Google only adds
 * an identity. Spring does the whole OAuth flow (BFF); the browser gets a
 * session cookie, never tokens.
 *
 * 1. GET /oauth2/authorization/google starts the login, Google redirects back
 *    to /login/oauth2/code/google, the session is stored in Postgres.
 * 2. POST /logout ends it (204, the SPA stays where it is).
 * 3. CSRF for the SPA: the token travels in the XSRF-TOKEN cookie, Angular sends
 *    it back as the X-XSRF-TOKEN header on POSTs.
 */
@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity, onLogin: LinkVisitorOnLogin): SecurityFilterChain = http
        .authorizeHttpRequests { it.anyRequest().permitAll() }
        .oauth2Login { it.successHandler(onLogin) }
        .logout { it.logoutSuccessHandler(HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)) }
        .csrf { it.spa() }
        .build()
}

/** After a Google login: store the user, stitch the browser's visitor to it, go home. */
@org.springframework.stereotype.Component
class LinkVisitorOnLogin(
    private val users: UserService,
    private val visitors: VisitorService,
    @org.springframework.beans.factory.annotation.Value("\${app.secure-cookies}") private val secure: Boolean,
) : SimpleUrlAuthenticationSuccessHandler("/") {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val userId = users.upsert(authentication.principal as OidcUser)
        val current = VisitorCookieFilter.visitorId(request)
        val kept = visitors.linkToUser(current, userId)
        if (kept != current) response.addHeader("Set-Cookie", VisitorCookieFilter.cookie(kept, secure))
        super.onAuthenticationSuccess(request, response, authentication)
    }
}
