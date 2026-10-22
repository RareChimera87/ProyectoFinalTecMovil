package me.santiagoreina.parcheu.data.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult

/**
 * Fake de [PlayBillingClient]: responde la conexión con [connectionResponses] en orden (el último se
 * repite) y la consulta con [productDetails]. Registra cuántas veces se intentó conectar.
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
    private var onDisconnected: (() -> Unit)? = null

    override var isReady: Boolean = false
        private set

    override suspend fun startConnection(onDisconnected: () -> Unit): BillingResult {
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

    override fun endConnection() {
        endConnectionCalls++
        isReady = false
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
            val constructor = ProductDetails::class.java.getDeclaredConstructor(String::class.java)
            constructor.isAccessible = true
            return constructor.newInstance(json)
        }
    }
}
