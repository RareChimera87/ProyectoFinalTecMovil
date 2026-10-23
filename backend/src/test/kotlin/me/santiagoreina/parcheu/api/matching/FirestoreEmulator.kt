package me.santiagoreina.parcheu.api.matching

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions

/**
 * Emulador de Firestore para las pruebas del espejo. Lo arranca `firebase emulators:exec`, que
 * define `FIRESTORE_EMULATOR_HOST` (CI: backend-ci; local: ver docs/desarrollo-local.md). Sin esa
 * variable las pruebas que lo necesitan se omiten.
 */
object FirestoreEmulator {
    private val host: String? = System.getenv("FIRESTORE_EMULATOR_HOST")?.takeIf { it.isNotBlank() }

    val enabled: Boolean = host != null

    /** Mismo proyecto de demostración que usan las pruebas de reglas de `firebase/`. */
    const val PROJECT_ID = "demo-parcheu"

    val firestore: Firestore by lazy {
        FirestoreOptions.newBuilder()
            .setProjectId(PROJECT_ID)
            .setEmulatorHost(requireNotNull(host) { "Define FIRESTORE_EMULATOR_HOST" })
            .build()
            .service
    }
}
