package me.santiagoreina.parcheu.api.platform.db.tables

import kotlinx.serialization.json.JsonElement
import me.santiagoreina.parcheu.contract.ContractJson
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.json.jsonb
import java.util.UUID

/*
 * Ayudas para declarar las tablas Exposed. El esquema lo define Flyway (`db/migration`); estos
 * objetos solo lo describen para consultar, así que nunca se usa `SchemaUtils.create`.
 */

/** Columna `uuid` de clave primaria: Postgres tiene `gen_random_uuid()`, pero el cliente la genera para conocerla antes del INSERT. */
internal fun Table.uuidPk(name: String = "id"): Column<UUID> = javaUUID(name).clientDefault { UUID.randomUUID() }

/** Enum guardado como `text` con CHECK en la base (convención de arquitectura.md §4). */
internal inline fun <reified E : Enum<E>> Table.enumText(name: String): Column<E> =
    text(name).transform(wrap = { enumValueOf<E>(it) }, unwrap = { it.name })

/** Columna `jsonb` sin tipar: cada dominio decodifica con el `Json` del contrato. */
internal fun Table.jsonbColumn(name: String): Column<JsonElement> =
    jsonb(name, ContractJson, JsonElement.serializer())

/** Referencia a `users.id`. */
internal fun Table.userRef(name: String, onDelete: ReferenceOption? = null): Column<UUID> =
    javaUUID(name).references(Users.id, onDelete = onDelete)
