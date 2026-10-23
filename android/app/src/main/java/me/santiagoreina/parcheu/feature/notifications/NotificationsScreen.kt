package me.santiagoreina.parcheu.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.NotificationDto
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.designsystem.R as DsR
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarInitials
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarSizes
import me.santiagoreina.parcheu.designsystem.components.compound.EmptyState
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.components.compound.IconCircle
import me.santiagoreina.parcheu.designsystem.components.compound.NotificationRow
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Etiquetas de prueba de C2. */
object NotificationsTags {
    const val ROOT = "notifications:root"
    const val LIST = "notifications:list"
    const val EMPTY = "notifications:empty"
    const val ERROR = "notifications:error"
    const val MARK_ALL = "notifications:markAll"
    const val ACTION_ERROR = "notifications:actionError"

    fun item(id: String) = "notifications:item:$id"

    fun locked(id: String) = "notifications:locked:$id"
}

/** Acciones de C2; la ruta las conecta con la navegación y el ViewModel. */
data class NotificationsCallbacks(
    val onBack: () -> Unit = {},
    val onOpen: (NotificationDto) -> Unit = {},
    val onMarkAllRead: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissError: () -> Unit = {}
)

/**
 * C2 · Notificaciones (mockup 05): grupos Hoy / Esta semana / Anteriores, punto de no leída, el aviso
 * bloqueado 'quieren conectar contigo' para el plan gratis, 'Marcar leídas' y el estado vacío.
 */
@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    callbacks: NotificationsCallbacks,
    now: Instant,
    modifier: Modifier = Modifier
) {
    val loaded = state as? NotificationsUiState.Loaded
    Column(
        modifier.fillMaxSize().background(ParcheUTheme.colors.bg).testTag(NotificationsTags.ROOT)
    ) {
        ParcheUChildTopBar(
            title = "Notificaciones",
            navigationIcon = painterResource(DsR.drawable.ic_arrow_back),
            navigationContentDescription = "Volver",
            onNavigationClick = callbacks.onBack,
            action = {
                if (loaded != null && !loaded.isEmpty) {
                    ParcheUTextButton(
                        text = "Marcar leídas",
                        onClick = callbacks.onMarkAllRead,
                        enabled = loaded.unread > 0,
                        size = ParcheUButtonSize.Medium,
                        modifier = Modifier.testTag(NotificationsTags.MARK_ALL)
                    )
                }
            }
        )
        when (state) {
            NotificationsUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ParcheUTheme.colors.primary)
                }
            is NotificationsUiState.Error ->
                ErrorState(
                    title = "No pudimos cargar tus notificaciones",
                    message = notificationsErrorMessage(state.error),
                    retryLabel = "Reintentar",
                    onRetryClick = callbacks.onRetry,
                    modifier = Modifier.fillMaxSize().testTag(NotificationsTags.ERROR)
                )
            is NotificationsUiState.Loaded ->
                if (state.isEmpty) {
                    EmptyState(
                        title = "Todo al día",
                        message = EMPTY_MESSAGE,
                        illustration = {
                            IconCircle(
                                icon = painterResource(DsR.drawable.ic_notifications),
                                size = 72.dp
                            )
                        },
                        modifier = Modifier.fillMaxSize().testTag(NotificationsTags.EMPTY)
                    )
                } else {
                    Inbox(state, callbacks, now)
                }
        }
    }
}

@Composable
private fun Inbox(
    state: NotificationsUiState.Loaded,
    callbacks: NotificationsCallbacks,
    now: Instant
) {
    val colors = ParcheUTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().testTag(NotificationsTags.LIST),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        state.actionError?.let { error ->
            item(key = "actionError") {
                Text(
                    text = notificationsErrorMessage(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Descartar",
                            onClick = callbacks.onDismissError
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(NotificationsTags.ACTION_ERROR)
                )
            }
        }
        section(SECTION_TODAY, state.inbox.today, callbacks, now)
        section(SECTION_WEEK, state.inbox.week, callbacks, now)
        section(SECTION_OLDER, state.inbox.older, callbacks, now)
    }
}

private fun LazyListScope.section(
    title: String,
    items: List<NotificationDto>,
    callbacks: NotificationsCallbacks,
    now: Instant
) {
    if (items.isEmpty()) return
    item(key = "header:$title") { SectionHeader(title) }
    items(items, key = { it.id }) { item ->
        NotificationRow(
            title = item.title,
            body = item.body,
            time = footerOf(item, now),
            unread = !item.read,
            onClick = { callbacks.onOpen(item) },
            leading = { Leading(item) },
            modifier = Modifier.testTag(NotificationsTags.item(item.id))
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        ),
        color = ParcheUTheme.colors.mu,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)
            .semantics { heading() }
    )
}

/** Candado para lo bloqueado, avatar de iniciales si hay actor y, si no, el icono del tipo. */
@Composable
private fun Leading(item: NotificationDto) {
    val colors = ParcheUTheme.colors
    val actor = item.actor
    when {
        item.locked ->
            IconCircle(
                icon = painterResource(DsR.drawable.ic_lock),
                container = colors.s2,
                tint = colors.tx,
                modifier = Modifier.testTag(NotificationsTags.locked(item.id))
            )
        actor != null ->
            AvatarInitials(
                name = actor.label,
                affinity = ACTOR_AVATAR_AFFINITY,
                size = AvatarSizes.List,
                anonymous = actor.id == null,
                anonymousIcon = painterResource(DsR.drawable.ic_person)
            )
        else ->
            IconCircle(
                icon = painterResource(iconOf(item.kind)),
                container = if (item.kind in TWIN_KINDS) colors.prc else colors.s2,
                tint = if (item.kind in TWIN_KINDS) colors.onprc else colors.tx
            )
    }
}

private const val EMPTY_MESSAGE =
    "Aquí verás tus resultados, presentaciones, invitaciones y los avisos de tu gemelo."

/** Alpha del avatar del mockup 05 (violeta casi sólido). */
private const val ACTOR_AVATAR_AFFINITY = 90

private val TWIN_KINDS = setOf(NotificationKinds.TWIN_MEMORIES, NotificationKinds.TWIN_FEEDBACK)

private fun iconOf(kind: String): Int = when (kind) {
    NotificationKinds.SIMULATION_READY,
    NotificationKinds.SIMULATION_FAILED -> DsR.drawable.ic_bubble_chart
    NotificationKinds.MATCH_FOUND, NotificationKinds.MATCH_EXPIRED -> DsR.drawable.ic_shuffle
    NotificationKinds.CONNECT_REQUEST, NotificationKinds.NEW_CONNECTION -> DsR.drawable.ic_group
    NotificationKinds.INVITE_PENDING -> DsR.drawable.ic_person_add
    NotificationKinds.INTRODUCTION_PROPOSAL -> DsR.drawable.ic_handshake
    NotificationKinds.CHAT_MESSAGE -> DsR.drawable.ic_chat_bubble
    NotificationKinds.TWIN_MEMORIES, NotificationKinds.TWIN_FEEDBACK -> DsR.drawable.ic_auto_awesome
    NotificationKinds.COMMUNITY -> DsR.drawable.ic_diversity_3
    else -> DsR.drawable.ic_autorenew
}
