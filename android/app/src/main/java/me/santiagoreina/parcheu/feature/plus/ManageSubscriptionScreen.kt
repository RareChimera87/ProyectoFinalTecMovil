package me.santiagoreina.parcheu.feature.plus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.R
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.designsystem.components.base.OutlineButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCardVariant
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUPlusBadge
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PlusBadgeSize
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.components.compound.ParcheUModalDialog
import me.santiagoreina.parcheu.designsystem.components.compound.SettingsRow
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Acciones de G4. Cancelar, cambiar de plan y arreglar el pago llevan a Google Play ([onOpenPlay]). */
data class ManageSubscriptionActions(
    val onBack: () -> Unit = {},
    val onOpenPlay: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSubscribe: () -> Unit = {},
    val onDismissMessage: () -> Unit = {}
)

/**
 * G4 Gestionar suscripción: activa anual o mensual, cancelada, en gracia o en pausa, y sin suscripción.
 * Restaurar compras se hace aquí; cancelar pide confirmación y abre la gestión de Play (la cancelación la
 * ejecuta Google, no la app). `showCancelDialog` es parte del estado de la pantalla para poder capturarlo.
 */
@Composable
fun ManageSubscriptionScreen(
    state: ManageUiState,
    actions: ManageSubscriptionActions,
    modifier: Modifier = Modifier,
    showCancelDialog: Boolean = false,
    onShowCancelDialog: (Boolean) -> Unit = {}
) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).testTag(PlusTags.MANAGE)) {
        ParcheUChildTopBar(
            title = stringResource(R.string.plus_manage_title),
            navigationIcon = rememberPlusIcon(PlusIcons.ArrowBack),
            navigationContentDescription = stringResource(R.string.plus_manage_back),
            onNavigationClick = actions.onBack
        )
        when (state) {
            ManageUiState.Loading ->
                Box(
                    Modifier.fillMaxSize().testTag(PlusTags.MANAGE_LOADING),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.primary)
                }
            is ManageUiState.Error ->
                ErrorState(
                    title = stringResource(R.string.plus_manage_error_title),
                    message = stringResource(R.string.plus_manage_error_message),
                    retryLabel = stringResource(R.string.plus_retry),
                    onRetryClick = actions.onRetry,
                    modifier = Modifier.testTag(PlusTags.MANAGE_ERROR)
                )
            is ManageUiState.Loaded -> {
                LoadedContent(state, actions, onShowCancelDialog)
                if (showCancelDialog) {
                    CancelDialog(
                        onConfirm = {
                            onShowCancelDialog(false)
                            actions.onOpenPlay()
                        },
                        onKeep = { onShowCancelDialog(false) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadedContent(
    state: ManageUiState.Loaded,
    actions: ManageSubscriptionActions,
    onShowCancelDialog: (Boolean) -> Unit
) {
    val colors = ParcheUTheme.colors
    val entitlement = state.entitlement
    val status = entitlement.status()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        state.message?.let { MessageCard(it, actions.onDismissMessage) }
        PlanCard(state, status)
        Column {
            if (status == SubscriptionStatus.Active && entitlement.isAnnual) {
                SettingsRow(
                    title = stringResource(R.string.plus_manage_change_monthly),
                    subtitle = state.plans?.monthly?.formattedPrice?.let {
                        stringResource(R.string.plus_manage_change_monthly_detail, it)
                    } ?: stringResource(R.string.plus_manage_change_monthly_detail_nopr),
                    icon = rememberPlusIcon(PlusIcons.SwapHoriz),
                    onClick = actions.onOpenPlay,
                    trailing = { Chevron() },
                    modifier = Modifier.testTag(PlusTags.MANAGE_CHANGE)
                )
            }
            if (status == SubscriptionStatus.Grace || status == SubscriptionStatus.OnHold) {
                SettingsRow(
                    title = stringResource(R.string.plus_manage_fix_payment),
                    subtitle = stringResource(R.string.plus_manage_fix_payment_detail),
                    icon = rememberPlusIcon(PlusIcons.CreditCard),
                    onClick = actions.onOpenPlay,
                    trailing = { Chevron() },
                    modifier = Modifier.testTag(PlusTags.MANAGE_FIX)
                )
            }
            SettingsRow(
                title = stringResource(R.string.plus_manage_restore),
                subtitle = stringResource(
                    if (state.restoring) {
                        R.string.plus_restoring
                    } else {
                        R.string.plus_manage_restore_detail
                    }
                ),
                icon = rememberPlusIcon(PlusIcons.Restore),
                onClick = actions.onRestore,
                enabled = !state.restoring,
                trailing = { Chevron() },
                modifier = Modifier.testTag(PlusTags.MANAGE_RESTORE)
            )
            when (status) {
                SubscriptionStatus.Active ->
                    SettingsRow(
                        title = stringResource(R.string.plus_manage_cancel),
                        subtitle = stringResource(R.string.plus_manage_cancel_detail),
                        icon = rememberPlusIcon(PlusIcons.Cancel),
                        onClick = { onShowCancelDialog(true) },
                        destructive = true,
                        modifier = Modifier.testTag(PlusTags.MANAGE_CANCEL)
                    )
                SubscriptionStatus.Canceled ->
                    SettingsRow(
                        title = stringResource(R.string.plus_manage_reactivate),
                        icon = rememberPlusIcon(PlusIcons.Restore),
                        onClick = actions.onOpenPlay,
                        trailing = { Chevron() },
                        modifier = Modifier.testTag(PlusTags.MANAGE_FIX)
                    )
                else -> Unit
            }
        }
        if (status == SubscriptionStatus.Inactive) {
            PrimaryButton(
                text = stringResource(R.string.plus_manage_subscribe),
                onClick = actions.onSubscribe,
                modifier = Modifier.fillMaxWidth().testTag(PlusTags.MANAGE_SUBSCRIBE)
            )
        }
        infoText(entitlement, status)?.let { info ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    rememberPlusIcon(PlusIcons.Info),
                    contentDescription = null,
                    tint = colors.mu,
                    modifier = Modifier.size(18.dp)
                )
                Text(info, style = MaterialTheme.typography.bodySmall, color = colors.mu)
            }
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        rememberPlusIcon(PlusIcons.ChevronRight),
        contentDescription = null,
        tint = ParcheUTheme.colors.mu,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun PlanCard(state: ManageUiState.Loaded, status: SubscriptionStatus) {
    val colors = ParcheUTheme.colors
    val entitlement = state.entitlement
    val variant =
        if (status ==
            SubscriptionStatus.Inactive
        ) {
            ParcheUCardVariant.Standard
        } else {
            ParcheUCardVariant.ActivePlan
        }
    ParcheUCard(Modifier.fillMaxWidth().testTag(PlusTags.MANAGE_STATUS), variant = variant) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ParcheUPlusBadge(size = PlusBadgeSize.Large)
            Text(
                stringResource(statusLabel(status)),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (status == SubscriptionStatus.Active) colors.prt else colors.mu
            )
        }
        Text(
            planTitle(state),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.tx
        )
        Text(
            detailText(entitlement, status),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
            color = colors.mu
        )
    }
}

private fun statusLabel(status: SubscriptionStatus): Int = when (status) {
    SubscriptionStatus.Active -> R.string.plus_manage_status_active
    SubscriptionStatus.Canceled -> R.string.plus_manage_status_canceled
    SubscriptionStatus.Grace -> R.string.plus_manage_status_grace
    SubscriptionStatus.OnHold -> R.string.plus_manage_status_on_hold
    SubscriptionStatus.Inactive -> R.string.plus_manage_status_inactive
}

@Composable
private fun planTitle(state: ManageUiState.Loaded): String {
    val entitlement = state.entitlement
    val plan = state.plans?.plan(entitlement.basePlan.orEmpty())
    return when {
        entitlement.status() == SubscriptionStatus.Inactive ->
            stringResource(R.string.plus_manage_plan_none)
        entitlement.isAnnual && plan != null ->
            stringResource(R.string.plus_manage_plan_annual_price, plan.formattedPrice)
        entitlement.isAnnual -> stringResource(R.string.plus_manage_plan_annual)
        plan != null -> stringResource(R.string.plus_manage_plan_monthly_price, plan.formattedPrice)
        else -> stringResource(R.string.plus_manage_plan_monthly)
    }
}

@Composable
private fun detailText(entitlement: EntitlementDto, status: SubscriptionStatus): String {
    val date = entitlement.expiresAt?.let(::formatLongDate)
    val lines =
        when (status) {
            SubscriptionStatus.Active ->
                listOfNotNull(
                    date?.let { stringResource(R.string.plus_manage_renews, it) },
                    stringResource(R.string.plus_manage_pay_with_play)
                )
            SubscriptionStatus.Canceled ->
                listOfNotNull(
                    date?.let { stringResource(R.string.plus_manage_ends, it) },
                    stringResource(R.string.plus_manage_pay_with_play)
                )
            SubscriptionStatus.Grace ->
                listOfNotNull(date?.let { stringResource(R.string.plus_manage_grace_until, it) })
            SubscriptionStatus.OnHold -> listOf(stringResource(R.string.plus_manage_on_hold_detail))
            SubscriptionStatus.Inactive -> listOf(
                stringResource(R.string.plus_manage_inactive_detail)
            )
        }
    return lines.joinToString("\n")
}

@Composable
private fun infoText(entitlement: EntitlementDto, status: SubscriptionStatus): String? {
    val date = entitlement.expiresAt?.let(::formatLongDate)
    return when (status) {
        SubscriptionStatus.Active -> date?.let {
            stringResource(R.string.plus_manage_info_active, it)
        }
        SubscriptionStatus.Canceled -> date?.let {
            stringResource(R.string.plus_manage_info_canceled, it)
        }
        SubscriptionStatus.Inactive -> stringResource(R.string.plus_manage_info_inactive)
        else -> null
    }
}

@Composable
private fun CancelDialog(onConfirm: () -> Unit, onKeep: () -> Unit) {
    ParcheUModalDialog(onDismissRequest = onKeep) { CancelDialogBody(onConfirm, onKeep) }
}

/** Contenido del diálogo de cancelar; aparte de la ventana para poder capturarlo sin ella. */
@Composable
internal fun ColumnScope.CancelDialogBody(onConfirm: () -> Unit, onKeep: () -> Unit) {
    Column(
        Modifier.testTag(PlusTags.CANCEL_DIALOG),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.plus_cancel_dialog_title),
            style = MaterialTheme.typography.headlineSmall,
            color = ParcheUTheme.colors.tx
        )
        Text(
            stringResource(R.string.plus_cancel_dialog_body),
            style = MaterialTheme.typography.bodyMedium,
            color = ParcheUTheme.colors.mu
        )
        PrimaryButton(
            text = stringResource(R.string.plus_cancel_dialog_keep),
            onClick = onKeep,
            modifier = Modifier.fillMaxWidth()
        )
        OutlineButton(
            text = stringResource(R.string.plus_cancel_dialog_confirm),
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().testTag(PlusTags.CANCEL_CONFIRM)
        )
    }
}
