package me.santiagoreina.parcheu.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import me.santiagoreina.parcheu.R

/** Canales de notificación. Los IDs son los del backend (`PushChannel` en notifications/push). */
enum class PushChannelSpec(
    val id: String,
    val nameRes: Int,
    val descriptionRes: Int,
    val importance: Int
) {
    CHAT(
        "chat",
        R.string.push_channel_chat,
        R.string.push_channel_chat_desc,
        NotificationManager.IMPORTANCE_HIGH
    ),
    RESULTADOS(
        "resultados",
        R.string.push_channel_resultados,
        R.string.push_channel_resultados_desc,
        NotificationManager.IMPORTANCE_DEFAULT
    ),
    SOCIAL(
        "social",
        R.string.push_channel_social,
        R.string.push_channel_social_desc,
        NotificationManager.IMPORTANCE_DEFAULT
    ),
    CUENTA(
        "cuenta",
        R.string.push_channel_cuenta,
        R.string.push_channel_cuenta_desc,
        NotificationManager.IMPORTANCE_LOW
    )
    ;

    companion object {
        /** Canal de un mensaje; un ID desconocido (backend más nuevo que la app) cae en `cuenta`. */
        fun forId(id: String?): PushChannelSpec = entries.firstOrNull { it.id == id } ?: CUENTA
    }
}

object PushChannels {
    /** Crea los cuatro canales (idempotente). Antes de Android 8 los canales no existen. */
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        PushChannelSpec.entries.forEach { spec ->
            val channel =
                NotificationChannel(spec.id, context.getString(spec.nameRes), spec.importance)
            channel.description = context.getString(spec.descriptionRes)
            manager.createNotificationChannel(channel)
        }
    }
}
