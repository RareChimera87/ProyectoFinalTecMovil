package me.santiagoreina.parcheu.api.ai

import me.santiagoreina.parcheu.api.platform.config.Env
import java.math.BigDecimal
import java.util.Properties

/** Modelos por nivel (arquitectura.md §7.3). */
data class AiModels(val chat: String, val judge: String, val lite: String) {
    fun all(): Set<String> = setOf(chat, judge, lite)
}

/**
 * Configuración de la pasarela de IA (ADR-0008, ADR-0010). Variables de entorno, con
 * prioridad sobre `ai/modelos.properties`:
 *
 * - `AI_PROVIDER`: `gemini` (predeterminado) o `claude`; `fake` solo con `ENV=local`. Un valor
 *   inválido hace fallar el arranque. Con `ENV=local` y sin `AI_PROVIDER` se usa `fake`.
 * - `AI_MODEL_CHAT`, `AI_MODEL_JUDGE`, `AI_MODEL_LITE`: IDs de modelo; por defecto los del
 *   proveedor en el archivo de propiedades.
 * - `AI_DAILY_BUDGET_USD`: presupuesto diario (> 0).
 * - `AI_VERTEX_PROJECT` (o `GCP_PROJECT`): proyecto de Vertex; obligatorio fuera de `fake`.
 * - `AI_VERTEX_LOCATION`: ubicación de Vertex (`us` por defecto, multirregión de EE. UU.).
 *
 * Todos los modelos configurados deben tener precio en la tabla para esa ubicación: sin precio
 * el guard no vería el gasto, así que el arranque falla.
 */
data class AiConfig(
    val provider: AiProvider,
    val models: AiModels,
    val dailyBudgetUsd: BigDecimal,
    val vertexProject: String?,
    val vertexLocation: String,
    /** Nivel de razonamiento de Gemini 3.x (`LOW`, `MEDIUM`...); null = el del modelo. */
    val geminiThinkingLevel: String?,
    val prices: PriceTable,
) {
    companion object {
        const val PROPERTIES_RESOURCE = "/ai/modelos.properties"

        fun loadDefaults(): Map<String, String> {
            val props = Properties()
            val stream = AiConfig::class.java.getResourceAsStream(PROPERTIES_RESOURCE)
                ?: error("Falta $PROPERTIES_RESOURCE en el classpath")
            stream.use { props.load(it.reader(Charsets.UTF_8)) }
            return props.stringPropertyNames().associateWith { props.getProperty(it).trim() }
        }

        fun fromEnv(vars: Map<String, String>, env: Env, defaults: Map<String, String> = loadDefaults()): AiConfig {
            fun v(name: String): String? = vars[name]?.takeIf { it.isNotBlank() }?.trim()

            val rawProvider = v("AI_PROVIDER") ?: if (env == Env.LOCAL) AiProvider.FAKE.id else defaults["provider"] ?: "gemini"
            val provider = AiProvider.parse(rawProvider)
                ?: throw IllegalArgumentException("AI_PROVIDER inválido: '$rawProvider' (se espera gemini o claude)")
            require(provider != AiProvider.FAKE || env == Env.LOCAL) {
                "AI_PROVIDER=fake solo se admite con ENV=local (ENV=$env)"
            }
            // El Fake usa los IDs de Gemini para que los costos se calculen con precios reales.
            val prefix = if (provider == AiProvider.CLAUDE) "claude" else "gemini"
            fun model(envName: String, key: String): String =
                v(envName) ?: defaults["$prefix.model.$key"] ?: error("Falta $envName y $prefix.model.$key")
            val models = AiModels(
                chat = model("AI_MODEL_CHAT", "chat"),
                judge = model("AI_MODEL_JUDGE", "judge"),
                lite = model("AI_MODEL_LITE", "lite"),
            )
            val rawBudget = v("AI_DAILY_BUDGET_USD") ?: defaults["budget.daily.usd"] ?: error("Falta AI_DAILY_BUDGET_USD")
            val budget = rawBudget.toBigDecimalOrNull()
                ?: throw IllegalArgumentException("AI_DAILY_BUDGET_USD inválido: '$rawBudget'")
            require(budget > BigDecimal.ZERO) { "AI_DAILY_BUDGET_USD debe ser mayor que 0" }

            val location = v("AI_VERTEX_LOCATION") ?: defaults["vertex.location"] ?: "us"
            val project = v("AI_VERTEX_PROJECT") ?: v("GCP_PROJECT")
            require(provider == AiProvider.FAKE || project != null) {
                "AI_VERTEX_PROJECT (o GCP_PROJECT) es obligatorio con AI_PROVIDER=${provider.id}"
            }
            val prices = PriceTable.fromProperties(defaults, location)
            val missing = models.all().filterNot(prices::hasPrice)
            require(missing.isEmpty()) {
                "Sin precio en ai/modelos.properties para ${missing.joinToString()} en la ubicación '$location'"
            }
            return AiConfig(
                provider = provider,
                models = models,
                dailyBudgetUsd = budget,
                vertexProject = project,
                vertexLocation = location,
                geminiThinkingLevel = v("AI_GEMINI_THINKING_LEVEL") ?: defaults["gemini.thinking.level"],
                prices = prices,
            )
        }
    }
}
