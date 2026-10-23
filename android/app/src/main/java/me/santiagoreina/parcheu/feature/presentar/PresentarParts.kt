package me.santiagoreina.parcheu.feature.presentar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/* Piezas compartidas de E1-E3 (E6.2). Iconos Material Symbols como vectores locales. */

private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()

internal object PresentarIcons {
    val ArrowBack = icon(
        "arrow_back",
        "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"
    )
    val Close = icon(
        "close",
        "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 " +
            "12 13.41 17.59 19 19 17.59 13.41 12z"
    )
    val People = icon(
        "group",
        "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0" +
            "c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2" +
            "c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05" +
            " 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"
    )
    val Send = icon("send", "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z")
    val Info = icon(
        "info",
        "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6z" +
            "m0-8h-2V7h2v2z"
    )
    val VisibilityOff = icon(
        "visibility_off",
        "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92" +
            "c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7" +
            "l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12" +
            "c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42" +
            "L19.73 22 21 20.73 3.27 3 2 4.27z" +
            "M7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08" +
            "l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2z" +
            "m4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z"
    )
    val Sparkle = icon(
        "auto_awesome",
        "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9z" +
            "m-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5z" +
            "M19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z"
    )
}

@Composable
internal fun rememberPresentarIcon(vector: ImageVector): Painter = rememberVectorPainter(vector)

/** Texto de un error de E1-E3. */
internal fun presentarErrorText(error: AppError): String = when (error) {
    AppError.Network -> "Sin conexión. Inténtalo de nuevo."
    AppError.Blocked -> "No puedes presentar a estas personas."
    AppError.Forbidden -> "Solo puedes presentar a amigos conectados contigo en este modo."
    AppError.NotFound -> "Esta presentación ya no está disponible."
    AppError.Conflict -> "Esto ya se había decidido."
    is AppError.AiBudget -> "Los gemelos están descansando. Inténtalo más tarde."
    is AppError.Validation -> error.message
    else -> "Algo salió mal. Inténtalo de nuevo."
}

/** Nota de una línea con icono (12 mu), como 'Pareja no está disponible aquí'. */
@Composable
internal fun PresentarHint(text: String, icon: Painter, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = colors.mu, modifier = Modifier.size(18.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.mu
        )
    }
}

/** Aviso descartable al tocarlo (errores de una acción). */
@Composable
internal fun PresentarNotice(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    ParcheUCard(modifier.fillMaxWidth(), onClick = onDismiss) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = ParcheUTheme.colors.error
        )
    }
}

@Composable
internal fun PresentarLoading(tag: String) {
    Box(Modifier.fillMaxSize().testTag(tag), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ParcheUTheme.colors.primary)
    }
}

@Composable
internal fun PresentarLoadError(error: AppError, onRetry: () -> Unit, tag: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            presentarErrorText(error),
            style = MaterialTheme.typography.bodyLarge,
            color = ParcheUTheme.colors.tx,
            textAlign = TextAlign.Center
        )
        if (error != AppError.NotFound) PrimaryButton("Reintentar", onClick = onRetry)
    }
}
