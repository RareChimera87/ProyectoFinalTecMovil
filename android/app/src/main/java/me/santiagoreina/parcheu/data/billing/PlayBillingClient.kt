package me.santiagoreina.parcheu.data.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.CompletableDeferred

/**
 * Costura mínima sobre [BillingClient] (clase abstracta de Play difícil de falsear). Solo expone lo que
 * usa [BillingClientProvider]; en los tests se reemplaza por un fake.
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

    fun endConnection()
}

/** Implementación real con Play Billing Library 8 (`billing-ktx`). */
class AndroidPlayBillingClient(
    private val context: Context,
    private val purchasesUpdatedListener: PurchasesUpdatedListener =
        PurchasesUpdatedListener { _, _ -> }
) : PlayBillingClient {
    // La compra (launchBillingFlow y el listener real) llega en E7.2; aquí solo se conecta y se consulta.
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
