package me.santiagoreina.parcheu.api.quota

import me.santiagoreina.parcheu.api.platform.config.Env
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

/**
 * Cableado del dominio según el entorno:
 * - prod: Postgres + stub 'gratis' de [EntitlementService] (hasta E7.1) y sin `grant-plus`.
 * - dev: Postgres + `entitlements` con `grant-plus`.
 * - local: igual que dev; sin `DB_NAME` usa memoria para poder probar la API sin base.
 */
fun quotaModule(env: Env, db: Database?, clock: Clock, limits: QuotaLimits = QuotaLimits.fromEnv()): QuotaModule {
    if (db == null) {
        check(env == Env.LOCAL) { "Las cuotas necesitan la base de datos en $env: define DB_NAME y CLOUD_SQL_INSTANCE" }
        return inMemoryQuotaModule(clock, limits)
    }
    val dev = if (env == Env.PROD) null else ExposedDevEntitlementService(db)
    val entitlements: EntitlementService = dev ?: FreeEntitlementService
    return QuotaModule(
        service = QuotaService(ExposedUsageRepository(db), entitlements, limits, clock),
        users = ExposedQuotaUserLookup(db),
        devPlus = dev,
        clock = clock,
    )
}

/** Todo en memoria (pruebas y `ENV=local` sin base). Los UID se traducen con [DerivedQuotaUserLookup]. */
fun inMemoryQuotaModule(
    clock: Clock,
    limits: QuotaLimits = QuotaLimits(),
    usage: UsageRepository = InMemoryUsageRepository(),
    entitlements: InMemoryDevEntitlementService = InMemoryDevEntitlementService(),
): QuotaModule = QuotaModule(
    service = QuotaService(usage, entitlements, limits, clock),
    users = DerivedQuotaUserLookup,
    devPlus = entitlements,
    clock = clock,
)
