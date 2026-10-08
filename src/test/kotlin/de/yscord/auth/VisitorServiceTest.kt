package de.yscord.auth

import de.yscord.player.TestcontainersConfig
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfig::class)
class VisitorServiceTest(@Autowired val visitors: VisitorService) {

    private fun user(sub: String): Long = transaction {
        AppUsers.insert {
            it[googleSub] = sub
            it[email] = "$sub@example.com"
            it[createdAt] = Instant.now()
            it[lastLoginAt] = Instant.now()
        }[AppUsers.id]
    }

    private fun ownerOf(id: UUID): Long? = transaction {
        Visitors.selectAll().where { Visitors.id eq id }.single()[Visitors.userId]
    }

    @Test
    fun `a browser without cookie gets a new visitor, with cookie keeps it`() {
        val first = visitors.touch(null)
        assertEquals(first, visitors.touch(first))
    }

    @Test
    fun `a cookie whose visitor is gone gets a fresh one`() {
        val unknown = UUID.randomUUID()
        assertNotEquals(unknown, visitors.touch(unknown))
    }

    @Test
    fun `login stitches the anonymous visitor to the user`() {
        val visitor = visitors.touch(null)
        val alice = user("alice-${UUID.randomUUID()}")
        assertEquals(visitor, visitors.linkToUser(visitor, alice))
        assertEquals(alice, ownerOf(visitor))
        // Logging in again from the same browser changes nothing.
        assertEquals(visitor, visitors.linkToUser(visitor, alice))
    }

    @Test
    fun `a browser linked to someone else gets a new visitor for the new user`() {
        val visitor = visitors.touch(null)
        val alice = user("alice-${UUID.randomUUID()}")
        val bob = user("bob-${UUID.randomUUID()}")
        visitors.linkToUser(visitor, alice)
        val bobs = visitors.linkToUser(visitor, bob)
        assertNotEquals(visitor, bobs)
        assertEquals(alice, ownerOf(visitor))
        assertEquals(bob, ownerOf(bobs))
    }
}
