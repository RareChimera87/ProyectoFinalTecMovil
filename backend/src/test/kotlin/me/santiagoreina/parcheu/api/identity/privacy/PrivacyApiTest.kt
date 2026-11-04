package me.santiagoreina.parcheu.api.identity.privacy

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
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
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ConsentChange
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PrivacyDto
import me.santiagoreina.parcheu.contract.UpdatePrivacy
import me.santiagoreina.parcheu.contract.Visibility
import java.time.LocalDate

/** Criterios de servidor de E8.2 (`GET/PATCH /v1/me/privacy`) con el repositorio en memoria. */
class PrivacyApiTest : FunSpec({
    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    suspend fun HttpClient.getPrivacy(uid: String): HttpResponse = get("/v1/me/privacy") { auth(uid) }

    suspend fun HttpClient.patchPrivacy(uid: String, body: UpdatePrivacy): HttpResponse = patch("/v1/me/privacy") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    fun repoWithAna(adult: Boolean = true): InMemoryIdentityRepository = InMemoryIdentityRepository().apply {
        seedAcceptedUser("ana", TEST_LEGAL_VERSION)
        if (adult) replace(user("ana").copy(birthDate = LocalDate.of(2005, 3, 14), ageVerifiedAt = FIXED_CLOCK.instant()))
        val id = user("ana").id
        enabledModes[id] = mutableSetOf(Mode.AMISTAD, Mode.ESTUDIO)
        // B4 'Acepto': Amistad y Estudio concedidos; Pareja apagado por defecto.
        addConsent(id, ConsentKind.CHAT_LEARNING_AMISTAD, true, TEST_LEGAL_VERSION)
        addConsent(id, ConsentKind.CHAT_LEARNING_ESTUDIO, true, TEST_LEGAL_VERSION)
    }

    test("GET devuelve visibilidad ANONYMOUS por defecto, los modos y el aprendizaje (Pareja apagado)") {
        val repo = repoWithAna()
        testApplication {
            val client = startApp(testContainer(repo = repo))
            val dto = client.getPrivacy("ana").body<PrivacyDto>()
            dto.visibility shouldBe Visibility.ANONYMOUS
            dto.modes shouldBe mapOf(Mode.AMISTAD to true, Mode.PAREJA to false, Mode.ESTUDIO to true)
            dto.learning shouldBe mapOf(Mode.AMISTAD to true, Mode.PAREJA to false, Mode.ESTUDIO to true)
        }
    }

    test("PATCH cambia la visibilidad y GET la devuelve") {
        val repo = repoWithAna()
        testApplication {
            val client = startApp(testContainer(repo = repo))
            for (v in listOf(Visibility.NAME, Visibility.HIDDEN, Visibility.ANONYMOUS)) {
                client.patchPrivacy("ana", UpdatePrivacy(visibility = v)).body<PrivacyDto>().visibility shouldBe v
                client.getPrivacy("ana").body<PrivacyDto>().visibility shouldBe v
                repo.user("ana").visibility shouldBe v
            }
        }
    }

    test("apagar el aprendizaje de un modo inserta una fila en consents y borra las memorias PENDING de ese modo") {
        val repo = repoWithAna()
        val id = repo.user("ana").id
        repo.memories[id] = mutableListOf(
            Mode.AMISTAD to MemoryStatus.PENDING,
            Mode.AMISTAD to MemoryStatus.ACCEPTED,
            Mode.ESTUDIO to MemoryStatus.PENDING,
        )
        val rowsBefore = repo.consentRows(id)
        testApplication {
            val client = startApp(testContainer(repo = repo))
            val dto = client.patchPrivacy("ana", UpdatePrivacy(learning = mapOf(Mode.AMISTAD to false))).body<PrivacyDto>()
            dto.learning shouldBe mapOf(Mode.AMISTAD to false, Mode.PAREJA to false, Mode.ESTUDIO to true)
            repo.consentRows(id) shouldBe rowsBefore + 1
            repo.latestConsents(id).first { it.kind == ConsentKind.CHAT_LEARNING_AMISTAD }.granted shouldBe false
            // Solo se van las PENDING de Amistad; la aceptada y las de Estudio quedan.
            repo.memories.getValue(id) shouldBe listOf(
                Mode.AMISTAD to MemoryStatus.ACCEPTED,
                Mode.ESTUDIO to MemoryStatus.PENDING,
            )

            // Repetir no agrega otra fila de revocación.
            client.patchPrivacy("ana", UpdatePrivacy(learning = mapOf(Mode.AMISTAD to false)))
            repo.consentRows(id) shouldBe rowsBefore + 1
        }
    }

    test("revocar con POST /me/consent también descarta las memorias PENDING del modo") {
        val repo = repoWithAna()
        val id = repo.user("ana").id
        repo.memories[id] = mutableListOf(Mode.ESTUDIO to MemoryStatus.PENDING, Mode.AMISTAD to MemoryStatus.PENDING)
        testApplication {
            val client = startApp(testContainer(repo = repo))
            client.post("/v1/me/consent") {
                auth("ana")
                contentType(ContentType.Application.Json)
                setBody(ConsentChange(ConsentKind.CHAT_LEARNING_ESTUDIO, false, TEST_LEGAL_VERSION))
            }.status shouldBe HttpStatusCode.OK
            repo.memories.getValue(id) shouldBe listOf(Mode.AMISTAD to MemoryStatus.PENDING)
        }
    }

    test("encender el aprendizaje de Pareja exige Pareja activo y +18; sin eso 403 AGE_REQUIRED y no cambia nada") {
        val repo = repoWithAna(adult = true)
        val id = repo.user("ana").id
        testApplication {
            val client = startApp(testContainer(repo = repo))
            val off = client.patchPrivacy("ana", UpdatePrivacy(learning = mapOf(Mode.PAREJA to true)))
            off.status shouldBe HttpStatusCode.Forbidden
            off.body<ApiError>().code shouldBe ErrorCode.AGE_REQUIRED

            // Con Pareja activo y confirmada la edad, se concede.
            enabledModes(repo, id).add(Mode.PAREJA)
            repo.replace(repo.user("ana").copy(parejaConfirmedAt = FIXED_CLOCK.instant()))
            val ok = client.patchPrivacy("ana", UpdatePrivacy(learning = mapOf(Mode.PAREJA to true)))
            ok.status shouldBe HttpStatusCode.OK
            ok.body<PrivacyDto>().learning.getValue(Mode.PAREJA) shouldBe true
        }
    }

    test("encender el aprendizaje concede con la versión vigente del texto legal y es idempotente") {
        val repo = repoWithAna()
        val id = repo.user("ana").id
        repo.addConsent(id, ConsentKind.CHAT_LEARNING_AMISTAD, false, TEST_LEGAL_VERSION)
        val rowsBefore = repo.consentRows(id)
        testApplication {
            val client = startApp(testContainer(repo = repo))
            repeat(2) {
                client.patchPrivacy("ana", UpdatePrivacy(learning = mapOf(Mode.AMISTAD to true)))
                    .body<PrivacyDto>().learning.getValue(Mode.AMISTAD) shouldBe true
            }
            repo.consentRows(id) shouldBe rowsBefore + 1
            repo.latestConsents(id).first { it.kind == ConsentKind.CHAT_LEARNING_AMISTAD }.textVersion shouldBe TEST_LEGAL_VERSION
        }
    }

    test("un PATCH vacío responde 400 VALIDATION") {
        val repo = repoWithAna()
        testApplication {
            val client = startApp(testContainer(repo = repo))
            val response = client.patchPrivacy("ana", UpdatePrivacy())
            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ApiError>().code shouldBe ErrorCode.VALIDATION
        }
    }

    test("sin token responde 401") {
        testApplication {
            val client = startApp(testContainer(repo = repoWithAna()))
            client.get("/v1/me/privacy").status shouldBe HttpStatusCode.Unauthorized
        }
    }
})

private fun enabledModes(repo: InMemoryIdentityRepository, id: java.util.UUID) = repo.enabledModes.getValue(id)
