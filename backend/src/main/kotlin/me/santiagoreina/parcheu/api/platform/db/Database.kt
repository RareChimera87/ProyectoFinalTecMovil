package me.santiagoreina.parcheu.api.platform.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import me.santiagoreina.parcheu.api.platform.config.DbConfig
import me.santiagoreina.parcheu.api.platform.config.Env
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
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

/** Ubicación de las migraciones: `V1__esquema.sql`, `V2__semillas.sql` y luego `V<yyyyMMdd>_<HHmm>__<descripcion>.sql`. */
const val MIGRATIONS_LOCATION = "classpath:db/migration"

/**
 * Aplica las migraciones Flyway (ADR-0011). [outOfOrder] va activo en dev, local y CI, donde varias
 * ramas mezclan migraciones con fechas cruzadas; en prod va apagado porque las migraciones llegan
 * en lote y en orden (lo garantiza `scripts/check-migration-order.sh` en cada PR).
 */
fun runMigrations(dataSource: DataSource, outOfOrder: Boolean = true): Int {
    val result = Flyway.configure()
        .dataSource(dataSource)
        .locations(MIGRATIONS_LOCATION)
        .outOfOrder(outOfOrder)
        .load()
        .migrate()
    return result.migrationsExecuted
}

/** `outOfOrder` según el entorno: solo prod lo apaga. */
fun outOfOrderFor(env: Env): Boolean = env != Env.PROD

/** Conecta Exposed al pool. Las transacciones se abren explícitamente en los servicios (§3.2). */
fun connectExposed(dataSource: DataSource): Database = Database.connect(dataSource)
