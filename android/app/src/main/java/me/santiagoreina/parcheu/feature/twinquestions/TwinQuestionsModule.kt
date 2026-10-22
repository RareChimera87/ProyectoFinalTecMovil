package me.santiagoreina.parcheu.feature.twinquestions

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
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
        viewModel { TwinQuestionsViewModel(get(), get()) }
    }
