package de.yscord.auth

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

/** Someone who logged in with Google. */
object AppUsers : Table("app_user") {
    val id = long("id").autoIncrement()
    // Google's stable account id ("sub" claim). Email can change, this can't.
    val googleSub = varchar("google_sub", 255).uniqueIndex()
    val email = varchar("email", 320)
    val name = text("name").nullable()
    val picture = text("picture").nullable()
    val createdAt = timestamp("created_at")
    val lastLoginAt = timestamp("last_login_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * One browser, identified by the long-lived visitor_id cookie set on its first
 * request. Logging in links it to a user (identity stitching): everything that
 * browser did anonymously now belongs to that user, without rewriting history.
 */
object Visitors : Table("visitor") {
    val id = uuid("id")
    val createdAt = timestamp("created_at")
    val lastSeenAt = timestamp("last_seen_at")
    val userId = long("user_id").references(AppUsers.id).nullable().index()

    override val primaryKey = PrimaryKey(id)
}
