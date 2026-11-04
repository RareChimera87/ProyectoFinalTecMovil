package me.santiagoreina.parcheu.api.platform.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import me.santiagoreina.parcheu.api.platform.db.tables.Edges
import java.util.UUID

/** `Edges.orderedPair` ordena como Postgres compara `uuid` (bytes sin signo). */
class EdgesOrderTest : FunSpec({
    test("ordena por bytes sin signo, no con UUID.compareTo") {
        val low = UUID.fromString("7fffffff-ffff-4fff-bfff-ffffffffffff")
        val high = UUID.fromString("80000000-0000-4000-8000-000000000000")
        // UUID.compareTo compara con signo y diría lo contrario.
        (high < low) shouldBe true
        Edges.orderedPair(high, low) shouldBe (low to high)
        Edges.orderedPair(low, high) shouldBe (low to high)
    }

    test("coincide con comparar las cadenas en hexadecimal (orden de Postgres)") {
        checkAll(Arb.long(), Arb.long(), Arb.long(), Arb.long()) { m1, l1, m2, l2 ->
            val a = UUID(m1, l1)
            val b = UUID(m2, l2)
            if (a != b) {
                val (first, second) = Edges.orderedPair(a, b)
                (first.toString() < second.toString()) shouldBe true
            }
        }
    }

    test("rechaza un par con el mismo usuario") {
        val a = UUID.randomUUID()
        shouldThrow<IllegalArgumentException> { Edges.orderedPair(a, a) }
    }
})
