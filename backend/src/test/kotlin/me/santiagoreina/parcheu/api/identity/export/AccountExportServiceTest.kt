package me.santiagoreina.parcheu.api.identity.export

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.identity.ConsentRecord
import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.twin.TwinState
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.contract.ExportDto
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import java.time.Duration
import java.util.UUID

/**
 * Criterio de E8.3: la exportación no incluye datos privados de otros usuarios, solo su `anonId`. El
 * repositorio de la exportación ni siquiera lee nombres ni mensajes ajenos (ver `ExportDbTest`); aquí
 * se comprueba que el servicio tampoco deja salir sus uuid internos.
 */
class AccountExportServiceTest : FunSpec({
    val signer = AnonIdSigner.forTests()
    val me = UUID.randomUUID()
    val laura = UUID.randomUUID()
    val pedro = UUID.randomUUID()
    val now = FIXED_CLOCK.instant()

    val rows = ExportRows(
        consents = listOf(
            ConsentRecord(1, ConsentKind.TERMS, true, TEST_LEGAL_VERSION, now),
            ConsentRecord(2, ConsentKind.CHAT_LEARNING_AMISTAD, true, TEST_LEGAL_VERSION, now),
            ConsentRecord(3, ConsentKind.CHAT_LEARNING_AMISTAD, false, TEST_LEGAL_VERSION, now),
        ),
        simulations = listOf(
            ExportSimulationRow(UUID.randomUUID(), SimulationStatus.DONE, Mode.AMISTAD, SimulationOrigin.PARCHE, laura, false, 82, now),
        ),
        connections = listOf(ExportConnectionRow(pedro, Mode.ESTUDIO, EdgeState.CONNECTED, now)),
        chats = listOf(
            ExportChatRow(UUID.randomUUID(), ChatType.DIRECT, Mode.ESTUDIO, listOf(ExportMessageRow(UUID.randomUUID(), "nos vemos a las 3", now))),
        ),
        blocks = listOf(ExportBlockRow(laura, now)),
        entitlement = ExportEntitlementRow(EntitlementState.CANCELED, "anual", now + Duration.ofDays(3), false),
    )

    fun service() = AccountExportService(
        IdentityService(InMemoryIdentityRepository(), FIXED_CLOCK, TEST_LEGAL_VERSION),
        { rows },
        signer,
        { null },
        FIXED_CLOCK,
    )

    val meDto = MeDto(id = me.toString(), displayName = "Ana", createdAt = kotlin.time.Instant.fromEpochSeconds(0))

    test("los terceros salen solo con su anonId: nunca con su uuid interno") {
        val dto = service().build(me, meDto, rows, null)
        val json = ContractJson.encodeToString(ExportDto.serializer(), dto)
        json shouldNotContain laura.toString()
        json shouldNotContain pedro.toString()
        val lauraAnon = signer.anonId(me, laura)
        dto.simulations.single().other.anonId shouldBe lauraAnon
        dto.simulations.single().other.id shouldBe null
        dto.simulations.single().other.photoUrl shouldBe null
        dto.blocks.single().userId shouldBe lauraAnon
        dto.connections.single().label shouldContain signer.anonId(me, pedro)
    }

    test("incluye perfil, historial de consentimientos, mensajes propios y suscripción") {
        val twin = TwinState(listOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6), personaReady = true, version = 2, level = 1, xp = 120, accuracy = 70)
        val dto = service().build(me, meDto, rows, twin)
        dto.me.id shouldBe me.toString()
        dto.consents.map { it.granted } shouldContainExactly listOf(true, true, false)
        dto.chats.single().messages.single().text shouldBe "nos vemos a las 3"
        dto.twin?.traits?.size shouldBe 6
        dto.entitlement?.plus shouldBe true
        dto.notifications shouldBe emptyList()
    }

    test("avisos que citan a terceros salen con texto neutro, sin ruta y con actor anónimo; las compras sin token") {
        val extra = rows.copy(
            notifications = listOf(
                ExportNotificationRow(UUID.randomUUID(), "CHAT_MESSAGE", "Laura", "texto-privado-de-laura", "parcheu://chat/x", laura, false, now, null),
                ExportNotificationRow(UUID.randomUUID(), "MATCH_EXPIRED", "Nadie por ahora", "Intenta de nuevo", "parcheu://match", null, false, now, now),
            ),
            purchases = listOf(ExportPurchaseRow(now, 4)),
            introductions = listOf(
                ExportIntroductionRow(UUID.randomUUID(), pedro, me, laura, Mode.AMISTAD, IntroductionStatus.DECLINED, "INTERESTED", now),
            ),
        )
        val svc = AccountExportService(
            IdentityService(InMemoryIdentityRepository(), FIXED_CLOCK, TEST_LEGAL_VERSION), { extra }, signer, { null }, FIXED_CLOCK,
        )
        val dto = svc.build(me, meDto, extra, null)
        val json = ContractJson.encodeToString(ExportDto.serializer(), dto)
        json shouldNotContain "texto-privado-de-laura"
        json shouldNotContain laura.toString()
        dto.notifications.first().actor?.anonId shouldBe signer.anonId(me, laura)
        dto.notifications.first().route shouldBe null
        dto.notifications.last().body shouldBe "Intenta de nuevo"
        dto.notifications.last().read shouldBe true
        dto.purchases.single().notificationType shouldBe 4
        dto.introductions.single().status shouldBe null
        dto.introductions.single().myResponse shouldBe "INTERESTED"
    }
})
