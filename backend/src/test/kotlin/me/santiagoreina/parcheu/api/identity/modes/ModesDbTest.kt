package me.santiagoreina.parcheu.api.identity.modes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
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
import me.santiagoreina.parcheu.api.TASKS_ACCOUNT
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.identity.consent.LearningConsentPolicy
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
import me.santiagoreina.parcheu.api.platform.db.tables.TwinMemories
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testConfig
import me.santiagoreina.parcheu.api.twin.inMemoryTwinModule
import me.santiagoreina.parcheu.contract.AcceptTerms
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ConsentChange
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SessionDto
import me.santiagoreina.parcheu.contract.UpdateModes
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Criterios de servidor de E2.5 contra Postgres real (upsert de `user_modes`, `pareja_confirmed_at`
 * y filas de `consents`). Necesita Docker o `PARCHEU_TEST_DB_URL`.
 */
class ModesDbTest : FunSpec({
    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    fun container(db: Database): AppContainer {
        val identity = IdentityService(ExposedIdentityRepository(db), FIXED_CLOCK, TEST_LEGAL_VERSION)
        val profile = ProfileService(identity, ExposedProfileRepository(db), FIXED_CLOCK)
        val quota = inMemoryQuotaModule(FIXED_CLOCK)
        val twin = inMemoryTwinModule(FIXED_CLOCK, testGateway())
        return AppContainer(
            config = testConfig(),
            clock = FIXED_CLOCK,
            tokenVerifier = FakeTokenVerifier(),
            oidcVerifier = FakeOidcVerifier(TASKS_ACCOUNT),
            identity = identity,
            profile = profile,
            quota = quota,
            twin = twin,
            domains = allDomains(identity, profile, quota, twin),
        )
    }

    suspend fun HttpClient.onboard(uid: String): UUID {
        val id = post("/v1/session") { auth(uid) }.body<SessionDto>().me.id
        post("/v1/me/terms") { auth(uid); contentType(ContentType.Application.Json); setBody(AcceptTerms(TEST_LEGAL_VERSION)) }
        return UUID.fromString(id)
    }

    suspend fun HttpClient.putModes(uid: String, body: UpdateModes): HttpResponse = put("/v1/me/modes") {
        auth(uid); contentType(ContentType.Application.Json); setBody(body)
    }

    fun verifyAge(db: Database, userId: UUID) = transaction(db) {
        Users.update({ Users.id eq userId }) {
            it[birthDate] = LocalDate.of(2005, 3, 14)
            it[ageVerifiedAt] = OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC)
        }
    }

    fun withApp(block: suspend (HttpClient, Database) -> Unit) {
        TestDatabase.withCleanDatabase { ds ->
            runMigrations(ds)
            val db = connectExposed(ds)
            testApplication { block(startApp(container(db)), db) }
        }
    }

    val pareja = mapOf(Mode.PAREJA to true)

    test("Pareja: sin edad 403 AGE_REQUIRED, sin confirmación 400 VALIDATION, con ambas OK y pareja_confirmed_at fijado")
        .config(enabled = TestDatabase.enabled) {
            withApp { client, db ->
                val userId = client.onboard("lucia")
                val noAge = client.putModes("lucia", UpdateModes(pareja, parejaConfirmed = true))
                noAge.status shouldBe HttpStatusCode.Forbidden
                noAge.body<ApiError>().code shouldBe ErrorCode.AGE_REQUIRED

                verifyAge(db, userId)
                val noConfirm = client.putModes("lucia", UpdateModes(pareja, parejaConfirmed = false))
                noConfirm.status shouldBe HttpStatusCode.BadRequest
                noConfirm.body<ApiError>().code shouldBe ErrorCode.VALIDATION
                transaction(db) { UserModes.selectAll().where { UserModes.userId eq userId }.count() } shouldBe 0L

                val ok = client.putModes("lucia", UpdateModes(pareja + (Mode.ESTUDIO to true), parejaConfirmed = true))
                ok.status shouldBe HttpStatusCode.OK
                ok.body<MeDto>().modes shouldBe mapOf(Mode.AMISTAD to false, Mode.PAREJA to true, Mode.ESTUDIO to true)
                transaction(db) {
                    Users.selectAll().where { Users.id eq userId }.single()[Users.parejaConfirmedAt].shouldNotBeNull()
                    val rows = UserModes.selectAll().where { UserModes.userId eq userId }.associateBy { it[UserModes.mode] }
                    rows.size shouldBe 3
                    rows.getValue(Mode.PAREJA)[UserModes.enabledAt].shouldNotBeNull()
                    rows.getValue(Mode.AMISTAD)[UserModes.enabledAt].shouldBeNull()
                }

                // Apagar Pareja borra enabled_at y una segunda escritura no duplica filas (upsert).
                client.putModes("lucia", UpdateModes(mapOf(Mode.ESTUDIO to true))).status shouldBe HttpStatusCode.OK
                transaction(db) {
                    UserModes.selectAll().where { UserModes.userId eq userId }.count() shouldBe 3L
                    UserModes.selectAll().where { (UserModes.userId eq userId) and (UserModes.mode eq Mode.PAREJA) }
                        .single()[UserModes.enabledAt].shouldBeNull()
                }
            }
        }

    test("B4 'Acepto' inserta CHAT_LEARNING_AMISTAD y CHAT_LEARNING_ESTUDIO con granted=true y ninguna de Pareja")
        .config(enabled = TestDatabase.enabled) {
            withApp { client, db ->
                val userId = client.onboard("lucia")
                LearningConsentPolicy.ONBOARDING.forEach { kind ->
                    client.post("/v1/me/consent") {
                        auth("lucia"); contentType(ContentType.Application.Json)
                        setBody(ConsentChange(kind, granted = true, textVersion = TEST_LEGAL_VERSION))
                    }.status shouldBe HttpStatusCode.OK
                }
                val learning = transaction(db) {
                    Consents.selectAll().where { Consents.userId eq userId }
                        .map { it[Consents.kind] to it[Consents.granted] }
                        .filterNot { it.first == ConsentKind.TERMS || it.first == ConsentKind.PRIVACY }
                }
                learning shouldContainExactly listOf(
                    ConsentKind.CHAT_LEARNING_AMISTAD to true,
                    ConsentKind.CHAT_LEARNING_ESTUDIO to true,
                )
            }
        }

    test("apagar Pareja agrega la revocación de CHAT_LEARNING_PAREJA en la misma transacción")
        .config(enabled = TestDatabase.enabled) {
            withApp { client, db ->
                val userId = client.onboard("lucia")
                verifyAge(db, userId)
                client.putModes("lucia", UpdateModes(pareja + (Mode.AMISTAD to true), parejaConfirmed = true)).status shouldBe HttpStatusCode.OK
                client.post("/v1/me/consent") {
                    auth("lucia"); contentType(ContentType.Application.Json)
                    setBody(ConsentChange(ConsentKind.CHAT_LEARNING_PAREJA, granted = true, textVersion = TEST_LEGAL_VERSION))
                }.status shouldBe HttpStatusCode.OK

                client.putModes("lucia", UpdateModes(mapOf(Mode.AMISTAD to true))).status shouldBe HttpStatusCode.OK
                val history = transaction(db) {
                    Consents.selectAll().where { (Consents.userId eq userId) and (Consents.kind eq ConsentKind.CHAT_LEARNING_PAREJA) }
                        .orderBy(Consents.id)
                        .map { it[Consents.granted] to it[Consents.textVersion] }
                }
                history shouldContainExactly listOf(true to TEST_LEGAL_VERSION, false to TEST_LEGAL_VERSION)
            }
        }

    test("apagar Pareja borra solo las memorias PENDING de Pareja en la misma transacción")
        .config(enabled = TestDatabase.enabled) {
            withApp { client, db ->
                val userId = client.onboard("lucia")
                verifyAge(db, userId)
                client.putModes("lucia", UpdateModes(pareja + (Mode.AMISTAD to true), parejaConfirmed = true)).status shouldBe HttpStatusCode.OK
                fun insertMemory(mode: Mode, status: MemoryStatus) = transaction(db) {
                    TwinMemories.insert {
                        it[TwinMemories.userId] = userId
                        it[TwinMemories.mode] = mode
                        it[text] = "Le gusta el café"
                        it[TwinMemories.status] = status
                        it[createdAt] = java.time.OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), java.time.ZoneOffset.UTC)
                    }
                }
                insertMemory(Mode.PAREJA, MemoryStatus.PENDING)
                insertMemory(Mode.PAREJA, MemoryStatus.ACCEPTED)
                insertMemory(Mode.AMISTAD, MemoryStatus.PENDING)

                client.putModes("lucia", UpdateModes(mapOf(Mode.AMISTAD to true))).status shouldBe HttpStatusCode.OK
                val left = transaction(db) {
                    TwinMemories.selectAll().where { TwinMemories.userId eq userId }
                        .map { it[TwinMemories.mode] to it[TwinMemories.status] }
                }
                left.toSet() shouldBe setOf(Mode.PAREJA to MemoryStatus.ACCEPTED, Mode.AMISTAD to MemoryStatus.PENDING)
            }
        }
})
