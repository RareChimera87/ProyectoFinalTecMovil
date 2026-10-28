package me.santiagoreina.parcheu.api.safety

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.contract.ReportSource
import java.time.Instant
import java.util.UUID

/** Un bloqueo de quien consulta, con los datos para presentarlo en la lista de bloqueados. */
data class BlockRow(
    val blocked: PersonRow,
    val createdAt: Instant,
)

/** Mensaje copiado a `reports.snapshot` (dato personal: lo limpian E8.3 y E8.8). */
data class SnapshotMessage(
    val id: UUID,
    val senderId: UUID?,
    val text: String,
    val createdAt: Instant,
    val hidden: Boolean,
)

/** Reporte ya validado por [SafetyService], listo para guardar. */
data class NewReport(
    val id: UUID,
    /** Nulo si [source] es AUTO (moderación con IA, E8.6). */
    val reporterId: UUID?,
    val targetId: UUID?,
    val kind: ReportKind,
    val chatId: UUID?,
    val messageId: UUID?,
    val simulationId: UUID?,
    val turnIndex: Int?,
    val reason: ReportReason,
    val details: String?,
    val snapshot: List<SnapshotMessage>?,
    val priority: Int,
    val createdAt: Instant,
    val source: ReportSource = ReportSource.USER,
)

/**
 * Forma de `reports.snapshot`: arreglo en orden cronológico de `{id, senderId, text, createdAt, hidden}`
 * (`senderId` es el uuid interno o null si es del sistema). Lo lee la administración de reportes (E8.5).
 */
fun snapshotJson(messages: List<SnapshotMessage>): JsonArray = buildJsonArray {
    messages.forEach { m ->
        add(
            buildJsonObject {
                put("id", m.id.toString())
                put("senderId", m.senderId?.toString())
                put("text", m.text)
                put("createdAt", m.createdAt.toString())
                put("hidden", m.hidden)
            },
        )
    }
}
