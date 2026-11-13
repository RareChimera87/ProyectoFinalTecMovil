package me.santiagoreina.parcheu.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.R
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.data.auth.SignInErrors
import me.santiagoreina.parcheu.designsystem.brand.ParcheULogo
import me.santiagoreina.parcheu.designsystem.brand.WordmarkSize
import me.santiagoreina.parcheu.designsystem.components.base.GoogleSignInButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PillChip
import me.santiagoreina.parcheu.designsystem.components.base.PillChipVariant
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.graph.GraphEdgeState
import me.santiagoreina.parcheu.designsystem.graph.GraphMode
import me.santiagoreina.parcheu.designsystem.graph.GraphNode
import me.santiagoreina.parcheu.designsystem.graph.PuGraph
import me.santiagoreina.parcheu.designsystem.mascot.MascotMouth
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotGlyph
import me.santiagoreina.parcheu.designsystem.theme.ParcheUPalette
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Conexiones de ejemplo del grafo de A4 (datos fijos: la persona aún no tiene conexiones). */
internal val ONBOARDING_GRAPH_NODES =
    listOf(
        GraphNode("o1", "Laura P.", "LP", 82, GraphMode.AMISTAD, GraphEdgeState.CONNECTED),
        GraphNode("o2", "Mateo R.", "MR", 74, GraphMode.ESTUDIO, GraphEdgeState.CONNECTED),
        GraphNode("o3", "Sofía G.", "SG", 66, GraphMode.PAREJA, GraphEdgeState.SIMULATED),
        GraphNode("o4", "Andrés V.", "AV", 58, GraphMode.AMISTAD, GraphEdgeState.SIMULATED),
        GraphNode("o5", "Camila T.", "CT", 41, GraphMode.ESTUDIO, GraphEdgeState.PENDING),
        GraphNode("o6", "Juan D.", "JD", 33, GraphMode.AMISTAD, GraphEdgeState.PENDING)
    )

/** Número de páginas del pager A2-A4. */
const val ONBOARDING_PAGES = 3

/** Etiquetas de prueba de A2-A4. */
object OnboardingTags {
    const val SCREEN = "onboarding"
    const val SKIP = "onboarding:skip"
    const val EXAMPLE = "onboarding:example"
    const val NEXT = "onboarding:next"
    const val GOOGLE = "login:google"
    const val LOADING = "login:loading"
    const val ERROR = "login:error"
    const val RETRY = "login:retry"
    const val LEGAL = "login:legal"
    const val TERMS_SHEET = "terms:sheet"
    const val TERMS_ACCEPT = "terms:accept"
    const val TERMS_DECLINE = "terms:decline"

    fun page(index: Int) = "onboarding:page:$index"
}

/** Texto legal de A4 (ficha E2.2, ADR-0017 y ADR-0020). */
internal fun legalNotice(termsUrl: String, privacyUrl: String, linkColor: Color): AnnotatedString {
    val linkStyle =
        TextLinkStyles(
            SpanStyle(
                color = linkColor,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline
            )
        )
    return buildAnnotatedString {
        append("Al continuar aceptas los ")
        withLink(LinkAnnotation.Url(termsUrl, linkStyle)) { append("Términos") }
        append(" y la ")
        withLink(LinkAnnotation.Url(privacyUrl, linkStyle)) { append("Política de privacidad") }
        append(" (Ley 1581 de 2012). Tus respuestas y mensajes se procesan con IA de ")
        append("Google Cloud en EE. UU.")
    }
}

/** Mensaje para la persona según el error del login. */
fun loginErrorMessage(error: AppError): String = when (error) {
    AppError.Network -> "No hay conexión. Revisa tu internet e inténtalo de nuevo."
    AppError.Unauthorized -> "Tu sesión expiró. Vuelve a iniciar sesión con Google."
    AppError.Suspended -> "Tu cuenta está suspendida por ahora."
    AppError.TermsRequired ->
        "No pudimos registrar tu aceptación de los Términos. Inténtalo de nuevo."
    is AppError.AiBudget, is AppError.Unknown ->
        when ((error as? AppError.Unknown)?.message) {
            SignInErrors.NOT_CONFIGURED ->
                "El inicio de sesión con Google todavía no está disponible en esta versión."
            SignInErrors.NO_GOOGLE_ACCOUNT ->
                "Agrega una cuenta de Google en el teléfono para continuar."
            else -> "No pudimos iniciar sesión. Inténtalo de nuevo."
        }
    else -> "No pudimos iniciar sesión. Inténtalo de nuevo."
}

/**
 * A2-A4 sin estado: pager con 'Saltar' (lleva a A4), indicador, 'Siguiente' y, en A4, 'Continuar con
 * Google' con el aviso legal. Cargando, error y la hoja de términos se superponen según [state].
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    pagerState: PagerState,
    termsUrl: String,
    privacyUrl: String,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onContinueWithGoogle: () -> Unit,
    onAcceptTerms: () -> Unit,
    onDeclineTerms: () -> Unit,
    modifier: Modifier = Modifier,
    animateLoading: Boolean = true
) {
    val colors = ParcheUTheme.colors
    val login = state.login
    val busy = login == LoginState.Loading || login is LoginState.TermsSheet
    val page = pagerState.currentPage
    val isLoginPage = page == ONBOARDING_PAGES - 1
    Box(modifier.fillMaxSize().background(colors.bg).testTag(OnboardingTags.SCREEN)) {
        Column(
            // Con la hoja visible el fondo no es alcanzable por TalkBack (la hoja se comporta como modal).
            Modifier.fillMaxSize().then(
                if (login is LoginState.TermsSheet) Modifier.clearAndSetSemantics {} else Modifier
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ParcheULogo(size = WordmarkSize.Small)
                Spacer(Modifier.weight(1f))
                if (!isLoginPage) {
                    ParcheUTextButton(
                        text = "Saltar",
                        onClick = onSkip,
                        size = ParcheUButtonSize.Medium,
                        modifier = Modifier.testTag(OnboardingTags.SKIP)
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !busy,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top
            ) { index ->
                OnboardingPage(index)
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 12.dp,
                    bottom = 32.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PageIndicator(count = ONBOARDING_PAGES, current = page)
                Spacer(Modifier.height(12.dp))
                if (!isLoginPage) {
                    PrimaryButton(
                        text = "Siguiente",
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth().testTag(OnboardingTags.NEXT)
                    )
                } else {
                    LoginFooter(
                        login = login,
                        termsUrl = termsUrl,
                        privacyUrl = privacyUrl,
                        onContinueWithGoogle = onContinueWithGoogle,
                        animateLoading = animateLoading
                    )
                }
            }
        }
        if (login is LoginState.TermsSheet) {
            TermsSheet(
                sheet = login,
                termsUrl = termsUrl,
                privacyUrl = privacyUrl,
                onAccept = onAcceptTerms,
                onDecline = onDeclineTerms,
                animateLoading = animateLoading
            )
        }
    }
}

@Composable
private fun LoginFooter(
    login: LoginState,
    termsUrl: String,
    privacyUrl: String,
    onContinueWithGoogle: () -> Unit,
    animateLoading: Boolean
) {
    val colors = ParcheUTheme.colors
    when (login) {
        LoginState.Loading ->
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag(OnboardingTags.LOADING),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LoadingDots(animate = animateLoading)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Iniciando sesión…",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.mu
                )
            }
        is LoginState.Error -> {
            Text(
                text = loginErrorMessage(login.error),
                style = MaterialTheme.typography.bodySmall,
                color = colors.tx,
                textAlign = TextAlign.Center,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.s2, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(OnboardingTags.ERROR)
            )
            PrimaryButton(
                text = "Reintentar",
                onClick = onContinueWithGoogle,
                modifier = Modifier.fillMaxWidth().testTag(OnboardingTags.RETRY)
            )
        }
        else ->
            GoogleSignInButton(
                text = "Continuar con Google",
                onClick = onContinueWithGoogle,
                enabled = login == LoginState.Idle,
                modifier = Modifier.fillMaxWidth().testTag(OnboardingTags.GOOGLE)
            )
    }
    Text(
        text = legalNotice(termsUrl, privacyUrl, colors.prt),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        color = colors.mu,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag(OnboardingTags.LEGAL)
    )
}

@Composable
private fun OnboardingPage(index: Int) {
    val colors = ParcheUTheme.colors
    val (title, body) =
        when (index) {
            0 ->
                "Crea tu gemelo digital" to
                    "Es un agente de IA que aprende cómo eres: cómo hablas, qué te gusta y " +
                    "cómo resuelves las cosas."
            1 ->
                "Sus gemelos conversan primero" to
                    "Antes de conocer a alguien, sus gemelos simulan una conversación y te " +
                    "cuentan qué tan afines son y por qué."
            else ->
                "Arma tu parche" to
                    "Activa Amistad, Pareja o Estudio y mira cómo crece tu red de personas afines."
        }
    // Dirección A: cada página tiene un bloque protagonista (radio 24, sin sombra). A2 es el héroe violeta con
    // tu P; A3, el contenedor de Amistad con las dos P frente a frente; A4, el grafo sobre s1.
    val panel =
        when (index) {
            0 -> colors.hero
            1 -> colors.friendship.container
            else -> colors.s1
        }
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .testTag(OnboardingTags.page(index)),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp)
                .clip(ParcheUTheme.shapes.hero)
                .background(panel),
            contentAlignment = Alignment.Center
        ) {
            when (index) {
                0 -> TwinIllustration()
                1 -> SimulationIllustration()
                else -> {
                    PuGraph(nodes = ONBOARDING_GRAPH_NODES, size = 260.dp)
                    ExampleCaption(
                        color = colors.mu,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = colors.tx,
                modifier = Modifier.semantics { heading() }
            )
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.mu)
        }
    }
}

/** A2 sobre el héroe violeta: tú (iniciales) y tu gemelo, la P blanca grande, con lo que aprende alrededor. */
@Composable
private fun TwinIllustration() {
    val colors = ParcheUTheme.colors
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            InitialsAvatar(
                "MG",
                72.dp,
                background = colors.onHero,
                content = colors.hero
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("✦", color = ParcheUPalette.Orange, fontSize = 26.sp)
                TwinMascotGlyph(width = 112.dp, color = colors.onHero, eyeColor = colors.hero)
            }
        }
        PillChip("Cómo hablas", Modifier.align(Alignment.TopStart).offset(24.dp, 30.dp))
        PillChip("Qué te gusta", Modifier.align(Alignment.TopEnd).offset((-20).dp, 52.dp))
        PillChip("Cómo resuelves", Modifier.align(Alignment.BottomStart).offset(36.dp, (-30).dp))
    }
}

/**
 * A3 sobre el contenedor de Amistad: tu P violeta y la P menta de la otra persona se miran (`mirrored`) y
 * conversan; abajo, la afinidad de ejemplo.
 */
@Composable
private fun SimulationIllustration() {
    val colors = ParcheUTheme.colors
    Column(Modifier.width(272.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TwinMascotGlyph(
                width = 60.dp,
                color = ParcheUPalette.Violet,
                mouth = MascotMouth.Talking
            )
            Bubble(
                "¿Un plan el sábado? Yo propongo la Mesa.",
                RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            Bubble(
                "¡Hágale! Pero salimos temprano.",
                RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
                Modifier.weight(1f, fill = false)
            )
            TwinMascotGlyph(
                width = 60.dp,
                color = colors.friendship.solid,
                mirrored = true
            )
        }
        PillChip(
            "Afinidad 84 %",
            Modifier.align(Alignment.CenterHorizontally),
            variant = PillChipVariant.Affinity
        )
        ExampleCaption(
            color = colors.friendship.onContainer,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

/** Rótulo discreto: los nombres, la conversación y la afinidad de A2-A4 son un ejemplo, no datos reales. */
@Composable
private fun ExampleCaption(color: Color, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.onboarding_example_label),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier.testTag(OnboardingTags.EXAMPLE)
    )
}

@Composable
private fun Bubble(text: String, shape: RoundedCornerShape, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
        color = colors.tx,
        modifier = modifier.background(
            colors.s2,
            shape
        ).padding(horizontal = 13.dp, vertical = 9.dp)
    )
}

/**
 * Hoja de confirmación cuando cambió la versión de los Términos (la sesión ya existía). Se dibuja dentro de
 * la pantalla (no es un diálogo) para que las capturas de Roborazzi la incluyan.
 */
@Composable
private fun TermsSheet(
    sheet: LoginState.TermsSheet,
    termsUrl: String,
    privacyUrl: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    animateLoading: Boolean
) {
    val colors = ParcheUTheme.colors
    // Atrás equivale a "Ahora no": Onboarding es la única entrada de la pila y, sin esto, cerraría la app.
    BackHandler(enabled = true) { if (!sheet.submitting) onDecline() }
    Box(
        Modifier
            .fillMaxSize()
            .semantics {
                paneTitle = "Actualizamos los Términos y la Política"
                isTraversalGroup = true
            }
            .background(colors.scrim)
            // Absorbe los toques sobre el scrim sin exponer una acción de clic a TalkBack.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colors.s1, ParcheUTheme.shapes.bottomSheet)
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp)
                .testTag(OnboardingTags.TERMS_SHEET),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.ln, RoundedCornerShape(2.dp))
            )
            Text(
                text = "Actualizamos los Términos y la Política",
                style = MaterialTheme.typography.titleLarge,
                color = colors.tx,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text =
                "Para seguir usando ParcheU necesitamos que aceptes la versión vigente " +
                    "(${sheet.textVersion}).",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu
            )
            Text(
                text = legalNotice(termsUrl, privacyUrl, colors.prt),
                style = MaterialTheme.typography.bodySmall,
                color = colors.mu
            )
            Spacer(Modifier.height(4.dp))
            if (sheet.submitting) {
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                    LoadingDots(animate = animateLoading)
                }
            } else {
                PrimaryButton(
                    text = "Aceptar y continuar",
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth().testTag(OnboardingTags.TERMS_ACCEPT)
                )
            }
            ParcheUTextButton(
                text = "Ahora no (cerrar sesión)",
                onClick = onDecline,
                enabled = !sheet.submitting,
                modifier = Modifier.fillMaxWidth().testTag(OnboardingTags.TERMS_DECLINE)
            )
        }
    }
}
