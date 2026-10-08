package de.yscord.auth

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Who is this browser: always a visitor id, plus the user once logged in. */
@RestController
class MeController {
    data class Me(val visitorId: UUID?, val user: User?)
    data class User(val name: String?, val email: String?, val picture: String?)

    @GetMapping("/api/me")
    fun me(request: HttpServletRequest, @AuthenticationPrincipal google: OidcUser?) = Me(
        visitorId = VisitorCookieFilter.visitorId(request),
        user = google?.let { User(it.fullName, it.email, it.picture) },
    )
}
