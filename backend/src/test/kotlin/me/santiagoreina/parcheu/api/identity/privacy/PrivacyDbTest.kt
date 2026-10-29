package me.santiagoreina.parcheu.api.identity.privacy

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TASKS_ACCOUNT
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.TEST_OIDC_TOKEN
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.identity.IdentityService
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
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testConfig
import me.santiagoreina.parcheu.api.twin.inMemoryTwinModule
import me.santiagoreina.parcheu.contract.AcceptTerms
import me.santiagoreina.parcheu.contract.ConsentChange
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PrivacyDto
import me.santiagoreina.parcheu.contract.SessionDto
import me.santiagoreina.parcheu.contract.UpdatePrivacy
import me.santiagoreina.parcheu.contract.Visibility
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Criterios de E8.2 contra Postgres real: fila en `consents`, borrado de memorias PENDING y `users.visibility`. */
class PrivacyDbTest : FunSpec({
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
            oidcVerifier = FakeOidcVerifier(TASKS_ACCOUNT, TEST_OIDC_TOKEN),
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

    suspend fun HttpClient.consent(uid: String, kind: ConsentKind, granted: Boolean) =
        post("/v1/me/consent") {
            auth(uid); contentType(ContentType.Application.Json)
            setBody(ConsentChange(kind, granted, TEST_LEGAL_VERSION))
        }

    fun withApp(block: suspend (HttpClient, Database) -> Unit) {
        TestDatabase.withCleanDatabase { ds ->
            runMigrations(ds)
            val db = connectExposed(ds)
            testApplication { block(startApp(container(db)), db) }
        }
    }

    fun insertMemory(db: Database, userId: UUID, mode: Mode, status: MemoryStatus) = transaction(db) {
        TwinMemories.insert {
            it[TwinMemories.userId] = userId
            it[TwinMemories.mode] = mode
            it[text] = "Le gusta el café"
            it[TwinMemories.status] = status
            it[createdAt] = OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC)
        }
    }

    fun memories(db: Database, userId: UUID) = transaction(db) {
        TwinMemories.selectAll().where { TwinMemories.userId eq userId }.map { it[TwinMemories.mode] to it[TwinMemories.status] }
    }

    test("PATCH visibility persiste en users.visibility y GET lo devuelve")
        .config(enabled = TestDatabase.enabled) {
            withApp { client, db ->
                val userId = client.onboard("lucia")
                client.get("/v1/me/privacy") { auth("lucia") }.body<PrivacyDto>().visibility shouldBe Visibility.ANONYMOUS
                client.patch("/v1/me/privacy") {
                    auth("lucia"); contentType(ContentType.Application.Json)
                    setBody(UpdatePrivacy(visibility = Visibility.HIDDEN))
                }.status shouldBe HttpStatusCode.OK
                transaction(db) { Users.selectAll().where { Users.id eq userId }.single()[Users.visibility] } shouldBe Visibility.HIDDEN
                client.get("/v1/me/privacy") { auth("lucia") }.body<PrivacyDto>().visibility shouldBe Visibility.HIDDEN
            }
        }

    test("apagar el aprendizaje inserta una fila de revocación y borra solo las memorias PENDING de ese modo")
        .config(enabled = TestDatabase.enabled) {
            withApp { client, db ->
                val userId = client.onboard("lucia")
                client.consent("lucia", ConsentKind.CHAT_LEARNING_AMISTAD, true).status shouldBe HttpStatusCode.OK
                client.consent("lucia", ConsentKind.CHAT_LEARNING_ESTUDIO, true).status shouldBe HttpStatusCode.OK
                insertMemory(db, userId, Mode.AMISTAD, MemoryStatus.PENDING)
                insertMemory(db, userId, Mode.AMISTAD, MemoryStatus.ACCEPTED)
                insertMemory(db, userId, Mode.ESTUDIO, MemoryStatus.PENDING)

                val dto = client.patch("/v1/me/privacy") {
                    auth("lucia"); contentType(ContentType.Application.Json)
                    setBody(UpdatePrivacy(learning = mapOf(Mode.AMISTAD to false)))
                }.body<PrivacyDto>()
                dto.learning shouldBe mapOf(Mode.AMISTAD to false, Mode.PAREJA to false, Mode.ESTUDIO to true)

                val history = transaction(db) {
                    Consents.selectAll()
                        .where { (Consents.userId eq userId) and (Consents.kind eq ConsentKind.CHAT_LEARNING_AMISTAD) }
                        .orderBy(Consents.id)
                        .map { it[Consents.granted] }
                }
                history shouldContainExactly listOf(true, false)
                memories(db, userId) shouldContainExactly listOf(
                    Mode.AMISTAD to MemoryStatus.ACCEPTED,
                    Mode.ESTUDIO to MemoryStatus.PENDING,
                )
            }
        }
})
