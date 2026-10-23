package me.santiagoreina.parcheu

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import me.santiagoreina.parcheu.data.billing.BillingReconciler
import me.santiagoreina.parcheu.di.appModules
import me.santiagoreina.parcheu.push.PushChannels
import me.santiagoreina.parcheu.push.pushModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class ParcheUApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // stopKoin no hace nada en un arranque normal; evita el error de "Koin ya iniciado" cuando
        // Robolectric crea una Application nueva por test en el mismo proceso.
        stopKoin()
        startKoin {
            androidContext(this@ParcheUApplication)
            modules(appModules + pushModule)
        }
        // Concilia compras pagadas sin reconocer cada vez que la app vuelve a primer plano (E7.2).
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            GlobalContext.get().get<BillingReconciler>()
        )
        PushChannels.ensureCreated(this)
    }
}
