-- ParcheU · Semillas v1 (E0.4): catálogos fijos que la app necesita desde el primer arranque.
-- Cambiar un texto después de la 1.0 exige una migración nueva (nunca editar este archivo
-- una vez aplicado: Flyway valida el checksum).

-- =====================================================================
-- Universidades (B0, E2.6). Área metropolitana de Bucaramanga + 'Otra'.
-- =====================================================================
insert into universities (id, short_name, name) values
    (1,  'UIS',      'Universidad Industrial de Santander'),
    (2,  'UNAB',     'Universidad Autónoma de Bucaramanga'),
    (3,  'UPB',      'Universidad Pontificia Bolivariana, seccional Bucaramanga'),
    (4,  'USTA',     'Universidad Santo Tomás, seccional Bucaramanga'),
    (5,  'UDES',     'Universidad de Santander'),
    (6,  'UTS',      'Unidades Tecnológicas de Santander'),
    (7,  'UCC',      'Universidad Cooperativa de Colombia, sede Bucaramanga'),
    (8,  'UMB',      'Universidad Manuela Beltrán, sede Bucaramanga'),
    (9,  'UDI',      'Universidad de Investigación y Desarrollo'),
    (10, 'UNISANGIL','Fundación Universitaria de San Gil'),
    (11, 'SENA',     'Servicio Nacional de Aprendizaje (SENA)'),
    (99, 'Otra',     'Otra institución');

-- =====================================================================
-- Intereses (B0, perfil, F2). `icon` = nombre del símbolo de Material Symbols.
-- =====================================================================
insert into interests (slug, label, icon) values
    ('musica',        'Música',              'music_note'),
    ('conciertos',    'Conciertos',          'stadium'),
    ('futbol',        'Fútbol',              'sports_soccer'),
    ('gym',           'Gimnasio',            'fitness_center'),
    ('ciclismo',      'Ciclismo',            'directions_bike'),
    ('senderismo',    'Senderismo',          'hiking'),
    ('videojuegos',   'Videojuegos',         'sports_esports'),
    ('juegos_mesa',   'Juegos de mesa',      'casino'),
    ('series',        'Series',              'live_tv'),
    ('cine',          'Cine',                'movie'),
    ('anime',         'Anime',               'animation'),
    ('lectura',       'Lectura',             'menu_book'),
    ('escritura',     'Escritura',           'edit_note'),
    ('fotografia',    'Fotografía',          'photo_camera'),
    ('dibujo',        'Dibujo y diseño',     'brush'),
    ('cocina',        'Cocina',              'restaurant'),
    ('cafe',          'Café',                'local_cafe'),
    ('baile',         'Baile',               'nightlife'),
    ('viajes',        'Viajes',              'flight'),
    ('programacion',  'Programación',        'code'),
    ('emprendimiento','Emprendimiento',      'rocket_launch'),
    ('idiomas',       'Idiomas',             'translate'),
    ('voluntariado',  'Voluntariado',        'volunteer_activism'),
    ('mascotas',      'Mascotas',            'pets'),
    ('ciencia',       'Ciencia',             'science');

-- =====================================================================
-- Preguntas del gemelo (B1). 12 preguntas A / B / DEPENDS.
-- `weights`: rasgo -> peso. A empuja el rasgo hacia 1 y B hacia 0; DEPENDS suma la mitad del
-- peso a ambos polos (TraitEngine, arquitectura.md §7.1); un peso negativo invierte los polos.
-- Cada rasgo aparece al menos 2 veces.
-- =====================================================================
insert into questions (id, ord, text, option_a, option_b, weights) values
    (1, 1, 'Es viernes en la noche y no tienes planes. ¿Qué haces?',
        'Le escribo al parche para salir', 'Me quedo en casa recargando pilas',
        '{"SOCIAL_ENERGY": 1.0, "OPENNESS": 0.3}'),
    (2, 2, 'Se viene un parcial en dos semanas.',
        'Armo un horario y estudio por partes', 'Le meto todo la última semana',
        '{"PLANNING": 1.0, "ACADEMIC_AMBITION": 0.5}'),
    (3, 3, 'Alguien del grupo de trabajo no está haciendo su parte.',
        'Se lo digo de frente y buscamos solución', 'Lo dejo pasar para no armar problema',
        '{"CONFLICT_HANDLING": 1.0}'),
    (4, 4, 'En un parche donde no conoces a casi nadie...',
        'Rompo el hielo con un chiste', 'Observo primero y hablo después',
        '{"HUMOR": 1.0, "SOCIAL_ENERGY": 0.5}'),
    (5, 5, 'Te ofrecen entrar a un semillero de investigación que exige tiempo extra.',
        'De una, me sirve para la carrera', 'Prefiero tener tiempo libre',
        '{"ACADEMIC_AMBITION": 1.0}'),
    (6, 6, 'Un amigo te invita a probar comida que nunca has comido.',
        'Me le mido a lo que sea', 'Mejor pido lo de siempre',
        '{"OPENNESS": 1.0}'),
    (7, 7, 'Para un paseo de fin de semana con amigos...',
        'Llevo itinerario, presupuesto y reservas', 'Vamos viendo sobre la marcha',
        '{"PLANNING": 1.0, "OPENNESS": -0.3}'),
    (8, 8, 'Tienes un desacuerdo fuerte con alguien cercano.',
        'Prefiero hablarlo ya, aunque sea incómodo', 'Necesito tiempo antes de hablarlo',
        '{"CONFLICT_HANDLING": 1.0, "SOCIAL_ENERGY": 0.2}'),
    (9, 9, 'En el chat del grupo, tú eres más de...',
        'Mandar memes y stickers', 'Responder corto y al punto',
        '{"HUMOR": 1.0}'),
    (10, 10, 'Sacaste 3,5 en una materia que pasaste.',
        'Me molesta, sé que podía sacar más', 'Pasé, eso es lo importante',
        '{"ACADEMIC_AMBITION": 1.0, "PLANNING": 0.3}'),
    (11, 11, 'Te proponen un plan improvisado a las 10 p. m.',
        'Me apunto, para eso es la vida', 'Ya tenía mi noche organizada',
        '{"OPENNESS": 1.0, "SOCIAL_ENERGY": 0.5, "PLANNING": -0.3}'),
    (12, 12, 'Cuando algo te sale mal frente a otros...',
        'Me río de mí y sigo', 'Me da pena y prefiero que no lo comenten',
        '{"HUMOR": 1.0, "CONFLICT_HANDLING": 0.3}');

-- =====================================================================
-- Escenarios por modo (F3, F4). Por modo: 3 básicos (gratis: la simulación usa 3) y 2 premium.
-- `rubric`: dimensiones del juez (arquitectura.md §7.2, paso 6) con pesos que suman 1.
-- Pareja: sin contenido sexual ni datos personales (reglas del modo).
-- =====================================================================
insert into scenarios (id, mode, slug, title, prompt, is_basic, timer_sec, budget_cop, rubric) values
-- Amistad
    (1, 'AMISTAD', 'amistad-plan-finde', 'Planear el fin de semana',
        'Tienen un fin de semana libre en Bucaramanga y un presupuesto compartido. Pónganse de acuerdo en un plan para el sábado: a dónde ir, a qué hora y cómo repartir los gastos.',
        true, 600, 80000, '[]'),
    (2, 'AMISTAD', 'amistad-cumple-sorpresa', 'Cumpleaños sorpresa',
        'Un amigo en común cumple años el viernes. Organicen una sorpresa con el presupuesto disponible: dónde, quién invita a quién y qué regalo darle.',
        true, 600, 120000, '[]'),
    (3, 'AMISTAD', 'amistad-paseo-chicamocha', 'Paseo al Chicamocha',
        'Quieren ir al Parque Nacional del Chicamocha un domingo. Decidan transporte, horario, comida y qué hacer si llueve.',
        true, 600, 150000, '[]'),
    (4, 'AMISTAD', 'amistad-favor-incomodo', 'Un favor incómodo',
        'Uno de los dos le prestó plata a un conocido que no la ha devuelto y ahora ese conocido quiere unirse al plan del sábado. Decidan juntos qué hacer.',
        false, 600, 50000, '[]'),
    (5, 'AMISTAD', 'amistad-roomies', 'Ser roomies',
        'Están pensando en compartir apartamento el próximo semestre. Acuerden reglas de la casa: aseo, visitas, ruido y gastos.',
        false, 900, 1200000, '[]'),
-- Pareja
    (6, 'PAREJA', 'pareja-primera-cita', 'Primera cita',
        'Van a tener su primera salida. Pónganse de acuerdo en un plan que les guste a los dos con el presupuesto disponible.',
        true, 600, 100000, '[]'),
    (7, 'PAREJA', 'pareja-domingo-lluvioso', 'Domingo lluvioso',
        'Llueve todo el domingo y el plan al aire libre se cayó. Decidan qué hacer en lugar de eso.',
        true, 600, 60000, '[]'),
    (8, 'PAREJA', 'pareja-amigos-del-otro', 'Conocer a los amigos',
        'Uno de los dos quiere presentarle al otro a su grupo de amigos este fin de semana. Hablen de cómo se sienten y cómo organizarlo.',
        true, 600, 80000, '[]'),
    (9, 'PAREJA', 'pareja-vacaciones', 'Vacaciones juntos',
        'Tienen una semana de vacaciones de mitad de año. Decidan destino, presupuesto y cómo repartir el tiempo entre planes y descanso.',
        false, 900, 1500000, '[]'),
    (10, 'PAREJA', 'pareja-desacuerdo', 'Un desacuerdo',
        'Uno de los dos canceló un plan a último momento por estudiar. Conversen sobre lo que pasó y acuerden cómo manejarlo la próxima vez.',
        false, 600, 0, '[]'),
-- Estudio
    (11, 'ESTUDIO', 'estudio-proyecto-final', 'Proyecto final',
        'Les tocó hacer juntos el proyecto final de una materia con entrega en tres semanas. Repártanse el trabajo y acuerden fechas.',
        true, 600, 50000, '[]'),
    (12, 'ESTUDIO', 'estudio-parcial-manana', 'Parcial mañana',
        'Mañana tienen parcial y solo les queda esta noche. Decidan dónde estudiar, cómo dividir los temas y a qué hora parar.',
        true, 600, 30000, '[]'),
    (13, 'ESTUDIO', 'estudio-exposicion', 'Exposición en grupo',
        'Deben presentar una exposición de 15 minutos. Acuerden quién presenta qué, cómo hacer las diapositivas y cuándo ensayar.',
        true, 600, 20000, '[]'),
    (14, 'ESTUDIO', 'estudio-companero-fantasma', 'Compañero fantasma',
        'Un tercer integrante del grupo no responde y la entrega es en cuatro días. Decidan si le escriben al profesor, cómo redistribuir el trabajo y qué le dicen.',
        false, 600, 0, '[]'),
    (15, 'ESTUDIO', 'estudio-semillero', 'Semillero de investigación',
        'Quieren postularse juntos a un semillero que exige 6 horas semanales. Organicen horarios con sus otras materias y definan qué tema proponer.',
        false, 900, 100000, '[]');

update scenarios set rubric = '[
    {"name": "humor", "weight": 0.2, "description": "Se ríen juntos y el tono es ligero sin burlarse del otro."},
    {"name": "planes", "weight": 0.2, "description": "Llegan a un plan concreto que les sirve a los dos."},
    {"name": "valores", "weight": 0.2, "description": "Coinciden en lo que les importa (lealtad, respeto, plata, tiempo)."},
    {"name": "comunicacion", "weight": 0.25, "description": "Se escuchan, preguntan y responden a lo que el otro dice."},
    {"name": "ritmo", "weight": 0.15, "description": "Tienen una energía y un ritmo de conversación compatibles."}
]'::jsonb where mode = 'AMISTAD';

update scenarios set rubric = '[
    {"name": "valores", "weight": 0.25, "description": "Coinciden en lo importante y respetan las diferencias."},
    {"name": "comunicacion", "weight": 0.2, "description": "Expresan lo que sienten con claridad y escuchan al otro."},
    {"name": "manejo_del_conflicto", "weight": 0.2, "description": "Ante un desacuerdo buscan entenderse en lugar de ganar."},
    {"name": "proyectos", "weight": 0.15, "description": "Sus planes y prioridades de corto plazo encajan."},
    {"name": "quimica_conversacional", "weight": 0.2, "description": "La conversación fluye con interés y curiosidad mutua."}
]'::jsonb where mode = 'PAREJA';

update scenarios set rubric = '[
    {"name": "organizacion", "weight": 0.25, "description": "Reparten tareas con fechas claras y realistas."},
    {"name": "responsabilidad", "weight": 0.25, "description": "Cada uno asume compromisos y los sostiene."},
    {"name": "comunicacion", "weight": 0.2, "description": "Acuerdan canales y avisan a tiempo cuando algo cambia."},
    {"name": "horarios", "weight": 0.15, "description": "Sus disponibilidades y ritmos de estudio son compatibles."},
    {"name": "estilo_de_trabajo", "weight": 0.15, "description": "Sus formas de trabajar (solo o en grupo, detalle o visión) se complementan."}
]'::jsonb where mode = 'ESTUDIO';
