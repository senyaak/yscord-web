package de.yscord.player.db

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Component
import kotlin.system.exitProcess

/**
 * Runs the SQL migrations in `resources/db/migrations` at startup (see the README
 * there). `--db.rollback=N` reverts the last N instead (like `knex
 * migrate:rollback`), then exits.
 *
 * Runs in @PostConstruct (not as an ApplicationRunner) and @DependsOn the Exposed
 * connection, so the schema is ready before any DB-touching bean initializes.
 */
@Component
@DependsOn("exposedConfig")
class MigrationRunner(private val args: ApplicationArguments) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun run() {
        val migrator = Migrator(SqlMigration.loadAll())
        val rollbackSteps = args.getOptionValues("db.rollback")?.firstOrNull()?.toIntOrNull()
        if (rollbackSteps != null) {
            migrator.rollback(rollbackSteps)
            log.info("rollback complete — exiting")
            exitProcess(0)
        }
        migrator.migrate()
    }
}
