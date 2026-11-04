package me.santiagoreina.parcheu.feature.presentar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.designsystem.components.base.ModeSegment
import me.santiagoreina.parcheu.designsystem.components.base.ModeSegmentedControl
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCheckbox
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.base.SectionHeader
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarInitials
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarSizes
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.simulation.forMode
import me.santiagoreina.parcheu.feature.simulation.label

/** Etiquetas de prueba de E1. */
object IntroduceTags {
    const val ROOT = "introduce:root"
    const val LOADING = "introduce:loading"
    const val ERROR = "introduce:error"
    const val EMPTY = "introduce:empty"
    const val PAIR = "introduce:pair"
    const val SUBMIT = "introduce:submit"
    const val LIMIT = "introduce:limit"
    const val SEE_PLUS = "introduce:seePlus"

    fun friend(id: String) = "introduce:friend:$id"
}

data class IntroduceActions(
    val onBack: () -> Unit = {},
    val onMode: (Mode) -> Unit = {},
    val onToggle: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onSeePlus: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissNotice: () -> Unit = {}
)

/** E1 · Elegir modo y dos amigos: 0, 1 o 2 elegidos y límite de simulaciones alcanzado. */
@Composable
fun IntroduceScreen(
    state: IntroduceContent,
    actions: IntroduceActions,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).testTag(IntroduceTags.ROOT)) {
        ParcheUChildTopBar(
            title = "Presentar a dos amigos",
            navigationIcon = rememberPresentarIcon(PresentarIcons.ArrowBack),
            navigationContentDescription = "Volver",
            onNavigationClick = actions.onBack
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ModeSegmentedControl(
                segments = INTRODUCE_MODES.map { ModeSegment(it.label(), colors.forMode(it)) },
                selectedIndex = INTRODUCE_MODES.indexOf(state.mode).coerceAtLeast(0),
                onSelect = { actions.onMode(INTRODUCE_MODES[it]) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            )
            PairPreview(state)
            SectionHeader(title = "Tus amigos · ${state.selected.size} de $INTRODUCE_PICK")
            when {
                state.loadingFriends -> PresentarLoadingRow()
                state.friendsError != null -> Column(
                    Modifier.fillMaxWidth().testTag(IntroduceTags.ERROR),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        presentarErrorText(state.friendsError),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.mu,
                        textAlign = TextAlign.Center
                    )
                    ParcheUTextButton("Reintentar", onClick = actions.onRetry)
                }
                state.friends.size < INTRODUCE_PICK -> Text(
                    "Necesitas al menos dos amigos conectados en ${state.mode.label()} " +
                        "para presentarlos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mu,
                    modifier = Modifier.testTag(IntroduceTags.EMPTY)
                )
                else -> Column {
                    state.friends.forEach { friend ->
                        FriendSelectRow(
                            friend = friend,
                            checked = friend.id in state.selected,
                            enabled = !state.busy && state.isSelectable(friend.id),
                            onToggle = { actions.onToggle(friend.id) }
                        )
                    }
                }
            }
            PresentarHint(
                "Pareja no está disponible aquí.",
                rememberPresentarIcon(PresentarIcons.Info)
            )
            when (state.notice) {
                IntroduceNotice.QuotaExceeded, null -> Unit
                is IntroduceNotice.Failed ->
                    PresentarNotice(presentarErrorText(state.notice.error), actions.onDismissNotice)
            }
        }
        Footer(state, actions)
    }
}

@Composable
private fun PairPreview(state: IntroduceContent) {
    val colors = ParcheUTheme.colors
    val chosen = state.chosen
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).testTag(IntroduceTags.PAIR),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top
    ) {
        PairSlot(chosen.getOrNull(0))
        Box(Modifier.padding(top = 27.dp).size(36.dp, 2.dp).background(colors.ln))
        Box(
            Modifier.padding(top = 10.dp).size(36.dp).clip(CircleShape).background(colors.prc),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                rememberPresentarIcon(PresentarIcons.People),
                contentDescription = null,
                tint = colors.onprc,
                modifier = Modifier.size(20.dp)
            )
        }
        Box(Modifier.padding(top = 27.dp).size(36.dp, 2.dp).background(colors.ln))
        PairSlot(chosen.getOrNull(1))
    }
}

@Composable
private fun PairSlot(friend: FriendOption?) {
    Column(
        Modifier.width(88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (friend != null) {
            AvatarInitials(friend.label, friend.affinity, size = AvatarSizes.Xl)
            Text(
                friend.label.substringBefore(' '),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = ParcheUTheme.colors.tx,
                maxLines = 1
            )
        } else {
            AvatarInitials(
                "",
                0,
                size = AvatarSizes.Xl,
                anonymous = true,
                anonymousDescription = "Sin elegir"
            )
            Text(
                "Elige",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                color = ParcheUTheme.colors.mu
            )
        }
    }
}

/** FriendSelectRow: fila de 60dp con avatar, nombre, afinidad y casilla. */
@Composable
private fun FriendSelectRow(
    friend: FriendOption,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val colors = ParcheUTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 60.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = {
                onToggle()
            })
            .alpha(if (enabled) 1f else 0.5f)
            .testTag(IntroduceTags.friend(friend.id)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AvatarInitials(friend.label, friend.affinity, size = AvatarSizes.List)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                friend.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = colors.tx
            )
            Text(
                "Afinidad ${friend.affinity} % contigo",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = colors.mu
            )
        }
        ParcheUCheckbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            contentDescription = "Elegir a ${friend.label}",
            // La fila ya es el único objetivo para TalkBack (toggleable con rol Checkbox): la casilla es solo visual.
            modifier = Modifier.clearAndSetSemantics {},
            enabled = enabled
        )
    }
}

@Composable
private fun PresentarLoadingRow() {
    Box(
        Modifier.fillMaxWidth().padding(24.dp).testTag(IntroduceTags.LOADING),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = ParcheUTheme.colors.primary)
    }
}

@Composable
private fun Footer(state: IntroduceContent, actions: IntroduceActions) {
    val colors = ParcheUTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.limitReached) {
            Text(
                if (state.plus) {
                    "Ya usaste tus simulaciones de hoy. Vuelven mañana."
                } else {
                    "Ya usaste tus simulaciones de esta semana. Vuelven el lunes o simula " +
                        "sin límite con ParcheU+."
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = colors.mu,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag(IntroduceTags.LIMIT)
            )
            if (!state.plus) {
                PrimaryButton(
                    text = "Ver ParcheU+",
                    onClick = actions.onSeePlus,
                    modifier = Modifier.fillMaxWidth().testTag(IntroduceTags.SEE_PLUS)
                )
            }
        } else {
            PrimaryButton(
                text = if (state.busy) "Simulando…" else "Simular entre ellos",
                onClick = actions.onSubmit,
                enabled = state.canSubmit,
                icon = rememberPresentarIcon(PresentarIcons.Sparkle),
                modifier = Modifier.fillMaxWidth().testTag(IntroduceTags.SUBMIT)
            )
            Text(
                quotaText(state),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.mu,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

internal fun quotaText(state: IntroduceContent): String = when (val left = state.simulationsLeft) {
    null -> if (state.plus) {
        "Con ParcheU+ simulas sin límite semanal"
    } else {
        "Usa 1 de tus simulaciones de la semana"
    }
    1 -> "Usa tu última simulación de la semana"
    else -> if (state.plus) {
        "Usa 1 de tus $left simulaciones de hoy"
    } else {
        "Usa 1 de tus $left simulaciones restantes"
    }
}
