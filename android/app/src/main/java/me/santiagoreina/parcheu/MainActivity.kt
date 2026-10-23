package me.santiagoreina.parcheu

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import me.santiagoreina.parcheu.push.DeepLinks
import me.santiagoreina.parcheu.push.PendingDeepLink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) offerDeepLink(intent)
        setContent {
            ParcheUApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        offerDeepLink(intent)
    }

    /** Un toque en una notificación o un enlace `parcheu://` deja su ruta pendiente hasta pasar el Splash. */
    private fun offerDeepLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        PendingDeepLink.offer(DeepLinks.fromUri(intent.dataString))
    }
}
