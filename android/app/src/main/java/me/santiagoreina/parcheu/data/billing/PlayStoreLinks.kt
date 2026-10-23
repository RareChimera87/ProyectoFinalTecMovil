package me.santiagoreina.parcheu.data.billing

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Enlaces a Google Play para gestionar la suscripción (cancelar o cambiar de plan, arquitectura §9). */
object PlayStoreLinks {
    private const val SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"

    /**
     * Deep link a la gestión de `parcheu_plus` en Play. [packageName] es el del build instalado
     * (`me.santiagoreina.parcheu` en prod; `.dev` en el flavor dev).
     */
    fun manageSubscriptionUrl(packageName: String): String =
        "$SUBSCRIPTIONS_URL?sku=${PlusProduct.PRODUCT_ID}&package=$packageName"

    /** Intent listo para `startActivity` desde G4 ("Cancelar en Google Play"). */
    fun manageSubscriptionIntent(context: Context): Intent =
        Intent(Intent.ACTION_VIEW, manageSubscriptionUrl(context.packageName).toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
