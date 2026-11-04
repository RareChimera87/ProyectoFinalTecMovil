package me.santiagoreina.parcheu.feature.twinquestions

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import me.santiagoreina.parcheu.data.auth.SessionCleaner
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit

private val Context.twinQuestionsDataStore by preferencesDataStore(name = "twin_questions")

/** Dependencias de la feature de [TwinQuestionsRoute] (B1). */
val twinQuestionsModule =
    module {
        single<TwinQuestionsApi> { get<Retrofit>().create(TwinQuestionsApi::class.java) }
        single<TwinQuestionsRepository> { TwinQuestionsRepositoryImpl(get(), get()) }
        single<AnswerProgressStore> {
            DataStoreAnswerProgressStore(androidContext().twinQuestionsDataStore)
        }
        // Cerrar sesión (E8.2) borra el progreso de B1: es de la persona, no del dispositivo.
        single<SessionCleaner>(named("twin-questions-progress")) {
            val store = get<AnswerProgressStore>()
            SessionCleaner { store.clear() }
        }
        viewModel { TwinQuestionsViewModel(get(), get()) }
    }
