package me.santiagoreina.parcheu.feature.parche

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import retrofit2.Retrofit

private val Context.parcheDataStore by preferencesDataStore(name = "parche")

/** Dependencias de D1-D5 (E3.4): API del grafo, caché en DataStore y el ViewModel. */
val parcheModule =
    module {
        single<GraphApi> { get<Retrofit>().create(GraphApi::class.java) }
        single<GraphCache> { DataStoreGraphCache(androidContext().parcheDataStore, get<Json>()) }
        single<GraphRepository> { GraphRepositoryImpl(get(), get(), get()) }
        viewModelOf(::ParcheViewModel)
    }
