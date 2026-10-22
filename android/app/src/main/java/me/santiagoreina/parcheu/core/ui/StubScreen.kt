package me.santiagoreina.parcheu.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.BuildConfig
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.catalogEntryFor
import me.santiagoreina.parcheu.navigation.routeCatalog

/** Etiqueta de prueba del stub de una ruta: `stub:<NombreDeLaRuta>`. */
fun stubTag(route: AppRoute): String = "stub:${route::class.simpleName}"

/**
 * Pantalla provisional de una ruta (E0.7): título, IDs del inventario y argumentos. Cada feature la
 * reemplaza al implementar su `XRoute`.
 */
@Composable
fun StubScreen(
    route: AppRoute,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val entry = catalogEntryFor(route)
    Column(
        modifier =
        modifier
            .fillMaxSize()
            .testTag(stubTag(route))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = entry.title, style = MaterialTheme.typography.headlineSmall)
        Text(text = "Pantallas ${entry.screens}", style = MaterialTheme.typography.labelLarge)
        Text(
            text = route.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = "Pantalla en construcción.", style = MaterialTheme.typography.bodyMedium)
        content()
        if (navigator.canGoBack()) {
            TextButton(onClick = { navigator.back() }, modifier = Modifier.testTag("stub:back")) {
                Text("Volver")
            }
        }
    }
}

/**
 * Enlaces legales de A4 (Ley 1581): se muestran desde la 0.1. Las URLs salen de `BuildConfig`
 * (Firebase Hosting del entorno del flavor).
 */
@Composable
fun LegalLinks(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier) {
        TextButton(onClick = {
            uriHandler.openUri(BuildConfig.TERMS_URL)
        }, modifier = Modifier.testTag("legal:terms")) {
            Text("Términos", color = MaterialTheme.colorScheme.tertiary)
        }
        TextButton(onClick = {
            uriHandler.openUri(BuildConfig.PRIVACY_URL)
        }, modifier = Modifier.testTag("legal:privacy")) {
            Text("Política de privacidad", color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/** Catálogo de rutas para recorrer la app en un dispositivo mientras las pantallas son stubs. */
@Composable
fun RouteCatalogList(navigator: AppNavigator, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        routeCatalog.drop(1).forEach { entry ->
            TextButton(onClick = { navigator.navigate(entry.route) }) {
                Text("${entry.screens} · ${entry.title}")
            }
        }
    }
}
