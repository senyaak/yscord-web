package de.yscord.player.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * Wires Exposed onto the DataSource that Spring Boot autoconfigures (HikariCP from
 * spring.datasource.*). We drive transactions with Exposed's own `transaction { }`
 * rather than Spring's @Transactional, so this connect() is all the glue needed.
 */
@Configuration
class ExposedConfig(dataSource: DataSource) {
    init {
        Database.connect(dataSource)
    }
}
