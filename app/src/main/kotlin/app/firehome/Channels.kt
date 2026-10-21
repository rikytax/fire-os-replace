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
