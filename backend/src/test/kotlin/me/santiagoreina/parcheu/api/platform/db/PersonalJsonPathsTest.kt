package me.santiagoreina.parcheu.api.platform.db

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import me.santiagoreina.parcheu.api.platform.db.tables.PersonalJsonPaths
import me.santiagoreina.parcheu.api.platform.db.tables.Schema

/** Test de arquitectura de ADR-0025: toda columna jsonb está clasificada, y una sola vez. */
class PersonalJsonPathsTest : FunSpec({
    test("cada columna jsonb está en personal o en nonPersonal") {
        val classified = PersonalJsonPaths.personal + PersonalJsonPaths.nonPersonal
        Schema.jsonbColumns().map { it.toString() } shouldContainExactlyInAnyOrder classified.map { it.toString() }
    }

    test("ninguna columna está en las dos listas") {
        PersonalJsonPaths.personal.intersect(PersonalJsonPaths.nonPersonal.toSet()).shouldBeEmpty()
    }
})
