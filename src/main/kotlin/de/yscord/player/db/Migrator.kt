package de.yscord.player.db

import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.security.MessageDigest
import java.sql.Connection
import java.time.Instant

/** One SQL migration from `db/migrations`: `NNN_name.sql` plus an optional `.down.sql`. */
class SqlMigration(val version: String, val up: String, val down: String?) {
    val checksum: String = MessageDigest.getInstance("SHA-256")
        .digest(up.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val LOCATION = "db/migrations"

        /** Every migration on the classpath, in version order. */
        fun loadAll(): List<SqlMigration> {
            val files = PathMatchingResourcePatternResolver()
                .getResources("classpath:$LOCATION/*.sql")
                .associateBy { it.filename!! }
            return files.keys
                .filterNot { it.endsWith(".down.sql") }
                .sorted()
                .map { name ->
                    val version = name.removeSuffix(".sql")
                    SqlMigration(
                        version = version,
                        up = files.getValue(name).getContentAsString(Charsets.UTF_8),
                        down = files["$version.down.sql"]?.getContentAsString(Charsets.UTF_8),
                    )
                }
        }
    }
}

/**
 * Applies or reverts [migrations] on [db] (Exposed's default database when null).
 * Plain class, no Spring: [MigrationRunner] uses it at startup, the migration
 * generator and tests directly.
 *
 * Each run is one transaction holding a Postgres advisory lock, so two processes
 * starting at once (two replicas, a Job and a pod) can't both apply the same
 * migration: the second waits, then finds nothing pending. Postgres DDL is
 * transactional, so a failing migration leaves the schema untouched.
 */
class Migrator(private val migrations: List<SqlMigration>, private val db: Database? = null) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Applies pending migrations; returns how many ran. */
    fun migrate(): Int = locked {
        val applied = SchemaMigrations.selectAll()
            .associate { it[SchemaMigrations.version] to it[SchemaMigrations.checksum] }
        val pending = migrations.filter { m ->
            if (m.version in applied) verify(m, applied[m.version])
            m.version !in applied
        }
        pending.forEach { m ->
            execScript(m.up)
            SchemaMigrations.insert {
                it[version] = m.version
                it[appliedAt] = Instant.now()
                it[checksum] = m.checksum
            }
            log.info("migrate up  → {}", m.version)
        }
        if (pending.isEmpty()) log.info("DB schema up to date ({} applied)", applied.size)
        pending.size
    }

    /** Versions not applied yet. Read-only: no lock, creates nothing. */
    fun pending(): List<String> = transaction(db) {
        val applied = if (SchemaMigrations.exists()) {
            SchemaMigrations.selectAll().map { it[SchemaMigrations.version] }.toSet()
        } else {
            emptySet()
        }
        migrations.map { it.version }.filterNot { it in applied }
    }

    /** Reverts the last [steps] applied migrations. Local development only. */
    fun rollback(steps: Int) = locked {
        val done = SchemaMigrations.selectAll().map { it[SchemaMigrations.version] }.toSet()
        val toUndo = migrations.filter { it.version in done }.takeLast(steps).reversed()
        if (toUndo.isEmpty()) log.info("nothing to roll back")
        toUndo.forEach { m ->
            val down = m.down ?: error("${m.version} has no down file; can't roll back past it")
            execScript(down)
            SchemaMigrations.deleteWhere { SchemaMigrations.version eq m.version }
            log.info("rollback    ← {}", m.version)
        }
    }

    private fun <T> locked(block: JdbcTransaction.() -> T): T = transaction(db) {
        // Exposed retries a failed transaction 3 times by default. A broken
        // migration fails the same way every time, and the Job and Argo CD retry
        // on their own: retries in every layer multiply.
        maxAttempts = 1
        // Released automatically when the transaction ends.
        exec("SELECT pg_advisory_xact_lock($LOCK_KEY)")
        SchemaUtils.create(SchemaMigrations)
        // The history table's own schema change: checksums came later.
        exec("ALTER TABLE schema_migrations ADD COLUMN IF NOT EXISTS checksum VARCHAR(64)")
        block()
    }

    /** Warns only: this is a playground, a changed migration shouldn't stop the app. */
    private fun verify(m: SqlMigration, recorded: String?) {
        when (recorded) {
            null -> {
                SchemaMigrations.update({ SchemaMigrations.version eq m.version }) { it[checksum] = m.checksum }
                log.info("recorded checksum for {} (applied before checksums existed)", m.version)
            }
            m.checksum -> Unit
            else -> log.warn(
                "migration {} changed after it was applied (checksum {} → {}). " +
                    "Never edit an applied migration; add a new one.",
                m.version, recorded, m.checksum,
            )
        }
    }

    /** A file may hold several statements; plain JDBC runs them in one go. */
    private fun JdbcTransaction.execScript(sql: String) {
        (connection.connection as Connection).createStatement().use { it.execute(sql) }
    }

    private companion object {
        /** Arbitrary app-wide id for the advisory lock; any other user of it would block us. */
        const val LOCK_KEY = 4_217_390_551L
    }
}
