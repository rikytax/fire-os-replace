package app.firehome

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tile di domotica in Home, per Home Assistant. Si configura con `domotica.json`, da caricare con adb in
 * /sdcard/Android/data/app.firehome/files/ (come playlist.m3u). Due modi per ogni azione:
 *  - "webhook": l'id di un webhook di Home Assistant (consigliato: sulla Fire Stick non resta nessun token);
 *  - "service" + "entity" (+ "token" nel file): chiama l'API di Home Assistant, es. "light/toggle" su "light.salotto".
 */
class HomeAction(val name: String, val path: String, val body: String)

class Domotica(val baseUrl: String, val token: String?, val actions: List<HomeAction>)

fun loadDomotica(ctx: Context): Domotica? {
    val file = File(ctx.getExternalFilesDir(null), "domotica.json")
    if (!file.exists()) return null
    return runCatching {
        val root = JSONObject(file.readText())
        val token = root.optString("token").ifEmpty { null }
        val array = root.getJSONArray("actions")
        val actions = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val webhook = o.optString("webhook")
            if (webhook.isNotEmpty()) {
                HomeAction(o.getString("name"), "/api/webhook/$webhook", "{}")
            } else {
                val entity = o.optString("entity")
                val body = if (entity.isEmpty()) "{}" else JSONObject().put("entity_id", entity).toString()
                HomeAction(o.getString("name"), "/api/services/" + o.getString("service"), body)
            }
        }
        Domotica(root.getString("url").trimEnd('/'), token, actions)
    }.getOrNull()
}

/** Chiama Home Assistant fuori dal thread principale; [done] riceve true se la risposta è 2xx. */
fun Domotica.trigger(action: HomeAction, done: (Boolean) -> Unit) {
    Thread {
        val ok = runCatching {
            val connection = URL(baseUrl + action.path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
                connection.outputStream.use { it.write(action.body.toByteArray()) }
                connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
        done(ok)
    }.start()
}
