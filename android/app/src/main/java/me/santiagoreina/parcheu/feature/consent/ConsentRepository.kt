package me.santiagoreina.parcheu.feature.consent

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /**
     * Concede [ONBOARDING_LEARNING] con la versión vigente del texto legal. Si falla a la mitad, revoca
     * lo ya concedido (mejor esfuerzo) para no dejar un consentimiento parcial.
     */
    suspend fun grantOnboardingLearning(): ApiResult<Unit>

    /**
     * Revoca (granted=false) lo que un intento fallido de [grantOnboardingLearning] haya dejado concedido y
     * no se pudo deshacer. 'Ahora no' lo llama: si el usuario rechaza, el gemelo no aprende de esos chats.
     * Devuelve [ApiResult.Failure] si alguna revocación falla (queda pendiente para reintentar) y
     * [ApiResult.Success] si no queda nada concedido.
     */
    suspend fun revokePartialGrants(): ApiResult<Unit>
}

/**
 * La versión del texto que exige el servidor es `legalTextVersion` de `POST /v1/session` (idempotente);
 * luego se envía un `POST /v1/me/consent` por cada tipo, en orden. Como son llamadas separadas, el repositorio
 * recuerda qué tipos quedaron concedidos para poder revocarlos si el intento no termina.
 */
class ApiConsentRepository(
    private val api: ConsentApi,
    private val sessionApi: SessionApi,
    private val errorMapper: ApiErrorMapper,
) : ConsentRepository {
    private val mutex = Mutex()
    private val granted = linkedSetOf<ConsentKind>()
    private var version: String? = null

    override suspend fun grantOnboardingLearning(): ApiResult<Unit> = mutex.withLock {
        val result = apiCall(errorMapper) {
            val legalVersion = sessionApi.session().legalTextVersion
            version = legalVersion
            ONBOARDING_LEARNING.forEach { kind ->
                // Se anota antes de llamar: si el servidor confirma pero la respuesta se pierde (timeout),
                // igual hay que revocarlo. Revocar algo no concedido es inocuo.
                granted += kind
                api.change(ConsentChange(kind, granted = true, textVersion = legalVersion))
            }
        }
        if (result is ApiResult.Failure) revokeGranted() else granted.clear()
        result
    }

    override suspend fun revokePartialGrants() = mutex.withLock { revokeGranted() }

    /** Revoca cada tipo concedido; los que no se logran revocar quedan pendientes para 'Ahora no'. */
    private suspend fun revokeGranted(): ApiResult<Unit> {
        val legalVersion = version ?: return ApiResult.Success(Unit)
        var failure: ApiResult.Failure? = null
        granted.toList().forEach { kind ->
            val revoked =
                apiCall(errorMapper) { api.change(ConsentChange(kind, granted = false, textVersion = legalVersion)) }
            when (revoked) {
                is ApiResult.Success -> granted -= kind
                is ApiResult.Failure -> if (failure == null) failure = revoked
            }
        }
        return failure ?: ApiResult.Success(Unit)
    }
}
