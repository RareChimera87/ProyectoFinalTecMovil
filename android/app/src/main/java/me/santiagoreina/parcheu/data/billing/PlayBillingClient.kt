package me.santiagoreina.parcheu.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Lo que Play entrega en `onPurchasesUpdated` (tras `launchBillingFlow` o al resolverse una pendiente). */
data class PurchasesUpdate(val billingResult: BillingResult, val purchases: List<Purchase>)

/**
 * Costura mínima sobre [BillingClient] (clase abstracta de Play difícil de falsear). Solo expone lo que
 * usan [BillingClientProvider] y [BillingRepositoryImpl]; en los tests se reemplaza por un fake.
 */
interface PlayBillingClient {
    val isReady: Boolean

    /**
     * Inicia la conexión y suspende hasta `onBillingSetupFinished`. [onDisconnected] se llama si Play
     * corta el servicio después de una conexión exitosa. Debe funcionar también después de
     * [endConnection] (p. ej. al volver a iniciar sesión sin matar el proceso).
     */
    suspend fun startConnection(onDisconnected: () -> Unit): BillingResult

    /** Consulta los `ProductDetails` de suscripciones con los IDs dados. */
    suspend fun querySubscriptionDetails(productIds: List<String>): ProductDetailsResult

    /** Compras que llegan por `onPurchasesUpdated`. Sobrevive a reconexiones del cliente. */
    val purchaseUpdates: SharedFlow<PurchasesUpdate>

    /**
     * Abre la hoja de compra de Play para la oferta [offerToken] de [productDetails]. [obfuscatedAccountId]
     * es siempre `sha256(userId)` ([obfuscatedAccountId]); nunca el userId en claro. El resultado final de
     * la compra llega por [purchaseUpdates]; el valor devuelto solo dice si la hoja se pudo abrir.
     */
    fun launchSubscriptionFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String,
        obfuscatedAccountId: String
    ): BillingResult

    /** `queryPurchasesAsync(SUBS)`: suscripciones vigentes de la cuenta de Play del dispositivo. */
    suspend fun querySubscriptionPurchases(): PurchasesResult

    fun endConnection()
}

/** Implementación real con Play Billing Library 8 (`billing-ktx`). */
class AndroidPlayBillingClient(private val context: Context) : PlayBillingClient {
    private val _purchaseUpdates =
        MutableSharedFlow<PurchasesUpdate>(
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    override val purchaseUpdates: SharedFlow<PurchasesUpdate> = _purchaseUpdates.asSharedFlow()

    // Play llama al listener en el hilo principal; tryEmit no bloquea y el buffer absorbe ráfagas.
    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { result, purchases ->
            _purchaseUpdates.tryEmit(PurchasesUpdate(result, purchases.orEmpty()))
        }

    // Un BillingClient nuevo conserva el mismo listener, así que las compras siguen llegando tras reconectar.
    private fun newClient(): BillingClient = BillingClient
        .newBuilder(context.applicationContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        ).build()

    @Volatile
    private var client: BillingClient = newClient()

    override val isReady: Boolean get() = client.isReady

    private val setups = SetupCoalescer()

    @Volatile
    private var disconnectedCallback: () -> Unit = {}

    override suspend fun startConnection(onDisconnected: () -> Unit): BillingResult {
        disconnectedCallback = onDisconnected
        return setups.getOrStart(::beginSetup).await()
    }

    private fun beginSetup(): CompletableDeferred<BillingResult> {
        // endConnection() deja el BillingClient en CLOSED y Play no permite reutilizarlo
        // ("Client was already closed and can't be reused"): se crea uno nuevo para reconectar.
        if (client.connectionState == BillingClient.ConnectionState.CLOSED) client = newClient()
        val setup = CompletableDeferred<BillingResult>()
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    setup.complete(billingResult)
                }

                override fun onBillingServiceDisconnected() {
                    // Si el corte llega antes de terminar el setup, cuenta como intento fallido.
                    val pending =
                        setup.complete(
                            BillingResult
                                .newBuilder()
                                .setResponseCode(
                                    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED
                                )
                                .build()
                        )
                    if (!pending) disconnectedCallback()
                }
            }
        )
        return setup
    }

    override suspend fun querySubscriptionDetails(productIds: List<String>): ProductDetailsResult {
        val products =
            productIds.map { id ->
                QueryProductDetailsParams.Product
                    .newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        return client.queryProductDetails(params)
    }

    override fun launchSubscriptionFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String,
        obfuscatedAccountId: String
    ): BillingResult {
        val product =
            BillingFlowParams.ProductDetailsParams
                .newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        val params =
            BillingFlowParams
                .newBuilder()
                .setProductDetailsParamsList(listOf(product))
                .setObfuscatedAccountId(obfuscatedAccountId)
                .build()
        return client.launchBillingFlow(activity, params)
    }

    override suspend fun querySubscriptionPurchases(): PurchasesResult = client.queryPurchasesAsync(
        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
    )

    override fun endConnection() = client.endConnection()
}

/**
 * Reutiliza el setup en curso. Si quien llamó se cancela, Play sigue en CONNECTING y rechaza otro
 * `startConnection` con DEVELOPER_ERROR ("Client is already in the process of connecting"); por eso el
 * siguiente llamador espera el mismo setup en vez de iniciar otro.
 */
internal class SetupCoalescer {
    private var pending: CompletableDeferred<BillingResult>? = null

    @Synchronized
    fun getOrStart(
        start: () -> CompletableDeferred<BillingResult>
    ): CompletableDeferred<BillingResult> =
        pending?.takeIf { it.isActive } ?: start().also { pending = it }
}
