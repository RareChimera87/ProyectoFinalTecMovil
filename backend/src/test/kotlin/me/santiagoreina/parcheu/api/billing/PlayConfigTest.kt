package me.santiagoreina.parcheu.api.billing

import com.google.api.client.http.GenericUrl
import com.google.api.client.testing.http.MockHttpTransport
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.santiagoreina.parcheu.api.platform.config.Env

/** Configuración de la API de Play desde variables de entorno (sin red: nada se crea hasta el primer uso). */
class PlayConfigTest : FunSpec({
    test("local sin credenciales: API sin configurar con un mensaje que apunta a grant-plus") {
        val config = PlayConfig.fromEnv(Env.LOCAL, emptyMap())
        config.packageName shouldBe "me.santiagoreina.parcheu"
        config.client shouldBe UnconfiguredPlaySubscriptions
        shouldThrow<IllegalStateException> { config.client.get("t") }.message!! shouldContain "grant-plus"
    }

    test("dev o prod sin credenciales: el servicio arranca y falla solo al primer uso") {
        listOf(Env.DEV, Env.PROD).forEach { env ->
            val config = PlayConfig.fromEnv(env, emptyMap())
            config.client.shouldBeInstanceOf<LazyPlaySubscriptions>()
            shouldThrow<IllegalStateException> { config.client.acknowledge("parcheu_plus", "t") }.message!! shouldContain
                "PLAY_SERVICE_ACCOUNT"
        }
    }

    test("con PLAY_SERVICE_ACCOUNT o SECRET_PLAY_SA el cliente es perezoso y PLAY_PACKAGE_NAME cambia el paquete") {
        val config = PlayConfig.fromEnv(
            Env.PROD,
            mapOf("PLAY_SERVICE_ACCOUNT" to "sa-play@parcheu-prod.iam.gserviceaccount.com", "PLAY_PACKAGE_NAME" to " otra.app "),
        )
        config.packageName shouldBe "otra.app"
        config.client.shouldBeInstanceOf<LazyPlaySubscriptions>()
        PlayConfig.fromEnv(Env.DEV, mapOf("SECRET_PLAY_SA" to "projects/p/secrets/play-sa")).client
            .shouldBeInstanceOf<LazyPlaySubscriptions>()
    }

    test("las llamadas a Play llevan tiempos máximos de 5 s de conexión y 10 s de lectura, con o sin credenciales (M1)") {
        var delegated = 0
        val request = MockHttpTransport().createRequestFactory().buildGetRequest(GenericUrl("https://example.com"))
        GooglePlaySubscriptions.withTimeouts(null).initialize(request)
        request.connectTimeout shouldBe 5_000
        request.readTimeout shouldBe 10_000

        val other = MockHttpTransport().createRequestFactory().buildGetRequest(GenericUrl("https://example.com"))
        GooglePlaySubscriptions.withTimeouts { delegated++ }.initialize(other)
        delegated shouldBe 1
        other.connectTimeout shouldBe 5_000
        other.readTimeout shouldBe 10_000
    }

    test("estados de subscriptionsv2 → estado guardado (nulo = la compra aún no da derecho)") {
        PlaySubscriptionState.parse("SUBSCRIPTION_STATE_ACTIVE") shouldBe PlaySubscriptionState.ACTIVE
        PlaySubscriptionState.parse("SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED") shouldBe
            PlaySubscriptionState.PENDING_PURCHASE_CANCELED
        PlaySubscriptionState.parse("ALGO_NUEVO") shouldBe PlaySubscriptionState.UNSPECIFIED
        PlaySubscriptionState.parse(null) shouldBe PlaySubscriptionState.UNSPECIFIED
        PlaySubscriptionState.PENDING.toEntitlementState() shouldBe null
        PlaySubscriptionState.PENDING_PURCHASE_CANCELED.toEntitlementState() shouldBe null
        PlaySubscriptionState.UNSPECIFIED.toEntitlementState() shouldBe null
    }
})
