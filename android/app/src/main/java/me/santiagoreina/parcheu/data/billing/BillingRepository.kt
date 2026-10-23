package me.santiagoreina.parcheu.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Planes de ParcheU+ para el paywall (G1). */
sealed interface PlansResult {
    data class Loaded(val plans: PlusPlans) : PlansResult

    /** Play no está disponible en el dispositivo o no respondió tras los reintentos. */
    data object BillingUnavailable : PlansResult

    /** Play respondió pero `parcheu_plus` o alguno de sus planes base no existe o no está activo. */
    data object ProductUnavailable : PlansResult
}

/** Por qué falló una compra; la cancelación del usuario no es una falla (vuelve a [PurchaseState.Idle]). */
sealed interface PurchaseFailure {
    /** Código de Play distinto de OK y de USER_CANCELED (p. ej. BILLING_UNAVAILABLE, ERROR). */
    data class Play(val responseCode: Int) : PurchaseFailure

    /** Play no conecta o no hay `ProductDetails` para lanzar la compra. */
    data object BillingUnavailable : PurchaseFailure

    /** La API rechazó la verificación (p. ej. FORBIDDEN: compra de otra cuenta). */
    data class Verification(val error: AppError) : PurchaseFailure
}

/**
 * Estado de la compra en curso, para G1 y G3 (E7.3).
 * Flujo: [Idle] → [AwaitingPlay] → ([VerificationPending] |) [Verifying] → [Completed] o [Failed].
 */
sealed interface PurchaseState {
    data object Idle : PurchaseState

    /** La hoja de compra de Play está abierta. */
    data object AwaitingPlay : PurchaseState

    /**
     * Pago aún no confirmado: compra PENDING en Play (p. ej. efectivo) o compra hecha que la API todavía no
     * pudo verificar por red. La UI muestra 'verificación pendiente' (G3); se verifica cuando Play avise o
     * al restaurar.
     */
    data object VerificationPending : PurchaseState

    /** `POST /v1/billing/verify` en curso. */
    data object Verifying : PurchaseState

    data class Completed(val entitlement: EntitlementDto) : PurchaseState

    data class Failed(val failure: PurchaseFailure) : PurchaseState
}

/** Resultado de "Restaurar compras" (G4). */
sealed interface RestoreResult {
    /** Se verificaron [verifiedPurchases] compras activas; [entitlement] es el derecho resultante. */
    data class Restored(val entitlement: EntitlementDto, val verifiedPurchases: Int) : RestoreResult

    data class Failed(val failure: PurchaseFailure) : RestoreResult
}

/**
 * Billing de ParcheU+ en el cliente (E7.2, ADR-0013) sobre Play Billing Library 8. El servidor es la
 * fuente de verdad: toda compra se manda a `POST /v1/billing/verify`, que la reconoce, y el derecho
 * vigente ([entitlement]) siempre viene de la API. Las pruebas y las previews usan
 * `FakeBillingRepository`.
 */
interface BillingRepository {
    /** Último derecho conocido; `null` hasta la primera consulta o verificación. */
    val entitlement: StateFlow<EntitlementDto?>

    val purchaseState: StateFlow<PurchaseState>

    /** Conecta (con reconexión) y consulta `parcheu_plus` con sus planes `mensual` y `anual`. */
    suspend fun loadPlans(): PlansResult

    /**
     * Abre la compra de [basePlanId] sobre [activity] con `obfuscatedAccountId = sha256(userId)`
     * ([userId] es `MeDto.id`). El desenlace se ve en [purchaseState]; al terminar bien se verifica y se
     * refresca [entitlement].
     */
    suspend fun purchase(activity: Activity, basePlanId: String, userId: String)

    /** `queryPurchasesAsync` y `verify` por cada compra activa; luego refresca [entitlement]. */
    suspend fun restorePurchases(): RestoreResult

    /**
     * Concilia compras pagadas que la API aún no reconoció (PENDING resuelta con la app cerrada, o
     * `verify` caído por red o 5xx): `queryPurchasesAsync` y `verify` de cada `parcheu_plus` PURCHASED sin
     * reconocer; si no hay ninguna, refresca [entitlement]. No toca [purchaseState] salvo para pasar de
     * [PurchaseState.VerificationPending] a [PurchaseState.Completed]. Nunca lanza; sin sesión o sin Play
     * no hace nada. Se llama al volver la app a primer plano con sesión (`BillingReconciler`).
     */
    suspend fun syncPurchases()

    /** `GET /v1/billing/entitlement`. */
    suspend fun refreshEntitlement(): ApiResult<EntitlementDto>

    /** Vuelve [purchaseState] a [PurchaseState.Idle] tras mostrar un desenlace. */
    fun acknowledgePurchaseState()

    /** Olvida el derecho y el estado (al cerrar sesión). */
    fun clear()
}
