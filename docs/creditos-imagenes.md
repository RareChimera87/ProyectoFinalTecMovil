# Créditos de imágenes

Fotos de las tarjetas de opción de B1 (las 12 preguntas del gemelo de la semilla
`backend/src/main/resources/db/migration/V2__semillas.sql`), en
`android/app/src/main/res/drawable-nodpi/` y referenciadas desde
`feature/twinquestions/TwinQuestionImages.kt`.

Todas vienen de Wikimedia Commons, que aloja copias de fotos publicadas en Unsplash antes de junio de
2017, cuando Unsplash las licenciaba como **CC0 1.0 (dominio público)**. La licencia se verificó en la
página de cada archivo en Commons (campo de licencia de la página) el día de la descarga. CC0 permite uso
comercial y modificación sin pedir permiso ni atribuir; igual se deja el crédito a los autores.

Se descargó la versión de 1280 px de ancho, se validó que fuera un JPEG real y se convirtió con ImageMagick
a WebP de 720 x 450 (recorte 16:10 centrado, calidad 75; 50 en `twin_q05_b` y `twin_q07_a` para quedar por
debajo de 60 KB), sin metadatos (`-strip`). Peso total: unos 690 KB.

| Archivo | Pregunta / opción | Autor | Página de la foto | Licencia | Descarga |
| --- | --- | --- | --- | --- | --- |
| `twin_q01_a.webp` | P1 A: Le escribo al parche para salir | Alan Chen | [Yellow (Unsplash wdTEHCq1mRo).jpg](https://commons.wikimedia.org/wiki/File:Yellow_%28Unsplash_wdTEHCq1mRo%29.jpg) (original: <https://unsplash.com/photos/wdTEHCq1mRo>) | CC0 1.0 | 2026-11-13 |
| `twin_q01_b.webp` | P1 B: Me quedo en casa recargando pilas | Alice Hampson | [Getting cozy with a book (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Getting_cozy_with_a_book_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/xZiHAa09Jcs>) | CC0 1.0 | 2026-11-13 |
| `twin_q02_a.webp` | P2 A: Armo un horario y estudio por partes | Giulia Bertelli | [Giulia Bertelli 2016 (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Giulia_Bertelli_2016_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/2OU30mdtseY>) | CC0 1.0 | 2026-11-13 |
| `twin_q02_b.webp` | P2 B: Le meto todo la última semana | Jez Timms | [Vintage lamp and books (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Vintage_lamp_and_books_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/8muUTAmcWU4>) | CC0 1.0 | 2026-11-13 |
| `twin_q03_a.webp` | P3 A: Se lo digo de frente y buscamos solución | Alejandro Escamilla | [Two people meeting with iphone and ipad (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Two_people_meeting_with_iphone_and_ipad_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/BbQLHCpVUqA>) | CC0 1.0 | 2026-11-13 |
| `twin_q03_b.webp` | P3 B: Lo dejo pasar para no armar problema | John Silliman | [Calm lakeside sunset (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Calm_lakeside_sunset_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/24mYUFbeHto>) | CC0 1.0 | 2026-11-13 |
| `twin_q04_a.webp` | P4 A: Rompo el hielo con un chiste | Matheus Ferrero | [Men laughing by green valley (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Men_laughing_by_green_valley_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/TkrRvwxjb_8>) | CC0 1.0 | 2026-11-13 |
| `twin_q04_b.webp` | P4 B: Observo primero y hablo después | Kate Williams | [A woman looking out the window (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:A_woman_looking_out_the_window_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/v4kBbdsYo_I>) | CC0 1.0 | 2026-11-13 |
| `twin_q05_a.webp` | P5 A: De una, me sirve para la carrera | chuttersnap | [Plants in beakers (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Plants_in_beakers_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/UmncJq4KPcA>) | CC0 1.0 | 2026-11-13 |
| `twin_q05_b.webp` | P5 B: Prefiero tener tiempo libre | Lionello DelPiccolo | [Lionello DelPiccolo 2017 (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Lionello_DelPiccolo_2017_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/5xPnw59QFLQ>) | CC0 1.0 | 2026-11-13 |
| `twin_q06_a.webp` | P6 A: Me le mido a lo que sea | Akhil Chandran | [Do I look colorful ? (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Do_I_look_colorful_%3F_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/lzfk5IMVpgo>) | CC0 1.0 | 2026-11-13 |
| `twin_q06_b.webp` | P6 B: Mejor pido lo de siempre | Thomas Habr | [Best Cheeseburger ever (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Best_Cheeseburger_ever_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/MjMLAst5pUI>) | CC0 1.0 | 2026-11-13 |
| `twin_q07_a.webp` | P7 A: Llevo itinerario, presupuesto y reservas | rawpixel.com | [Planning a trip over coffee (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Planning_a_trip_over_coffee_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/lRssALOk1fU>) | CC0 1.0 | 2026-11-13 |
| `twin_q07_b.webp` | P7 B: Vamos viendo sobre la marcha | Jonatan Pie | [Mountain Road Trip (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Mountain_Road_Trip_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/Xj4thPRvSSs>) | CC0 1.0 | 2026-11-13 |
| `twin_q08_a.webp` | P8 A: Prefiero hablarlo ya, aunque sea incómodo | Joshua Ness | [Coffee Talks (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Coffee_Talks_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/-bEZ_OfWu3Y>) | CC0 1.0 | 2026-11-13 |
| `twin_q08_b.webp` | P8 B: Necesito tiempo antes de hablarlo | frank mckenna | [Man sitting bench sunset (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Man_sitting_bench_sunset_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/5sMtjoDwfH8>) | CC0 1.0 | 2026-11-13 |
| `twin_q09_a.webp` | P9 A: Mandar memes y stickers | Kate SERBIN | [Kate SERBIN 2015 (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Kate_SERBIN_2015_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/B3rMsmQi4Qk>) | CC0 1.0 | 2026-11-13 |
| `twin_q09_b.webp` | P9 B: Responder corto y al punto | Gilles Lambert | [Man on a smartphone (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Man_on_a_smartphone_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/pb_lF8VWaPU>) | CC0 1.0 | 2026-11-13 |
| `twin_q10_a.webp` | P10 A: Me molesta, sé que podía sacar más | Angelina Litvin | [Pencil shavings on a notebook (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Pencil_shavings_on_a_notebook_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/K3uOmmlQmOo>) | CC0 1.0 | 2026-11-13 |
| `twin_q10_b.webp` | P10 B: Pasé, eso es lo importante | Levi Guzman | [Orange sunset celebration (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Orange_sunset_celebration_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/zdSoe8za6Hs>) | CC0 1.0 | 2026-11-13 |
| `twin_q11_a.webp` | P11 A: Me apunto, para eso es la vida | Ian Schneider | [Ian Schneider 2016 (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Ian_Schneider_2016_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/PAykYb-8Er8>) | CC0 1.0 | 2026-11-13 |
| `twin_q11_b.webp` | P11 B: Ya tenía mi noche organizada | Aaron Burden | [Close up book shot (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Close_up_book_shot_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/cTQwN8J08uM>) | CC0 1.0 | 2026-11-13 |
| `twin_q12_a.webp` | P12 A: Me río de mí y sigo | Flo Karr | [Rooftop Skateboarding (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Rooftop_Skateboarding_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/gTX7loRSKlQ>) | CC0 1.0 | 2026-11-13 |
| `twin_q12_b.webp` | P12 B: Me da pena y prefiero que no lo comenten | Drew Hays | [Hiding face behind hands (Unsplash).jpg](https://commons.wikimedia.org/wiki/File:Hiding_face_behind_hands_%28Unsplash%29.jpg) (original: <https://unsplash.com/photos/hdGyEhAMYdo>) | CC0 1.0 | 2026-11-13 |
