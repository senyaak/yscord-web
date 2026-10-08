package de.yscord.auth

import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class UserService {
    /** Creates or refreshes the user behind a Google login; returns its id. */
    fun upsert(google: OidcUser): Long = transaction {
        val now = Instant.now()
        // Both come with the openid + email scopes; Spring types them as nullable.
        val sub = requireNotNull(google.subject) { "Google login without a sub claim" }
        val mail = requireNotNull(google.email) { "Google login without an email" }
        AppUsers.upsert(AppUsers.googleSub, onUpdateExclude = listOf(AppUsers.createdAt)) {
            it[googleSub] = sub
            it[email] = mail
            it[name] = google.fullName
            it[picture] = google.picture
            it[createdAt] = now
            it[lastLoginAt] = now
        }
        AppUsers.selectAll().where { AppUsers.googleSub eq sub }.single()[AppUsers.id]
    }
}
