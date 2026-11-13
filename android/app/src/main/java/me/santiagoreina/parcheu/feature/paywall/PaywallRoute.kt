package me.santiagoreina.parcheu.feature.paywall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.billing.BillingRepository
import me.santiagoreina.parcheu.feature.basicprofile.BasicProfileRepository
import me.santiagoreina.parcheu.feature.plus.PaywallEntry
import me.santiagoreina.parcheu.feature.plus.PlusEntry
import me.santiagoreina.parcheu.feature.plus.PlusSources
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Paywall
import org.koin.compose.koinInject

/**
 * Punto de entrada de la ruta [Paywall] (G1, I4 y la hoja G2). La pantalla vive en `feature/plus` (E7.3);
 * este archivo solo la conecta con AppNavHost.kt, que no cambia.
 *
 * La hoja G2 elige entre la versión gratis y la de uso justo con el `plus` que confirma el servidor en
 * `GET /v1/me` (el mismo dato de Inicio y Perfil). El derecho en memoria de [BillingRepository] está vacío
 * hasta abrir G1 o G4, así que solo sirve de respaldo si `/v1/me` falla. Mientras `/v1/me` responde, la hoja
 * no se dibuja, para no mostrar un instante la versión equivocada.
 */
@Composable
fun PaywallRoute(
    route: Paywall,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    billing: BillingRepository = koinInject(),
    profile: BasicProfileRepository = koinInject()
) {
    val entitlement by billing.entitlement.collectAsStateWithLifecycle()
    val isLimit = PlusSources.parse(route.source) is PlusEntry.Limit
    val serverPlus by
        produceState<ServerPlus>(ServerPlus.Unknown, profile, isLimit) {
            if (!isLimit) return@produceState
            value =
                when (val me = profile.me()) {
                    is ApiResult.Success -> ServerPlus.Known(me.value.plus)
                    is ApiResult.Failure -> ServerPlus.Unavailable
                }
        }
    val plus: Boolean? =
        when (val known = serverPlus) {
            is ServerPlus.Known -> known.plus
            ServerPlus.Unavailable -> entitlement?.plus == true
            ServerPlus.Unknown -> if (isLimit) null else entitlement?.plus == true
        }
    if (plus != null) {
        PaywallEntry(route = route, navigator = navigator, modifier = modifier, isPlus = plus)
    }
}

/** Lo que se sabe de `MeDto.plus` mientras se abre la ruta. */
private sealed interface ServerPlus {
    data object Unknown : ServerPlus

    data object Unavailable : ServerPlus

    data class Known(val plus: Boolean) : ServerPlus
}
