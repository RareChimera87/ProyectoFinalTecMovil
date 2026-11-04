package me.santiagoreina.parcheu.api.identity.deletion

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import me.santiagoreina.parcheu.api.platform.db.tables.PersonalJsonPaths

/**
 * Test de arquitectura de E8.3 (ADR-0025): la purga sabe limpiar cada columna jsonb con datos
 * personales. Si alguien agrega una a [PersonalJsonPaths.personal] sin su regla, falla aquí; si la
 * agrega sin clasificar, falla `PersonalJsonPathsTest` (y `AccountPurgeDbTest` contra el catálogo).
 */
class JsonbPurgeRulesTest : FunSpec({
    test("hay exactamente una regla de purga por cada columna jsonb personal") {
        JsonbPurgeRules.rules.map { it.column.toString() } shouldContainExactlyInAnyOrder
            PersonalJsonPaths.personal.map { it.toString() }
    }

    test("ninguna regla toca una columna clasificada como no personal") {
        val nonPersonal = PersonalJsonPaths.nonPersonal.map { it.toString() }.toSet()
        JsonbPurgeRules.rules.forEach { rule -> (rule.column.toString() in nonPersonal) shouldBe false }
    }

    test("cada regla declara un parámetro por cada ? y actúa sobre la tabla de su columna") {
        JsonbPurgeRules.rules.forEach { rule ->
            withClue(rule.column.toString()) {
                rule.sql.count { it == '?' } shouldBe rule.params.size
                rule.sql.substringAfter(' ').substringBefore(' ').let { first ->
                    // `update <tabla>` o `delete from <tabla>`
                    val table = if (first == "from") rule.sql.split(' ')[2] else first
                    table shouldBe rule.column.table.tableName
                }
                rule.sql.lowercase() shouldStartWith (if (rule.sql.startsWith("delete")) "delete from" else "update")
            }
        }
    }

    test("la lápida no contiene el uid de Firebase en claro y es estable") {
        val uid = "firebase-uid-123"
        tombstoneUid(uid) shouldBe tombstoneUid(uid)
        tombstoneUid(uid).contains(uid) shouldBe false
        tombstoneUid(uid) shouldStartWith "deleted:"
    }
})
