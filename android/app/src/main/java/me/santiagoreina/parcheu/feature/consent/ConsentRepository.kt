package me.santiagoreina.parcheu.feature.consent

import me.santiagoreina.parcheu.contract.ConsentChange
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.ConsentsDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import me.santiagoreina.parcheu.data.auth.SessionApi
import retrofit2.http.Body
import retrofit2.http.POST

/** `POST /v1/me/consent` (arquitectura.md §5.2): agrega una fila a `consents`, nunca edita. */
interface ConsentApi {
    @POST("v1/me/consent")
    suspend fun change(@Body body: ConsentChange): ConsentsDto
}

/**
 * Lo que concede B4 'Acepto': aprender de los chats de Amistad y Estudio. Pareja queda apagado y solo se
 * activa en H3 (ficha E2.5).
 */
val ONBOARDING_LEARNING: List<ConsentKind> =
    listOf(ConsentKind.CHAT_LEARNING_AMISTAD, ConsentKind.CHAT_LEARNING_ESTUDIO)

/** Consentimiento de aprendizaje de B4. Las pruebas usan un fake. */
interface ConsentRepository {
    /** Concede [ONBOARDING_LEARNING] con la versión vigente del texto legal. */
    suspend fun grantOnboardingLearning(): ApiResult<Unit>
}

/**
 * La versión del texto que exige el servidor es `legalTextVersion` de `POST /v1/session` (idempotente);
 * luego se envía un `POST /v1/me/consent` por cada tipo, en orden.
 */
class ApiConsentRepository(
    private val api: ConsentApi,
    private val sessionApi: SessionApi,
    private val errorMapper: ApiErrorMapper,
) : ConsentRepository {
    override suspend fun grantOnboardingLearning(): ApiResult<Unit> = apiCall(errorMapper) {
        val version = sessionApi.session().legalTextVersion
        ONBOARDING_LEARNING.forEach { kind -> api.change(ConsentChange(kind, granted = true, textVersion = version)) }
    }
}
