package app.firehome

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Suggerimenti di film e serie dai dati di TMDB (themoviedb.org), con la disponibilità in streaming in Italia
 * (fornita da JustWatch tramite TMDB). Servono una chiave gratuita di TMDB e una riga di configurazione:
 * `python3 scripts/firestick.py suggerimenti LA_TUA_CHIAVE`, che crea `suggerimenti.json` sulla Fire Stick.
 * Questo prodotto usa l'API di TMDB ma non è approvato né certificato da TMDB.
 */
data class Suggestion(
    val title: String,
    val kind: String,
    val year: String,
    val rating: Double,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String,
    val providers: List<String>,
)

private const val CACHE_HOURS = 12
private const val MAX_CANDIDATES = 24
private const val MAX_SHOWN = 14
private val SERVICES = Regex("netflix|prime|disney|raiplay|infinity", RegexOption.IGNORE_CASE)

fun loadSuggestions(ctx: Context): List<Suggestion> {
    val key = readKey(ctx) ?: return emptyList()
    val cache = File(ctx.cacheDir, "suggestions.json")
    if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < CACHE_HOURS * 3_600_000L) {
        runCatching { return parse(JSONArray(cache.readText())) }
    }
    val fresh = runCatching { fetch(key) }.getOrNull() ?: return runCatching { parse(JSONArray(cache.readText())) }.getOrDefault(emptyList())
    runCatching { cache.writeText(toJson(fresh).toString()) }
    return fresh
}

private fun readKey(ctx: Context): String? {
    val file = File(ctx.getExternalFilesDir(null), "suggerimenti.json")
    if (!file.exists()) return null
    return runCatching { JSONObject(file.readText()).optString("tmdb").trim().ifEmpty { null } }.getOrNull()
}

private fun get(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 6000
    connection.readTimeout = 8000
    try {
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun fetch(key: String): List<Suggestion> {
    val trending = JSONObject(get("https://api.themoviedb.org/3/trending/all/week?api_key=$key&language=it-IT"))
        .getJSONArray("results")
    val found = mutableListOf<Suggestion>()
    for (i in 0 until minOf(trending.length(), MAX_CANDIDATES)) {
        if (found.size >= MAX_SHOWN) break
        val o = trending.getJSONObject(i)
        val kind = o.optString("media_type")
        if (kind != "movie" && kind != "tv") continue
        val providers = runCatching { providersOf(key, kind, o.getInt("id")) }.getOrDefault(emptyList())
        if (providers.isEmpty()) continue // si propone solo ciò che si può guardare sui servizi che usi
        val date = o.optString("release_date").ifEmpty { o.optString("first_air_date") }
        found += Suggestion(
            title = o.optString("title").ifEmpty { o.optString("name") },
            kind = if (kind == "movie") "Film" else "Serie",
            year = date.take(4),
            rating = o.optDouble("vote_average", 0.0),
            posterUrl = o.optString("poster_path").ifEmpty { null }?.let { "https://image.tmdb.org/t/p/w342$it" },
            backdropUrl = o.optString("backdrop_path").ifEmpty { null }?.let { "https://image.tmdb.org/t/p/w780$it" },
            overview = o.optString("overview"),
            providers = providers,
        )
    }
    return found
}

private fun providersOf(key: String, kind: String, id: Int): List<String> {
    val flatrate = JSONObject(get("https://api.themoviedb.org/3/$kind/$id/watch/providers?api_key=$key"))
        .optJSONObject("results")?.optJSONObject("IT")?.optJSONArray("flatrate") ?: return emptyList()
    return (0 until flatrate.length())
        .map { flatrate.getJSONObject(it).optString("provider_name") }
        .filter { SERVICES.containsMatchIn(it) }
        .distinct()
}

private fun toJson(list: List<Suggestion>) = JSONArray().also { array ->
    list.forEach {
        array.put(
            JSONObject()
                .put("title", it.title).put("kind", it.kind).put("year", it.year).put("rating", it.rating)
                .put("poster", it.posterUrl ?: "").put("backdrop", it.backdropUrl ?: "").put("overview", it.overview)
                .put("providers", JSONArray(it.providers)),
        )
    }
}

private fun parse(array: JSONArray): List<Suggestion> = (0 until array.length()).map {
    val o = array.getJSONObject(it)
    val providers = o.getJSONArray("providers")
    Suggestion(
        title = o.getString("title"),
        kind = o.getString("kind"),
        year = o.optString("year"),
        rating = o.optDouble("rating", 0.0),
        posterUrl = o.optString("poster").ifEmpty { null },
        backdropUrl = o.optString("backdrop").ifEmpty { null },
        overview = o.optString("overview"),
        providers = (0 until providers.length()).map { i -> providers.getString(i) },
    )
}

/** Come riconoscere sulla Fire Stick l'app del servizio indicato da TMDB (per package o per nome). */
fun providerQueries(name: String): List<String> = when {
    name.contains("netflix", true) -> listOf("com.netflix.ninja", "Netflix")
    name.contains("prime", true) -> listOf("com.amazon.firebat", "Prime Video")
    name.contains("disney", true) -> listOf("com.disney.disneyplus", "Disney")
    name.contains("raiplay", true) -> listOf("RaiPlay")
    name.contains("infinity", true) -> listOf("Mediaset Infinity")
    else -> emptyList()
}

fun shortProvider(name: String): String = when {
    name.contains("netflix", true) -> "Netflix"
    name.contains("prime", true) -> "Prime Video"
    name.contains("disney", true) -> "Disney+"
    name.contains("raiplay", true) -> "RaiPlay"
    name.contains("infinity", true) -> "Infinity"
    else -> name
}
