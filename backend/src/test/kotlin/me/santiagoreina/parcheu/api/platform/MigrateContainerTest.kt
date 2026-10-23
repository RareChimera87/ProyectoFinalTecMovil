package me.santiagoreina.parcheu.api.platform

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.platform.config.DbConfig
import me.santiagoreina.parcheu.api.platform.db.createDataSource
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import org.testcontainers.postgresql.PostgreSQLContainer

/** Necesita Docker: corre solo en CI. En local se omite con -PsinContenedores. */
class MigrateContainerTest : FunSpec({
    val enabled = System.getProperty("sinContenedores") == null

    test("migrate conecta a Postgres 16 y no falla sin migraciones").config(enabled = enabled) {
        PostgreSQLContainer("postgres:16-alpine").use { postgres ->
            postgres.start()
            val config = DbConfig(
                name = postgres.databaseName,
                user = postgres.username,
                password = postgres.password,
                cloudSqlInstance = null,
                jdbcUrl = postgres.jdbcUrl,
            )
            createDataSource(config, maxPoolSize = 2).use { ds ->
                runMigrations(ds) shouldBe 0
                ds.connection.use { it.createStatement().executeQuery("select 1").next() shouldBe true }
            }
        }
    }
})
