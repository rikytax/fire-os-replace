package app.firehome

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Pensata per lo schermo della Fire Stick: circa 960 x 540 dp. Tutto scorre, così nulla viene tagliato.
private val Bg = Color(0xFF0A1024)
private val Neutral = Color.Unspecified
private val Accent = Color(0xFF4F7DFF)
private val Amber = Color(0xFFB7791F)

/**
 * Sfondo "aurora": un gradiente scuro con tre aloni di colore. Il vetro dei tile è fatto di sole trasparenze
 * e gradienti (niente sfocatura reale, che su una Fire Stick scatterebbe).
 */
@Composable
fun FireTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Bg)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF0A1024), Color(0xFF131B3F), Color(0xFF1E1240))))
                .drawBehind {
                    fun halo(color: Color, x: Float, y: Float, radius: Float) = drawRect(
                        Brush.radialGradient(
                            listOf(color, Color.Transparent),
                            center = Offset(size.width * x, size.height * y),
                            radius = size.minDimension * radius,
                        ),
                    )
                    halo(Color(0x553B6CFF), 0.12f, 0.05f, 0.9f)
                    halo(Color(0x558A3FFC), 0.95f, 0.95f, 1.0f)
                    halo(Color(0x3319C2B8), 0.70f, 0.15f, 0.6f)
                },
        ) { content() }
    }
}

/** Mette il focus su un elemento appena la schermata compare (il telecomando parte da lì). */
@Composable
private fun rememberInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    return requester
}

// --- vetro ------------------------------------------------------------------

/** Un riquadro di vetro: trasparente, con bordo luminoso e riflesso; al focus si alza con una molla e si accende. */
@Composable
private fun Glass(
    modifier: Modifier = Modifier,
    tint: Color = Neutral,
    corner: Dp = 20.dp,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(dampingRatio = 0.62f, stiffness = 380f), label = "glass-scale")
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "glass-lift")
    val shape = RoundedCornerShape(corner)
    val tinted = tint != Neutral
    val glow = if (tinted) tint else Color.White
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (lift > 0f) {
                    drawCircle(
                        Brush.radialGradient(listOf(glow.copy(alpha = 0.40f * lift), Color.Transparent), center = center, radius = size.maxDimension * 0.8f),
                        radius = size.maxDimension * 0.8f,
                    )
                }
            }
            .border(
                1.2.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f + 0.55f * lift), Color.White.copy(alpha = 0.05f + 0.10f * lift))),
                shape,
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f + 0.12f * lift), Color.White.copy(alpha = 0.06f + 0.06f * lift))))
            .then(if (tinted) Modifier.background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.42f), tint.copy(alpha = 0.14f)))) else Modifier)
            .clickable(onClick = onClick),
        content = content,
    )
}

// --- icone ------------------------------------------------------------------

private enum class Glyph { Tv, Cast, Grid, Gear, House, Play }

@Composable
private fun GlyphIcon(glyph: Glyph, s: Dp) {
    val color = Color.White
    when (glyph) {
        Glyph.Gear -> Icon(Icons.Filled.Settings, null, Modifier.size(s), tint = color)
        Glyph.House -> Icon(Icons.Filled.Home, null, Modifier.size(s), tint = color)
        Glyph.Play -> Icon(Icons.Filled.PlayArrow, null, Modifier.size(s), tint = color)
        else -> Canvas(Modifier.size(s)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = w * 0.075f, cap = StrokeCap.Round)
            when (glyph) {
                Glyph.Tv -> {
                    drawRoundRect(color, Offset(w * 0.08f, h * 0.16f), Size(w * 0.84f, h * 0.56f), CornerRadius(w * 0.12f), style = stroke)
                    drawLine(color, Offset(w * 0.30f, h * 0.86f), Offset(w * 0.70f, h * 0.86f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                Glyph.Cast -> {
                    drawRoundRect(color, Offset(w * 0.08f, h * 0.20f), Size(w * 0.84f, h * 0.60f), CornerRadius(w * 0.10f), style = stroke)
                    val cx = w * 0.20f
                    val cy = h * 0.68f
                    drawCircle(color, radius = w * 0.045f, center = Offset(cx, cy))
                    for (r in listOf(w * 0.17f, w * 0.31f)) {
                        drawArc(color, 270f, 90f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = stroke)
                    }
                }
                else -> {
                    val cell = w * 0.32f
                    for (x in listOf(0.14f, 0.54f)) for (y in listOf(0.14f, 0.54f)) {
                        drawRoundRect(color, Offset(w * x, h * y), Size(cell, cell), CornerRadius(w * 0.07f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlyphBadge(glyph: Glyph, badge: Dp) {
    Box(Modifier.size(badge).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        GlyphIcon(glyph, badge * 0.55f)
    }
}

/** L'icona vera dell'app installata. */
@Composable
private fun AppIcon(pkg: String, s: Dp) {
    val context = LocalContext.current
    val bitmap = remember(pkg) {
        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, null, Modifier.size(s).clip(RoundedCornerShape(s * 0.22f)))
    } else {
        Box(Modifier.size(s).clip(RoundedCornerShape(s * 0.22f)).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Text(pkg.substringAfterLast('.').take(1).uppercase(), fontSize = 20.sp, color = Color.White)
        }
    }
}

// --- elementi della Home ------------------------------------------------------

/** Scheda larga: icona a sinistra, titolo e spiegazione a destra. */
@Composable
private fun WideCard(
    title: String,
    desc: String?,
    modifier: Modifier,
    glyph: Glyph? = null,
    iconPkg: String? = null,
    tint: Color = Neutral,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Glass(modifier, tint, focusRequester = focusRequester, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                iconPkg != null -> AppIcon(iconPkg, 44.dp)
                glyph != null -> GlyphBadge(glyph, 44.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.96f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (desc != null) {
                    Text(desc, fontSize = 13.sp, color = Color.White.copy(alpha = 0.68f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Scheda verticale: icona in alto, nome e spiegazione sotto. */
@Composable
private fun AppCard(
    title: String,
    desc: String?,
    modifier: Modifier,
    glyph: Glyph? = null,
    iconPkg: String? = null,
    tint: Color = Neutral,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Glass(modifier, tint, focusRequester = focusRequester, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                iconPkg != null -> AppIcon(iconPkg, 44.dp)
                glyph != null -> GlyphBadge(glyph, 44.dp)
            }
            Spacer(Modifier.height(6.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.96f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (desc != null) {
                Text(desc, fontSize = 11.sp, color = Color.White.copy(alpha = 0.66f), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

private val posterCache = LruCache<String, ImageBitmap>(24)

@Composable
private fun rememberPoster(url: String?): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = url?.let { posterCache.get(it) }, url) {
        if (url != null && value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
            }?.also { posterCache.put(url, it) }
        }
    }
    return state.value
}

@Composable
private fun PosterCard(s: Suggestion, onClick: () -> Unit) {
    val poster = rememberPoster(s.posterUrl)
    Glass(Modifier.size(112.dp, 168.dp), corner = 16.dp, onClick = onClick) {
        if (poster != null) {
            Image(poster, s.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.40f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f))))
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Text(s.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val where = s.providers.firstOrNull()?.let(::shortProvider) ?: ""
            Text("$where · ★ ${String.format(Locale.ITALY, "%.1f", s.rating)}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(Locale.ITALY), fontSize = 12.sp, letterSpacing = 1.6.sp, color = Color.White.copy(alpha = 0.55f))
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
        Text(time, fontSize = 52.sp, fontWeight = FontWeight.ExtraLight, color = Color.White.copy(alpha = 0.96f))
        Text(date, fontSize = 15.sp, fontWeight = FontWeight.Light, color = Color.White.copy(alpha = 0.65f))
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Buongiorno"
    in 12..17 -> "Buon pomeriggio"
    in 18..22 -> "Buonasera"
    else -> "Buonanotte"
}

// --- schermate ----------------------------------------------------------------

@Composable
fun HomeScreen(
    channels: List<Channel>,
    last: Channel?,
    shortcuts: List<Shortcut>,
    casa: List<String>,
    suggestions: List<Suggestion>,
    onLive: () -> Unit,
    onLast: () -> Unit,
    onApp: (InstalledApp) -> Unit,
    onAllApps: () -> Unit,
    onCasa: (Int) -> Unit,
    onSettings: () -> Unit,
    onMirror: () -> Unit,
    onSuggestion: (Suggestion) -> Unit,
) {
    val focus = rememberInitialFocus()
    val liveDesc = if (channels.any { it.stream != null }) "I tuoi canali e le dirette ufficiali" else "Rai, Mediaset e La7 in diretta"
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Clock()
                Text(greeting(), fontSize = 22.sp, fontWeight = FontWeight.ExtraLight, color = Color.White.copy(alpha = 0.8f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                WideCard("TV Live", liveDesc, Modifier.size(300.dp, 100.dp), glyph = Glyph.Tv, tint = Accent, focusRequester = focus, onClick = onLive)
                if (last != null) {
                    WideCard("Riprendi", last.name, Modifier.size(220.dp, 100.dp), glyph = Glyph.Play, onClick = onLast)
                }
                WideCard("Duplica schermo", "Dal tablet alla TV", Modifier.size(220.dp, 100.dp), glyph = Glyph.Cast, onClick = onMirror)
            }
        }
        item { SectionLabel("Le tue app") }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            ) {
                items(shortcuts) { s ->
                    AppCard(s.label, s.desc, Modifier.size(132.dp, 124.dp), iconPkg = s.app.pkg, tint = s.color) { onApp(s.app) }
                }
                itemsIndexed(casa) { i, name ->
                    AppCard(name, "Domotica", Modifier.size(132.dp, 124.dp), glyph = Glyph.House, tint = Amber) { onCasa(i) }
                }
                item { AppCard("Tutte le app", "Tutto ciò che è installato", Modifier.size(132.dp, 124.dp), glyph = Glyph.Grid, onClick = onAllApps) }
                item { AppCard("Impostazioni", "Wi-Fi, schermo e suoni", Modifier.size(132.dp, 124.dp), glyph = Glyph.Gear, onClick = onSettings) }
            }
        }
        if (suggestions.isNotEmpty()) {
            item { SectionLabel("Consigliati per te · dati TMDB e JustWatch") }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    items(suggestions) { s -> PosterCard(s) { onSuggestion(s) } }
                }
            }
        }
    }
}

/** Sottotitolo di un canale: dice subito se parte al volo o se devi prima installare l'app. */
private fun subtitleOf(c: Channel, installed: List<InstalledApp>): String = when {
    c.stream != null -> "Player integrato"
    c.url != null && c.app != null && installed.findApp(listOf(c.app)) != null -> "Apre direttamente il canale"
    c.app != null && installed.findApp(listOf(c.app)) == null -> "Installa dall'Appstore"
    else -> "App ufficiale"
}

@Composable
fun LiveScreen(channels: List<Channel>, installed: List<InstalledApp>, onOpen: (Channel) -> Unit) {
    val focus = rememberInitialFocus()
    val groups = channels.groupBy { it.group }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("TV Live", fontSize = 34.sp, fontWeight = FontWeight.ExtraLight, color = Color.White.copy(alpha = 0.96f))
                Text("Scegli un canale", fontSize = 15.sp, fontWeight = FontWeight.Light, color = Color.White.copy(alpha = 0.65f))
            }
        }
        groups.forEach { (group, list) ->
            item { SectionLabel(group) }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    items(list) { c ->
                        val pkg = c.app?.let { installed.findApp(listOf(it))?.pkg }
                        WideCard(
                            c.name,
                            subtitleOf(c, installed),
                            Modifier.size(250.dp, 92.dp),
                            glyph = if (pkg == null) Glyph.Tv else null,
                            iconPkg = pkg,
                            tint = Color(c.color),
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
        GridCells.Adaptive(130.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(apps) { a ->
            AppCard(
                a.label,
                null,
                Modifier.fillMaxWidth().height(110.dp),
                iconPkg = a.pkg,
                focusRequester = if (a === apps.first()) focus else null,
            ) { onApp(a) }
        }
        item {
            AppCard("Home Amazon", "Resta finché apri un'app", Modifier.fillMaxWidth().height(110.dp), glyph = Glyph.House, onClick = onAmazon)
        }
    }
}
