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
 * Aplica las migraciones Flyway (ADR-0011). [outOfOrder] va activo en todos los entornos, prod
 * incluido: el check de orden solo corre al abrir o actualizar un PR hacia dev, y con auto-merge
 * dos PR paralelos pueden mezclarse en orden inverso. Con `outOfOrder=false` esa migración tardía
 * haría fallar la validación de Flyway y cortaría el despliegue; con `true` se aplica igual que en dev.
 */
fun runMigrations(
    dataSource: DataSource,
    outOfOrder: Boolean = true,
    location: String = MIGRATIONS_LOCATION,
): Int {
    val result = Flyway.configure()
        .dataSource(dataSource)
        .locations(location)
        .outOfOrder(outOfOrder)
        .load()
        .migrate()
    return result.migrationsExecuted
}

/** Conecta Exposed al pool. Las transacciones se abren explícitamente en los servicios (§3.2). */
fun connectExposed(dataSource: DataSource): Database = Database.connect(dataSource)
