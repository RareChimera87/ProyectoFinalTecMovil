package me.santiagoreina.parcheu.api.quota

import me.santiagoreina.parcheu.api.billing.ExposedBillingStore
import me.santiagoreina.parcheu.api.billing.StoredEntitlementService
import me.santiagoreina.parcheu.api.platform.config.Env
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

/**
 * Cableado del dominio según el entorno. Plus sale siempre de la tabla `entitlements`
 * ([StoredEntitlementService], E7.1), que mantienen `verify` y las RTDN:
 * - prod: Postgres, sin `grant-plus`.
 * - dev: Postgres, con `grant-plus` (fila `dev-grant` en la misma tabla).
 * - local: igual que dev; sin `DB_NAME` usa memoria para poder probar la API sin base.
 */
fun quotaModule(env: Env, db: Database?, clock: Clock, limits: QuotaLimits = QuotaLimits.fromEnv()): QuotaModule {
    if (db == null) {
        check(env == Env.LOCAL) { "Las cuotas necesitan la base de datos en $env: define DB_NAME y CLOUD_SQL_INSTANCE" }
        return inMemoryQuotaModule(clock, limits)
    }
    val entitlements = StoredEntitlementService(ExposedBillingStore(db))
    return QuotaModule(
        service = QuotaService(ExposedUsageRepository(db), entitlements, limits, clock),
        users = ExposedQuotaUserLookup(db),
        devPlus = if (env == Env.PROD) null else entitlements,
        clock = clock,
        daily = DailyLimiter(ExposedDailyCounterRepository(db), entitlements, limits, clock),
    )
}

/**
 * Todo en memoria (pruebas y `ENV=local` sin base). Los UID se traducen con [DerivedQuotaUserLookup].
 * [devPlus] es por defecto el mismo [entitlements] si sabe conceder Plus.
 */
fun inMemoryQuotaModule(
    clock: Clock,
    limits: QuotaLimits = QuotaLimits(),
    usage: UsageRepository = InMemoryUsageRepository(),
    entitlements: EntitlementService = InMemoryDevEntitlementService(),
    devPlus: DevPlusGranter? = entitlements as? DevPlusGranter,
    counters: DailyCounterRepository = InMemoryDailyCounterRepository(),
): QuotaModule = QuotaModule(
    service = QuotaService(usage, entitlements, limits, clock),
    users = DerivedQuotaUserLookup,
    devPlus = devPlus,
    clock = clock,
    daily = DailyLimiter(counters, entitlements, limits, clock),
)
