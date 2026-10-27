package me.santiagoreina.parcheu.api.identity

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
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
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.AcceptTerms
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ConsentChange
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.ConsentsDto
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.FcmToken
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.OnboardingStep
import me.santiagoreina.parcheu.contract.SessionDto
import me.santiagoreina.parcheu.contract.UpdateMe
import java.time.Instant
import java.time.LocalDate

/** Rutas de identity de punta a punta (Ktor test host) con el repositorio en memoria. */
class IdentityApiTest : FunSpec({
    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    suspend fun HttpClient.session(uid: String): SessionDto = post("/v1/session") { auth(uid) }.also {
        it.status shouldBe HttpStatusCode.OK
    }.body()

    suspend fun HttpClient.acceptTerms(uid: String, version: String = TEST_LEGAL_VERSION): HttpResponse =
        post("/v1/me/terms") {
            auth(uid)
            contentType(ContentType.Application.Json)
            setBody(AcceptTerms(version))
        }

    suspend fun HttpClient.patchMe(uid: String, body: UpdateMe): HttpResponse = patch("/v1/me") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    suspend fun HttpClient.consent(uid: String, change: ConsentChange): HttpResponse = post("/v1/me/consent") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(change)
    }

    suspend fun HttpResponse.error() = body<ApiError>().code

    test("dos POST /v1/session con el mismo token crean un solo usuario") {
        val repo = InMemoryIdentityRepository()
        testApplication {
            val client = startApp(testContainer(repo = repo))
            val first = client.session("lucia")
            val second = client.session("lucia")
            repo.users.size shouldBe 1
            second.me.id shouldBe first.me.id
            first.legalTextVersion shouldBe TEST_LEGAL_VERSION
            first.onboarding shouldBe OnboardingStep.NEEDS_TERMS
        }
    }

    test("un uid de prueba (e2e-) crea la cuenta marcada is_test y los demás no (E10.6)") {
        val repo = InMemoryIdentityRepository()
        testApplication {
            val client = startApp(testContainer(repo = repo))
            client.session("e2e-ana")
            client.session("lucia")
            repo.testUids shouldBe setOf("e2e-ana")
        }
    }

    test("la sesión sin token responde 401") {
        testApplication {
            startApp().post("/v1/session").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("PATCH con nombre de más de 40 caracteres responde 400 VALIDATION") {
        testApplication {
            val client = startApp()
            val response = client.patchMe("ana", UpdateMe(displayName = "a".repeat(41)))
            response.status shouldBe HttpStatusCode.BadRequest
            response.error() shouldBe ErrorCode.VALIDATION
            client.patchMe("ana", UpdateMe(displayName = "a".repeat(40))).status shouldBe HttpStatusCode.OK
            client.patchMe("ana", UpdateMe(displayName = "   ")).status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("PATCH valida universidad, intereses y carrera, y solo cambia los campos presentes") {
        testApplication {
            val client = startApp()
            client.patchMe("ana", UpdateMe(universityId = 1234)).status shouldBe HttpStatusCode.BadRequest
            client.patchMe("ana", UpdateMe(interests = listOf("no-existe"))).status shouldBe HttpStatusCode.BadRequest
            client.patchMe("ana", UpdateMe(interests = listOf("musica", "musica"))).status shouldBe HttpStatusCode.BadRequest
            client.patchMe(
                "ana",
                UpdateMe(interests = listOf("musica", "futbol", "cine", "lectura", "gym", "series")),
            ).status shouldBe HttpStatusCode.BadRequest
            client.patchMe("ana", UpdateMe(career = "x".repeat(61))).status shouldBe HttpStatusCode.BadRequest

            val me = client.patchMe(
                "ana",
                UpdateMe(displayName = " Ana ", career = "Ing. Industrial", universityId = 1, interests = listOf("musica", "cine")),
            ).body<MeDto>()
            me.displayName shouldBe "Ana"
            me.career shouldBe "Ing. Industrial"
            me.universityId shouldBe 1
            me.interests shouldContainExactly listOf("musica", "cine")

            val onlyName = client.patchMe("ana", UpdateMe(displayName = "Anita")).body<MeDto>()
            onlyName.displayName shouldBe "Anita"
            onlyName.career shouldBe "Ing. Industrial"
            client.patchMe("ana", UpdateMe(career = "")).body<MeDto>().career shouldBe null
            client.patchMe("ana", UpdateMe()).status shouldBe HttpStatusCode.OK
        }
    }

    test("sin sesión previa, GET /v1/me responde 404 y no crea el usuario") {
        val repo = InMemoryIdentityRepository()
        testApplication {
            val response = startApp(testContainer(repo = repo)).get("/v1/me") { auth("nadie") }
            response.status shouldBe HttpStatusCode.NotFound
            repo.users.size shouldBe 0
        }
    }

    test("sin aceptar términos las rutas protegidas responden 403 TERMS_REQUIRED; las exentas funcionan") {
        val repo = InMemoryIdentityRepository()
        testApplication {
            val client = startApp(testContainer(repo = repo))
            client.session("lucia")
            // /v1/twin/memories es un stub de otro dominio (E9.x): la puerta actúa antes que él.
            val blocked = client.get("/v1/twin/memories") { auth("lucia") }
            blocked.status shouldBe HttpStatusCode.Forbidden
            blocked.error() shouldBe ErrorCode.TERMS_REQUIRED
            client.get("/v1/me/consents") { auth("lucia") }.error() shouldBe ErrorCode.TERMS_REQUIRED
            client.patchMe("lucia", UpdateMe(displayName = "Lu")).error() shouldBe ErrorCode.TERMS_REQUIRED
            client.get("/v1/twin") { auth("lucia") }.error() shouldBe ErrorCode.TERMS_REQUIRED
            // Exentas: sesión, GET /me, DELETE /me y el propio POST terms.
            client.get("/v1/me") { auth("lucia") }.status shouldBe HttpStatusCode.OK
            client.acceptTerms("lucia").status shouldBe HttpStatusCode.OK
            client.get("/v1/twin/memories") { auth("lucia") }.status shouldBe HttpStatusCode.NotImplemented
            // DELETE /me (E8.3) borra de verdad: se prueba con otra cuenta que tampoco aceptó los términos.
            client.session("nico")
            client.delete("/v1/me") { auth("nico") }.status shouldBe HttpStatusCode.Accepted
        }
    }

    test("POST /v1/me/terms agrega TERMS y PRIVACY con la versión vigente y repetirlo no duplica filas") {
        val repo = InMemoryIdentityRepository()
        testApplication {
            val client = startApp(testContainer(repo = repo))
            val me = client.session("lucia").me
            val userId = repo.user("lucia").id
            val dto = client.acceptTerms("lucia").body<ConsentsDto>()
            dto.items.map { it.kind } shouldContainExactly listOf(ConsentKind.TERMS, ConsentKind.PRIVACY)
            dto.items.all { it.granted && it.textVersion == TEST_LEGAL_VERSION } shouldBe true
            repo.consentRows(userId) shouldBe 2
            client.acceptTerms("lucia")
            repo.consentRows(userId) shouldBe 2
            me.id shouldBe userId.toString()
        }
    }

    test("aceptar una versión que no es la vigente responde 400 VALIDATION") {
        testApplication {
            val client = startApp(testContainer(repo = InMemoryIdentityRepository()))
            client.session("lucia")
            val response = client.acceptTerms("lucia", "2020-01-01")
            response.status shouldBe HttpStatusCode.BadRequest
            response.error() shouldBe ErrorCode.VALIDATION
        }
    }

    test("subir LEGAL_TEXT_VERSION devuelve NEEDS_TERMS y vuelve a bloquear las rutas") {
        val repo = InMemoryIdentityRepository()
        testApplication {
            startApp(testContainer(repo = repo)).apply {
                session("lucia")
                acceptTerms("lucia")
                get("/v1/twin/memories") { auth("lucia") }.status shouldBe HttpStatusCode.NotImplemented
            }
        }
        testApplication {
            val client = startApp(testContainer(repo = repo, legalTextVersion = "2026-12-01"))
            client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_TERMS
            client.get("/v1/twin/memories") { auth("lucia") }.error() shouldBe ErrorCode.TERMS_REQUIRED
            client.acceptTerms("lucia", "2026-12-01").status shouldBe HttpStatusCode.OK
            // Quedan 4 filas: las 2 viejas se conservan y se agregan 2 nuevas con la versión nueva.
            repo.consentRows(repo.user("lucia").id) shouldBe 4
            client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_PROFILE
        }
    }

    test("onboarding recorre NEEDS_TERMS, NEEDS_PROFILE, NEEDS_TWIN, NEEDS_MODES y DONE") {
        val repo = InMemoryIdentityRepository()
        testApplication {
            val client = startApp(testContainer(repo = repo))
            client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_TERMS
            client.acceptTerms("lucia")
            client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_PROFILE

            // La edad la fija E2.6 (POST /me/age-verification); aquí se simula su efecto en el repositorio.
            val user = repo.user("lucia")
            repo.replace(user.copy(birthDate = LocalDate.parse("2003-04-05"), ageVerifiedAt = Instant.parse("2026-10-25T12:00:00Z")))
            // Sin universidad B0 no está completo (E2.6).
            client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_PROFILE
            repo.replace(repo.user("lucia").copy(universityId = 1))
            client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_TWIN

            repo.twins += user.id
            client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_MODES

            repo.enabledModes[user.id] = mutableSetOf(Mode.AMISTAD)
            val done = client.session("lucia")
            done.onboarding shouldBe OnboardingStep.DONE
            done.me.modes[Mode.AMISTAD] shouldBe true
            done.me.modes[Mode.PAREJA] shouldBe false
            done.me.ageVerified shouldBe true
        }
    }

    test("revocar un consentimiento inserta una fila nueva con granted=false y no toca las anteriores") {
        val repo = InMemoryIdentityRepository()
        testApplication {
            val client = startApp(testContainer(repo = repo))
            client.session("lucia")
            client.acceptTerms("lucia")
            val userId = repo.user("lucia").id
            val grant = ConsentChange(ConsentKind.CHAT_LEARNING_AMISTAD, true, TEST_LEGAL_VERSION)
            client.consent("lucia", grant).status shouldBe HttpStatusCode.OK
            val before = repo.consentRows(userId)
            val snapshot = repo.consents.map { it.second }

            val revoked = client.consent("lucia", grant.copy(granted = false)).body<ConsentsDto>()
            repo.consentRows(userId) shouldBe before + 1
            repo.consents.map { it.second }.take(before) shouldContainExactly snapshot
            revoked.items.single { it.kind == ConsentKind.CHAT_LEARNING_AMISTAD }.granted shouldBe false

            // Repetir el mismo estado no agrega filas; volver a conceder sí.
            client.consent("lucia", grant.copy(granted = false))
            repo.consentRows(userId) shouldBe before + 1
            client.consent("lucia", grant)
            repo.consentRows(userId) shouldBe before + 2
            client.get("/v1/me/consents") { auth("lucia") }.body<ConsentsDto>()
                .items.single { it.kind == ConsentKind.CHAT_LEARNING_AMISTAD }.granted shouldBe true
        }
    }

    test("POST /me/consent rechaza TERMS y PRIVACY, versiones viejas al conceder y textVersion vacío") {
        testApplication {
            val client = startApp()
            client.consent("ana", ConsentChange(ConsentKind.TERMS, true, TEST_LEGAL_VERSION)).status shouldBe HttpStatusCode.BadRequest
            client.consent("ana", ConsentChange(ConsentKind.CHAT_LEARNING_PAREJA, true, "vieja")).status shouldBe HttpStatusCode.BadRequest
            client.consent("ana", ConsentChange(ConsentKind.CHAT_LEARNING_PAREJA, false, "")).status shouldBe HttpStatusCode.BadRequest
            // Revocar con una versión anterior sí se acepta: el usuario retira lo que dio.
            client.consent("ana", ConsentChange(ConsentKind.CHAT_LEARNING_PAREJA, false, "vieja")).status shouldBe HttpStatusCode.OK
        }
    }

    test("un usuario suspendido recibe 403 SUSPENDED en las rutas protegidas pero puede ver su cuenta") {
        val repo = InMemoryIdentityRepository().also { it.seedAcceptedUser("ana", TEST_LEGAL_VERSION) }
        repo.replace(repo.user("ana").copy(suspendedUntil = Instant.parse("2026-11-30T00:00:00Z")))
        testApplication {
            val client = startApp(testContainer(repo = repo))
            client.get("/v1/twin/memories") { auth("ana") }.error() shouldBe ErrorCode.SUSPENDED
            client.get("/v1/me") { auth("ana") }.body<MeDto>().suspendedUntil?.toString() shouldBe "2026-11-30T00:00:00Z"
        }
    }

    test("una cuenta eliminada recibe 401") {
        val repo = InMemoryIdentityRepository().also { it.seedAcceptedUser("ana", TEST_LEGAL_VERSION) }
        repo.replace(repo.user("ana").copy(deletedAt = Instant.parse("2026-10-20T00:00:00Z")))
        testApplication {
            val client = startApp(testContainer(repo = repo))
            client.post("/v1/session") { auth("ana") }.status shouldBe HttpStatusCode.Unauthorized
            client.get("/v1/me") { auth("ana") }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("tokens FCM: registrar, mover entre usuarios, borrar solo el propio; 204 y validación") {
        val repo = InMemoryIdentityRepository().also {
            it.seedAcceptedUser("ana", TEST_LEGAL_VERSION)
            it.seedAcceptedUser("beto", TEST_LEGAL_VERSION)
        }
        testApplication {
            val client = startApp(testContainer(repo = repo))
            suspend fun register(uid: String, token: String) = client.post("/v1/me/fcm-token") {
                auth(uid); contentType(ContentType.Application.Json); setBody(FcmToken(token))
            }
            suspend fun remove(uid: String, token: String) = client.delete("/v1/me/fcm-token") {
                auth(uid); contentType(ContentType.Application.Json); setBody(FcmToken(token))
            }
            register("ana", "tok-1").status shouldBe HttpStatusCode.NoContent
            repo.fcmTokens["tok-1"] shouldBe repo.user("ana").id
            register("beto", "tok-1").status shouldBe HttpStatusCode.NoContent
            repo.fcmTokens["tok-1"] shouldBe repo.user("beto").id
            remove("ana", "tok-1").status shouldBe HttpStatusCode.NoContent
            repo.fcmTokens.containsKey("tok-1") shouldBe true
            remove("beto", "tok-1").status shouldBe HttpStatusCode.NoContent
            repo.fcmTokens.containsKey("tok-1") shouldBe false
            register("ana", "  ").status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("el cuerpo mal formado responde 400 VALIDATION") {
        testApplication {
            val response = startApp().post("/v1/me/terms") {
                auth("ana"); contentType(ContentType.Application.Json); setBody("{no es json")
            }
            response.status shouldBe HttpStatusCode.BadRequest
            response.error() shouldBe ErrorCode.VALIDATION
        }
    }
})
