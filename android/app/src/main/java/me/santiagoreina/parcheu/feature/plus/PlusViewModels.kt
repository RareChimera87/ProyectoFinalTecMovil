package me.santiagoreina.parcheu.feature.plus

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.billing.BillingRepository
import me.santiagoreina.parcheu.data.billing.PlansResult
import me.santiagoreina.parcheu.data.billing.PlusPlans
import me.santiagoreina.parcheu.data.billing.PlusProduct
import me.santiagoreina.parcheu.data.billing.PurchaseFailure
import me.santiagoreina.parcheu.data.billing.PurchaseState
import me.santiagoreina.parcheu.data.billing.RestoreResult

/** Valor de la ruta `PurchaseSuccess(plan)` cuando el pago aún no se confirmó. */
const val PLAN_PENDING = "pendiente"

/** Planes de G1 cargados desde Play. */
sealed interface PlansUi {
    data object Loading : PlansUi

    data class Loaded(val plans: PlusPlans) : PlansUi

    /** Play no disponible o producto sin planes activos: se muestra el error con reintento. */
    data class Error(val billingUnavailable: Boolean) : PlansUi
}

/** Avisos efímeros de G1 y G4; la cancelación del usuario nunca es uno de ellos. */
sealed interface PlusMessage {
    data class PurchaseFailed(val failure: PurchaseFailure) : PlusMessage

    /** Restaurar no encontró compras activas. */
    data object NothingToRestore : PlusMessage

    data class RestoreFailed(val failure: PurchaseFailure) : PlusMessage

    data object Restored : PlusMessage

    data object NoAccount : PlusMessage
}

data class PaywallUiState(
    val plans: PlansUi = PlansUi.Loading,
    /** Plan base elegido; el anual va por defecto (G1). */
    val selected: String = PlusProduct.BASE_PLAN_ANNUAL,
    val purchasing: Boolean = false,
    val restoring: Boolean = false,
    val message: PlusMessage? = null
)

sealed interface PaywallEffect {
    /** Abre G3 reemplazando G1; [plan] es `anual`, `mensual` o [PLAN_PENDING]. */
    data class OpenSuccess(val plan: String) : PaywallEffect

    /** El usuario ya tiene ParcheU+: G1 no le vende lo que ya tiene y abre G4. */
    data object OpenManage : PaywallEffect
}

/**
 * G1 (E7.3). Carga los planes de Play, deja elegir anual (por defecto) o mensual y lanza la compra de
 * [BillingRepository]; el desenlace llega por `purchaseState`: cancelar no muestra error, pendiente
 * abre G3 con 'verificación pendiente' y completada abre G3. [userId] devuelve `MeDto.id` (null si no se
 * pudo leer); de él sale el `obfuscatedAccountId`.
 */
class PaywallViewModel(
    private val billing: BillingRepository,
    private val userId: suspend () -> String?
) : ViewModel() {
    private val _state = MutableStateFlow(PaywallUiState())
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()

    private val _effects = Channel<PaywallEffect>(Channel.BUFFERED)
    val effects: Flow<PaywallEffect> = _effects.receiveAsFlow()

    init {
        // Un desenlace viejo (p. ej. de una compra anterior) no debe abrir G3 al entrar.
        billing.acknowledgePurchaseState()
        viewModelScope.launch { billing.purchaseState.collect(::onPurchaseState) }
        loadPlans()
        viewModelScope.launch {
            val result = billing.refreshEntitlement()
            if (result is ApiResult.Success && result.value.plus) {
                _effects.send(PaywallEffect.OpenManage)
            }
        }
    }

    fun loadPlans() {
        _state.update { it.copy(plans = PlansUi.Loading) }
        viewModelScope.launch {
            val plans =
                when (val result = billing.loadPlans()) {
                    is PlansResult.Loaded -> PlansUi.Loaded(result.plans)
                    PlansResult.BillingUnavailable -> PlansUi.Error(billingUnavailable = true)
                    PlansResult.ProductUnavailable -> PlansUi.Error(billingUnavailable = false)
                }
            _state.update { it.copy(plans = plans) }
        }
    }

    fun onSelect(basePlanId: String) = _state.update { it.copy(selected = basePlanId) }

    fun onDismissMessage() = _state.update { it.copy(message = null) }

    fun onSubscribe(activity: Activity) {
        val current = _state.value
        if (current.purchasing || current.plans !is PlansUi.Loaded) return
        _state.update { it.copy(purchasing = true, message = null) }
        viewModelScope.launch {
            val id = userId()
            if (id == null) {
                _state.update { it.copy(purchasing = false, message = PlusMessage.NoAccount) }
            } else {
                billing.purchase(activity, current.selected, id)
            }
        }
    }

    fun onRestore() {
        if (_state.value.restoring) return
        _state.update { it.copy(restoring = true, message = null) }
        viewModelScope.launch {
            when (val result = billing.restorePurchases()) {
                is RestoreResult.Restored -> {
                    _state.update { it.copy(restoring = false) }
                    if (result.verifiedPurchases > 0 && result.entitlement.plus) {
                        _effects.send(PaywallEffect.OpenManage)
                    } else {
                        _state.update { it.copy(message = PlusMessage.NothingToRestore) }
                    }
                }
                is RestoreResult.Failed ->
                    _state.update {
                        it.copy(
                            restoring = false,
                            message = PlusMessage.RestoreFailed(result.failure)
                        )
                    }
            }
        }
    }

    private suspend fun onPurchaseState(purchase: PurchaseState) {
        when (purchase) {
            PurchaseState.Idle ->
                // Incluye cancelar en la hoja de Play: se vuelve a poder pulsar, sin error.
                _state.update { it.copy(purchasing = false) }
            PurchaseState.AwaitingPlay, PurchaseState.Verifying ->
                _state.update { it.copy(purchasing = true) }
            PurchaseState.VerificationPending -> {
                _state.update { it.copy(purchasing = false) }
                _effects.send(PaywallEffect.OpenSuccess(PLAN_PENDING))
            }
            is PurchaseState.Completed -> {
                _state.update { it.copy(purchasing = false) }
                val plan = purchase.entitlement.basePlan ?: _state.value.selected
                _effects.send(PaywallEffect.OpenSuccess(plan))
            }
            is PurchaseState.Failed -> {
                _state.update {
                    it.copy(
                        purchasing = false,
                        message = PlusMessage.PurchaseFailed(purchase.failure)
                    )
                }
                billing.acknowledgePurchaseState()
            }
        }
    }
}

/** Lo que G3 muestra: plan, precio y próximo cobro (cuando se conocen) o la verificación pendiente. */
data class PurchaseSuccessUiState(
    val plan: String,
    /** Precio del plan ya formateado por Play (`$45.000`); `null` si Play no lo dio. */
    val price: String? = null,
    /** Fecha del próximo cobro ("21 de octubre de 2027"); `null` si la API aún no la devolvió. */
    val nextCharge: String? = null
) {
    val pending: Boolean get() = plan == PLAN_PENDING
    val annual: Boolean get() = plan == PlusProduct.BASE_PLAN_ANNUAL
}

/** G3 (E7.3). Lee el derecho recién verificado para mostrar el próximo cobro. */
class PurchaseSuccessViewModel(private val plan: String, private val billing: BillingRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(PurchaseSuccessUiState(plan))
    val state: StateFlow<PurchaseSuccessUiState> = _state.asStateFlow()

    init {
        // G3 ya muestra este desenlace; si no se limpia, G1 volvería a abrirlo.
        billing.acknowledgePurchaseState()
        if (plan != PLAN_PENDING) {
            viewModelScope.launch {
                val entitlement =
                    billing.entitlement.value
                        ?: (billing.refreshEntitlement() as? ApiResult.Success)?.value
                val price =
                    (billing.loadPlans() as? PlansResult.Loaded)?.plans?.plan(plan)?.formattedPrice
                _state.update {
                    it.copy(
                        price = price,
                        nextCharge = entitlement?.expiresAt?.let(::formatLongDate)
                    )
                }
            }
        }
    }
}

sealed interface ManageUiState {
    data object Loading : ManageUiState

    /** [plans] puede ser `null` si Play no responde: se muestra el plan sin precio. */
    data class Loaded(
        val entitlement: EntitlementDto,
        val plans: PlusPlans?,
        val restoring: Boolean = false,
        val message: PlusMessage? = null
    ) : ManageUiState

    data class Error(val error: AppError) : ManageUiState
}

/** G4 (E7.3): activa, cancelada, en gracia o mensual; restaurar; cancelar y cambiar de plan van a Play. */
class ManageSubscriptionViewModel(private val billing: BillingRepository) : ViewModel() {
    private val _state = MutableStateFlow<ManageUiState>(ManageUiState.Loading)
    val state: StateFlow<ManageUiState> = _state.asStateFlow()

    fun load() {
        if (_state.value !is ManageUiState.Loaded) _state.value = ManageUiState.Loading
        viewModelScope.launch {
            when (val result = billing.refreshEntitlement()) {
                is ApiResult.Failure ->
                    if (_state.value !is ManageUiState.Loaded) {
                        _state.value = ManageUiState.Error(result.error)
                    }
                is ApiResult.Success -> {
                    val plans = (billing.loadPlans() as? PlansResult.Loaded)?.plans
                    _state.value = ManageUiState.Loaded(result.value, plans)
                }
            }
        }
    }

    fun onRestore() {
        val loaded = _state.value as? ManageUiState.Loaded ?: return
        if (loaded.restoring) return
        _state.value = loaded.copy(restoring = true, message = null)
        viewModelScope.launch {
            val result = billing.restorePurchases()
            val current = _state.value as? ManageUiState.Loaded ?: return@launch
            _state.value =
                when (result) {
                    is RestoreResult.Restored ->
                        current.copy(
                            entitlement = result.entitlement,
                            restoring = false,
                            message =
                            if (result.verifiedPurchases > 0) {
                                PlusMessage.Restored
                            } else {
                                PlusMessage.NothingToRestore
                            }
                        )
                    is RestoreResult.Failed ->
                        current.copy(
                            restoring = false,
                            message = PlusMessage.RestoreFailed(result.failure)
                        )
                }
        }
    }

    fun onDismissMessage() {
        val loaded = _state.value as? ManageUiState.Loaded ?: return
        _state.value = loaded.copy(message = null)
    }
}
