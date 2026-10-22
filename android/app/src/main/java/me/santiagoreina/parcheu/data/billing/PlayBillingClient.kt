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

    override suspend fun startConnection(onDisconnected: () -> Unit): BillingResult {
        // endConnection() deja el BillingClient en CLOSED y Play no permite reutilizarlo
        // ("Client was already closed and can't be reused"): se crea uno nuevo para reconectar.
        if (client.connectionState == BillingClient.ConnectionState.CLOSED) client = newClient()
        val client = client
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
                    if (!pending) onDisconnected()
                }
            }
        )
        return setup.await()
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
