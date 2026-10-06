package de.yscord.player.db

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import kotlin.system.exitProcess

/**
 * Runs the SQL migrations in `resources/db/migrations` (see the README there).
 * `db.migrate` picks the mode:
 * 1. `on-startup` (default): migrate, then start the app. Local dev and Compose.
 * 2. `only`: migrate and exit; a failure exits non-zero. The Kubernetes migration
 *    Job runs this through the `migrate` profile.
 * 3. `off`: don't touch the schema, but refuse to start while migrations are
 *    pending, i.e. the Job didn't run. App pods in Kubernetes.
 *
 * `--db.rollback=N` reverts the last N instead (like `knex migrate:rollback`),
 * then exits. Local development only.
 *
 * Runs in @PostConstruct (not as an ApplicationRunner) and @DependsOn the Exposed
 * connection, so the schema is ready before any DB-touching bean initializes.
 * Never lazy: the `migrate` profile makes every other bean lazy.
 */
@Component
@Lazy(false)
@DependsOn("exposedConfig")
class MigrationRunner(
    private val args: ApplicationArguments,
    @Value("\${db.migrate:on-startup}") private val mode: String,
) {
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
        when (mode) {
            "on-startup" -> migrator.migrate()
            "only" -> {
                migrator.migrate()
                log.info("migrations complete — exiting")
                exitProcess(0)
            }
            "off" -> {
                val pending = migrator.pending()
                check(pending.isEmpty()) {
                    "Pending migrations $pending: the migration Job must run before this version starts"
                }
                log.info("DB schema up to date, migrations are run elsewhere")
            }
            else -> error("db.migrate must be on-startup, only or off, got '$mode'")
        }
    }
}
