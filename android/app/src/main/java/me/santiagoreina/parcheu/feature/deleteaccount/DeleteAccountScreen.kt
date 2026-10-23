package me.santiagoreina.parcheu.feature.deleteaccount

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCheckbox
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.InfoBanner
import me.santiagoreina.parcheu.designsystem.components.compound.InfoListCard
import me.santiagoreina.parcheu.designsystem.components.compound.InfoListItem
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

object DeleteAccountTags {
    const val SCREEN = "delete-account"
    const val CONFIRM = "delete-account:confirm"
    const val DELETE = "delete-account:delete"
    const val DOWNLOAD_FIRST = "delete-account:download"
    const val PLAY = "delete-account:play"
    const val DONE = "delete-account:done"
    const val DONE_SCREEN = "delete-account:deleted"
}

/** Acciones de la confirmación de borrado y de la pantalla de cuenta borrada. */
data class DeleteAccountActions(
    val onBack: () -> Unit = {},
    val onConfirmChange: (Boolean) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onDownloadFirst: () -> Unit = {},
    val onManageSubscription: () -> Unit = {},
    val onDone: () -> Unit = {}
)

/** Lo que se borra y lo que se conserva (arquitectura.md §10.2, ADR-0025): el mismo texto que `/privacidad`. */
internal val DeletedItems =
    listOf(
        "Tu perfil, tu gemelo, tus respuestas y tus memorias.",
        "Tus conexiones, tu lugar en la fila, tus avisos y tus bloqueos.",
        "Tus mensajes quedan como de 'Usuario eliminado' en los chats que compartías.",
        "En las simulaciones compartidas, la otra persona solo conserva el puntaje y la fecha."
    )

internal val KeptItems =
    listOf(
        "Tus autorizaciones (términos, política y aprendizaje) y los registros de " +
            "compra, sin datos de contacto, porque la ley lo exige."
    )

/** Confirmación de borrado (pantalla nueva, E8.3) o, ya borrada, la confirmación final. */
@Composable
fun DeleteAccountScreen(
    state: DeleteAccountUiState,
    actions: DeleteAccountActions,
    modifier: Modifier = Modifier
) {
    if (state.deleted) {
        DeletedScreen(actions.onDone, modifier)
    } else {
        ConfirmScreen(state, actions, modifier)
    }
}

@Composable
private fun ConfirmScreen(
    state: DeleteAccountUiState,
    actions: DeleteAccountActions,
    modifier: Modifier
) {
    val colors = ParcheUTheme.colors
    val spacing = ParcheUTheme.spacing
    Column(modifier.fillMaxSize().background(colors.bg).testTag(DeleteAccountTags.SCREEN)) {
        ParcheUChildTopBar(
            title = "Borrar cuenta",
            navigationIcon = rememberVectorPainter(AccountBackIcon),
            navigationContentDescription = "Atrás",
            onNavigationClick = actions.onBack
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(spacing.blockGap)
        ) {
            Text(
                "¿Borrar tu cuenta?",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.tx,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                "Tu cuenta desaparece de inmediato para todos y en unos minutos borramos " +
                    "tus datos. No se puede deshacer.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu
            )
            InfoListCard(
                title = "Qué se borra",
                items = DeletedItems.map {
                    InfoListItem(it, rememberVectorPainter(DeleteForeverIcon))
                }
            )
            InfoListCard(title = "Qué se conserva", items = KeptItems.map { InfoListItem(it) })
            InfoBanner(
                text =
                "Si tienes ParcheU+, borrar la cuenta no cancela tu suscripción: cancélala en " +
                    "Google Play para que no te cobren más.",
                actionLabel = "Ir a Google Play",
                onActionClick = actions.onManageSubscription,
                modifier = Modifier.testTag(DeleteAccountTags.PLAY)
            )
            ParcheUTextButton(
                text = "Descargar mis datos primero",
                onClick = actions.onDownloadFirst,
                size = ParcheUButtonSize.Medium,
                icon = rememberVectorPainter(DownloadIcon),
                enabled = !state.deleting,
                modifier = Modifier.testTag(DeleteAccountTags.DOWNLOAD_FIRST)
            )
        }
        // La casilla y el error van junto al botón: siempre visibles, sin desplazarse.
        Column(
            Modifier.fillMaxWidth().padding(spacing.footer),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ParcheUCheckbox(
                    checked = state.confirmed,
                    onCheckedChange = actions.onConfirmChange,
                    contentDescription = CONFIRM_LABEL,
                    enabled = !state.deleting,
                    modifier = Modifier.testTag(DeleteAccountTags.CONFIRM)
                )
                Text(CONFIRM_LABEL, style = MaterialTheme.typography.bodyMedium, color = colors.tx)
            }
            state.error?.let {
                Text(
                    deleteErrorMessage(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.coralt
                )
            }
            PrimaryButton(
                text = if (state.deleting) "Borrando…" else "Borrar mi cuenta",
                onClick = actions.onDelete,
                enabled = state.canDelete,
                modifier = Modifier.fillMaxWidth().testTag(DeleteAccountTags.DELETE)
            )
        }
    }
}

@Composable
private fun DeletedScreen(onDone: () -> Unit, modifier: Modifier) {
    val colors = ParcheUTheme.colors
    val spacing = ParcheUTheme.spacing
    Column(modifier.fillMaxSize().background(colors.bg).testTag(DeleteAccountTags.DONE_SCREEN)) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutterWide, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.blockGap)
        ) {
            Icon(
                rememberVectorPainter(AccountDoneIcon),
                contentDescription = null,
                tint = colors.prt,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Borramos tu cuenta",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.tx,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                "Ya nadie puede verte en ParcheU y cerramos tu sesión en este teléfono. En unos " +
                    "minutos terminamos de borrar tus datos de nuestros servidores.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu
            )
            Text(
                "Gracias por haber sido parte del parche.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.tx
            )
        }
        PrimaryButton(
            text = "Listo",
            onClick = onDone,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(spacing.footer)
                .testTag(DeleteAccountTags.DONE)
        )
    }
}

internal const val CONFIRM_LABEL = "Entiendo que borrar mi cuenta no se puede deshacer"

/** Mensaje de error del borrado para la persona. */
internal fun deleteErrorMessage(error: AppError): String = when (error) {
    AppError.Network -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    AppError.Unauthorized -> "Tu sesión venció. Vuelve a iniciar sesión para borrar tu cuenta."
    else -> "No pudimos borrar tu cuenta. Inténtalo de nuevo en un momento."
}
