package me.santiagoreina.parcheu.api.demo

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.TwinAnswer
import java.time.LocalDate

/**
 * Un usuario demo (ADR-0027). Todo es ficticio: nombre con el sufijo "(demo)", sin correo ni foto, y
 * `users.is_demo = true`, que es lo que hace aparecer la insignia en la app.
 *
 * - [answers]: 12 letras (A, B o D = DEPENDS) en el orden de `questions.ord`; de ahí salen los rasgos con
 *   `TraitEngine`, igual que para cualquier persona.
 * - [queueMode]: el modo en el que espera en la fila (`match_queue` guarda un ticket por persona). Los demo
 *   solo tienen activos Amistad y Estudio; **nunca** Pareja.
 */
data class DemoProfile(
    val key: String,
    val name: String,
    val career: String,
    val universityId: Int,
    val interests: List<String>,
    val birthDate: LocalDate,
    val queueMode: Mode,
    val answers: String,
    val persona: String,
) {
    val firebaseUid: String get() = "$UID_PREFIX$key"

    fun answerFor(index: Int): TwinAnswer = when (answers[index]) {
        'A' -> TwinAnswer.A
        'B' -> TwinAnswer.B
        else -> TwinAnswer.DEPENDS
    }

    companion object {
        /** Prefijo de `users.firebase_uid` de los demo: nunca coincide con un UID real de Firebase (28 caracteres alfanuméricos). */
        const val UID_PREFIX = "demo-"
    }
}

/** Los 20 demo que siembra `seed-demo`. Modos permitidos: Amistad y Estudio. */
object DemoProfiles {
    /** Modos que se activan en `user_modes`. Pareja queda siempre apagado (ADR-0027). */
    val MODES: Set<Mode> = setOf(Mode.AMISTAD, Mode.ESTUDIO)

    private const val SUFFIX = " (demo)"

    val all: List<DemoProfile> = listOf(
        p("01", "Valentina", "Ingeniería de Sistemas", 1, listOf("programacion", "videojuegos", "cafe"), "2004-03-12", "ABAADBABADAB",
            "Le encanta resolver problemas con código y explicar lo que aprende. Habla rápido cuando algo la emociona y propone planes concretos."),
        p("02", "Santiago", "Medicina", 1, listOf("gym", "futbol", "series"), "2003-07-21", "BAABABDAABBA",
            "Disciplinado y competitivo, pero bromea para bajar la tensión. Prefiere estudiar temprano y con un horario claro."),
        p("03", "Mariana", "Diseño Gráfico", 3, listOf("dibujo", "fotografia", "cine"), "2005-01-30", "ADBABAABDBAA",
            "Creativa y observadora; se fija en los detalles y comparte referencias visuales. Le cuesta decir que no a un plan improvisado."),
        p("04", "Andrés", "Derecho", 2, listOf("lectura", "escritura", "cafe"), "2002-11-05", "BBADABBAABAD",
            "Argumenta con calma y escucha antes de responder. Le gustan las conversaciones largas y odia dejar las cosas para última hora."),
        p("05", "Camila", "Psicología", 2, listOf("voluntariado", "musica", "baile"), "2004-09-18", "AADABAABAADA",
            "Empática y conversadora; pregunta mucho por los demás. Organiza al grupo cuando nadie más lo hace."),
        p("06", "Juan Pablo", "Ingeniería Mecánica", 1, listOf("ciclismo", "senderismo", "ciencia"), "2003-04-02", "BABBAADBABBA",
            "Práctico y directo; prefiere hacer a planear. Disfruta salir a rodar el fin de semana y explicar cómo funcionan las cosas."),
        p("07", "Laura", "Administración de Empresas", 4, listOf("emprendimiento", "viajes", "idiomas"), "2004-06-27", "AAABABAAABAB",
            "Ambiciosa y organizada; siempre tiene una idea de negocio nueva. Le gusta conocer gente de otras carreras."),
        p("08", "Sebastián", "Música", 5, listOf("musica", "conciertos", "series"), "2005-02-14", "DABADBDAADBD",
            "Relajado y con humor absurdo; improvisa en todo. Se toma en serio la música y muy poco lo demás."),
        p("09", "Daniela", "Enfermería", 5, listOf("mascotas", "cocina", "series"), "2004-12-09", "ABBAABBAABAB",
            "Paciente y cuidadosa; tranquiliza a los demás en momentos de estrés. Planea sus semanas con anticipación."),
        p("10", "Felipe", "Ingeniería Electrónica", 6, listOf("programacion", "anime", "juegos_mesa"), "2003-08-16", "BDABBAABDBAB",
            "Introvertido al principio, muy divertido cuando entra en confianza. Le apasiona construir cosas con Arduino."),
        p("11", "Sofía", "Comunicación Social", 2, listOf("escritura", "fotografia", "viajes"), "2004-10-03", "AABADABAADAA",
            "Curiosa y sociable; siempre está contando una historia. Le gusta debatir temas de actualidad sin pelear."),
        p("12", "Nicolás", "Economía", 1, listOf("lectura", "futbol", "emprendimiento"), "2002-05-25", "BAAABBABAABB",
            "Analítico y competitivo en lo académico; le gusta armar grupos de estudio eficientes y sin distracciones."),
        p("13", "Isabella", "Arquitectura", 3, listOf("dibujo", "viajes", "cafe"), "2005-03-08", "ADBAABDAABAD",
            "Detallista y nocturna; trabaja mejor en equipo cuando hay música. Le gusta caminar la ciudad mirando edificios."),
        p("14", "Mateo", "Licenciatura en Educación Física", 7, listOf("futbol", "gym", "senderismo"), "2003-01-19", "AAADBAABADBA",
            "Energético y motivador; convierte cualquier plan en un reto. Prefiere hablar en persona a escribir."),
        p("15", "Gabriela", "Química", 1, listOf("ciencia", "cocina", "lectura"), "2004-07-11", "BBABABBAABBA",
            "Reservada y metódica; disfruta explicar paso a paso. Su plan ideal es un café tranquilo y una buena conversación."),
        p("16", "Tomás", "Ingeniería Industrial", 4, listOf("ciclismo", "juegos_mesa", "emprendimiento"), "2003-10-28", "ADABABAADABA",
            "Optimiza todo, hasta las salidas con amigos. Es buen mediador cuando el grupo no se pone de acuerdo."),
        p("17", "Paula", "Fisioterapia", 7, listOf("baile", "gym", "mascotas"), "2004-02-22", "AABBADAABBAD",
            "Alegre y espontánea; contagia energía. Le gusta estudiar con alguien que la mantenga enfocada."),
        p("18", "Alejandro", "Historia", 12, listOf("lectura", "cine", "idiomas"), "2002-09-13", "BDBADBBDABBD",
            "Pausado y reflexivo; conecta cualquier tema con una anécdota histórica. Disfruta los planes tranquilos."),
        p("19", "Natalia", "Contaduría Pública", 6, listOf("series", "cocina", "viajes"), "2005-05-06", "ABABABABABAB",
            "Ordenada y puntual; lleva las cuentas del grupo sin que nadie se lo pida. Se ríe fácil y es muy leal."),
        p("20", "Esteban", "Ingeniería Civil", 1, listOf("senderismo", "fotografia", "musica"), "2003-12-01", "DAABDAADBAAB",
            "Aventurero y tranquilo a la vez; siempre propone salir al aire libre. Estudia mejor en grupos pequeños."),
    )

    private fun p(
        key: String,
        name: String,
        career: String,
        universityId: Int,
        interests: List<String>,
        birthDate: String,
        answers: String,
        persona: String,
    ): DemoProfile {
        require(answers.length == QUESTIONS && answers.all { it in "ABD" }) { "El demo $key necesita $QUESTIONS respuestas A/B/D" }
        val index = key.toInt()
        return DemoProfile(
            key = key,
            name = name + SUFFIX,
            career = career,
            universityId = universityId,
            interests = interests,
            birthDate = LocalDate.parse(birthDate),
            // Mitad espera en Amistad y mitad en Estudio.
            queueMode = if (index % 2 == 1) Mode.AMISTAD else Mode.ESTUDIO,
            answers = answers,
            // La persona alimenta al agente de la simulación y, como la de PersonaBuilder, no lleva nombre, carrera,
            // universidad ni edad (la transcripción es anónima antes de conectar). Que es un demo lo dicen la
            // insignia, el sufijo del nombre y el banner del chat (ADR-0027), no el diálogo simulado.
            persona = persona,
        )
    }

    /** Preguntas de B1 que contestan los demo (las 12 de `V2__semillas.sql`). */
    const val QUESTIONS = 12
}
