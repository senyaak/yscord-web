package de.yscord.player.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection

/**
 * Production databases are never empty when a migration runs; the Spring tests'
 * database is. This test applies the migrations one by one on a fresh Postgres
 * and loads `db/seeds/<version>.sql` (test resources) right after the migration
 * that created those tables, so every later migration runs against data.
 *
 * A seed is written once, in the schema of its migration, and never updated.
 */
class MigrationsOnDataTest {
    @Test
    fun `every migration applies to a database that holds data`() {
        PostgreSQLContainer("postgres:17-alpine").use { pg ->
            pg.start()
            val db = Database.connect(pg.jdbcUrl, user = pg.username, password = pg.password)
            try {
                val all = SqlMigration.loadAll()
                all.forEachIndexed { i, m ->
                    Migrator(all.take(i + 1), db).migrate()
                    seedFor(m.version)?.let { sql ->
                        transaction(db) { (connection.connection as Connection).createStatement().use { it.execute(sql) } }
                    }
                }
            } finally {
                // Don't leave a dead database registered for the Spring tests in this JVM.
                TransactionManager.closeAndUnregister(db)
            }
        }
    }

    private fun seedFor(version: String): String? =
        ClassPathResource("db/seeds/$version.sql").takeIf { it.exists() }?.getContentAsString(Charsets.UTF_8)
}
