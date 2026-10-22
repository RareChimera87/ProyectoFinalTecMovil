package me.santiagoreina.parcheu.api.identity

import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.simulation.inMemorySimulationModule
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.api.twin.inMemoryTwinModule
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.inMemoryDomains
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.identity.profile.ExposedProfileRepository
import me.santiagoreina.parcheu.api.identity.profile.ProfileService
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.platform.allDomains
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Consents
import me.santiagoreina.parcheu.api.platform.db.tables.FcmTokens
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testConfig
import me.santiagoreina.parcheu.contract.AcceptTerms
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ConsentChange
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.FcmToken
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.OnboardingStep
import me.santiagoreina.parcheu.contract.SessionDto
import me.santiagoreina.parcheu.contract.UpdateMe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Criterios de aceptación de E2.1 contra Postgres real y el repositorio Exposed (triggers de
 * `consents`, `ON CONFLICT` del alta, catálogos sembrados). Necesita Docker o `PARCHEU_TEST_DB_URL`.
 */
class IdentityDbTest : FunSpec({
    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    fun container(db: Database, version: String): AppContainer {
        val identity = IdentityService(ExposedIdentityRepository(db), FIXED_CLOCK, version)
        val profile = ProfileService(identity, ExposedProfileRepository(db), FIXED_CLOCK)
        return AppContainer(testConfig(), FIXED_CLOCK, FakeTokenVerifier(), FakeOidcVerifier("sa-tasks@parcheu-test.iam.gserviceaccount.com"), identity, profile, domains = inMemoryDomains(identity, profile))
    }

    suspend fun HttpClient.session(uid: String): SessionDto = post("/v1/session") { auth(uid) }.body()

    suspend fun HttpClient.terms(uid: String, version: String): HttpResponse = post("/v1/me/terms") {
        auth(uid); contentType(ContentType.Application.Json); setBody(AcceptTerms(version))
    }

    fun consentCount(db: Database) = transaction(db) { Consents.selectAll().count() }

    fun withApp(version: String = TEST_LEGAL_VERSION, block: suspend ApplicationTestBuilder.(HttpClient, Database) -> Unit) {
        TestDatabase.withCleanDatabase { ds ->
            runMigrations(ds)
            val db = connectExposed(ds)
            testApplication { block(startApp(container(db, version)), db) }
        }
    }

    test("dos POST /v1/session con el mismo token crean un solo usuario (y el alta concurrente también)")
        .config(enabled = TestDatabase.enabled) {
            withApp { client, db ->
                val first = client.session("lucia")
                val second = client.session("lucia")
                second.me.id shouldBe first.me.id
                first.me.displayName shouldBe "lucia"
                transaction(db) { Users.selectAll().count() } shouldBe 1L

                // Dos altas simultáneas del mismo uid: ON CONFLICT DO NOTHING deja una sola fila.
                val repo = ExposedIdentityRepository(db)
                coroutineScope {
                    (1..8).map { async(Dispatchers.Default) { repo.createIfAbsent("carrera", null, null, FIXED_CLOCK.instant()) } }
                        .awaitAll().map { it.id }.toSet().size shouldBe 1
                }
                transaction(db) { Users.selectAll().where { Users.firebaseUid eq "carrera" }.count() } shouldBe 1L
            }
        }

    test("PATCH /me guarda y valida contra el catálogo sembrado; más de 40 caracteres es 400 VALIDATION")
        .config(enabled = TestDatabase.enabled) {
            withApp { client, _ ->
                client.session("lucia"); client.terms("lucia", TEST_LEGAL_VERSION)
                suspend fun patch(body: UpdateMe) = client.patch("/v1/me") {
                    auth("lucia"); contentType(ContentType.Application.Json); setBody(body)
                }
                val long = patch(UpdateMe(displayName = "a".repeat(41)))
                long.status shouldBe HttpStatusCode.BadRequest
                long.body<ApiError>().code shouldBe ErrorCode.VALIDATION
                patch(UpdateMe(universityId = 1234)).status shouldBe HttpStatusCode.BadRequest
                patch(UpdateMe(interests = listOf("no-existe"))).status shouldBe HttpStatusCode.BadRequest
                val me = patch(UpdateMe(displayName = "Lucía", career = "Ing. Industrial", universityId = 1, interests = listOf("musica", "cine"))).body<MeDto>()
                me.universityId shouldBe 1
                me.interests shouldContainExactly listOf("musica", "cine")
                client.get("/v1/me") { auth("lucia") }.body<MeDto>() shouldBe me
            }
        }

    test("terms agrega dos filas con LEGAL_TEXT_VERSION, bloquea twin/answers antes y subir la versión devuelve NEEDS_TERMS")
        .config(enabled = TestDatabase.enabled) {
            TestDatabase.withCleanDatabase { ds ->
                runMigrations(ds)
                val db = connectExposed(ds)
                testApplication {
                    val client = startApp(container(db, TEST_LEGAL_VERSION))
                    client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_TERMS
                    val blocked = client.post("/v1/chats/x/read") { auth("lucia") }
                    blocked.status shouldBe HttpStatusCode.Forbidden
                    blocked.body<ApiError>().code shouldBe ErrorCode.TERMS_REQUIRED
                    consentCount(db) shouldBe 0L

                    client.terms("lucia", TEST_LEGAL_VERSION).status shouldBe HttpStatusCode.OK
                    client.terms("lucia", TEST_LEGAL_VERSION).status shouldBe HttpStatusCode.OK
                    val rows = transaction(db) { Consents.selectAll().map { it[Consents.kind] to it[Consents.textVersion] } }
                    rows shouldContainExactlyInAnyOrder listOf(
                        ConsentKind.TERMS to TEST_LEGAL_VERSION,
                        ConsentKind.PRIVACY to TEST_LEGAL_VERSION,
                    )
                    client.post("/v1/chats/x/read") { auth("lucia") }.status shouldBe HttpStatusCode.NotImplemented
                }
                // Se sube la versión: el mismo usuario vuelve a NEEDS_TERMS y a 403 TERMS_REQUIRED.
                testApplication {
                    val client = startApp(container(db, "2026-12-01"))
                    client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_TERMS
                    client.post("/v1/chats/x/read") { auth("lucia") }.body<ApiError>().code shouldBe ErrorCode.TERMS_REQUIRED
                    client.terms("lucia", "2026-12-01").status shouldBe HttpStatusCode.OK
                    consentCount(db) shouldBe 4L
                    client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_PROFILE
                }
            }
        }

    test("revocar un consentimiento inserta una fila nueva; consents nunca recibe UPDATE ni DELETE")
        .config(enabled = TestDatabase.enabled) {
            withApp { client, db ->
                client.session("lucia"); client.terms("lucia", TEST_LEGAL_VERSION)
                suspend fun change(granted: Boolean) = client.post("/v1/me/consent") {
                    auth("lucia"); contentType(ContentType.Application.Json)
                    setBody(ConsentChange(ConsentKind.CHAT_LEARNING_ESTUDIO, granted, TEST_LEGAL_VERSION))
                }
                change(true).status shouldBe HttpStatusCode.OK
                val antes = transaction(db) { Consents.selectAll().map { it[Consents.id] to it[Consents.granted] } }
                antes.size shouldBe 3

                change(false).status shouldBe HttpStatusCode.OK
                val despues = transaction(db) { Consents.selectAll().orderBy(Consents.id).map { it[Consents.id] to it[Consents.granted] } }
                despues.size shouldBe 4
                despues.take(3) shouldContainExactlyInAnyOrder antes
                despues.last().second shouldBe false

                // La base lo garantiza aunque el código lo intentara: el trigger rechaza UPDATE y DELETE.
                shouldThrow<Exception> { transaction(db) { Consents.update({ Consents.granted eq true }) { it[granted] = false } } }
                shouldThrow<Exception> { transaction(db) { exec("delete from consents") } }
                consentCount(db) shouldBe 4L
            }
        }

    test("onboarding devuelve cada estado según los datos reales")
        .config(enabled = TestDatabase.enabled) {
            withApp { client, db ->
                client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_TERMS
                client.terms("lucia", TEST_LEGAL_VERSION)
                client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_PROFILE

                val userId = UUID.fromString(client.session("lucia").me.id)
                val now = OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC)
                transaction(db) { Users.update({ Users.id eq userId }) { it[ageVerifiedAt] = now } }
                // Sin universidad B0 no está completo (E2.6).
                client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_PROFILE
                transaction(db) { Users.update({ Users.id eq userId }) { it[universityId] = 1 } }
                client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_TWIN

                transaction(db) { Twins.insert { it[Twins.userId] = userId; it[updatedAt] = now } }
                client.session("lucia").onboarding shouldBe OnboardingStep.NEEDS_MODES

                transaction(db) { UserModes.insert { it[UserModes.userId] = userId; it[mode] = Mode.ESTUDIO; it[enabled] = true; it[enabledAt] = now } }
                val done = client.session("lucia")
                done.onboarding shouldBe OnboardingStep.DONE
                done.me.modes[Mode.ESTUDIO] shouldBe true
                done.me.modes[Mode.AMISTAD] shouldBe false
            }
        }

    test("tokens FCM: upsert, reasignación entre usuarios y borrado del propio")
        .config(enabled = TestDatabase.enabled) {
            withApp { client, db ->
                for (u in listOf("ana", "beto")) { client.session(u); client.terms(u, TEST_LEGAL_VERSION) }
                suspend fun post(u: String, t: String) = client.post("/v1/me/fcm-token") {
                    auth(u); contentType(ContentType.Application.Json); setBody(FcmToken(t))
                }
                suspend fun del(u: String, t: String) = client.delete("/v1/me/fcm-token") {
                    auth(u); contentType(ContentType.Application.Json); setBody(FcmToken(t))
                }
                fun owner(t: String) = transaction(db) { FcmTokens.selectAll().where { FcmTokens.token eq t }.singleOrNull()?.get(FcmTokens.userId) }
                post("ana", "tok").status shouldBe HttpStatusCode.NoContent
                post("ana", "tok").status shouldBe HttpStatusCode.NoContent
                val ana = owner("tok")
                post("beto", "tok").status shouldBe HttpStatusCode.NoContent
                (owner("tok") == ana) shouldBe false
                del("ana", "tok").status shouldBe HttpStatusCode.NoContent
                (owner("tok") != null) shouldBe true
                del("beto", "tok").status shouldBe HttpStatusCode.NoContent
                owner("tok") shouldBe null
            }
        }
})
