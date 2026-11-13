package me.santiagoreina.parcheu.api.platform.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import me.santiagoreina.parcheu.api.platform.config.DbConfig
import me.santiagoreina.parcheu.api.platform.config.Env
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

/** Conexiones de los jobs (`migrate`, `seed-demo`, `purge-demo`): trabajan en serie, con dos sobra. */
const val JOB_POOL_SIZE = 2

/** Conexiones que el pool mantiene abiertas sin uso: pocas, para no acaparar el límite de Cloud SQL (db-f1-micro). */
private const val MIN_IDLE = 1

/** Una conexión ociosa por encima de [MIN_IDLE] se cierra a los 2 min; Hikari exige ≥ 10 s. */
private const val IDLE_TIMEOUT_MS = 120_000L

/**
 * Vida máxima de una conexión: 10 min, muy por debajo de los cortes del servidor y del conector de Cloud SQL, para
 * que Hikari la recicle antes de que la cierren por debajo (avisos `Failed to validate connection … This connection
 * has been closed` en prod tras ratos sin tráfico).
 */
private const val MAX_LIFETIME_MS = 600_000L

/**
 * Ping a las conexiones ociosas cada 2 min (menor que [MAX_LIFETIME_MS], mínimo de Hikari 30 s). Con `cpu_idle` de
 * Cloud Run la CPU se estrangula sin peticiones y el ping puede no correr; ahí [MAX_LIFETIME_MS] y la validación al
 * prestar la conexión siguen evitando que una petición reciba una conexión muerta.
 */
private const val KEEPALIVE_TIME_MS = 120_000L

/**
 * Pool Hikari hacia Postgres. En Cloud Run usa el conector de Cloud SQL (autenticación IAM, sin
 * VPC connector); en local/CI usa `DB_URL` JDBC directo.
 *
 * El pool crece bajo demanda hasta [maxPoolSize] (por defecto `DB_MAX_POOL_SIZE`) y vuelve a [MIN_IDLE] tras
 * [IDLE_TIMEOUT_MS]: con el `minimumIdle` de Hikari por defecto (= máximo) cada instancia abría todas sus
 * conexiones al arrancar y las retenía, y con varias instancias se agotaba `max_connections` de Cloud SQL.
 */
fun createDataSource(config: DbConfig, maxPoolSize: Int = config.maxPoolSize, failFast: Boolean = true): HikariDataSource {
    val hikari = HikariConfig().apply {
        jdbcUrl = when {
            config.cloudSqlInstance != null -> "jdbc:postgresql:///${config.name}"
            config.jdbcUrl != null -> config.jdbcUrl
            else -> error("Falta CLOUD_SQL_INSTANCE o DB_URL para conectar a la base de datos")
        }
        username = config.user
        password = config.password
        maximumPoolSize = maxPoolSize
        minimumIdle = minOf(MIN_IDLE, maxPoolSize)
        idleTimeout = IDLE_TIMEOUT_MS
        maxLifetime = MAX_LIFETIME_MS
        keepaliveTime = KEEPALIVE_TIME_MS
        // El servicio no debe caerse al arrancar si Cloud SQL tarda: Hikari reintenta al pedir la conexión.
        if (!failFast) initializationFailTimeout = -1
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
