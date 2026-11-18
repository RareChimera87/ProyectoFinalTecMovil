package me.santiagoreina.parcheu.feature.conocer.match

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import me.santiagoreina.parcheu.data.auth.SessionCleaner
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit

/** Datos compartidos por F1 y F2; lo cargan `matchQueueModule` y `matchFoundModule` con `includes`. */
val conocerMatchDataModule =
    module {
        single<MatchApi> { get<Retrofit>().create(MatchApi::class.java) }
        single<MatchTicketSource> {
            FirestoreMatchTicketSource(
                firestore = { FirebaseFirestore.getInstance() },
                auth = { FirebaseAuth.getInstance() }
            )
        }
        single<MatchRepository> { MatchRepositoryImpl(get(), get(), get()) }
        // Cerrar sesión olvida el último modo de F1: es de la persona, no del teléfono.
        single<SessionCleaner>(named("match-last-mode")) {
            val repository = get<MatchRepository>()
            SessionCleaner { repository.lastMode = null }
        }
    }
