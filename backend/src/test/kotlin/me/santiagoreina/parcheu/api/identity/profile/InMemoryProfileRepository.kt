package me.santiagoreina.parcheu.api.identity.profile

import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.contract.CatalogDto
import me.santiagoreina.parcheu.contract.Interest
import me.santiagoreina.parcheu.contract.University
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** [ProfileRepository] en memoria sobre los mismos usuarios y catálogos de [InMemoryIdentityRepository]. */
class InMemoryProfileRepository(private val identity: InMemoryIdentityRepository) : ProfileRepository {
    override suspend fun catalog() = CatalogDto(
        universities = identity.universities.sorted().map { University(it, "U$it", "Universidad $it") },
        interests = identity.interestSlugs.sorted().map { Interest(it, it.replaceFirstChar(Char::uppercase), "star") },
    )

    override suspend fun setBirthDate(
        userId: UUID,
        birthDate: LocalDate,
        ageVerifiedAt: Instant?,
        underageAt: Instant?,
        now: Instant,
    ): Boolean {
        val user = identity.users.first { it.id == userId }
        if (user.birthDate != null) return false
        identity.replace(user.copy(birthDate = birthDate, ageVerifiedAt = ageVerifiedAt, underageAt = underageAt))
        return true
    }
}
