package de.yscord.player.db

import jakarta.annotation.PostConstruct
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import kotlin.system.exitProcess

/**
 * A tiny Knex-style migration runner. It applies every pending migration in
 * version order and records it in `schema_migrations`; `--db.rollback=N` reverts
 * the last N instead (like `knex migrate:rollback`), then exits.
 *
 * The whole run is one transaction holding a Postgres advisory lock, so two
 * processes starting at once (two replicas, a Job and a pod) can't both apply
 * the same migration: the second waits, then finds nothing pending. Postgres DDL
 * is transactional, so a failing migration leaves the schema untouched.
 *
 * Runs in @PostConstruct (not as an ApplicationRunner) and @DependsOn the Exposed
 * connection, so the schema is ready before any DB-touching bean initializes.
 */
@Component
@DependsOn("exposedConfig")
class MigrationRunner(
    migrations: List<Migration>,
    private val args: ApplicationArguments,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ordered = migrations.sortedBy { it.version }

    @PostConstruct
    fun run() {
        val rollbackSteps = args.getOptionValues("db.rollback")?.firstOrNull()?.toIntOrNull()
        transaction {
            // Released automatically when the transaction ends.
            exec("SELECT pg_advisory_xact_lock($LOCK_KEY)")
            ensureHistoryTable()
            if (rollbackSteps != null) rollback(rollbackSteps) else migrateUp()
        }
        if (rollbackSteps != null) {
            log.info("rollback complete — exiting")
            exitProcess(0)
        }
    }

    private fun JdbcTransaction.ensureHistoryTable() {
        SchemaUtils.create(SchemaMigrations)
        // The history table's own schema change: checksums came later.
        exec("ALTER TABLE schema_migrations ADD COLUMN IF NOT EXISTS checksum VARCHAR(64)")
    }

    private fun JdbcTransaction.migrateUp() {
        val applied = SchemaMigrations.selectAll()
            .associate { it[SchemaMigrations.version] to it[SchemaMigrations.checksum] }
        var count = 0
        ordered.forEach { m ->
            val statements = m.up()
            val sum = checksum(statements)
            if (m.version in applied) {
                verify(m.version, applied[m.version], sum)
                return@forEach
            }
            statements.forEach { exec(it) }
            SchemaMigrations.insert {
                it[version] = m.version
                it[appliedAt] = Instant.now()
                it[checksum] = sum
            }
            log.info("migrate up  → {}", m.version)
            count++
        }
        if (count == 0) log.info("DB schema up to date ({} applied)", applied.size)
    }

    /** Warns only: this is a playground, a changed migration shouldn't stop the app. */
    private fun verify(version: String, recorded: String?, actual: String) {
        when (recorded) {
            null -> {
                SchemaMigrations.update({ SchemaMigrations.version eq version }) { it[checksum] = actual }
                log.info("recorded checksum for {} (applied before checksums existed)", version)
            }
            actual -> Unit
            else -> log.warn(
                "migration {} changed after it was applied (checksum {} → {}). " +
                    "Never edit an applied migration; add a new one.",
                version, recorded, actual,
            )
        }
    }

    private fun JdbcTransaction.rollback(steps: Int) {
        val done = SchemaMigrations.selectAll().map { it[SchemaMigrations.version] }.toSet()
        val toUndo = ordered.filter { it.version in done }.takeLast(steps).reversed()
        if (toUndo.isEmpty()) {
            log.info("nothing to roll back")
            return
        }
        toUndo.forEach { m ->
            m.down().forEach { exec(it) }
            SchemaMigrations.deleteWhere { SchemaMigrations.version eq m.version }
            log.info("rollback    ← {}", m.version)
        }
    }

    private fun checksum(statements: List<String>): String =
        MessageDigest.getInstance("SHA-256")
            .digest(statements.joinToString("\n").toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        /** Arbitrary app-wide id for the advisory lock; any other user of it would block us. */
        const val LOCK_KEY = 4_217_390_551L
    }
}
