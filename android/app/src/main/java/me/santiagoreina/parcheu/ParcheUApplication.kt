package me.santiagoreina.parcheu

import android.app.Application
import me.santiagoreina.parcheu.di.appModules
import org.koin.android.ext.koin.androidContext
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
            modules(appModules)
        }
    }
}
