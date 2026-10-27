package me.santiagoreina.parcheu.feature.plus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.R
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.data.billing.PlusPlans
import me.santiagoreina.parcheu.data.billing.PlusProduct
import me.santiagoreina.parcheu.data.billing.PurchaseFailure
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUIconButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.base.TonalButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Etiquetas de prueba de G1-G4. */
object PlusTags {
    const val PAYWALL = "plus:paywall"
    const val PLAN_ANNUAL = "plus:plan:anual"
    const val PLAN_MONTHLY = "plus:plan:mensual"
    const val PLANS_LOADING = "plus:plans:loading"
    const val PLANS_ERROR = "plus:plans:error"
    const val COMPARISON = "plus:comparison"
    const val SUBSCRIBE = "plus:subscribe"
    const val RESTORE = "plus:restore"
    const val CLOSE = "plus:close"
    const val MESSAGE = "plus:message"
    const val LIMIT = "plus:limit"
    const val LIMIT_SEE_PLUS = "plus:limit:seePlus"
    const val LIMIT_WAIT = "plus:limit:wait"
    const val SUCCESS = "plus:success"
    const val SUCCESS_PENDING = "plus:success:pending"
    const val SUCCESS_EXPLORE = "plus:success:explore"
    const val SUCCESS_HOME = "plus:success:home"
    const val MANAGE = "plus:manage"
    const val MANAGE_LOADING = "plus:manage:loading"
    const val MANAGE_ERROR = "plus:manage:error"
    const val MANAGE_STATUS = "plus:manage:status"
    const val MANAGE_CHANGE = "plus:manage:change"
    const val MANAGE_RESTORE = "plus:manage:restore"
    const val MANAGE_CANCEL = "plus:manage:cancel"
    const val MANAGE_FIX = "plus:manage:fix"
    const val MANAGE_SUBSCRIBE = "plus:manage:subscribe"
    const val MANAGE_BACK = "plus:manage:back"
    const val CANCEL_DIALOG = "plus:manage:cancelDialog"
    const val CANCEL_CONFIRM = "plus:manage:cancelConfirm"
}

/** Acciones de G1; la ruta las conecta con la navegación y el ViewModel. */
data class PaywallActions(
    val onClose: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onSelect: (String) -> Unit = {},
    val onSubscribe: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissMessage: () -> Unit = {}
)

/**
 * G1 Paywall (oscuro) e I4 (claro): anual por defecto, tabla comparativa, cargando precios y error. Todo
 * precio sale de `PlusPlans` (micros de `ProductDetails`); no hay contadores ni urgencia (ADR-0029).
 */
@Composable
fun PaywallScreen(state: PaywallUiState, actions: PaywallActions, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).testTag(PlusTags.PAYWALL)) {
        PaywallHero(state, actions)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(2.dp))
            state.message?.let { MessageCard(it, actions.onDismissMessage) }
            when (val plans = state.plans) {
                PlansUi.Loading -> PlanCardsSkeleton()
                is PlansUi.Error -> PlansError(plans.billingUnavailable, actions.onRetry)
                is PlansUi.Loaded -> PlanCards(plans.plans, state.selected, actions.onSelect)
            }
            ComparisonTable()
            Spacer(Modifier.height(4.dp))
        }
        PaywallFooter(state, actions)
    }
}

@Composable
private fun PaywallHero(state: PaywallUiState, actions: PaywallActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(ParcheUTheme.colors.paywallHeroGradient.brush())
            .padding(start = 4.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ParcheUIconButton(
                icon = rememberPlusIcon(PlusIcons.Close),
                contentDescription = stringResource(R.string.plus_close),
                onClick = actions.onClose,
                modifier = Modifier.testTag(PlusTags.CLOSE)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text =
                stringResource(
                    if (state.restoring) R.string.plus_restoring else R.string.plus_restore
                ),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ParcheUTheme.colors.white,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(
                        enabled = !state.restoring && !state.purchasing,
                        role = Role.Button,
                        onClick = actions.onRestore
                    )
                    .padding(horizontal = 8.dp, vertical = 14.dp)
                    .testTag(PlusTags.RESTORE)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.plus_hero_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                    color = ParcheUTheme.colors.white
                )
                Text(
                    stringResource(R.string.plus_hero_subtitle),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = ParcheUTheme.colors.white
                )
            }
        }
    }
}

@Composable
private fun PlanCards(plans: PlusPlans, selected: String, onSelect: (String) -> Unit) {
    val offer = plans.annualOffer()
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PlanCard(
            title = stringResource(R.string.plus_plan_annual),
            price = plans.annual.formattedPrice,
            lines =
            listOfNotNull(
                stringResource(R.string.plus_plan_annual_per_month, offer.perMonth),
                offer.savings?.let { stringResource(R.string.plus_plan_annual_savings, it) }
            ),
            selected = selected == PlusProduct.BASE_PLAN_ANNUAL,
            onClick = { onSelect(PlusProduct.BASE_PLAN_ANNUAL) },
            tag = PlusTags.PLAN_ANNUAL,
            modifier = Modifier.weight(1f)
        )
        PlanCard(
            title = stringResource(R.string.plus_plan_monthly),
            price = plans.monthly.formattedPrice,
            lines = listOf(stringResource(R.string.plus_plan_monthly_caption)),
            selected = selected == PlusProduct.BASE_PLAN_MONTHLY,
            onClick = { onSelect(PlusProduct.BASE_PLAN_MONTHLY) },
            tag = PlusTags.PLAN_MONTHLY,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    lines: List<String>,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) colors.prc else colors.s1, shape)
            .border(2.dp, if (selected) colors.primary else colors.ln, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.tx
            )
            Box(
                Modifier.size(
                    22.dp
                ).border(2.dp, if (selected) colors.primary else colors.mu, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Box(Modifier.size(10.dp).background(colors.primary, CircleShape))
            }
        }
        Text(price, style = ParcheUTheme.textStyles.price.copy(fontSize = 20.sp), color = colors.tx)
        lines.forEach {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.mu
            )
        }
    }
}

@Composable
private fun PlanCardsSkeleton() {
    val colors = ParcheUTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp).testTag(PlusTags.PLANS_LOADING),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(2) {
            Box(
                Modifier
                    .weight(1f)
                    .height(104.dp)
                    .background(colors.s1, RoundedCornerShape(20.dp))
                    .border(2.dp, colors.ln, RoundedCornerShape(20.dp))
            )
        }
    }
}

@Composable
private fun PlansError(billingUnavailable: Boolean, onRetry: () -> Unit) {
    ParcheUCard(Modifier.testTag(PlusTags.PLANS_ERROR)) {
        Text(
            stringResource(R.string.plus_error_title),
            style = MaterialTheme.typography.titleSmall,
            color = ParcheUTheme.colors.tx
        )
        Text(
            stringResource(
                if (billingUnavailable) R.string.plus_error_billing else R.string.plus_error_product
            ),
            style = MaterialTheme.typography.bodySmall,
            color = ParcheUTheme.colors.mu
        )
        TonalButton(
            text = stringResource(R.string.plus_retry),
            onClick = onRetry,
            size = ParcheUButtonSize.Medium
        )
    }
}

@Composable
internal fun MessageCard(message: PlusMessage, onDismiss: () -> Unit) {
    val colors = ParcheUTheme.colors
    ParcheUCard(Modifier.testTag(PlusTags.MESSAGE)) {
        Text(
            text = messageText(message),
            style = MaterialTheme.typography.bodySmall,
            color = if (message == PlusMessage.Restored) colors.tx else colors.error
        )
        ParcheUTextButton(
            text = stringResource(R.string.plus_msg_dismiss),
            onClick = onDismiss,
            size = ParcheUButtonSize.Small
        )
    }
}

@Composable
internal fun messageText(message: PlusMessage): String = stringResource(
    when (message) {
        is PlusMessage.PurchaseFailed ->
            when (val failure = message.failure) {
                PurchaseFailure.BillingUnavailable -> R.string.plus_msg_purchase_unavailable
                is PurchaseFailure.Verification ->
                    if (failure.error == AppError.Forbidden) {
                        R.string.plus_msg_purchase_forbidden
                    } else {
                        R.string.plus_msg_purchase_unverified
                    }
                is PurchaseFailure.Play -> R.string.plus_msg_purchase_failed
            }
        PlusMessage.NothingToRestore -> R.string.plus_msg_nothing_to_restore
        is PlusMessage.RestoreFailed -> R.string.plus_msg_restore_failed
        PlusMessage.Restored -> R.string.plus_msg_restored
        PlusMessage.NoAccount -> R.string.plus_msg_no_account
    }
)

private data class ComparisonRow(val label: Int, val free: Int, val plus: Int)

private val ComparisonRows =
    listOf(
        ComparisonRow(
            R.string.plus_row_simulations,
            R.string.plus_row_simulations_free,
            R.string.plus_row_simulations_plus
        ),
        ComparisonRow(
            R.string.plus_row_match,
            R.string.plus_row_match_free,
            R.string.plus_row_match_plus
        ),
        ComparisonRow(
            R.string.plus_row_report,
            R.string.plus_row_report_free,
            R.string.plus_row_report_plus
        ),
        ComparisonRow(
            R.string.plus_row_graph,
            R.string.plus_row_graph_free,
            R.string.plus_row_graph_plus
        ),
        ComparisonRow(
            R.string.plus_row_communities,
            R.string.plus_row_communities_free,
            R.string.plus_row_communities_plus
        ),
        ComparisonRow(
            R.string.plus_row_scenarios,
            R.string.plus_row_scenarios_free,
            R.string.plus_row_scenarios_plus
        ),
        ComparisonRow(
            R.string.plus_row_connect,
            R.string.plus_row_connect_free,
            R.string.plus_row_connect_plus
        ),
        ComparisonRow(
            R.string.plus_row_safety,
            R.string.plus_row_safety_free,
            R.string.plus_row_safety_plus
        )
    )

@Composable
private fun ComparisonTable() {
    val colors = ParcheUTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.s1, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag(PlusTags.COMPARISON)
    ) {
        TableLine(
            label = "",
            free = stringResource(R.string.plus_table_free),
            plus = stringResource(R.string.plus_table_plus),
            header = true
        )
        ComparisonRows.forEachIndexed { index, row ->
            TableLine(
                label = stringResource(row.label),
                free = stringResource(row.free),
                plus = stringResource(row.plus),
                header = false,
                last = index == ComparisonRows.lastIndex
            )
        }
    }
}

@Composable
private fun TableLine(
    label: String,
    free: String,
    plus: String,
    header: Boolean,
    last: Boolean = false
) {
    val colors = ParcheUTheme.colors
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp)
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                label,
                style = small.copy(fontSize = 12.5.sp),
                color = colors.tx,
                modifier = Modifier.weight(1.35f)
            )
            Text(
                free,
                style = small.copy(
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = colors.mu,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                plus,
                style = small.copy(fontWeight = FontWeight.SemiBold),
                color = if (header) colors.ort else colors.tx,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.ln))
    }
}

@Composable
private fun PaywallFooter(state: PaywallUiState, actions: PaywallActions) {
    val loaded = (state.plans as? PlansUi.Loaded)?.plans
    val selectedPlan = loaded?.plan(state.selected)
    val label =
        when {
            state.purchasing -> stringResource(R.string.plus_subscribing)
            selectedPlan == null -> stringResource(R.string.plus_cta_loading)
            state.selected == PlusProduct.BASE_PLAN_ANNUAL ->
                stringResource(R.string.plus_cta_annual, selectedPlan.formattedPrice)
            else -> stringResource(R.string.plus_cta_monthly, selectedPlan.formattedPrice)
        }
    Column(
        Modifier
            .fillMaxWidth()
            .background(ParcheUTheme.colors.bg)
            .padding(ParcheUTheme.spacing.footer)
            .padding(top = 0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PrimaryButton(
            text = label,
            onClick = actions.onSubscribe,
            enabled = selectedPlan != null && !state.purchasing,
            modifier = Modifier.fillMaxWidth().testTag(PlusTags.SUBSCRIBE)
        )
        Text(
            stringResource(R.string.plus_footnote),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = ParcheUTheme.colors.mu,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
