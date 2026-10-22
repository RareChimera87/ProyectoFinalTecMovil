package me.santiagoreina.parcheu.data.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Producto y planes base de ParcheU+ en Play Console (arquitectura §9, ADR-0013). */
object PlusProduct {
    const val PRODUCT_ID = "parcheu_plus"
    const val BASE_PLAN_MONTHLY = "mensual"
    const val BASE_PLAN_ANNUAL = "anual"
}

/** Estado de la conexión con Play Billing. */
sealed interface BillingConnectionState {
    data object Disconnected : BillingConnectionState

    data object Connecting : BillingConnectionState

    data object Connected : BillingConnectionState

    /** Play respondió con un error no recuperable o se agotaron los reintentos. */
    data class Unavailable(val responseCode: Int) : BillingConnectionState
}

/**
 * Conecta con Play Billing, reconecta si el servicio se cae y consulta los `ProductDetails` de
 * [PlusProduct.PRODUCT_ID] (E7.0). No compra ni reconoce compras: eso es E7.2 y el servidor (E7.1).
 *
 * Conexión: un intento y hasta [maxRetries] reintentos con backoff exponencial
 * ([initialBackoffMillis], ×2 en cada reintento) solo ante errores transitorios.
 */
class BillingClientProvider(
    private val client: PlayBillingClient,
    private val scope: CoroutineScope,
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val initialBackoffMillis: Long = DEFAULT_INITIAL_BACKOFF_MILLIS
) {
    private val connectMutex = Mutex()

    private val _connectionState =
        MutableStateFlow<BillingConnectionState>(BillingConnectionState.Disconnected)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

    private val _plusProductDetails = MutableStateFlow<ProductDetails?>(null)

    /** `ProductDetails` de `parcheu_plus` cuando Play los devolvió; `null` si aún no existen. */
    val plusProductDetails: StateFlow<ProductDetails?> = _plusProductDetails.asStateFlow()

    /** Conecta si hace falta. Devuelve `true` si quedó conectado. */
    suspend fun connect(): Boolean = connectMutex.withLock {
        if (client.isReady) {
            _connectionState.value = BillingConnectionState.Connected
            return@withLock true
        }
        _connectionState.value = BillingConnectionState.Connecting
        var backoff = initialBackoffMillis
        var lastCode = BillingResponseCode.SERVICE_UNAVAILABLE
        for (attempt in 0..maxRetries) {
            val result = client.startConnection(onDisconnected = ::onServiceDisconnected)
            lastCode = result.responseCode
            if (lastCode == BillingResponseCode.OK) {
                _connectionState.value = BillingConnectionState.Connected
                return@withLock true
            }
            if (!isTransient(lastCode) || attempt == maxRetries) break
            delay(backoff)
            backoff *= 2
        }
        _connectionState.value = BillingConnectionState.Unavailable(lastCode)
        false
    }

    /**
     * Consulta `parcheu_plus` y actualiza [plusProductDetails]. Si el servicio se desconectó entre la
     * conexión y la consulta, reconecta una vez y repite.
     */
    suspend fun refreshPlusProductDetails(): ProductDetails? {
        repeat(2) {
            if (!connect()) return _plusProductDetails.value
            val result = client.querySubscriptionDetails(listOf(PlusProduct.PRODUCT_ID))
            when (result.billingResult.responseCode) {
                BillingResponseCode.OK -> {
                    val details =
                        result.productDetailsList.orEmpty().firstOrNull {
                            it.productId == PlusProduct.PRODUCT_ID
                        }
                    _plusProductDetails.value = details
                    return details
                }

                BillingResponseCode.SERVICE_DISCONNECTED -> {
                    _connectionState.value = BillingConnectionState.Disconnected
                }

                else -> return _plusProductDetails.value
            }
        }
        return _plusProductDetails.value
    }

    /** Cierra la conexión (al cerrar sesión o en tests). */
    fun disconnect() {
        client.endConnection()
        _connectionState.value = BillingConnectionState.Disconnected
    }

    private fun onServiceDisconnected() {
        _connectionState.value = BillingConnectionState.Disconnected
        scope.launch { connect() }
    }

    companion object {
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_INITIAL_BACKOFF_MILLIS = 1_000L

        /** Códigos en los que Play recomienda reintentar con backoff. */
        fun isTransient(code: Int): Boolean = code == BillingResponseCode.SERVICE_UNAVAILABLE ||
            code == BillingResponseCode.SERVICE_DISCONNECTED ||
            code == BillingResponseCode.NETWORK_ERROR ||
            code == BillingResponseCode.ERROR
    }
}
