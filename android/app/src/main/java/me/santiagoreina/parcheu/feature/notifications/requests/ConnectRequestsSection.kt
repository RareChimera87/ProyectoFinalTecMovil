package me.santiagoreina.parcheu.feature.notifications.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.contract.ConnectRequest
import me.santiagoreina.parcheu.designsystem.R as DsR
import me.santiagoreina.parcheu.designsystem.components.base.ModeSpineCard
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarInitials
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarSizes
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.parche.ParchePlusEntry
import me.santiagoreina.parcheu.feature.parche.parcheSpine

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

/** Gratis (Parche Vivo): la misma tarjeta tonal de ParcheU+ que en Tu parche, con candado. */
@Composable
private fun LockedItem(count: Int, onClick: () -> Unit) {
    ParchePlusEntry(
        title = lockedTitle(count),
        subtitle = "$LOCKED_BODY · $LOCKED_HINT_TEXT",
        locked = true,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        tag = ConnectRequestsTags.LOCKED
    )
}

/**
 * ParcheU+ (Parche Vivo): una ModeSpineCard por persona con el lomo violeta de "por conocer" (la
 * solicitud no trae modo) y la afinidad en Sora 800 a la derecha.
 */
@Composable
private fun RequestItem(request: ConnectRequest, onOpen: (ConnectRequest) -> Unit) {
    val colors = ParcheUTheme.colors
    ModeSpineCard(
        mode = parcheSpine(),
        onClick = { onOpen(request) },
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .testTag(ConnectRequestsTags.item(request.simulationId))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvatarInitials(
                name = request.other.label,
                affinity = request.affinity,
                size = AvatarSizes.List,
                anonymous = true,
                anonymousIcon = painterResource(DsR.drawable.ic_person)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    request.other.label,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.tx,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(REQUEST_BODY, style = MaterialTheme.typography.bodySmall, color = colors.mu)
            }
            Text(
                affinityText(request.affinity),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = colors.prt
            )
        }
    }
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
