package me.santiagoreina.parcheu.data.billing

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Concilia las compras de ParcheU+ cada vez que la app vuelve a primer plano (`ProcessLifecycleOwner`
 * ON_START): una compra PENDING que se confirmó con la app cerrada o un `verify` que falló por red no
 * generan ningún evento, y sin esto el servidor nunca haría el acknowledge y Play reembolsaría a los 3 días
 * (ADR-0013). Solo actúa con sesión, porque `verify` exige token.
 */
class BillingReconciler(
    private val repository: BillingRepository,
    private val hasSession: () -> Boolean,
    private val scope: CoroutineScope
) : DefaultLifecycleObserver {
    private var running: Job? = null

    override fun onStart(owner: LifecycleOwner) {
        reconcile()
    }

    /** Lanza [BillingRepository.syncPurchases] si hay sesión y no hay otra conciliación en curso. */
    fun reconcile(): Job? {
        if (!hasSession()) return null
        if (running?.isActive == true) return running
        return scope.launch { repository.syncPurchases() }.also { running = it }
    }
}
