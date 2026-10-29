package me.santiagoreina.parcheu.di

import androidx.test.core.app.ApplicationProvider
import me.santiagoreina.parcheu.BuildConfig
import me.santiagoreina.parcheu.ParcheUApplication
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.TokenProvider
import me.santiagoreina.parcheu.data.auth.AuthRepository
import me.santiagoreina.parcheu.data.auth.SessionCleaner
import me.santiagoreina.parcheu.feature.notifications.requests.ConnectRequestsRepository
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit

/** Los módulos Koin de AppModules.kt arrancan y resuelven la red compartida (ADR-0005: errores de DI en test). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppModulesTest {
    @Test
    fun applicationStartsKoinWithEveryModule() {
        ApplicationProvider.getApplicationContext<ParcheUApplication>()
        val koin = GlobalContext.get()

        assertEquals(37, featureModules.size)
        assertNotNull(koin.get<OkHttpClient>())
        assertNotNull(koin.get<TokenProvider>())
        assertNotNull(koin.get<ApiErrorMapper>())
        // E6.6: la sección de C2 resuelve su repositorio y su API.
        assertNotNull(koin.get<ConnectRequestsRepository>())
        // Cerrar sesión borra la caché del grafo y el progreso de B1: el repositorio de auth recibe sus limpiadores.
        assertNotNull(koin.get<AuthRepository>())
        // grafo, B1, billing, JSON exportado (A-B3) y token de push (A-B3).
        assertEquals(5, koin.getAll<SessionCleaner>().size)
        assertEquals(BuildConfig.API_URL, koin.get<Retrofit>().baseUrl().toString())
    }
}
