package it.mappinando.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-local storage; no account, network connection, or location permission required.
 * Create one instance per application/activity and observe [places] from Compose.
 * New collections are empty. Legacy examples are removed only when still unmodified.
 */
class PlaceRepository(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences("mappinando_places", Context.MODE_PRIVATE)
    private val lock = Any()
    private val mutablePlaces = MutableStateFlow(load())
    val places: StateFlow<List<Place>> = mutablePlaces.asStateFlow()

    fun add(place: Place): Place = synchronized(lock) {
        val validated = PlaceValidation.normalized(place)
        require(mutablePlaces.value.none { it.id == validated.id }) { "Questo luogo è già salvato." }
        save(mutablePlaces.value + validated)
        validated
    }

    fun update(place: Place): Boolean = synchronized(lock) {
        val validated = PlaceValidation.normalized(place)
        if (mutablePlaces.value.none { it.id == validated.id }) return@synchronized false
        save(mutablePlaces.value.map { if (it.id == validated.id) validated else it })
        true
    }

    fun delete(id: String): Boolean = synchronized(lock) {
        val next = mutablePlaces.value.filterNot { it.id == id }
        if (next.size == mutablePlaces.value.size) return@synchronized false
        save(next)
        true
    }

    fun toggleVisited(id: String): Boolean = change(id) { it.copy(visited = !it.visited) }

    fun toggleFavorite(id: String): Boolean = change(id) { it.copy(favorite = !it.favorite) }

    fun toggleTask(placeId: String, taskId: String): Boolean = change(placeId) { place ->
        place.copy(tasks = place.tasks.map { if (it.id == taskId) it.copy(done = !it.done) else it })
    }

    fun addTask(placeId: String, title: String): Boolean = change(placeId) { place ->
        place.copy(tasks = place.tasks + PlaceTask(title = title))
    }

    fun deleteTask(placeId: String, taskId: String): Boolean = change(placeId) { place ->
        place.copy(tasks = place.tasks.filterNot { it.id == taskId })
    }

    fun clearAll() = synchronized(lock) { save(emptyList()) }

    private fun change(id: String, transform: (Place) -> Place): Boolean = synchronized(lock) {
        val current = mutablePlaces.value.firstOrNull { it.id == id } ?: return@synchronized false
        val validated = PlaceValidation.normalized(transform(current))
        save(mutablePlaces.value.map { if (it.id == id) validated else it })
        true
    }

    private fun save(places: List<Place>) {
        // apply updates SharedPreferences memory immediately, then writes off the UI thread.
        preferences.edit().putString(PLACES_KEY, encode(places).toString()).apply()
        mutablePlaces.value = places.toList()
    }

    private fun load(): List<Place> {
        if (!preferences.contains(PLACES_KEY)) {
            return emptyList()
        }
        val serialized = preferences.getString(PLACES_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(serialized)
            val seenIds = mutableSetOf<String>()
            val restored = buildList {
                for (index in 0 until array.length()) {
                    // One malformed record must not discard the user's other saved places.
                    runCatching { decodePlace(array.getJSONObject(index)) }.getOrNull()?.let { place ->
                        if (seenIds.add(place.id)) add(place)
                    }
                }
            }
            if (!preferences.getBoolean("examples.removed.v1", false)) {
                val untouched = DemoPlaces.create().toSet()
                val personal = restored.filterNot { it in untouched }
                preferences.edit().putString(PLACES_KEY, encode(personal).toString())
                    .putBoolean("examples.removed.v1", true).apply()
                personal
            } else restored
        }.getOrElse {
            // Preserve unreadable data for recovery; never replace it with demo content.
            if (!preferences.contains(CORRUPT_BACKUP_KEY)) {
                preferences.edit().putString(CORRUPT_BACKUP_KEY, serialized).apply()
            }
            emptyList()
        }
    }

    private fun encode(places: List<Place>): JSONArray = JSONArray().apply {
        places.forEach { place ->
            put(JSONObject().apply {
                put("id", place.id)
                put("name", place.name)
                put("category", place.category.name)
                put("latitude", place.latitude)
                put("longitude", place.longitude)
                put("address", place.address)
                put("city", place.city)
                put("note", place.note)
                put("visited", place.visited)
                put("favorite", place.favorite)
                put("tasks", JSONArray().apply {
                    place.tasks.forEach { task ->
                        put(JSONObject().apply {
                            put("id", task.id)
                            put("title", task.title)
                            put("done", task.done)
                        })
                    }
                })
            })
        }
    }

    private fun decodePlace(json: JSONObject): Place {
        val tasks = json.optJSONArray("tasks") ?: JSONArray()
        return PlaceValidation.normalized(Place(
            id = json.getString("id"),
            name = json.getString("name"),
            category = PlaceCategory.valueOf(json.getString("category")),
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            address = json.optString("address", ""),
            city = if (json.has("city") && !json.isNull("city")) {
                json.optString("city", "")
            } else {
                PlaceCity.legacyCity(json.optString("address", ""))
            },
            note = json.optString("note", ""),
            visited = json.optBoolean("visited", false),
            favorite = json.optBoolean("favorite", false),
            tasks = buildList {
                for (index in 0 until tasks.length()) {
                    val task = tasks.getJSONObject(index)
                    add(PlaceTask(task.getString("id"), task.getString("title"), task.optBoolean("done", false)))
                }
            }
        ))
    }

    private companion object {
        const val PLACES_KEY = "places.v1"
        const val CORRUPT_BACKUP_KEY = "places.v1.recovery"
    }
}
