package de.yscord.player

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * A throwaway Postgres in Docker for @SpringBootTest. @ServiceConnection points
 * the datasource at it, so no test datasource config is needed. Same image as
 * the cluster's StatefulSet.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {
    @Bean
    @ServiceConnection
    fun postgres() = PostgreSQLContainer("postgres:17-alpine")
}
