package me.santiagoreina.parcheu.api.privacy

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.ListSerializer
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.chat.inMemoryChatModule
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.safety.InMemorySafetyRepository
import me.santiagoreina.parcheu.api.safety.SafetyService
import me.santiagoreina.parcheu.contract.BlockedDto
import me.santiagoreina.parcheu.contract.ContractJson

/**
 * `GET /v1/blocks` y `DELETE /v1/blocks/{id}` (E8.1) con la regla del uuid de ADR-0012: bloquear
 * es gratis e ilimitado, así que si la lista de bloqueados devolviera el uuid interno, cualquiera
 * podría desanonimizar a una persona anónima bloqueando su `anonId` y leyendo la lista.
 */
class BlockListPrivacyPropertyTest : FunSpec({
    val signer = AnonIdSigner.forTests()

    /** El servicio de safety sobre el mundo: grafo en memoria y los bloqueos del mundo. */
    suspend fun serviceFor(world: PrivacyWorld): Pair<SafetyService, InMemorySafetyRepository> {
        val graphRepo = world.toGraphRepository()
        val repo = InMemorySafetyRepository(people = { id -> world.person(id) })
        world.blocks.forEach { (blocker, blocked) -> repo.block(blocker, blocked, FIXED_CLOCK.instant()) }
        val policy = GraphVisibilityPolicy(signer)
        val chat = inMemoryChatModule(FIXED_CLOCK, policy)
        val service = SafetyService(repo, chat.service, chat.repository, graphRepo, policy, signer, null, FIXED_CLOCK)
        return service to repo
    }

    test("ninguna respuesta de /v1/blocks contiene el uuid interno de una persona anónima para quien consulta") {
        val cases = PrivacyProperties.check("lista de bloqueados", PrivacyWorld.arb()) { world ->
            val (service, _) = serviceFor(world)
            world.observers().forEach { viewer ->
                val body = ContractJson.encodeToString(ListSerializer(BlockedDto.serializer()), service.blocks(viewer))
                val leaked = world.people
                    .filter { it.id != viewer && !world.connected(viewer, it.id) }
                    .map { it.id.toString() }
                    .filter { it in body }
                withClue("observador ${world.people.indexOfFirst { it.id == viewer }} en $world\n$body") {
                    leaked.shouldBeEmpty()
                }
            }
        }
        cases shouldBeGreaterThanOrEqual PrivacyProperties.ITERATIONS
    }

    test("DELETE /v1/blocks solo desbloquea con el id presentado; el uuid de una persona anónima no sirve") {
        val cases = PrivacyProperties.check("desbloqueo por id presentado", PrivacyWorld.arb()) { world ->
            val (service, repo) = serviceFor(world)
            world.observers().forEach { viewer ->
                val anonymousBlocked = repo.blocksOf(viewer).map { it.blocked.id }.filter { !world.connected(viewer, it) }
                val before = repo.allBlocks
                anonymousBlocked.forEach { service.unblock(viewer, it.toString()) }
                withClue("observador ${world.people.indexOfFirst { it.id == viewer }} en $world") {
                    repo.allBlocks shouldBe before
                }
                service.blocks(viewer).forEach { service.unblock(viewer, it.userId) }
                service.blocks(viewer).shouldBeEmpty()
                repo.allBlocks.filter { it.first == viewer }.map { it.second }
                    .filter { world.person(it)?.deletedAt == null }
                    .shouldBeEmpty()
            }
        }
        cases shouldBeGreaterThanOrEqual PrivacyProperties.ITERATIONS
    }
})
