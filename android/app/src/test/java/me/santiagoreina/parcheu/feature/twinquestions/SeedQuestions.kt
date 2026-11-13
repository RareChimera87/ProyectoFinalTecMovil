package me.santiagoreina.parcheu.feature.twinquestions

import me.santiagoreina.parcheu.contract.QuestionDto

/**
 * Las 12 preguntas de B1 tal como las sirve producción: copia de la semilla
 * backend/src/main/resources/db/migration/V2__semillas.sql (ninguna migración posterior las cambia).
 * Si la semilla cambia, actualizar aquí y en [TwinQuestionImages].
 */
val seedQuestions: List<QuestionDto> =
    listOf(
        QuestionDto(
            1,
            "Es viernes en la noche y no tienes planes. ¿Qué haces?",
            "Le escribo al parche para salir",
            "Me quedo en casa recargando pilas"
        ),
        QuestionDto(
            2,
            "Se viene un parcial en dos semanas.",
            "Armo un horario y estudio por partes",
            "Le meto todo la última semana"
        ),
        QuestionDto(
            3,
            "Alguien del grupo de trabajo no está haciendo su parte.",
            "Se lo digo de frente y buscamos solución",
            "Lo dejo pasar para no armar problema"
        ),
        QuestionDto(
            4,
            "En un parche donde no conoces a casi nadie...",
            "Rompo el hielo con un chiste",
            "Observo primero y hablo después"
        ),
        QuestionDto(
            5,
            "Te ofrecen entrar a un semillero de investigación que exige tiempo extra.",
            "De una, me sirve para la carrera",
            "Prefiero tener tiempo libre"
        ),
        QuestionDto(
            6,
            "Un amigo te invita a probar comida que nunca has comido.",
            "Me le mido a lo que sea",
            "Mejor pido lo de siempre"
        ),
        QuestionDto(
            7,
            "Para un paseo de fin de semana con amigos...",
            "Llevo itinerario, presupuesto y reservas",
            "Vamos viendo sobre la marcha"
        ),
        QuestionDto(
            8,
            "Tienes un desacuerdo fuerte con alguien cercano.",
            "Prefiero hablarlo ya, aunque sea incómodo",
            "Necesito tiempo antes de hablarlo"
        ),
        QuestionDto(
            9,
            "En el chat del grupo, tú eres más de...",
            "Mandar memes y stickers",
            "Responder corto y al punto"
        ),
        QuestionDto(
            10,
            "Sacaste 3,5 en una materia que pasaste.",
            "Me molesta, sé que podía sacar más",
            "Pasé, eso es lo importante"
        ),
        QuestionDto(
            11,
            "Te proponen un plan improvisado a las 10 p. m.",
            "Me apunto, para eso es la vida",
            "Ya tenía mi noche organizada"
        ),
        QuestionDto(
            12,
            "Cuando algo te sale mal frente a otros...",
            "Me río de mí y sigo",
            "Me da pena y prefiero que no lo comenten"
        )
    )
