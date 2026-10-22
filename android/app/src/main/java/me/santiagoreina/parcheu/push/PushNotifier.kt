package me.santiagoreina.parcheu.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import me.santiagoreina.parcheu.MainActivity
import me.santiagoreina.parcheu.R

/** Muestra un [PushPayload] como notificación del canal que le toca; al tocarla abre su deep link. */
object PushNotifier {
    fun canPost(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    /** Intent que abre la app en la ruta del aviso. Con `route` nulo o inválido, decide el `type` (§8.4). */
    fun openIntent(context: Context, payload: PushPayload): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(DeepLinks.resolveUri(payload.type, payload.route)),
        context,
        MainActivity::class.java,
    )
        .putExtra(EXTRA_TYPE, payload.type)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    @SuppressLint("MissingPermission") // canPost() lo verifica.
    fun show(context: Context, payload: PushPayload): Boolean {
        if (!canPost(context)) return false
        PushChannels.ensureCreated(context)
        val requestCode = (payload.id ?: payload.title).hashCode()
        val tap = PendingIntent.getActivity(
            context,
            requestCode,
            openIntent(context, payload),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, payload.channel.id)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .setPriority(
                if (payload.channel ==
                    PushChannelSpec.CHAT
                ) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            ).build()
        NotificationManagerCompat.from(context).notify(requestCode, notification)
        return true
    }

    const val EXTRA_TYPE = "parcheu.push.type"
}
