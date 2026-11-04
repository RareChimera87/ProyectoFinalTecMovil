package me.santiagoreina.parcheu.api.ai

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Precio de un modelo en USD por millón de tokens, vigente desde [from] (null = siempre). */
data class ModelPrice(
    val inputPerMillionUsd: BigDecimal,
    val outputPerMillionUsd: BigDecimal,
    val from: LocalDate? = null,
)

/**
 * Tabla de precios por modelo para una ubicación de Vertex (`us`, `global`...). Se lee de
 * `ai/modelos.properties` (claves `price.<ubicación>.<modelo>[@yyyy-MM-dd]`).
 *
 * Truco de unidades: USD por millón de tokens = micro-USD por token, así que
 * `cost_micros = tokensIn · precioEntrada + tokensOut · precioSalida` (redondeo hacia arriba
 * para no subestimar el gasto que vigila [DailyBudgetGuard]).
 */
class PriceTable(private val prices: Map<String, List<ModelPrice>>) {

    fun hasPrice(model: String): Boolean = prices[model].orEmpty().isNotEmpty()

    /** Precio vigente el día [day] (hora de Bogotá). */
    fun priceOn(model: String, day: LocalDate): ModelPrice {
        val rows = prices[model].orEmpty()
        return rows.filter { it.from == null || !it.from.isAfter(day) }
            .maxByOrNull { it.from ?: LocalDate.MIN }
            ?: throw IllegalStateException("Sin precio para el modelo '$model' el $day: agrégalo en ai/modelos.properties")
    }

    fun costMicros(model: String, day: LocalDate, usage: LlmUsage): Long {
        val p = priceOn(model, day)
        return (p.inputPerMillionUsd * BigDecimal(usage.tokensIn) + p.outputPerMillionUsd * BigDecimal(usage.tokensOut))
            .setScale(0, RoundingMode.CEILING)
            .longValueExact()
    }

    companion object {
        private val KEY = Regex("""price\.([a-z0-9-]+)\.([A-Za-z0-9._-]+?)(?:@(\d{4}-\d{2}-\d{2}))?""")

        /** Construye la tabla de [location] a partir de las propiedades `price.*`. */
        fun fromProperties(props: Map<String, String>, location: String): PriceTable {
            val table = mutableMapOf<String, MutableList<ModelPrice>>()
            props.forEach { (key, value) ->
                val m = KEY.matchEntire(key) ?: return@forEach
                val (loc, model, from) = m.destructured
                if (loc != location) return@forEach
                val parts = value.split(',').map { it.trim() }
                require(parts.size == 2) { "Precio inválido en $key: se espera 'entrada,salida'" }
                table.getOrPut(model) { mutableListOf() } += ModelPrice(
                    BigDecimal(parts[0]),
                    BigDecimal(parts[1]),
                    from.takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                )
            }
            return PriceTable(table)
        }
    }
}
