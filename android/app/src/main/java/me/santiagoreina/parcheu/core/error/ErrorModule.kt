package me.santiagoreina.parcheu.core.error

import me.santiagoreina.parcheu.data.auth.AuthRepository
import org.koin.dsl.module

/**
 * Bus global de errores (arquitectura.md §2.3). Se crea al arrancar Koin; el [ApiErrorMapper] compartido
 * (que define `networkModule`) lo recibe por constructor con `getOrNull()`, así toda llamada hecha con
 * `apiCall` lo alimenta. La sesión se
 * consulta en cada 401, no al crear el bus: el repositorio de auth se resuelve tarde.
 */
val errorModule = module {
    single(createdAtStart = true) {
        val scope = this
        GlobalErrorBus(hasSession = { scope.get<AuthRepository>().hasSession() })
    }
}
