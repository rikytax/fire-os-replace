package app.firehome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF0B0D12)
private val Surface = Color(0xFF1B2029)
private val Accent = Color(0xFF1B6EF3)

@Composable
fun FireTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Surface)) {
        Box(Modifier.fillMaxSize().background(Bg)) { content() }
    }
}

/** Mette il focus su un elemento appena la schermata compare (il telecomando parte da lì). */
@Composable
private fun rememberInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    return requester
}

@Composable
private fun Tile(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    color: Color = Surface,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "tile-scale")
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(if (focused) 3.dp else 0.dp, Color.White, shape)
            .clip(shape)
            .background(color)
            .clickable(onClick = onClick)
            .padding(18.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.ITALY).format(now) }
    val date = remember(now) {
        SimpleDateFormat("EEEE d MMMM", Locale.ITALY).format(now).replaceFirstChar { it.uppercase() }
    }
    Column {
        Text(time, fontSize = 64.sp, fontWeight = FontWeight.Light)
        Text(date, fontSize = 20.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
fun HomeScreen(
    channels: List<Channel>,
    last: Channel?,
    shortcuts: List<Shortcut>,
    onLive: () -> Unit,
    onLast: () -> Unit,
    onApp: (InstalledApp) -> Unit,
    onAllApps: () -> Unit,
    onSettings: () -> Unit,
    onMirror: () -> Unit,
) {
    val focus = rememberInitialFocus()
    Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 48.dp)) {
        Clock()
        Spacer(Modifier.height(40.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Tile(
                "TV Live",
                Modifier.size(380.dp, 190.dp),
                subtitle = "${channels.size} canali",
                color = Accent,
                focusRequester = focus,
                onClick = onLive,
            )
            if (last != null) {
                Tile("Ultimo canale", Modifier.size(280.dp, 190.dp), subtitle = last.name, onClick = onLast)
            }
            Tile("Duplica schermo", Modifier.size(280.dp, 190.dp), subtitle = "Tablet → TV", onClick = onMirror)
        }
        Spacer(Modifier.height(36.dp))
        Text("App", fontSize = 22.sp, color = Color.White.copy(alpha = 0.7f))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
        ) {
            items(shortcuts) { s -> Tile(s.label, Modifier.size(220.dp, 124.dp), color = s.color) { onApp(s.app) } }
            item { Tile("Tutte le app", Modifier.size(220.dp, 124.dp), onClick = onAllApps) }
            item { Tile("Impostazioni", Modifier.size(220.dp, 124.dp), onClick = onSettings) }
        }
    }
}

/** Sottotitolo di un canale: dice subito se parte al volo o se devi prima installare l'app. */
private fun subtitleOf(c: Channel, installed: List<InstalledApp>): String = when {
    c.stream != null -> "Player integrato"
    c.app != null && installed.findApp(listOf(c.app)) == null -> "Installa dall'Appstore"
    else -> "App ufficiale"
}

@Composable
fun LiveScreen(channels: List<Channel>, installed: List<InstalledApp>, onOpen: (Channel) -> Unit) {
    val focus = rememberInitialFocus()
    val groups = channels.groupBy { it.group }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 64.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { Text("TV Live", fontSize = 40.sp, fontWeight = FontWeight.Light) }
        groups.forEach { (group, list) ->
            item { Text(group, fontSize = 22.sp, color = Color.White.copy(alpha = 0.7f)) }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    items(list) { c ->
                        Tile(
                            c.name,
                            Modifier.size(260.dp, 150.dp),
                            subtitle = subtitleOf(c, installed),
                            color = Color(c.color),
                            focusRequester = if (c === channels.first()) focus else null,
                        ) { onOpen(c) }
                    }
                }
            }
        }
    }
}

@Composable
fun AllAppsScreen(apps: List<InstalledApp>, onApp: (InstalledApp) -> Unit, onAmazon: () -> Unit) {
    val focus = rememberInitialFocus()
    LazyVerticalGrid(
        GridCells.Adaptive(220.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 64.dp, vertical = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(apps) { a ->
            Tile(
                a.label,
                Modifier.height(110.dp),
                focusRequester = if (a === apps.first()) focus else null,
            ) { onApp(a) }
        }
        item { Tile("Home Amazon", Modifier.height(110.dp), subtitle = "Resta finché apri un'app", onClick = onAmazon) }
    }
}
