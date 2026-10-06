package de.yscord.player.db

import de.yscord.player.TestcontainersConfig
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.MigrationUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * The guard: after all migrations ran (at context startup), the database must look
 * exactly like PlayerTables. Fails when someone changed the tables and forgot the
 * migration, or the other way round.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class SchemaInSyncTest {
    @Test
    fun `migrations produce the schema PlayerTables describe`() {
        val diff = transaction { MigrationUtils.statementsRequiredForDatabaseMigration(*appTables) }
        assertEquals(
            emptyList<String>(), diff,
            "PlayerTables and migrations disagree; run ./gradlew generateMigration -Pname=...",
        )
    }
}
