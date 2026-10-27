package it.mappinando.app.data

import java.util.UUID

enum class PlaceCategory(val label: String, val emoji: String) {
    RESTAURANTS("Ristoranti", "🍝"),
    BARS("Bar e caffè", "☕"),
    VIEWS("Panorami", "🌅"),
    CULTURE("Cultura", "🏛️"),
    SHOPPING("Negozi", "🛍️"),
    NATURE("Natura", "🌿")
}

data class PlaceTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val done: Boolean = false
)

data class Place(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: PlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val note: String = "",
    val visited: Boolean = false,
    val favorite: Boolean = false,
    val tasks: List<PlaceTask> = emptyList(),
    val city: String = ""
)

/** The same bounds apply to both UI input and restored local data. */
object PlaceValidation {
    const val MAX_NAME_LENGTH = 160
    const val MAX_ADDRESS_LENGTH = 500
    const val MAX_CITY_LENGTH = 120
    const val MAX_NOTE_LENGTH = 8_000
    const val MAX_TASK_LENGTH = 300
    const val MAX_TASKS = 200

    fun normalized(place: Place): Place {
        val normalized = place.copy(
            name = place.name.trim(),
            address = place.address.trim(),
            city = PlaceCity.normalize(place.city),
            note = place.note.trim(),
            tasks = place.tasks.map { it.copy(title = it.title.trim()) }
        )
        require(normalized.id.isNotBlank()) { "Identificativo del luogo mancante." }
        require(normalized.name.isNotEmpty()) { "Dai un nome a questo luogo." }
        require(normalized.name.length <= MAX_NAME_LENGTH) { "Il nome è troppo lungo." }
        require(normalized.latitude.isFinite() && normalized.latitude in -90.0..90.0) {
            "Latitudine non valida."
        }
        require(normalized.longitude.isFinite() && normalized.longitude in -180.0..180.0) {
            "Longitudine non valida."
        }
        require(normalized.address.length <= MAX_ADDRESS_LENGTH) { "L'indirizzo è troppo lungo." }
        require(normalized.city.length <= MAX_CITY_LENGTH) { "Il nome della città è troppo lungo." }
        require(normalized.note.length <= MAX_NOTE_LENGTH) { "La nota è troppo lunga." }
        require(normalized.tasks.size <= MAX_TASKS) { "Hai raggiunto il limite di attività per questo luogo." }
        require(normalized.tasks.map { it.id }.distinct().size == normalized.tasks.size) {
            "Identificativi delle attività duplicati."
        }
        normalized.tasks.forEach { task ->
            require(task.id.isNotBlank()) { "Identificativo dell'attività mancante." }
            require(task.title.isNotEmpty()) { "Scrivi qualcosa da fare." }
            require(task.title.length <= MAX_TASK_LENGTH) { "Il testo dell'attività è troppo lungo." }
        }
        return normalized
    }
}
