package me.santiagoreina.parcheu.api.platform.db

import com.zaxxer.hikari.HikariDataSource
import me.santiagoreina.parcheu.api.platform.config.DbConfig
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Postgres 16 limpio para los tests de esquema.
 *
 * - CI: Testcontainers (`postgres:16-alpine`).
 * - Local sin Docker: `-PsinContenedores` los omite ([enabled] = false), salvo que se defina
 *   `PARCHEU_TEST_DB_URL` (JDBC a un Postgres 16 propio, p. ej. `jdbc:postgresql://localhost:54329/postgres`, usuario en
 *   `PARCHEU_TEST_DB_USER` (postgres por defecto) y clave opcional en `PARCHEU_TEST_DB_PASSWORD`).
 *   **Ese esquema `public` se borra entero** al empezar: úsalo solo con una base desechable.
 */
object TestDatabase {
    private val externalUrl: String? = System.getenv("PARCHEU_TEST_DB_URL")?.takeIf { it.isNotBlank() }

    val enabled: Boolean = externalUrl != null || System.getProperty("sinContenedores") == null

    /** Crea una base limpia, ejecuta [block] y libera los recursos. */
    fun <T> withCleanDatabase(block: (HikariDataSource) -> T): T {
        val url = externalUrl
        if (url != null) {
            return createDataSource(DbConfig("test", user = System.getenv("PARCHEU_TEST_DB_USER") ?: "postgres", password = System.getenv("PARCHEU_TEST_DB_PASSWORD"), cloudSqlInstance = null, jdbcUrl = url), maxPoolSize = 3)
                .use { ds ->
                    ds.connection.use { c -> c.createStatement().execute("drop schema public cascade; create schema public") }
                    block(ds)
                }
        }
        return PostgreSQLContainer("postgres:16-alpine").use { postgres ->
            postgres.start()
            val config = DbConfig(
                name = postgres.databaseName,
                user = postgres.username,
                password = postgres.password,
                cloudSqlInstance = null,
                jdbcUrl = postgres.jdbcUrl,
            )
            createDataSource(config, maxPoolSize = 3).use(block)
        }
    }
}
