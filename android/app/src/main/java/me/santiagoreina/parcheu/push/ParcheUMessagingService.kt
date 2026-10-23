package me.santiagoreina.parcheu.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Recibe los mensajes de datos de FCM (el backend no manda el bloque `notification`) y el token del
 * dispositivo. Solo muestra notificaciones con la app en segundo plano o cerrada; con la app abierta el
 * mismo aviso también se muestra en la barra, que es lo esperado para chat y resultados.
 */
class ParcheUMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = PushPayload.from(message.data) ?: return
        PushNotifier.show(applicationContext, payload)
    }

    override fun onNewToken(token: String) {
        val registrar = GlobalContext.getOrNull()?.getOrNull<PushTokenRegistrar>() ?: return
        scope.launch { registrar.onNewToken(token) }
    }
}
