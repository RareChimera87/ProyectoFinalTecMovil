package me.santiagoreina.parcheu.push

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Permiso POST_NOTIFICATIONS (Android 13+). Antes de la API 33 las notificaciones no piden permiso. */
object NotificationPermission {
    fun isRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("push", Context.MODE_PRIVATE)

    /** El permiso se pide una sola vez; si la persona lo niega, no se insiste (lo cambia desde Ajustes). */
    fun wasAsked(context: Context): Boolean = prefs(context).getBoolean("permission_asked", false)

    fun markAsked(context: Context) =
        prefs(context).edit().putBoolean("permission_asked", true).apply()
}

/**
 * Pide POST_NOTIFICATIONS una vez cuando [active] pasa a verdadero (ParcheUApp lo activa al llegar a
 * Inicio, o sea tras B5 'Gemelo listo'), y registra el token al terminar.
 */
@Composable
fun RequestNotificationPermissionOnce(active: Boolean, context: Context, onDone: () -> Unit = {}) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onDone()
    }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        if (!NotificationPermission.isRequired()) {
            onDone()
        } else if (!NotificationPermission.wasAsked(context) && !PushNotifier.canPost(context)) {
            NotificationPermission.markAsked(context)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onDone()
        }
    }
}
