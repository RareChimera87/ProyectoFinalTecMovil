package me.santiagoreina.parcheu.feature.notifications.requests

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.contract.ConnectRequest
import me.santiagoreina.parcheu.designsystem.R as DsR
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarInitials
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarSizes
import me.santiagoreina.parcheu.designsystem.components.compound.IconCircle
import me.santiagoreina.parcheu.designsystem.components.compound.NotificationRow
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Etiquetas de prueba de la sección. */
object ConnectRequestsTags {
    const val SECTION = "connectRequests:section"
    const val LOCKED = "connectRequests:locked"

    fun item(simulationId: String) = "connectRequests:item:$simulationId"
}

/** Acciones de la sección; la ruta de C2 las conecta con el ViewModel. */
data class ConnectRequestsCallbacks(
    val onOpenLocked: () -> Unit = {},
    val onOpen: (ConnectRequest) -> Unit = {}
)

/**
 * 'Quieren conectar contigo' (C2, E6.6). Gratis: un ítem bloqueado con el conteo ("2 personas quieren
 * conectar contigo") que lleva al paywall; el servidor no manda quiénes son. Plus: una fila por persona
 * (siempre enmascarada: nombre y foto llegan solo con la conexión mutua) con su afinidad. Sin solicitudes,
 * no se dibuja nada.
 */
@Composable
fun ConnectRequestsSection(
    state: ConnectRequestsUiState,
    callbacks: ConnectRequestsCallbacks,
    modifier: Modifier = Modifier
) {
    if (state is ConnectRequestsUiState.Hidden) return
    Column(modifier.testTag(ConnectRequestsTags.SECTION)) {
        Text(
            text = CONNECT_REQUESTS_TITLE.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            ),
            color = ParcheUTheme.colors.mu,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)
                .semantics { heading() }
        )
        when (state) {
            is ConnectRequestsUiState.Locked -> LockedItem(state.count, callbacks.onOpenLocked)
            is ConnectRequestsUiState.Plus ->
                state.items.forEach { request -> RequestItem(request, callbacks.onOpen) }
            ConnectRequestsUiState.Hidden -> Unit
        }
    }
}

@Composable
private fun LockedItem(count: Int, onClick: () -> Unit) {
    val colors = ParcheUTheme.colors
    NotificationRow(
        title = lockedTitle(count),
        body = LOCKED_BODY,
        time = LOCKED_HINT_TEXT,
        unread = false,
        onClick = onClick,
        leading = {
            IconCircle(
                icon = painterResource(DsR.drawable.ic_lock),
                container = colors.s2,
                tint = colors.tx
            )
        },
        modifier = Modifier.testTag(ConnectRequestsTags.LOCKED)
    )
}

@Composable
private fun RequestItem(request: ConnectRequest, onOpen: (ConnectRequest) -> Unit) {
    NotificationRow(
        title = request.other.label,
        body = REQUEST_BODY,
        time = affinityText(request.affinity),
        unread = false,
        onClick = { onOpen(request) },
        leading = {
            AvatarInitials(
                name = request.other.label,
                affinity = request.affinity,
                size = AvatarSizes.List,
                anonymous = true,
                anonymousIcon = painterResource(DsR.drawable.ic_person)
            )
        },
        modifier = Modifier.testTag(ConnectRequestsTags.item(request.simulationId))
    )
}

internal const val CONNECT_REQUESTS_TITLE = "Quieren conectar contigo"
internal const val LOCKED_BODY = "Descubre quiénes son"
internal const val LOCKED_HINT_TEXT = "Míralas con ParcheU+"
internal const val REQUEST_BODY = "Quiere conectar contigo"

internal fun lockedTitle(count: Int): String = if (count ==
    1
) {
    "1 persona quiere conectar contigo"
} else {
    "$count personas quieren conectar contigo"
}

internal fun affinityText(affinity: Int): String = "$affinity % afín"
