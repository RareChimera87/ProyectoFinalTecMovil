package me.santiagoreina.parcheu.feature.notifications.requests

import me.santiagoreina.parcheu.contract.ConnectRequest
import me.santiagoreina.parcheu.contract.ConnectRequestsDto
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Solicitudes en memoria: [results] se entregan en orden (el último se repite). */
class FakeConnectRequestsRepository(vararg results: ApiResult<ConnectRequestsDto>) :
    ConnectRequestsRepository {
    private val queue =
        results.toMutableList().ifEmpty { mutableListOf(ApiResult.Failure(AppError.Network)) }

    var calls = 0
        private set

    override suspend fun requests(): ApiResult<ConnectRequestsDto> {
        calls++
        return if (queue.size > 1) queue.removeAt(0) else queue.first()
    }
}

/** Datos de ejemplo de la sección (pruebas y capturas Roborazzi). */
object ConnectRequestsSamples {
    private fun person(anon: String) =
        PersonRef(anonId = anon, label = "Alguien de la UIS", initials = "A")

    val first = ConnectRequest("sim-1", person("anon-1"), affinity = 84)
    val second = ConnectRequest("sim-2", person("anon-2"), affinity = 71)

    /** Gratis: solo el conteo. */
    val free = ConnectRequestsDto(count = 2)

    /** ParcheU+: la lista. */
    val plus = ConnectRequestsDto(count = 2, items = listOf(first, second))
}
