package me.santiagoreina.parcheu.api.graph

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import java.time.Instant

class GraphVisibilityPolicyTest : FunSpec({
    val policy = GraphVisibilityPolicy(AnonIdSigner.forTests())
    val viewer = person(0)
    val other = person(1, name = "Laura Pérez")

    test("anonId: estable para el mismo observador, distinto entre observadores y opaco") {
        val signer = AnonIdSigner.forTests()
        signer.anonId(viewer.id, other.id) shouldBe signer.anonId(viewer.id, other.id)
        signer.anonId(viewer.id, other.id) shouldNotBe signer.anonId(person(2).id, other.id)
        signer.anonId(viewer.id, other.id) shouldMatch Regex("a_[A-Za-z0-9_-]{22}")
        AnonIdSigner { "otra-clave".toByteArray() }.anonId(viewer.id, other.id) shouldNotBe signer.anonId(viewer.id, other.id)
    }

    test("nunca bloqueados ni borrados; HIDDEN solo se oculta en segundo grado") {
        policy.canSee(viewer.id, other, ViewContext.EGO, blocked = setOf(other.id)) shouldBe false
        policy.canSee(viewer.id, other.copy(deletedAt = Instant.EPOCH), ViewContext.EGO, emptySet()) shouldBe false
        val hidden = other.copy(visibility = Visibility.HIDDEN)
        policy.canSee(viewer.id, hidden, ViewContext.EGO, emptySet()) shouldBe true
        policy.canSee(viewer.id, hidden, ViewContext.THIRD_PARTY, emptySet()) shouldBe false
    }

    test("Pareja solo es visible para sus extremos") {
        val third = person(2)
        policy.canSeeEdge(viewer.id, viewer.id, other.id, Mode.PAREJA) shouldBe true
        policy.canSeeEdge(third.id, viewer.id, other.id, Mode.PAREJA) shouldBe false
        policy.canSeeEdge(third.id, viewer.id, other.id, Mode.AMISTAD) shouldBe true
    }

    test("nombre con conexión mutua; en segundo grado solo NAME muestra el nombre y nunca el uuid") {
        policy.present(viewer.id, other, connected = true, ViewContext.EGO).label shouldBe "Laura Pérez"
        policy.present(viewer.id, other, connected = false, ViewContext.EGO).label shouldBe "Alguien de la UIS"
        val named = policy.present(viewer.id, other.copy(visibility = Visibility.NAME), connected = false, ViewContext.THIRD_PARTY)
        named.label shouldBe "Laura Pérez"
        named.id shouldNotBe other.id.toString()
        policy.present(viewer.id, other.copy(universityShort = "Otra"), connected = false, ViewContext.THIRD_PARTY).label shouldBe "Alguien"
    }
})
