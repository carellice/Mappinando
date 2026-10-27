package it.mappinando.app.data

/** Sample entries for the first launch, removable just like any user-created place. */
object DemoPlaces {
    fun create(): List<Place> = listOf(
        Place(
            id = "demo-pave", name = "Pavé", category = PlaceCategory.BARS,
            latitude = 45.47714, longitude = 9.20575,
            address = "Via Felice Casati, 27 · Milano", city = "Milano",
            note = "La prossima colazione lenta. Un caffè, una brioche e nessuna fretta.",
            visited = true, favorite = true,
            tasks = listOf(PlaceTask("pave-1", "Provare la brioche al pistacchio"))
        ),
        Place(
            id = "demo-duomo", name = "Terrazze del Duomo", category = PlaceCategory.VIEWS,
            latitude = 45.46421, longitude = 9.19138,
            address = "Piazza del Duomo · Milano", city = "Milano",
            note = "Salire prima del tramonto e guardare la città dall'alto.",
            favorite = true,
            tasks = listOf(PlaceTask("duomo-1", "Prenotare la visita alle terrazze"), PlaceTask("duomo-2", "Fare una foto al tramonto"))
        ),
        Place(
            id = "demo-madonnina", name = "Trattoria Madonnina", category = PlaceCategory.RESTAURANTS,
            latitude = 45.44991, longitude = 9.17983,
            address = "Via Gentilino, 6 · Milano", city = "Milano",
            note = "Un tavolo in cortile per una serata tra amici.",
            visited = true, favorite = true,
            tasks = listOf(PlaceTask("madonnina-1", "Assaggiare il risotto alla milanese", true), PlaceTask("madonnina-2", "Tornarci con gli amici"))
        ),
        Place(
            id = "demo-prada", name = "Fondazione Prada", category = PlaceCategory.CULTURE,
            latitude = 45.44496, longitude = 9.20513,
            address = "Largo Isarco, 2 · Milano", city = "Milano",
            note = "Un pomeriggio da dedicare all'arte, poi una sosta al Bar Luce.",
            tasks = listOf(PlaceTask("prada-1", "Scoprire la mostra in programma"), PlaceTask("prada-2", "Bere un caffè al Bar Luce"))
        ),
        Place(
            id = "demo-verso", name = "Libreria Verso", category = PlaceCategory.SHOPPING,
            latitude = 45.45683, longitude = 9.18078,
            address = "Corso di Porta Ticinese, 40 · Milano", city = "Milano",
            note = "Perdersi tra gli scaffali e scegliere un libro senza fretta.",
            tasks = listOf(PlaceTask("verso-1", "Scegliere il prossimo romanzo"))
        ),
        Place(
            id = "demo-sempione", name = "Parco Sempione", category = PlaceCategory.NATURE,
            latitude = 45.47360, longitude = 9.17690,
            address = "Piazza Sempione · Milano", city = "Milano",
            note = "Il mio angolo verde in città. Perfetto per staccare un momento.",
            visited = true, favorite = true,
            tasks = listOf(PlaceTask("sempione-1", "Fare una passeggiata fino all'Arco della Pace", true), PlaceTask("sempione-2", "Organizzare un picnic"))
        )
    )
}
