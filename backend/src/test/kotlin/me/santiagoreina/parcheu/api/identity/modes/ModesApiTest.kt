package me.santiagoreina.parcheu.api.identity.modes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.identity.consent.LearningConsentPolicy
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ConsentChange
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.ConsentsDto
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.OnboardingStep
import me.santiagoreina.parcheu.contract.SessionDto
import me.santiagoreina.parcheu.contract.UpdateModes
import java.time.LocalDate

/** Criterios de servidor de E2.5 (modos, +18 y consentimiento) con el repositorio en memoria. */
class ModesApiTest : FunSpec({
    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    suspend fun HttpClient.putModes(uid: String, body: UpdateModes): HttpResponse = put("/v1/me/modes") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    suspend fun HttpClient.consent(uid: String, kind: ConsentKind, granted: Boolean = true): HttpResponse =
        post("/v1/me/consent") {
            auth(uid)
            contentType(ContentType.Application.Json)
            setBody(ConsentChange(kind, granted, TEST_LEGAL_VERSION))
        }

    suspend fun HttpResponse.error() = body<ApiError>().code

    fun repoWithAna(ageVerified: Boolean): InMemoryIdentityRepository = InMemoryIdentityRepository().apply {
        seedAcceptedUser("ana", TEST_LEGAL_VERSION)
        if (ageVerified) {
            replace(user("ana").copy(birthDate = LocalDate.of(2005, 3, 14), ageVerifiedAt = FIXED_CLOCK.instant()))
        }
    }

    val pareja = mapOf(Mode.AMISTAD to false, Mode.PAREJA to true, Mode.ESTUDIO to false)

    test("activar Pareja sin age_verified_at responde 403 AGE_REQUIRED y no cambia nada") {
        val repo = repoWithAna(ageVerified = false)
        testApplication {
            val client = startApp(testContainer(repo = repo))
            val response = client.putModes("ana", UpdateModes(pareja, parejaConfirmed = true))
            response.status shouldBe HttpStatusCode.Forbidden
            response.error() shouldBe ErrorCode.AGE_REQUIRED
            repo.enabledModes[repo.user("ana").id].orEmpty().shouldBeEmpty()
            repo.user("ana").parejaConfirmedAt.shouldBeNull()
        }
    }

    test("activar Pareja con age_verified_at pero una birth_date de menos de 18 años responde 403 AGE_REQUIRED") {
        val repo = InMemoryIdentityRepository().apply {
            seedAcceptedUser("ana", TEST_LEGAL_VERSION)
            // Cumple 18 mañana (2026-10-26) según el reloj fijo: el servidor no debe dejarla activar Pareja.
            replace(user("ana").copy(birthDate = LocalDate.of(2008, 10, 26), ageVerifiedAt = FIXED_CLOCK.instant()))
        }
        testApplication {
            val client = startApp(testContainer(repo = repo))
            val response = client.putModes("ana", UpdateModes(pareja, parejaConfirmed = true))
            response.status shouldBe HttpStatusCode.Forbidden
            response.error() shouldBe ErrorCode.AGE_REQUIRED
            repo.enabledModes[repo.user("ana").id].orEmpty().shouldBeEmpty()
            repo.user("ana").parejaConfirmedAt.shouldBeNull()
        }
    }

    test("activar Pareja con la edad verificada pero sin confirmación explícita responde 400 VALIDATION") {
        val repo = repoWithAna(ageVerified = true)
        testApplication {
            val client = startApp(testContainer(repo = repo))
            val response = client.putModes("ana", UpdateModes(pareja, parejaConfirmed = false))
            response.status shouldBe HttpStatusCode.BadRequest
            response.error() shouldBe ErrorCode.VALIDATION
            repo.enabledModes[repo.user("ana").id].orEmpty().shouldBeEmpty()
            repo.user("ana").parejaConfirmedAt.shouldBeNull()
        }
    }

    test("con edad verificada y confirmación Pareja queda activo y se fija pareja_confirmed_at") {
        val repo = repoWithAna(ageVerified = true)
        testApplication {
            val client = startApp(testContainer(repo = repo))
            val response = client.putModes("ana", UpdateModes(pareja + (Mode.AMISTAD to true), parejaConfirmed = true))
            response.status shouldBe HttpStatusCode.OK
            val me = response.body<MeDto>()
            me.modes shouldBe mapOf(Mode.AMISTAD to true, Mode.PAREJA to true, Mode.ESTUDIO to false)
            me.parejaConfirmed shouldBe true
            repo.user("ana").parejaConfirmedAt shouldBe FIXED_CLOCK.instant()

            // Con Pareja ya activo, cambiar otro modo no vuelve a pedir la confirmación.
            client.putModes("ana", UpdateModes(mapOf(Mode.PAREJA to true, Mode.ESTUDIO to true)))
                .body<MeDto>().modes shouldBe mapOf(Mode.AMISTAD to false, Mode.PAREJA to true, Mode.ESTUDIO to true)
        }
    }

    test("apagar todos los modos responde 400 VALIDATION; Amistad o Estudio no piden edad") {
        val repo = repoWithAna(ageVerified = false)
        testApplication {
            val client = startApp(testContainer(repo = repo))
            val none = client.putModes("ana", UpdateModes(Mode.entries.associateWith { false }))
            none.status shouldBe HttpStatusCode.BadRequest
            none.error() shouldBe ErrorCode.VALIDATION
            client.putModes("ana", UpdateModes(emptyMap())).status shouldBe HttpStatusCode.BadRequest

            val me = client.putModes("ana", UpdateModes(mapOf(Mode.AMISTAD to true, Mode.ESTUDIO to true))).body<MeDto>()
            me.modes shouldBe mapOf(Mode.AMISTAD to true, Mode.PAREJA to false, Mode.ESTUDIO to true)
        }
    }

    test("guardar modos completa el paso NEEDS_MODES del onboarding") {
        val repo = repoWithAna(ageVerified = true).apply { twins += user("ana").id }
        testApplication {
            val client = startApp(testContainer(repo = repo))
            client.post("/v1/session") { auth("ana") }.body<SessionDto>().onboarding shouldBe OnboardingStep.NEEDS_MODES
            client.putModes("ana", UpdateModes(mapOf(Mode.ESTUDIO to true))).status shouldBe HttpStatusCode.OK
            client.post("/v1/session") { auth("ana") }.body<SessionDto>().onboarding shouldBe OnboardingStep.DONE
        }
    }

    test("B4 'Acepto' inserta CHAT_LEARNING_AMISTAD y CHAT_LEARNING_ESTUDIO concedidos y ninguna fila de Pareja") {
        val repo = repoWithAna(ageVerified = true)
        testApplication {
            val client = startApp(testContainer(repo = repo))
            val userId = repo.user("ana").id
            val before = repo.consentRows(userId)
            LearningConsentPolicy.ONBOARDING.forEach { kind ->
                client.consent("ana", kind).status shouldBe HttpStatusCode.OK
            }
            val added = repo.consents.filter { it.first == userId }.map { it.second }.drop(before)
            added.map { it.kind } shouldContainExactly listOf(ConsentKind.CHAT_LEARNING_AMISTAD, ConsentKind.CHAT_LEARNING_ESTUDIO)
            added.all { it.granted } shouldBe true
            repo.consents.none { it.first == userId && it.second.kind == ConsentKind.CHAT_LEARNING_PAREJA } shouldBe true
        }
    }

    test("conceder el aprendizaje de Pareja sin Pareja activo responde 403 AGE_REQUIRED; con Pareja activo se permite") {
        val repo = repoWithAna(ageVerified = true)
        testApplication {
            val client = startApp(testContainer(repo = repo))
            val denied = client.consent("ana", ConsentKind.CHAT_LEARNING_PAREJA)
            denied.status shouldBe HttpStatusCode.Forbidden
            denied.error() shouldBe ErrorCode.AGE_REQUIRED
            // Revocar siempre se permite (no hay nada que proteger).
            client.consent("ana", ConsentKind.CHAT_LEARNING_PAREJA, granted = false).status shouldBe HttpStatusCode.OK

            client.putModes("ana", UpdateModes(pareja, parejaConfirmed = true)).status shouldBe HttpStatusCode.OK
            client.consent("ana", ConsentKind.CHAT_LEARNING_PAREJA).status shouldBe HttpStatusCode.OK
            repo.latestConsents(repo.user("ana").id)
                .first { it.kind == ConsentKind.CHAT_LEARNING_PAREJA }.granted shouldBe true
        }
    }

    test("apagar Pareja revoca CHAT_LEARNING_PAREJA; mantenerlo encendido o apagar otro modo no toca el consentimiento") {
        val repo = repoWithAna(ageVerified = true)
        testApplication {
            val client = startApp(testContainer(repo = repo))
            suspend fun learningParejaGranted() = client.get("/v1/me/consents") { auth("ana") }
                .body<ConsentsDto>().items.first { it.kind == ConsentKind.CHAT_LEARNING_PAREJA }.granted

            client.putModes("ana", UpdateModes(pareja + (Mode.AMISTAD to true), parejaConfirmed = true)).status shouldBe HttpStatusCode.OK
            client.consent("ana", ConsentKind.CHAT_LEARNING_PAREJA).status shouldBe HttpStatusCode.OK
            learningParejaGranted() shouldBe true

            // Seguir con Pareja encendido (apagando Amistad) no revoca nada.
            client.putModes("ana", UpdateModes(pareja)).status shouldBe HttpStatusCode.OK
            learningParejaGranted() shouldBe true

            // Apagar Pareja revoca el aprendizaje y GET /v1/me/consents lo refleja.
            client.putModes("ana", UpdateModes(mapOf(Mode.AMISTAD to true))).status shouldBe HttpStatusCode.OK
            learningParejaGranted() shouldBe false

            // Volver a encender Pareja no reanuda el aprendizaje: hay que pasar por H3.
            client.putModes("ana", UpdateModes(pareja + (Mode.AMISTAD to true), parejaConfirmed = true)).status shouldBe HttpStatusCode.OK
            learningParejaGranted() shouldBe false
        }
    }

    test("PUT /v1/me/modes sin términos vigentes responde 403 TERMS_REQUIRED") {
        val repo = InMemoryIdentityRepository()
        testApplication {
            val client = startApp(testContainer(repo = repo))
            client.post("/v1/session") { auth("nuevo") }.status shouldBe HttpStatusCode.OK
            val response = client.putModes("nuevo", UpdateModes(mapOf(Mode.AMISTAD to true)))
            response.status shouldBe HttpStatusCode.Forbidden
            response.error() shouldBe ErrorCode.TERMS_REQUIRED
            repo.user("nuevo").parejaConfirmedAt.shouldBeNull()
        }
    }

    test("LearningConsentPolicy asigna cada consentimiento a su modo y B4 nunca incluye Pareja") {
        LearningConsentPolicy.modeOf(ConsentKind.CHAT_LEARNING_PAREJA) shouldBe Mode.PAREJA
        LearningConsentPolicy.modeOf(ConsentKind.TERMS).shouldBeNull()
        LearningConsentPolicy.ONBOARDING.mapNotNull { LearningConsentPolicy.modeOf(it) }
            .shouldNotBeNull() shouldContainExactly listOf(Mode.AMISTAD, Mode.ESTUDIO)
    }
})
