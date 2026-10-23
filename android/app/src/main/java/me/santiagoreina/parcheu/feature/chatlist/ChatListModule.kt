package me.santiagoreina.parcheu.feature.chatlist

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependencias de la lista de chats (E5.2). El `ChatRepository` viene de `chatModule`. */
val chatListModule =
    module {
        viewModel { ChatListViewModel(get()) }
    }
