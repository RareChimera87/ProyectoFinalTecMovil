package me.santiagoreina.parcheu.di

import androidx.test.core.app.ApplicationProvider
import me.santiagoreina.parcheu.BuildConfig
import me.santiagoreina.parcheu.ParcheUApplication
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.TokenProvider
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

        assertEquals(36, featureModules.size)
        assertNotNull(koin.get<OkHttpClient>())
        assertNotNull(koin.get<TokenProvider>())
        assertNotNull(koin.get<ApiErrorMapper>())
        assertEquals(BuildConfig.API_URL, koin.get<Retrofit>().baseUrl().toString())
    }
}
