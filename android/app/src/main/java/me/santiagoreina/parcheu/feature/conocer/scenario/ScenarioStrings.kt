package me.santiagoreina.parcheu.feature.conocer.scenario

import me.santiagoreina.parcheu.core.error.AppError

/** Textos de F3 (los afina I2 con ux-copy). */
internal object ScenarioStrings {
    const val AGREE = "Llegamos a un acuerdo"
    const val WAITING_OTHER = "Esperando a que la otra persona confirme el acuerdo"
    const val OTHER_AGREED = "La otra persona propone un acuerdo. Confírmalo si estás de acuerdo."
    const val JUDGING = "Se acabó. Estamos evaluando la conversación…"
    const val DONE_NO_RESULT =
        "No hubo nada que evaluar o no se pudo completar. Puedes buscar otro parche."
    const val SEE_RESULT = "Ver resultado"
    const val ENDED_ACTION = "El tiempo se acabó antes de confirmar el acuerdo."

    fun budget(cop: Int): String =
        "Presupuesto $" + "%,d".format(java.util.Locale.forLanguageTag("es-CO"), cop)

    fun clock(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

    fun error(error: AppError): String = when (error) {
        AppError.ScenarioEnded -> ENDED_ACTION
        AppError.Network -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
        else -> "Algo salió mal. Inténtalo de nuevo."
    }
}
