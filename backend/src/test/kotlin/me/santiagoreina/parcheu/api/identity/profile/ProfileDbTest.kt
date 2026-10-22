package me.santiagoreina.parcheu.api.identity.profile

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.platform.allDomains
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testConfig
import me.santiagoreina.parcheu.contract.AcceptTerms
import me.santiagoreina.parcheu.contract.AgeVerification
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.CatalogDto
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.IsoDate
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** E2.6 contra Postgres real: catálogo sembrado, fecha inmutable y `underage_at`. Necesita Docker o `PARCHEU_TEST_DB_URL`. */
class ProfileDbTest : FunSpec({
    val clock = Clock.fixed(Instant.parse("2026-10-24T04:30:00Z"), ZoneOffset.UTC)

    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    suspend fun HttpClient.verifyAge(uid: String, date: String): HttpResponse = post("/v1/me/age-verification") {
        auth(uid); contentType(ContentType.Application.Json); setBody(AgeVerification(IsoDate(date)))
    }

    test("catálogo con UNIPAZ y 'Otra' al final; fecha inmutable; menor queda con underage_at")
        .config(enabled = TestDatabase.enabled) {
            TestDatabase.withCleanDatabase { ds ->
                runMigrations(ds)
                val db = connectExposed(ds)
                val identity = IdentityService(ExposedIdentityRepository(db), clock, TEST_LEGAL_VERSION)
                val profile = ProfileService(identity, ExposedProfileRepository(db), clock)
                val container = AppContainer(
                    testConfig(), clock, FakeTokenVerifier(), FakeOidcVerifier("sa-tasks@parcheu-test.iam.gserviceaccount.com"),
                    identity, profile, allDomains(identity, profile),
                )
                testApplication {
                    val client = startApp(container)
                    listOf("lucia", "nino").forEach { uid ->
                        client.post("/v1/session") { auth(uid) }
                        client.post("/v1/me/terms") { auth(uid); contentType(ContentType.Application.Json); setBody(AcceptTerms(TEST_LEGAL_VERSION)) }
                    }

                    val catalog = client.get("/v1/catalog") { auth("lucia") }.body<CatalogDto>()
                    catalog.universities.map { it.shortName } shouldContain "UNIPAZ"
                    catalog.universities.last().shortName shouldBe "Otra"
                    catalog.interests.map { it.slug } shouldContain "musica"

                    client.verifyAge("lucia", "2008-10-23").status.value shouldBe 200
                    client.verifyAge("lucia", "2001-01-01").body<ApiError>().code shouldBe ErrorCode.CONFLICT

                    client.verifyAge("nino", "2008-10-24").body<ApiError>().code shouldBe ErrorCode.UNDERAGE
                    transaction(db) {
                        val row = Users.selectAll().where { Users.firebaseUid eq "nino" }.single()
                        (row[Users.underageAt] != null) shouldBe true
                        row[Users.ageVerifiedAt] shouldBe null
                        row[Users.birthDate] shouldBe LocalDate.parse("2008-10-24")
                    }
                    client.post("/v1/session") { auth("nino") }.body<ApiError>().code shouldBe ErrorCode.UNDERAGE
                }

                // El UPDATE condicionado deja una sola fecha aunque lleguen varias a la vez.
                val repo = ExposedProfileRepository(db)
                val userId: UUID = transaction(db) { Users.selectAll().where { Users.firebaseUid eq "lucia" }.single()[Users.id] }
                transaction(db) {
                    Users.update({ Users.id eq userId }) { it[Users.birthDate] = null }
                }
                val results = runBlocking {
                    (1..6).map { i ->
                        async(Dispatchers.Default) {
                            repo.setBirthDate(userId, LocalDate.of(2000, 1, i), clock.instant(), null, clock.instant())
                        }
                    }.awaitAll()
                }
                results.count { it } shouldBe 1
            }
        }
})
