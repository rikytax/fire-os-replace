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
                        onSettings = { start(Intent(Settings.ACTION_SETTINGS)) },
                    )
                    Screen.Live -> LiveScreen(channels, onOpen = ::open)
                    Screen.AllApps -> AllAppsScreen(apps, onApp = ::launch)
                    is Screen.Playing -> PlayerScreen(s.channel, zap = ::zap)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ricarica a ogni ritorno: vede le app appena installate e un channels.json aggiornato.
        apps = installedApps(this)
        channels = loadChannels(this)
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

    private fun installFromAppstore(name: String) {
        Toast.makeText(this, "$name non è installata", Toast.LENGTH_SHORT).show()
        start(Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?s=" + Uri.encode(name))))
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Impossibile aprire", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Impossibile aprire", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val KEY_LAST = "last"
    }
}
