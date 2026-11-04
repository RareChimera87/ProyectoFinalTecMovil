package me.santiagoreina.parcheu.feature.safety

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Mensaje de error de H1 y H2 para la persona. */
internal fun safetyErrorMessage(error: AppError): String = when (error) {
    AppError.Network -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    AppError.NotFound -> "No encontramos a esta persona. Puede que haya borrado su cuenta."
    is AppError.Validation -> "Revisa los datos e inténtalo de nuevo."
    else -> "Algo salió mal. Inténtalo de nuevo."
}

@Composable
internal fun ErrorText(error: AppError) {
    Text(
        text = safetyErrorMessage(error),
        style = MaterialTheme.typography.bodySmall,
        color = ParcheUTheme.colors.coralt
    )
}
