package me.santiagoreina.parcheu.api.graph

import me.santiagoreina.parcheu.contract.Visibility
import java.time.Instant
import java.util.UUID

/** Persona de prueba con id determinista a partir de [n]. */
fun person(
    n: Int,
    name: String? = "Persona $n",
    university: String? = "UIS",
    visibility: Visibility = Visibility.ANONYMOUS,
    deletedAt: Instant? = null,
) = PersonRow(
    id = UUID.fromString("00000000-0000-4000-8000-%012d".format(n)),
    displayName = name,
    photoUrl = "https://example.test/$n.png",
    universityShort = university,
    visibility = visibility,
    isDemo = false,
    deletedAt = deletedAt,
)

/** Repositorio en memoria con [people] ya agregadas. */
fun memoryRepo(vararg people: PersonRow) = InMemoryGraphRepository().also { repo -> people.forEach(repo::addPerson) }

fun graphService(repo: GraphRepository) = GraphService(repo, GraphVisibilityPolicy(AnonIdSigner.forTests()))
