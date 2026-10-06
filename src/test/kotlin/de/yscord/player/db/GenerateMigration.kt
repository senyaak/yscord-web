package de.yscord.player.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.MigrationUtils
import org.testcontainers.postgresql.PostgreSQLContainer
import java.io.File
import kotlin.system.exitProcess

/**
 * `./gradlew generateMigration --name add_something`: starts a throwaway Postgres,
 * applies every existing migration, diffs the result against [appTables] and
 * writes the difference as the next `NNN_name.sql`. A draft — review it.
 */
fun main(args: Array<String>) {
    val name = args.firstOrNull().orEmpty()
    if (!name.matches(Regex("[a-z0-9_]+"))) {
        System.err.println("usage: ./gradlew generateMigration --name add_something (lowercase, digits, _)")
        exitProcess(2)
    }
    val dir = File("src/main/resources/${SqlMigration.LOCATION}")

    PostgreSQLContainer("postgres:17-alpine").use { pg ->
        pg.start()
        Database.connect(pg.jdbcUrl, user = pg.username, password = pg.password)
        val existing = SqlMigration.loadAll()
        Migrator(existing).migrate()

        val diff = transaction { MigrationUtils.statementsRequiredForDatabaseMigration(*appTables) }
        if (diff.isEmpty()) {
            println("PlayerTables already match the migrations, nothing to generate.")
            return
        }
        val next = (existing.maxOfOrNull { it.version.substringBefore('_').toInt() } ?: 0) + 1
        val file = File(dir, "%03d_%s.sql".format(next, name))
        file.writeText(
            "-- Generated from PlayerTables. Review before committing: a rename shows up\n" +
                "-- as DROP + ADD (data loss), type changes may need USING, data moves are manual.\n" +
                diff.joinToString("") { "$it;\n" },
        )
        println("Wrote ${file.path}:\n${file.readText()}")
    }
}
