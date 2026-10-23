package me.santiagoreina.parcheu.feature.chat

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

/** Dependencias de F9 (E5.2). El repositorio también lo usa la lista de chats ([chatListModule]). */
val chatModule =
    module {
        single<ChatApi> { get<Retrofit>().create(ChatApi::class.java) }
        single<ChatRepository> { FirestoreChatRepository(get(), get(), get()) }
        viewModel { params -> ChatViewModel(params.get(), get()) }
    }
