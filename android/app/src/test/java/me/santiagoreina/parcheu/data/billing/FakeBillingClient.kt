package me.santiagoreina.parcheu.data.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Lo que la app pidió a Play en `launchBillingFlow`. */
data class LaunchRequest(
    val productId: String,
    val offerToken: String,
    val obfuscatedAccountId: String
)

/**
 * Fake de [PlayBillingClient]: responde la conexión con [connectionResponses] en orden (el último se
 * repite) y la consulta con [productDetails]. Registra cuántas veces se intentó conectar, las compras
 * lanzadas ([launches]) y permite simular `onPurchasesUpdated` con [emitPurchasesUpdate].
 */
class FakeBillingClient(
    private val connectionResponses: List<Int> = listOf(BillingResponseCode.OK),
    var productDetails: List<ProductDetails> = emptyList(),
    var queryResponses: MutableList<Int> = mutableListOf()
) : PlayBillingClient {
    var connectionAttempts = 0
        private set
    var queryCalls = 0
        private set
    var endConnectionCalls = 0
        private set

    /** Cuántos clientes de Play se crearon (uno inicial + uno por cada reconexión tras cerrar). */
    var clientsCreated = 1
        private set
    private var closed = false
    private var onDisconnected: (() -> Unit)? = null

    override var isReady: Boolean = false
        private set

    override suspend fun startConnection(onDisconnected: () -> Unit): BillingResult {
        // Modela BillingClient real: tras endConnection queda CLOSED y hay que crear uno nuevo.
        if (closed) {
            closed = false
            clientsCreated++
        }
        val code = connectionResponses[minOf(connectionAttempts, connectionResponses.lastIndex)]
        connectionAttempts++
        isReady = code == BillingResponseCode.OK
        this.onDisconnected = onDisconnected
        return billingResult(code)
    }

    override suspend fun querySubscriptionDetails(productIds: List<String>): ProductDetailsResult {
        queryCalls++
        val code = queryResponses.removeFirstOrNull() ?: BillingResponseCode.OK
        if (code == BillingResponseCode.SERVICE_DISCONNECTED) isReady = false
        val list =
            if (code == BillingResponseCode.OK) {
                productDetails.filter { it.productId in productIds }
            } else {
                null
            }
        return ProductDetailsResult(billingResult(code), list)
    }

    /** Respuesta de `launchBillingFlow` (si la hoja de Play se pudo abrir). */
    var launchResponse: Int = BillingResponseCode.OK
    val launches = mutableListOf<LaunchRequest>()

    /** Compras que devuelve `queryPurchasesAsync`. */
    var activePurchases: List<Purchase> = emptyList()
    var purchasesQueryResponse: Int = BillingResponseCode.OK
    var purchasesQueryCalls = 0
        private set

    private val _purchaseUpdates = MutableSharedFlow<PurchasesUpdate>(extraBufferCapacity = 16)
    override val purchaseUpdates: SharedFlow<PurchasesUpdate> = _purchaseUpdates

    override fun launchSubscriptionFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String,
        obfuscatedAccountId: String
    ): BillingResult {
        launches += LaunchRequest(productDetails.productId, offerToken, obfuscatedAccountId)
        return billingResult(launchResponse)
    }

    override suspend fun querySubscriptionPurchases(): PurchasesResult {
        purchasesQueryCalls++
        return PurchasesResult(billingResult(purchasesQueryResponse), activePurchases)
    }

    /** Simula `onPurchasesUpdated` de Play. */
    suspend fun emitPurchasesUpdate(code: Int, purchases: List<Purchase> = emptyList()) {
        _purchaseUpdates.emit(PurchasesUpdate(billingResult(code), purchases))
    }

    override fun endConnection() {
        endConnectionCalls++
        isReady = false
        closed = true
    }

    /** Simula que Play corta el servicio después de conectar. */
    fun simulateServiceDisconnected() {
        isReady = false
        onDisconnected?.invoke()
    }

    companion object {
        fun billingResult(code: Int): BillingResult =
            BillingResult.newBuilder().setResponseCode(code).build()

        /**
         * `Purchase` real desde el JSON de Play. En el JSON `purchaseState` 0 es PURCHASED y 4 es PENDING.
         * Requiere Robolectric por `org.json`.
         */
        fun purchase(
            token: String,
            pending: Boolean = false,
            productId: String = PlusProduct.PRODUCT_ID,
            acknowledged: Boolean = false
        ): Purchase {
            val json =
                """
                {"orderId":"GPA.$token","packageName":"me.santiagoreina.parcheu","productIds":["$productId"],
                 "purchaseTime":1730000000000,"purchaseState":${if (pending) 4 else 0},
                 "purchaseToken":"$token","quantity":1,"autoRenewing":true,"acknowledged":$acknowledged}
                """.trimIndent()
            return Purchase(json, "firma")
        }

        /**
         * Crea un `ProductDetails` real desde el JSON que devuelve Play (su constructor es privado del
         * paquete, por eso la reflexión). Requiere Robolectric por `org.json`.
         */
        fun subscription(
            productId: String = PlusProduct.PRODUCT_ID,
            basePlans: List<Pair<String, Long>> =
                listOf(
                    PlusProduct.BASE_PLAN_MONTHLY to 5_000_000_000L,
                    PlusProduct.BASE_PLAN_ANNUAL to 45_000_000_000L
                )
        ): ProductDetails {
            val offers =
                basePlans.joinToString(",") { (basePlan, micros) ->
                    val period = if (basePlan == PlusProduct.BASE_PLAN_ANNUAL) "P1Y" else "P1M"
                    """
                    {"basePlanId":"$basePlan","offerIdToken":"token-$basePlan","offerTags":[],
                     "pricingPhases":[{"priceAmountMicros":$micros,"priceCurrencyCode":"COP",
                     "formattedPrice":"$ ${micros / 1_000_000}","billingPeriod":"$period",
                     "recurrenceMode":1,"billingCycleCount":0}]}
                    """.trimIndent()
                }
            val json =
                """
                {"productId":"$productId","type":"subs","title":"ParcheU+ (ParcheU)","name":"ParcheU+",
                 "description":"Suscripción ParcheU+","localizedIn":["es-419"],"skuDetailsToken":"token",
                 "subscriptionOfferDetails":[$offers]}
                """.trimIndent()
            return productDetailsFromJson(json)
        }

        /** `ProductDetails` desde el JSON de Play (constructor privado del paquete). */
        fun productDetailsFromJson(json: String): ProductDetails {
            val constructor = ProductDetails::class.java.getDeclaredConstructor(String::class.java)
            constructor.isAccessible = true
            return constructor.newInstance(json)
        }
    }
}
