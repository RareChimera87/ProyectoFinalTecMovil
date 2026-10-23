package me.santiagoreina.parcheu.contract

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/*
 * Tipos transversales de la API v1 (arquitectura.md §5): configuración JSON, errores,
 * paginación, enums compartidos y PersonRef. Los DTOs de cada dominio viven en su archivo.
 *
 * Convenciones del contrato:
 * - Todo campo opcional es nullable con valor por defecto `null`; las colecciones opcionales
 *   tienen `emptyList()`/`emptyMap()` por defecto.
 * - Con `explicitNulls = false` los nulos no se escriben: los campos premium que el servidor
 *   omite para usuarios gratis no aparecen como claves en el JSON.
 * - Fechas con hora: `kotlin.time.Instant` (ISO-8601 con zona). Fechas sin hora: [IsoDate].
 *   Meses: texto `yyyy-MM`.
 */

/** Configuración JSON única para cliente y servidor. */
public val ContractJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** Códigos de error de la API con su status HTTP (arquitectura.md §5.1). */
@Serializable
public enum class ErrorCode(public val httpStatus: Int) {
    VALIDATION(400),
    UNAUTHENTICATED(401),
    PLUS_REQUIRED(402),
    QUOTA_EXCEEDED(402),
    TERMS_REQUIRED(403),
    AGE_REQUIRED(403),
    UNDERAGE(403),
    BLOCKED(403),
    SUSPENDED(403),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409),
    SCENARIO_ENDED(409),
    AI_BUDGET(503),
}

/**
 * Cuerpo de toda respuesta de error. `kind` acompaña a QUOTA_EXCEEDED con un valor de
 * [QuotaKind] (como texto); `resetsAt` indica cuándo se renueva el cupo y `retryAt`
 * cuándo reintentar ante AI_BUDGET.
 */
@Serializable
public data class ApiError(
    val code: ErrorCode,
    val message: String,
    val kind: String? = null,
    val resetsAt: Instant? = null,
    val retryAt: Instant? = null,
)

/** Cupos que se cuentan (tabla usage_events). */
@Serializable
public enum class QuotaKind { SIMULATION, RANDOM_MATCH, FULL_REPORT }

/** Página por cursor: `?cursor=&limit=`. `nextCursor` nulo = no hay más. */
@Serializable
public data class Page<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
)

/** Modos de conexión. */
@Serializable
public enum class Mode { AMISTAD, PAREJA, ESTUDIO }

/** Visibilidad del usuario en contextos de segundo grado (arquitectura.md §6.1). */
@Serializable
public enum class Visibility { NAME, ANONYMOUS, HIDDEN }

/**
 * Referencia a otra persona. Antes de la conexión mutua `id` y `photoUrl` son nulos,
 * `anonId` es la HMAC del observador y `label` = "Alguien de la UIS".
 */
@Serializable
public data class PersonRef(
    val id: String? = null,
    val anonId: String? = null,
    val label: String,
    val initials: String,
    val photoUrl: String? = null,
    val isDemo: Boolean = false,
)

/** GET /v1/health */
@Serializable
public data class Health(
    val version: String,
    val time: Instant,
)

/** Cuerpo de las tareas internas de Cloud Tasks (`POST /internal/tasks/...`). */
@Serializable
public data class InternalTask(
    val id: String,
)

/** Fecha sin hora en formato ISO-8601 `yyyy-MM-dd` (por ejemplo, la fecha de nacimiento). */
@Serializable(with = IsoDateSerializer::class)
public data class IsoDate(val value: String) {
    init {
        require(ISO_DATE_PATTERN.matches(value)) { "Fecha inválida, se espera yyyy-MM-dd: $value" }
        val month = value.substring(5, 7).toInt()
        val day = value.substring(8, 10).toInt()
        require(month in 1..12 && day in 1..31) { "Fecha inválida: $value" }
    }

    val year: Int get() = value.substring(0, 4).toInt()
    val month: Int get() = value.substring(5, 7).toInt()
    val day: Int get() = value.substring(8, 10).toInt()

    override fun toString(): String = value
}

private val ISO_DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

internal object IsoDateSerializer : KSerializer<IsoDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("me.santiagoreina.parcheu.contract.IsoDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: IsoDate) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): IsoDate = IsoDate(decoder.decodeString())
}
