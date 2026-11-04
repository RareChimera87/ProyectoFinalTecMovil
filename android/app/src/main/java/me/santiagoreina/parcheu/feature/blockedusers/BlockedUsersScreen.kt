package me.santiagoreina.parcheu.feature.blockedusers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.BlockedDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.ui.UiState
import me.santiagoreina.parcheu.designsystem.components.base.OutlineButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarInitials
import me.santiagoreina.parcheu.designsystem.components.compound.EmptyState
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.components.compound.InfoNote
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.chat.BackIcon
import me.santiagoreina.parcheu.feature.parche.relativeDays
import me.santiagoreina.parcheu.feature.safety.safetyErrorMessage

/** Etiquetas de prueba de 'Personas bloqueadas'. */
object BlockedUsersTags {
    const val ROOT = "blocked:root"
    const val LOADING = "blocked:loading"
    const val EMPTY = "blocked:empty"
    const val ERROR = "blocked:error"
    const val LIST = "blocked:list"
    const val NOTE = "blocked:note"

    fun row(userId: String) = "blocked:row:$userId"

    fun unblock(userId: String) = "blocked:unblock:$userId"
}

/** Acciones de 'Personas bloqueadas'. */
data class BlockedUsersActions(
    val onBack: () -> Unit = {},
    val onUnblock: (String) -> Unit = {},
    val onRetry: () -> Unit = {}
)

/** Texto de la nota de la lista: lo que sí y lo que no hace desbloquear. */
const val BLOCKED_USERS_NOTE =
    "Desbloquear a alguien no restaura sus conexiones ni sus chats. Si quieren volver a hablar, " +
        "tendrán que conectarse de nuevo."

/**
 * 'Personas bloqueadas' (pantalla nueva desde H3, E8.7): lista de quienes bloqueaste, con el nombre solo si
 * había conexión mutua (si no, "Alguien de la UIS"), y 'Desbloquear'. Sin plan ni cupo.
 */
@Composable
fun BlockedUsersScreen(
    state: BlockedUsersState,
    actions: BlockedUsersActions,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now()
) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).testTag(BlockedUsersTags.ROOT)) {
        ParcheUChildTopBar(
            title = "Personas bloqueadas",
            navigationIcon = rememberVectorPainter(BackIcon),
            navigationContentDescription = "Volver",
            onNavigationClick = actions.onBack
        )
        when (val list = state.list) {
            UiState.Loading -> Box(
                Modifier.fillMaxSize().testTag(BlockedUsersTags.LOADING),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.primary)
            }
            UiState.Empty -> EmptyState(
                title = "No has bloqueado a nadie",
                message =
                "Si bloqueas a alguien desde un chat, un match o el grafo, aparecerá aquí " +
                    "para que puedas desbloquearlo.",
                modifier = Modifier.testTag(BlockedUsersTags.EMPTY)
            )
            is UiState.Error -> ErrorState(
                title = "No pudimos cargar tus bloqueos",
                message = safetyErrorMessage(list.error),
                retryLabel = "Reintentar",
                onRetryClick = actions.onRetry,
                modifier = Modifier.testTag(BlockedUsersTags.ERROR)
            )
            is UiState.Loaded -> BlockedList(list.data, state, actions, now)
        }
    }
}

@Composable
private fun BlockedList(
    items: List<BlockedDto>,
    state: BlockedUsersState,
    actions: BlockedUsersActions,
    now: Instant
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(BlockedUsersTags.LIST),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            InfoNote(text = BLOCKED_USERS_NOTE, modifier = Modifier.testTag(BlockedUsersTags.NOTE))
        }
        state.actionError?.let { error -> item { ActionError(error) } }
        items(items, key = { it.userId }) { blocked ->
            BlockedRow(
                blocked = blocked,
                busy = state.unblocking == blocked.userId,
                enabled = state.unblocking == null,
                now = now,
                onUnblock = { actions.onUnblock(blocked.userId) }
            )
        }
    }
}

@Composable
private fun ActionError(error: AppError) {
    Text(
        text = safetyErrorMessage(error),
        style = MaterialTheme.typography.bodySmall,
        color = ParcheUTheme.colors.coralt
    )
}

@Composable
private fun BlockedRow(
    blocked: BlockedDto,
    busy: Boolean,
    enabled: Boolean,
    now: Instant,
    onUnblock: () -> Unit
) {
    val colors = ParcheUTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.s1, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .testTag(BlockedUsersTags.row(blocked.userId)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // El servidor ya presenta a la persona según la regla de visibilidad: sin nombre, es anónima.
        AvatarInitials(
            name = blocked.label,
            affinity = 0,
            anonymous = blocked.label.isAnonymousLabel()
        )
        Column(Modifier.weight(1f)) {
            Text(
                blocked.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.tx,
                maxLines = 1
            )
            Text(
                "Bloqueaste ${relativeDays(blocked.blockedAt, now)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.mu
            )
        }
        OutlineButton(
            text = if (busy) "Desbloqueando…" else "Desbloquear",
            onClick = onUnblock,
            size = ParcheUButtonSize.Small,
            enabled = enabled,
            modifier = Modifier.testTag(BlockedUsersTags.unblock(blocked.userId))
        )
    }
}

private fun String.isAnonymousLabel(): Boolean = this == "Alguien" || startsWith("Alguien de ")
