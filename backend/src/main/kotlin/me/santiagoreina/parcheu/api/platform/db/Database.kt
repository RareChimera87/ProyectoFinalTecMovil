package me.santiagoreina.parcheu.api.platform.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import me.santiagoreina.parcheu.api.platform.config.DbConfig
import org.flywaydb.core.Flyway
import javax.sql.DataSource

/**
 * Pool Hikari hacia Postgres. En Cloud Run usa el conector de Cloud SQL (autenticación IAM, sin
 * VPC connector); en local/CI usa `DB_URL` JDBC directo.
 */
fun createDataSource(config: DbConfig, maxPoolSize: Int = 10): HikariDataSource {
    val hikari = HikariConfig().apply {
        jdbcUrl = when {
            config.cloudSqlInstance != null -> "jdbc:postgresql:///${config.name}"
            config.jdbcUrl != null -> config.jdbcUrl
            else -> error("Falta CLOUD_SQL_INSTANCE o DB_URL para conectar a la base de datos")
        }
        username = config.user
        password = config.password
        maximumPoolSize = maxPoolSize
        if (config.cloudSqlInstance != null) {
            addDataSourceProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory")
            addDataSourceProperty("cloudSqlInstance", config.cloudSqlInstance)
            if (config.password == null) addDataSourceProperty("enableIamAuth", "true")
        }
    }
    return HikariDataSource(hikari)
}

/**
 * Aplica las migraciones Flyway de `classpath:db/migration` (las agrega E0.4). `outOfOrder` está
 * activo según la arquitectura (§3.2); sin migraciones no hace nada.
 */
fun runMigrations(dataSource: DataSource): Int {
    val result = Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .failOnMissingLocations(false)
        .outOfOrder(true)
        .load()
        .migrate()
    return result.migrationsExecuted
}
