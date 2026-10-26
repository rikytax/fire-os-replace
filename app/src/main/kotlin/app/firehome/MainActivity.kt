package app.firehome

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private sealed interface Screen {
    data object Home : Screen
    data object Live : Screen
    data object AllApps : Screen
    data class Playing(val channel: Channel) : Screen
}

class MainActivity : ComponentActivity() {

    private var apps by mutableStateOf(emptyList<InstalledApp>())
    private var channels by mutableStateOf(emptyList<Channel>())
    private var domotica by mutableStateOf<Domotica?>(null)
    private var lastName by mutableStateOf<String?>(null)
    private var screen by mutableStateOf<Screen>(Screen.Home)

    private val prefs by lazy { getSharedPreferences("home", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastName = prefs.getString(KEY_LAST, null)
        setContent {
            FireTheme {
                // Il Back non chiude mai la Home: dalle altre schermate torna indietro, qui non fa nulla.
                BackHandler { screen = Screen.Home }
                when (val s = screen) {
                    Screen.Home -> HomeScreen(
                        channels = channels,
                        last = channels.firstOrNull { it.name == lastName },
                        shortcuts = apps.shortcuts(),
                        onLive = { screen = Screen.Live },
                        onLast = { channels.firstOrNull { it.name == lastName }?.let(::open) },
                        onApp = ::launch,
                        onAllApps = { screen = Screen.AllApps },
                        casa = domotica?.actions?.map { it.name } ?: emptyList(),
                        onCasa = ::runHomeAction,
                        onSettings = ::openSettings,
                        onMirror = ::openMirroring,
                    )
                    Screen.Live -> LiveScreen(channels, apps, onOpen = ::open)
                    Screen.AllApps -> AllAppsScreen(apps, onApp = ::launch, onAmazon = { HomeGuard.openAmazon(this) })
                    is Screen.Playing -> PlayerScreen(s.channel, zap = ::zap)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ricarica a ogni ritorno: vede le app appena installate e i file channels.json / playlist.m3u aggiornati.
        apps = installedApps(this)
        channels = loadChannels(this) + loadPlaylist(this)
        domotica = loadDomotica(this)
    }

    // Siamo sul display: il servizio Home toglie la copertura e non rilancia più nulla.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            HomeGuard.shown()
            HomeGuard.arrived(this)
        }
    }

    // Il tasto Home mentre siamo già aperti riporta alla schermata principale.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        screen = Screen.Home
    }

    private fun open(channel: Channel) {
        lastName = channel.name
        prefs.edit().putString(KEY_LAST, channel.name).apply()
        if (channel.stream != null) {
            screen = Screen.Playing(channel)
            return
        }
        val query = channel.app
        val target = query?.let { apps.findApp(listOf(it)) }
        val link = channel.url
        if (link != null && (target != null || query == null)) {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            target?.let { view.setPackage(it.pkg) }
            start(view)
            return
        }
        when {
            target != null -> launch(target)
            query != null -> installFromAppstore(query)
        }
    }

    private fun zap(step: Int) {
        val playable = channels.filter { it.stream != null }
        val current = (screen as? Screen.Playing)?.channel ?: return
        if (playable.size < 2) return
        val index = playable.indexOfFirst { it.name == current.name }
        open(playable[(index + step).mod(playable.size)])
    }

    private fun launch(app: InstalledApp) = start(app.intent)

    private fun runHomeAction(index: Int) {
        val ha = domotica ?: return
        val action = ha.actions.getOrNull(index) ?: return
        ha.trigger(action) { ok ->
            runOnUiThread { Toast.makeText(this, if (ok) "${action.name}: fatto" else "${action.name}: non riuscito", Toast.LENGTH_SHORT).show() }
        }
    }

    // Su Fire OS ACTION_SETTINGS apre un ponte che rimanda alla Home Amazon: serve la categoria LEANBACK_SETTINGS.
    private fun openSettings() =
        start(Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS"), fallback = Intent(Settings.ACTION_SETTINGS))

    // Non esiste un'attività verificata per la duplicazione: si apre Impostazioni e si dice dove andare.
    private fun openMirroring() {
        Toast.makeText(this, "Display e suoni → Abilita duplicazione schermo (oppure tieni premuto Home)", Toast.LENGTH_LONG).show()
        openSettings()
    }

    private fun installFromAppstore(name: String) {
        Toast.makeText(this, "$name non è installata", Toast.LENGTH_SHORT).show()
        start(Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?s=" + Uri.encode(name))))
    }

    private fun start(intent: Intent, fallback: Intent? = null) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            if (fallback != null) return start(fallback)
            Toast.makeText(this, "Impossibile aprire", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Impossibile aprire", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val KEY_LAST = "last"
    }
}
