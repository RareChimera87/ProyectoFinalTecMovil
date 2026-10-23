package me.santiagoreina.parcheu.api.identity.consent

import me.santiagoreina.parcheu.api.identity.UserRecord
import me.santiagoreina.parcheu.api.platform.BogotaZone
import me.santiagoreina.parcheu.api.platform.errors.AgeRequired
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.Mode
import java.time.Instant
import java.time.LocalDate

/**
 * Consentimiento de aprendizaje de chats por modo (E2.5, ADR-0017 y ADR-0032).
 *
 * B4 'Acepto' concede solo [ONBOARDING] (Amistad y Estudio) con `POST /v1/me/consent`, una fila por
 * tipo; 'Ahora no' no escribe nada. El de Pareja queda apagado y solo se concede desde H3 (E8.2),
 * con Pareja activo y la edad verificada y confirmada.
 */
object LearningConsentPolicy {
    /** Lo que concede B4 'Acepto', en orden. Nunca incluye Pareja. */
    val ONBOARDING: List<ConsentKind> = listOf(ConsentKind.CHAT_LEARNING_AMISTAD, ConsentKind.CHAT_LEARNING_ESTUDIO)

    /** Modo al que pertenece un consentimiento de aprendizaje; `null` para TERMS y PRIVACY. */
    fun modeOf(kind: ConsentKind): Mode? = when (kind) {
        ConsentKind.CHAT_LEARNING_AMISTAD -> Mode.AMISTAD
        ConsentKind.CHAT_LEARNING_PAREJA -> Mode.PAREJA
        ConsentKind.CHAT_LEARNING_ESTUDIO -> Mode.ESTUDIO
        ConsentKind.TERMS, ConsentKind.PRIVACY -> null
    }

    /**
     * Valida una concesión. Aprender de los chats de Pareja exige que Pareja esté activo, la edad
     * verificada y la confirmación +18 (403 AGE_REQUIRED). Revocar siempre se permite.
     */
    fun checkGrant(kind: ConsentKind, granted: Boolean, user: UserRecord, modes: Map<Mode, Boolean>, now: Instant) {
        if (!granted || modeOf(kind) != Mode.PAREJA) return
        val adult = isVerifiedAdult(user, now) && user.parejaConfirmedAt != null
        if (!adult || modes[Mode.PAREJA] != true) {
            throw AgeRequired("Activa Pareja (+18) antes de permitir que tu gemelo aprenda de esos chats")
        }
    }

    /**
     * +18 decidido solo con datos del servidor: edad verificada en B0 (`age_verified_at`), sin bloqueo
     * por edad (`underage_at`) y 18 años cumplidos según la `birth_date` guardada, en America/Bogota.
     * Nada de lo que mande el cliente en la petición cuenta para esta regla.
     */
    fun isVerifiedAdult(user: UserRecord, now: Instant): Boolean {
        val birth = user.birthDate ?: return false
        if (user.ageVerifiedAt == null || user.underageAt != null) return false
        val today = LocalDate.ofInstant(now, BogotaZone)
        return !birth.plusYears(18).isAfter(today)
    }
}
