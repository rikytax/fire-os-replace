package app.firehome

import android.content.Context
import android.graphics.Color
import org.json.JSONObject
import java.io.File

/**
 * Un canale può essere:
 *  - [stream] valorizzato → riprodotto dal player integrato (flusso tuo, HLS o DASH);
 *  - solo [app] → apre l'app ufficiale (cerca per nome o package tra le app installate).
 */
data class Channel(
    val name: String,
    val group: String,
    val color: Int,
    val app: String?,
    val stream: String?,
    val dash: Boolean,
    val license: String?,
)

/**
 * Legge `channels.json` da /sdcard/Android/data/app.firehome/files/ se esiste
 * (si aggiorna con `adb push`, senza ricompilare), altrimenti da quello incluso nell'APK.
 */
fun loadChannels(ctx: Context): List<Channel> {
    val override = File(ctx.getExternalFilesDir(null), "channels.json")
    return runCatching { parseChannels(override.readText()) }.getOrNull()
        ?: parseChannels(ctx.assets.open("channels.json").bufferedReader().use { it.readText() })
}

/**
 * Legge `playlist.m3u` (formato M3U, quello di qualunque player IPTV) da
 * /sdcard/Android/data/app.firehome/files/. I canali compaiono subito nella TV Live,
 * ciascuno con il suo gruppo (group-title). Se il file manca, non succede nulla.
 */
fun loadPlaylist(ctx: Context): List<Channel> {
    val file = File(ctx.getExternalFilesDir(null), "playlist.m3u")
    return if (file.exists()) runCatching { parseM3u(file.readText()) }.getOrDefault(emptyList()) else emptyList()
}

private fun parseM3u(text: String): List<Channel> {
    val channels = mutableListOf<Channel>()
    var name: String? = null
    var group = "Altri canali"
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        when {
            line.startsWith("#EXTINF") -> {
                name = line.substringAfterLast(',').trim()
                group = Regex("""group-title="([^"]*)"""").find(line)?.groupValues?.get(1)?.ifEmpty { null } ?: "Altri canali"
            }
            line.isNotEmpty() && !line.startsWith("#") -> {
                channels += Channel(
                    name = name?.ifEmpty { null } ?: line,
                    group = group,
                    color = Color.parseColor("#2A2F3A"),
                    app = null,
                    stream = line,
                    dash = line.substringBefore('?').endsWith(".mpd"),
                    license = null,
                )
                name = null
                group = "Altri canali"
            }
        }
    }
    return channels
}

private fun parseChannels(json: String): List<Channel> {
    val array = JSONObject(json).getJSONArray("channels")
    return (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        Channel(
            name = o.getString("name"),
            group = o.optString("group", "Dirette"),
            color = Color.parseColor(o.optString("color", "#2A2F3A")),
            app = o.optString("app").ifEmpty { null },
            stream = o.optString("stream").ifEmpty { null },
            dash = o.optString("type") == "dash",
            license = o.optString("license").ifEmpty { null },
        )
    }
}
