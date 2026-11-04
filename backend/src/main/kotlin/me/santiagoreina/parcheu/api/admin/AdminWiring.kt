package me.santiagoreina.parcheu.api.admin

import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.identity.IdentityRepository
import me.santiagoreina.parcheu.api.safety.InMemorySafetyRepository
import me.santiagoreina.parcheu.api.safety.SafetyModule
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

/** Piezas del dominio admin (E8.5). */
class AdminModule(
    val repository: AdminRepository,
    val service: AdminService,
)

/** Cableado con Postgres. */
fun adminModule(db: Database, identity: IdentityRepository, chat: ChatModule, clock: Clock): AdminModule {
    val repo = ExposedAdminRepository(db)
    return AdminModule(repo, AdminService(repo, identity, chat.service, clock))
}

/**
 * En memoria (pruebas y valor por defecto del contenedor): lee los reportes del repositorio de safety
 * en memoria; con otro repositorio de safety no hay reportes que ver.
 */
fun inMemoryAdminModule(identity: IdentityRepository, chat: ChatModule, safety: SafetyModule, clock: Clock): AdminModule {
    val source = (safety.repository as? InMemorySafetyRepository)
    val repo = InMemoryAdminRepository { source?.allReports.orEmpty() }
    return AdminModule(repo, AdminService(repo, identity, chat.service, clock))
}
