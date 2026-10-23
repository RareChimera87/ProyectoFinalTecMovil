package me.santiagoreina.parcheu.feature.conocer.match

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.dsl.module
import retrofit2.Retrofit

/** Datos compartidos por F1 y F2; lo cargan `matchQueueModule` y `matchFoundModule` con `includes`. */
val conocerMatchDataModule =
    module {
        single<MatchApi> { get<Retrofit>().create(MatchApi::class.java) }
        single<MatchTicketSource> {
            FirestoreMatchTicketSource(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance())
        }
        single<MatchRepository> { MatchRepositoryImpl(get(), get(), get()) }
    }
