package de.yscord.player.db

import jakarta.annotation.PostConstruct
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Component
import java.time.Instant
import kotlin.system.exitProcess

/**
 * A tiny Knex-style migration runner. It applies every pending migration in
 * version order and records it in `schema_migrations`; `--db.rollback=N` reverts
 * the last N instead (like `knex migrate:rollback`), then exits.
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
        transaction { SchemaUtils.create(SchemaMigrations) }
        val rollbackSteps = args.getOptionValues("db.rollback")?.firstOrNull()?.toIntOrNull()
        if (rollbackSteps != null) {
            rollback(rollbackSteps)
            log.info("rollback complete — exiting")
            exitProcess(0)
        }
        migrateUp()
    }

    private fun appliedVersions(): Set<String> = transaction {
        SchemaMigrations.selectAll().map { it[SchemaMigrations.version] }.toSet()
    }

    private fun migrateUp() {
        val done = appliedVersions()
        val pending = ordered.filter { it.version !in done }
        if (pending.isEmpty()) {
            log.info("DB schema up to date ({} applied)", done.size)
            return
        }
        pending.forEach { m ->
            transaction {
                m.up()
                SchemaMigrations.insert {
                    it[version] = m.version
                    it[appliedAt] = Instant.now()
                }
            }
            log.info("migrate up  → {}", m.version)
        }
    }

    private fun rollback(steps: Int) {
        val done = appliedVersions()
        val toUndo = ordered.filter { it.version in done }.takeLast(steps).reversed()
        if (toUndo.isEmpty()) {
            log.info("nothing to roll back")
            return
        }
        toUndo.forEach { m ->
            transaction {
                m.down()
                SchemaMigrations.deleteWhere { SchemaMigrations.version eq m.version }
            }
            log.info("rollback    ← {}", m.version)
        }
    }
}
