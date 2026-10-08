package de.yscord.auth

import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Browsers we've seen (visitor table) and their link to a logged-in user. */
@Service
class VisitorService {
    // Every request passes through here (audio range requests included): remember
    // when each visitor was last written and touch the row at most once an hour.
    private val lastTouched = ConcurrentHashMap<UUID, Instant>()

    /**
     * Returns the visitor id to use for this request: [cookieId] if that visitor
     * exists, otherwise a freshly created one (no cookie yet, or a cookie whose
     * row is gone, e.g. after a database restore).
     */
    fun touch(cookieId: UUID?): UUID {
        val now = Instant.now()
        if (cookieId != null && lastTouched[cookieId]?.isAfter(now - TOUCH_EVERY) == true) return cookieId
        return transaction {
            val known = cookieId != null &&
                Visitors.update({ Visitors.id eq cookieId }) { it[lastSeenAt] = now } > 0
            val id = if (known) cookieId else create(now)
            lastTouched[id] = now
            id
        }
    }

    /**
     * Identity stitching on login: the browser's visitor now belongs to [userId].
     * A browser already linked to someone else (shared computer) gets a new
     * visitor instead, so one person's history never moves to another. Returns
     * the visitor id the browser should keep.
     */
    fun linkToUser(visitorId: UUID?, userId: Long): UUID = transaction {
        val owner = visitorId?.let { id ->
            Visitors.selectAll().where { Visitors.id eq id }.firstOrNull()?.let { it[Visitors.userId] ?: NOBODY }
        }
        when (owner) {
            NOBODY -> {
                Visitors.update({ Visitors.id eq visitorId!! }) { it[Visitors.userId] = userId }
                visitorId!!
            }
            userId -> visitorId!!
            else -> create(Instant.now(), userId)
        }
    }

    private fun create(now: Instant, userId: Long? = null): UUID {
        val id = UUID.randomUUID()
        Visitors.insert {
            it[Visitors.id] = id
            it[createdAt] = now
            it[lastSeenAt] = now
            it[Visitors.userId] = userId
        }
        return id
    }

    private companion object {
        val TOUCH_EVERY: Duration = Duration.ofHours(1)
        /** Marks "visitor exists, not linked" apart from "no such visitor" (null). */
        const val NOBODY = -1L
    }
}
