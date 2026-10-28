package me.santiagoreina.parcheu.feature.conocer.scenario

import me.santiagoreina.parcheu.contract.ScenarioSessionDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall

/** Sesión del modo escenario (F3): estado, temporizador y acuerdo. Los mensajes van por el repositorio de chat. */
interface ScenarioRepository {
    suspend fun session(chatId: String): ApiResult<ScenarioSessionDto>

    suspend fun agree(chatId: String): ApiResult<ScenarioSessionDto>
}

class ScenarioRepositoryImpl(private val api: ScenarioApi, private val mapper: ApiErrorMapper) :
    ScenarioRepository {
    override suspend fun session(chatId: String) = apiCall(mapper) { api.session(chatId) }

    override suspend fun agree(chatId: String) = apiCall(mapper) { api.agree(chatId) }
}
