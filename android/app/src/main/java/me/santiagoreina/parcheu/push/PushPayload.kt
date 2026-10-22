package me.santiagoreina.parcheu.push

/** Mensaje de datos de FCM (arquitectura.md §8.4). `type` y `route` los fija el backend en `FcmNotifier`. */
data class PushPayload(
    val id: String?,
    val type: String?,
    val channel: PushChannelSpec,
    val title: String,
    val body: String,
    val route: String?,
) {
    companion object {
        /** `null` si el mensaje no trae texto que mostrar (no se muestra una notificación vacía). */
        fun from(data: Map<String, String>): PushPayload? {
            val title = data["title"]?.takeIf { it.isNotBlank() }
            val body = data["body"].orEmpty()
            if (title == null && body.isBlank()) return null
            return PushPayload(
                id = data["id"],
                type = data["type"],
                channel = PushChannelSpec.forId(data["channel"]),
                title = title ?: body,
                body = if (title == null) "" else body,
                route = data["route"],
            )
        }
    }
}
