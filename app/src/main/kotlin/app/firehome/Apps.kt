package app.firehome

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color

data class InstalledApp(val label: String, val pkg: String, val intent: Intent)

/** Un'app in evidenza nella Home. Si cerca per package esatto o per nome, nell'ordine dato. */
data class Shortcut(val label: String, val desc: String, val color: Color, val app: InstalledApp)

private class ShortcutSpec(val label: String, val desc: String, val color: Long, val queries: List<String>)

// Per aggiungere o togliere servizi dalla Home basta modificare questa lista.
private val SHORTCUTS = listOf(
    ShortcutSpec("Netflix", "Film e serie TV", 0xFFE50914, listOf("com.netflix.ninja", "Netflix")),
    ShortcutSpec("Prime Video", "Film e serie Amazon", 0xFF00A8E1, listOf("com.amazon.firebat", "Prime Video")),
    ShortcutSpec("Disney+", "Cartoni, Marvel, Star Wars", 0xFF113CCF, listOf("com.disney.disneyplus", "Disney")),
    ShortcutSpec("Jellyfin", "La tua libreria di film", 0xFF6B4FBB, listOf("org.jellyfin.androidtv", "Jellyfin")),
    ShortcutSpec("YouTube", "Video e musica", 0xFFD32F2F, listOf("org.smarttube.stable", "com.amazon.firetv.youtube")),
    ShortcutSpec("BuddyFit", "La tua app", 0xFF1FA67A, listOf("BuddyFit")),
)

fun installedApps(ctx: Context): List<InstalledApp> {
    val pm = ctx.packageManager
    return listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        .flatMap { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .distinctBy { it.first }
        .filter { it.first != ctx.packageName }
        .mapNotNull { (pkg, label) ->
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            intent?.let { InstalledApp(label, pkg, it) }
        }
        .sortedBy { it.label.lowercase() }
}

fun List<InstalledApp>.findApp(queries: List<String>): InstalledApp? =
    queries.firstNotNullOfOrNull { q ->
        firstOrNull { it.pkg == q } ?: firstOrNull { it.label.contains(q, ignoreCase = true) }
    }

fun List<InstalledApp>.shortcuts(): List<Shortcut> =
    SHORTCUTS.mapNotNull { spec -> findApp(spec.queries)?.let { Shortcut(spec.label, spec.desc, Color(spec.color), it) } }
