package me.santiagoreina.parcheu.api.billing

/**
 * [BillingStore] cuyo `save` escribe y luego falla mientras [failSaves] esté activo: simula que la base cae
 * (mantenimiento o failover de Cloud SQL) a mitad de una RTDN, después del reclamo y de una escritura parcial.
 */
internal class FlakyBillingStore(private val delegate: BillingStore) : BillingStore by delegate {
    @Volatile
    var failSaves = false

    override fun save(row: EntitlementRow) {
        delegate.save(row)
        if (failSaves) throw IllegalStateException("Cloud SQL no responde (simulado)")
    }
}
