package me.santiagoreina.parcheu.api.simulation

import me.santiagoreina.parcheu.contract.Mode

/**
 * Copia en memoria de los escenarios sembrados en `V2__semillas.sql` (mismos ids, títulos, básicos y
 * rúbricas). Solo la usan [InMemorySimulationRepository] y las pruebas; con base de datos manda la
 * tabla `scenarios`. `SimulationDbTest` comprueba que coinciden con la semilla.
 */
object DefaultScenarios {
    private val amistad = listOf(
        RubricItem("humor", 0.2, "Se ríen juntos y el tono es ligero sin burlarse del otro."),
        RubricItem("planes", 0.2, "Llegan a un plan concreto que les sirve a los dos."),
        RubricItem("valores", 0.2, "Coinciden en lo que les importa (lealtad, respeto, plata, tiempo)."),
        RubricItem("comunicacion", 0.25, "Se escuchan, preguntan y responden a lo que el otro dice."),
        RubricItem("ritmo", 0.15, "Tienen una energía y un ritmo de conversación compatibles."),
    )
    private val pareja = listOf(
        RubricItem("valores", 0.25, "Coinciden en lo importante y respetan las diferencias."),
        RubricItem("comunicacion", 0.2, "Expresan lo que sienten con claridad y escuchan al otro."),
        RubricItem("manejo_del_conflicto", 0.2, "Ante un desacuerdo buscan entenderse en lugar de ganar."),
        RubricItem("proyectos", 0.15, "Sus planes y prioridades de corto plazo encajan."),
        RubricItem("quimica_conversacional", 0.2, "La conversación fluye con interés y curiosidad mutua."),
    )
    private val estudio = listOf(
        RubricItem("organizacion", 0.25, "Reparten tareas con fechas claras y realistas."),
        RubricItem("responsabilidad", 0.25, "Cada uno asume compromisos y los sostiene."),
        RubricItem("comunicacion", 0.2, "Acuerdan canales y avisan a tiempo cuando algo cambia."),
        RubricItem("horarios", 0.15, "Sus disponibilidades y ritmos de estudio son compatibles."),
        RubricItem("estilo_de_trabajo", 0.15, "Sus formas de trabajar (solo o en grupo, detalle o visión) se complementan."),
    )

    /** `timer_sec` y `budget_cop` de la semilla por id de escenario (E4.8: tarjeta fijada de F3). */
    private val timerAndBudget: Map<Int, Pair<Int, Int>> = mapOf(
        1 to (600 to 80_000), 2 to (600 to 120_000), 3 to (600 to 150_000), 4 to (600 to 50_000), 5 to (900 to 1_200_000),
        6 to (600 to 100_000), 7 to (600 to 60_000), 8 to (600 to 80_000), 9 to (900 to 1_500_000), 10 to (600 to 0),
        11 to (600 to 50_000), 12 to (600 to 30_000), 13 to (600 to 20_000), 14 to (600 to 0), 15 to (900 to 100_000),
    )

    private fun s(id: Int, mode: Mode, slug: String, title: String, basic: Boolean, prompt: String) = ScenarioDef(
        id = id,
        mode = mode,
        slug = slug,
        title = title,
        prompt = prompt,
        isBasic = basic,
        timerSec = timerAndBudget.getValue(id).first,
        budgetCop = timerAndBudget.getValue(id).second,
        rubric = when (mode) {
            Mode.AMISTAD -> amistad
            Mode.PAREJA -> pareja
            Mode.ESTUDIO -> estudio
        },
    )

    val all: List<ScenarioDef> = listOf(
        s(1, Mode.AMISTAD, "amistad-plan-finde", "Planear el fin de semana", true, "Tienen un fin de semana libre en Bucaramanga y un presupuesto compartido. Pónganse de acuerdo en un plan para el sábado: a dónde ir, a qué hora y cómo repartir los gastos."),
        s(2, Mode.AMISTAD, "amistad-cumple-sorpresa", "Cumpleaños sorpresa", true, "Un amigo en común cumple años el viernes. Organicen una sorpresa con el presupuesto disponible: dónde, quién invita a quién y qué regalo darle."),
        s(3, Mode.AMISTAD, "amistad-paseo-chicamocha", "Paseo al Chicamocha", true, "Quieren ir al Parque Nacional del Chicamocha un domingo. Decidan transporte, horario, comida y qué hacer si llueve."),
        s(4, Mode.AMISTAD, "amistad-favor-incomodo", "Un favor incómodo", false, "Uno de los dos le prestó plata a un conocido que no la ha devuelto y ahora ese conocido quiere unirse al plan del sábado. Decidan juntos qué hacer."),
        s(5, Mode.AMISTAD, "amistad-roomies", "Ser roomies", false, "Están pensando en compartir apartamento el próximo semestre. Acuerden reglas de la casa: aseo, visitas, ruido y gastos."),
        s(6, Mode.PAREJA, "pareja-primera-cita", "Primera cita", true, "Van a tener su primera salida. Pónganse de acuerdo en un plan que les guste a los dos con el presupuesto disponible."),
        s(7, Mode.PAREJA, "pareja-domingo-lluvioso", "Domingo lluvioso", true, "Llueve todo el domingo y el plan al aire libre se cayó. Decidan qué hacer en lugar de eso."),
        s(8, Mode.PAREJA, "pareja-amigos-del-otro", "Conocer a los amigos", true, "Uno de los dos quiere presentarle al otro a su grupo de amigos este fin de semana. Hablen de cómo se sienten y cómo organizarlo."),
        s(9, Mode.PAREJA, "pareja-vacaciones", "Vacaciones juntos", false, "Tienen una semana de vacaciones de mitad de año. Decidan destino, presupuesto y cómo repartir el tiempo entre planes y descanso."),
        s(10, Mode.PAREJA, "pareja-desacuerdo", "Un desacuerdo", false, "Uno de los dos canceló un plan a último momento por estudiar. Conversen sobre lo que pasó y acuerden cómo manejarlo la próxima vez."),
        s(11, Mode.ESTUDIO, "estudio-proyecto-final", "Proyecto final", true, "Les tocó hacer juntos el proyecto final de una materia con entrega en tres semanas. Repártanse el trabajo y acuerden fechas."),
        s(12, Mode.ESTUDIO, "estudio-parcial-manana", "Parcial mañana", true, "Mañana tienen parcial y solo les queda esta noche. Decidan dónde estudiar, cómo dividir los temas y a qué hora parar."),
        s(13, Mode.ESTUDIO, "estudio-exposicion", "Exposición en grupo", true, "Deben presentar una exposición de 15 minutos. Acuerden quién presenta qué, cómo hacer las diapositivas y cuándo ensayar."),
        s(14, Mode.ESTUDIO, "estudio-companero-fantasma", "Compañero fantasma", false, "Un tercer integrante del grupo no responde y la entrega es en cuatro días. Decidan si le escriben al profesor, cómo redistribuir el trabajo y qué le dicen."),
        s(15, Mode.ESTUDIO, "estudio-semillero", "Semillero de investigación", false, "Quieren postularse juntos a un semillero que exige 6 horas semanales. Organicen horarios con sus otras materias y definan qué tema proponer."),
    )
}
