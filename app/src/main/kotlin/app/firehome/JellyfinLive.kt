package app.firehome

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * La TV in diretta del TUO server Jellyfin (Live TV): tuner, IPTV regolare o playlist che hai configurato lì.
 * FireHome non contiene né cerca liste di canali: mostra quelli che il tuo Jellyfin già conosce.
 *
 * Configurazione: `jellyfin.json` in /sdcard/Android/data/app.firehome/files/ (lo crea
 * `python3 scripts/firestick.py jellyfin ...`):
 *   { "url": "http://192.168.1.20:8096", "user": "firehome", "password": "..." }
 * Conviene un utente Jellyfin dedicato, con accesso alla sola Live TV.
 *
 * L'API usata (verificata sulla specifica ufficiale di Jellyfin 10.10/10.11):
 *   POST /Users/AuthenticateByName        { Username, Pw }          -> AccessToken, User.Id
 *   GET  /LiveTv/Channels?userId&addCurrentProgram=true             -> Items[] (Id, Name, Number, CurrentProgram)
 *   POST /Items/{id}/PlaybackInfo         { AutoOpenLiveStream, DeviceProfile, ... } -> MediaSources[]
 *   POST /LiveStreams/Close?liveStreamId
 */
class JellyfinConfig(val url: String, val user: String, val password: String)

class LiveChannel(val id: String, val name: String, val number: String, val nowPlaying: String)

class LiveStream(val url: String, val mime: String, val liveStreamId: String?)

object Jellyfin {
    private const val CLIENT = "FireHome"
    private const val VERSION = "0.4.0"
    private const val DEVICE = "Fire TV Stick"
    private const val DEVICE_ID = "firehome-fire-tv"

    @Volatile private var base: String? = null
    @Volatile private var token: String? = null
    @Volatile private var userId: String? = null

    fun loadConfig(ctx: Context): JellyfinConfig? {
        val file = File(ctx.getExternalFilesDir(null), "jellyfin.json")
        if (!file.exists()) return null
        return runCatching {
            val o = JSONObject(file.readText())
            JellyfinConfig(o.getString("url").trim().trimEnd('/'), o.getString("user"), o.getString("password"))
        }.getOrNull()
    }

    private fun authorization(withToken: Boolean): String {
        val parts = mutableListOf("Client=\"$CLIENT\"", "Version=\"$VERSION\"", "DeviceId=\"$DEVICE_ID\"", "Device=\"$DEVICE\"")
        if (withToken) token?.let { parts += "Token=\"$it\"" }
        return "MediaBrowser " + parts.joinToString(", ")
    }

    /** Intestazioni da aggiungere alle richieste verso il server Jellyfin (anche i segmenti dello streaming). */
    fun headersFor(uri: Uri): Map<String, String>? {
        val b = base ?: return null
        val t = token ?: return null
        if (!uri.toString().startsWith(b)) return null
        return mapOf("Authorization" to authorization(true), "X-Emby-Token" to t)
    }

    private fun request(method: String, path: String, body: String? = null, authenticated: Boolean = true): String {
        val b = base ?: throw IllegalStateException("Jellyfin non configurato")
        val connection = URL(b + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 6000
        connection.readTimeout = 15000
        connection.setRequestProperty("Authorization", authorization(authenticated))
        connection.setRequestProperty("X-Emby-Authorization", authorization(authenticated))
        if (authenticated) token?.let { connection.setRequestProperty("X-Emby-Token", it) }
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("Jellyfin: HTTP $code")
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun login(config: JellyfinConfig) {
        base = config.url
        token = null
        val answer = JSONObject(
            request(
                "POST", "/Users/AuthenticateByName",
                JSONObject().put("Username", config.user).put("Pw", config.password).toString(),
                authenticated = false,
            ),
        )
        token = answer.getString("AccessToken")
        userId = answer.getJSONObject("User").getString("Id")
    }

    /** Esegue [block] dopo il login; se il token è scaduto (401) riprova una volta con un nuovo login. */
    private fun <T> withSession(config: JellyfinConfig, block: () -> T): T {
        if (token == null || base != config.url) login(config)
        return try {
            block()
        } catch (e: IllegalStateException) {
            login(config)
            block()
        }
    }

    fun channels(config: JellyfinConfig): List<LiveChannel> = withSession(config) {
        val answer = JSONObject(request("GET", "/LiveTv/Channels?userId=$userId&addCurrentProgram=true&limit=300"))
        val items = answer.optJSONArray("Items") ?: JSONArray()
        (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            LiveChannel(
                id = o.getString("Id"),
                name = o.optString("Name"),
                number = o.optString("Number").ifEmpty { o.optString("ChannelNumber") },
                nowPlaying = o.optJSONObject("CurrentProgram")?.optString("Name").orEmpty(),
            )
        }.sortedBy { it.number.toDoubleOrNull() ?: Double.MAX_VALUE }
    }

    /** Apre il canale sul server e restituisce l'indirizzo da riprodurre: HLS se serve transcodifica, altrimenti il flusso diretto. */
    fun open(config: JellyfinConfig, channelId: String): LiveStream = withSession(config) {
        val profile = JSONObject()
            .put("Name", CLIENT)
            .put("MaxStreamingBitrate", 12_000_000)
            .put(
                "DirectPlayProfiles",
                JSONArray().put(
                    JSONObject().put("Container", "ts,mpegts").put("Type", "Video")
                        .put("VideoCodec", "h264,hevc,mpeg2video").put("AudioCodec", "aac,ac3,eac3,mp3,mp2"),
                ),
            )
            .put(
                "TranscodingProfiles",
                JSONArray().put(
                    JSONObject().put("Container", "ts").put("Type", "Video").put("VideoCodec", "h264")
                        .put("AudioCodec", "aac,mp3").put("Protocol", "hls").put("Context", "Streaming")
                        .put("MaxAudioChannels", "2").put("MinSegments", 1).put("BreakOnNonKeyFrames", true),
                ),
            )
        val body = JSONObject()
            .put("UserId", userId)
            .put("AutoOpenLiveStream", true)
            .put("MaxStreamingBitrate", 12_000_000)
            .put("MaxAudioChannels", 2)
            .put("EnableDirectPlay", true)
            .put("EnableDirectStream", true)
            .put("EnableTranscoding", true)
            .put("DeviceProfile", profile)
        val info = JSONObject(request("POST", "/Items/$channelId/PlaybackInfo", body.toString()))
        val sources = info.optJSONArray("MediaSources")
        if (sources == null || sources.length() == 0) throw IllegalStateException("Jellyfin: nessun flusso per questo canale")
        val source = sources.getJSONObject(0)
        val liveStreamId = source.optString("LiveStreamId").ifEmpty { null }
        val transcoding = source.optString("TranscodingUrl")
        val t = token.orEmpty()
        if (transcoding.isNotEmpty()) {
            val sep = if (transcoding.contains('?')) "&" else "?"
            LiveStream("${config.url}$transcoding${sep}ApiKey=$t", "hls", liveStreamId)
        } else {
            if (liveStreamId == null) throw IllegalStateException("Jellyfin: canale non apribile")
            val container = source.optString("Container").substringBefore(',').ifEmpty { "ts" }
            LiveStream("${config.url}/LiveTv/LiveStreamFiles/$liveStreamId/stream.$container?ApiKey=$t", "auto", liveStreamId)
        }
    }

    /** Chiude il flusso aperto sul server (se resta aperto, occupa un sintonizzatore o una connessione dell'IPTV). */
    fun close(config: JellyfinConfig, liveStreamId: String) {
        runCatching { withSession(config) { request("POST", "/LiveStreams/Close?liveStreamId=$liveStreamId") } }
    }
}
