package me.santiagoreina.parcheu.api.identity.deletion

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Borra el usuario de Firebase Auth con el Admin SDK (`sa-api` tiene `roles/firebaseauth.admin`, E0.5).
 * Si ya no existe (reintento de la tarea) no es un error. La conexión se crea en el primer uso.
 */
class FirebaseAuthAccountRemover(
    private val projectId: String,
    private val credentials: () -> GoogleCredentials = { GoogleCredentials.getApplicationDefault() },
) : AuthAccountRemover {
    private val log = LoggerFactory.getLogger(FirebaseAuthAccountRemover::class.java)

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(defaultFirebaseApp(projectId, credentials)) }

    override suspend fun remove(firebaseUid: String) {
        withContext(Dispatchers.IO) {
            try {
                auth.deleteUser(firebaseUid)
            } catch (e: FirebaseAuthException) {
                if (e.authErrorCode != AuthErrorCode.USER_NOT_FOUND) throw e
                log.info("El usuario de Firebase Auth ya no existía: nada que borrar")
            }
        }
    }
}
