package me.santiagoreina.parcheu.feature.traintwin

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.FeedbackKind
import me.santiagoreina.parcheu.contract.FeedbackPrompt
import me.santiagoreina.parcheu.contract.MemoryDto
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.TwinDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Datos de ejemplo de C4 para pruebas y capturas. */
object TrainTwinSamples {
    val pending =
        MemoryDto(
            id = "m-1",
            text = "Prefiere estudiar en grupo antes de los parciales.",
            mode = Mode.ESTUDIO,
            status = MemoryStatus.PENDING,
            createdAt = Instant.fromEpochSeconds(0)
        )
    val accepted =
        MemoryDto(
            id = "m-2",
            text = "Le encanta el senderismo los domingos.",
            mode = Mode.AMISTAD,
            status = MemoryStatus.ACCEPTED,
            createdAt = Instant.fromEpochSeconds(0)
        )
    val rating =
        FeedbackPrompt(
            id = "sim-1:2",
            kind = FeedbackKind.RATING,
            text = "Yo digo que sí a casi todo plan, mientras no sea madrugar."
        )
    val control =
        FeedbackPrompt(
            id = "c-1",
            kind = FeedbackKind.CONTROL,
            text = "Un amigo te invita a probar comida que nunca has comido.",
            options = listOf("Me le mido a lo que sea", "Mejor pido lo de siempre", "Depende")
        )
    val twin = TwinDto(level = 1, xp = 120, xpNext = 500, version = 1, personaReady = true)
}

class FakeTrainTwinRepository(
    var pending: ApiResult<List<MemoryDto>> = ApiResult.Success(listOf(TrainTwinSamples.pending)),
    var accepted: ApiResult<List<MemoryDto>> = ApiResult.Success(listOf(TrainTwinSamples.accepted)),
    var prompts: ApiResult<List<FeedbackPrompt>> =
        ApiResult.Success(listOf(TrainTwinSamples.control, TrainTwinSamples.rating)),
    var decision: ApiResult<MemoryDto>? = null,
    var sendResult: ApiResult<TwinDto> = ApiResult.Success(TrainTwinSamples.twin)
) : TrainTwinRepository {
    val calls = mutableListOf<String>()

    override suspend fun pendingMemories() = pending

    override suspend fun acceptedMemories() = accepted

    override suspend fun accept(id: String): ApiResult<MemoryDto> {
        calls += "accept:$id"
        return decision
            ?: ApiResult.Success(
                TrainTwinSamples.pending.copy(id = id, status = MemoryStatus.ACCEPTED)
            )
    }

    override suspend fun reject(id: String): ApiResult<MemoryDto> {
        calls += "reject:$id"
        return decision
            ?: ApiResult.Success(
                TrainTwinSamples.pending.copy(id = id, status = MemoryStatus.REJECTED)
            )
    }

    override suspend fun prompts() = prompts

    override suspend fun rate(promptId: String, positive: Boolean): ApiResult<TwinDto> {
        calls += "rate:$promptId:$positive"
        return sendResult
    }

    override suspend fun answerControl(promptId: String, answer: String): ApiResult<TwinDto> {
        calls += "control:$promptId:$answer"
        return sendResult
    }

    companion object {
        val failure = ApiResult.Failure(AppError.Network)
    }
}
