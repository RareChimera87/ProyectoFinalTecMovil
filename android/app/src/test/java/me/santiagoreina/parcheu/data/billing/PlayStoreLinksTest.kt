package me.santiagoreina.parcheu.data.billing

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** E7.2: deep link a la gestión de la suscripción en Play (arquitectura §9). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayStoreLinksTest {
    @Test
    fun manageUrlPointsToParcheuPlusOfThisPackage() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?sku=parcheu_plus" +
                "&package=me.santiagoreina.parcheu",
            PlayStoreLinks.manageSubscriptionUrl("me.santiagoreina.parcheu")
        )
    }

    @Test
    fun intentOpensTheUrlForTheInstalledPackage() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = PlayStoreLinks.manageSubscriptionIntent(context)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(
            PlayStoreLinks.manageSubscriptionUrl(context.packageName),
            intent.data.toString()
        )
    }
}
