package app.firehome

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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

// Stile "vetro" alla tvOS: sfondo scuro che prende il colore dell'elemento selezionato, riquadri trasparenti con
// bordo luminoso e riflesso, nomi sempre leggibili. Niente sfocature reali (costose su una Fire Stick):
// solo gradienti. Pensato per 960 x 540 dp e si scala da solo (vedi FireTheme).
private val Bg = Color(0xFF070A12)
private val Ink = Color(0xFFF2F5FA)
private val InkDim = Color(0xFFAAB5C5)
private val Accent = Color(0xFF8CCBFF)
private val DefaultAmbient = Color(0xFF3E6BFF)
private val Neutral = Color.Unspecified
private val Amber = Color(0xFFE5A00D)
private val LiveRed = Color(0xFFFF5A5A)

/** Il colore che illumina lo sfondo: cambia con l'elemento su cui si trova il focus. */
private val LocalAmbient = compositionLocalOf { mutableStateOf(DefaultAmbient) }

private const val DESIGN_WIDTH_DP = 960f
private const val DESIGN_HEIGHT_DP = 540f

/**
 * Sfondo scuro con due aloni (uno segue il focus). La scala si adatta alla risoluzione reale: se cambi TV, 720p o 4K,
 * tutto si ridimensiona da solo perché l'interfaccia occupa sempre lo stesso "spazio di progetto" di 960 x 540.
 */
@Composable
fun FireTheme(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val base = LocalDensity.current
    val factor = minOf(configuration.screenWidthDp / DESIGN_WIDTH_DP, configuration.screenHeightDp / DESIGN_HEIGHT_DP).coerceIn(0.6f, 2.5f)
    val ambient = remember { mutableStateOf(DefaultAmbient) }
    val glow by animateColorAsState(ambient.value, tween(600), label = "ambient")
    CompositionLocalProvider(LocalDensity provides Density(base.density * factor, base.fontScale), LocalAmbient provides ambient) {
        MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Bg)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Bg)
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                listOf(glow.copy(alpha = 0.36f), Color.Transparent),
                                center = Offset(size.width * 0.28f, -size.height * 0.05f),
                                radius = size.width * 0.80f,
                            ),
                        )
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color(0x336A3CFF), Color.Transparent),
                                center = Offset(size.width * 0.96f, size.height),
                                radius = size.width * 0.70f,
                            ),
                        )
                    },
            ) { content() }
        }
    }
}

/** Mette il focus su un elemento appena la schermata compare (il telecomando parte da lì). */
@Composable
private fun rememberInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    return requester
}

// --- vetro ----------------------------------------------------------------------

/**
 * Riquadro di vetro: trasparente, con riflesso diagonale e bordo luminoso. Al focus si ingrandisce con una molla,
 * si accende e dà il suo colore allo sfondo.
 */
@Composable
private fun Glass(
    modifier: Modifier = Modifier,
    tint: Color = Neutral,
    corner: Dp = 22.dp,
    focusRequester: FocusRequester? = null,
    onFocus: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val ambient = LocalAmbient.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(dampingRatio = 0.62f, stiffness = 380f), label = "glass-scale")
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "glass-lift")
    val shape = RoundedCornerShape(corner)
    val tinted = tint != Neutral
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) ambient.value = if (tinted) tint else DefaultAmbient
                onFocus?.invoke(it.isFocused)
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(
                (1.1f + 1.1f * lift).dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.50f + 0.45f * lift), Color.White.copy(alpha = 0.06f + 0.10f * lift))),
                shape,
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.17f + 0.10f * lift), Color.White.copy(alpha = 0.05f + 0.06f * lift))))
            .background(Brush.linearGradient(0f to Color.White.copy(alpha = 0.20f), 0.55f to Color.Transparent, 1f to Color.Transparent))
            .then(if (tinted) Modifier.background(tint.copy(alpha = 0.20f)) else Modifier)
            // L'alone del focus sta DENTRO il riquadro: fuori verrebbe tagliato dalle righe che scorrono (rettangolo scuro).
            .background(Brush.radialGradient(listOf((if (tinted) tint else Color.White).copy(alpha = 0.30f * lift), Color.Transparent)))
            .clickable(onClick = onClick),
        content = content,
    )
}

// --- icone ------------------------------------------------------------------------

private enum class Glyph { Tv, Cast, Grid, Gear, House, Play }

@Composable
private fun GlyphIcon(glyph: Glyph, s: Dp, color: Color = Color.White) {
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
    Box(
        Modifier.size(badge).clip(RoundedCornerShape(badge * 0.28f)).background(Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, badge * 0.55f, Ink)
    }
}

/** L'icona vera dell'app installata. */
@Composable
private fun AppIcon(pkg: String, s: Dp) {
    val context = LocalContext.current
    val bitmap = remember(pkg) {
        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(160, 160).asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, null, Modifier.size(s).clip(RoundedCornerShape(s * 0.22f)))
    } else {
        Box(Modifier.size(s).clip(RoundedCornerShape(s * 0.22f)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(pkg.substringAfterLast('.').take(1).uppercase(), fontSize = 22.sp, color = Ink)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color = InkDim, filled: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) color.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

// --- immagini di rete ----------------------------------------------------------------

private val posterCache = LruCache<String, ImageBitmap>(24)

@Composable
private fun rememberRemoteImage(url: String?): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = url?.let { posterCache.get(it) }, url) {
        if (url != null && value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
            }?.also { posterCache.put(url, it) }
        }
    }
    return state.value
}

// --- elementi ------------------------------------------------------------------------

/** Una voce della riga delle app. */
private class DockEntry(val label: String, val pkg: String?, val glyph: Glyph?, val tint: Color, val onClick: () -> Unit)

/** Icona grande di vetro con il nome sotto, come il dock di tvOS. */
@Composable
private fun DockItem(entry: DockEntry, focusRequester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Glass(Modifier.size(100.dp), entry.tint, 24.dp, focusRequester, { focused = it }, entry.onClick) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    entry.pkg != null -> AppIcon(entry.pkg, 58.dp)
                    entry.glyph != null -> GlyphBadge(entry.glyph, 58.dp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            entry.label, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = Ink.copy(alpha = if (focused) 1f else 0.62f),
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/** Scheda larga: icona a sinistra, titolo e spiegazione a destra. */
@Composable
private fun WideCard(
    title: String,
    desc: String?,
    modifier: Modifier,
    glyph: Glyph? = null,
    iconPkg: String? = null,
    tint: Color = Neutral,
    compact: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val iconSize = if (compact) 34.dp else 44.dp
    Glass(modifier, tint, 20.dp, focusRequester, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                iconPkg != null -> AppIcon(iconPkg, iconSize)
                glyph != null -> GlyphBadge(glyph, iconSize)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (desc != null) {
                    Text(desc, fontSize = if (compact) 11.sp else 12.sp, color = InkDim, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Un canale della Live TV di Jellyfin: nome e programma in onda. */
@Composable
private fun LiveCard(channel: Channel, onClick: () -> Unit) {
    Glass(Modifier.size(200.dp, 96.dp), corner = 20.dp, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(LiveRed))
                Spacer(Modifier.width(6.dp))
                Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LiveRed)
            }
            Column {
                Text(channel.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(channel.nowPlaying ?: "In diretta", fontSize = 11.sp, color = InkDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PosterCard(s: Suggestion, onClick: () -> Unit) {
    val poster = rememberRemoteImage(s.posterUrl)
    Glass(Modifier.size(104.dp, 156.dp), corner = 18.dp, onClick = onClick) {
        if (poster != null) {
            Image(poster, s.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.40f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f))))
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Text(s.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val where = s.providers.firstOrNull()?.let(::shortProvider) ?: ""
            Text("$where · ★ ${String.format(Locale.ITALY, "%.1f", s.rating)}", fontSize = 10.sp, color = InkDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HeroButton(label: String, glyph: Glyph?, primary: Boolean, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, spring(dampingRatio = 0.62f, stiffness = 380f), label = "hero-button")
    val shape = RoundedCornerShape(14.dp)
    val foreground = if (primary || focused) Bg else Ink
    Row(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (primary || focused) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, 18.dp, foreground)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = foreground, maxLines = 1)
    }
}

/** Il titolo in primo piano: locandina larga di sfondo, voto, trama e il pulsante per guardarlo sul servizio giusto. */
@Composable
private fun Hero(spot: Suggestion, watchLabel: String, focus: FocusRequester?, onWatch: () -> Unit, onNext: () -> Unit) {
    val backdrop = rememberRemoteImage(spot.backdropUrl)
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(176.dp)
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f))), shape)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f)),
    ) {
        if (backdrop != null) {
            Image(backdrop, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.55f)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg.copy(alpha = 0.92f)))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Bg.copy(alpha = 0.85f), Color.Transparent))))
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp).widthIn(max = 520.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip("In primo piano", Accent, filled = true)
                Chip(spot.kind)
                if (spot.year.isNotEmpty()) Chip(spot.year)
                Chip("★ ${String.format(Locale.ITALY, "%.1f", spot.rating)}")
            }
            Spacer(Modifier.height(8.dp))
            Text(spot.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (spot.overview.isNotEmpty()) {
                Text(spot.overview, fontSize = 12.sp, color = InkDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroButton(watchLabel, Glyph.Play, primary = true, focusRequester = focus, onClick = onWatch)
                HeroButton("Altro titolo", null, primary = false, onClick = onNext)
            }
        }
    }
}

// --- barra in alto e struttura -----------------------------------------------------------

enum class Section { Home, Live, Apps }

@Composable
private fun TabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(dampingRatio = 0.7f, stiffness = 420f), label = "tab")
    Box(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.94f)
                    selected -> Color.White.copy(alpha = 0.18f)
                    else -> Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            color = when {
                focused -> Bg
                selected -> Ink
                else -> InkDim
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusChips() {
    val context = LocalContext.current
    val network by produceState(networkLabel(context)) {
        while (true) {
            delay(15_000)
            value = networkLabel(context)
        }
    }
    val memory by produceState(freeMemoryMb(context)) {
        while (true) {
            delay(15_000)
            value = freeMemoryMb(context)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip(network, Accent, filled = true)
        Chip("RAM $memory MB")
    }
}

@Composable
private fun ClockCompact() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.ITALY).format(now) }
    Text(time, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ink)
}

/** Barra in alto: a sinistra lo stato, al centro le sezioni, a destra l'ora. */
@Composable
private fun TopBar(selected: Section?, onHome: () -> Unit, onLive: () -> Unit, onApps: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { StatusChips() }
        Row(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TabItem("Home", selected == Section.Home, onHome)
            TabItem("TV Live", selected == Section.Live, onLive)
            TabItem("App", selected == Section.Apps, onApps)
            TabItem("Impostazioni", false, onSettings)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { ClockCompact() }
    }
}

/** Barra in alto con le sezioni e, sotto, il contenuto. */
@Composable
fun Shell(
    selected: Section?,
    onHome: () -> Unit,
    onLive: () -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(selected, onHome, onLive, onApps, onSettings)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Buongiorno"
    in 12..17 -> "Buon pomeriggio"
    in 18..22 -> "Buonasera"
    else -> "Buonanotte"
}

@Composable
private fun SectionTitle(text: String, note: String? = null) {
    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        if (note != null) Chip(note)
    }
}

// --- schermate --------------------------------------------------------------------------------

@Composable
fun HomeScreen(
    last: Channel?,
    shortcuts: List<Shortcut>,
    casa: List<String>,
    suggestions: List<Suggestion>,
    live: List<Channel>,
    watchLabel: (Suggestion) -> String,
    onLast: () -> Unit,
    onApp: (InstalledApp) -> Unit,
    onAllApps: () -> Unit,
    onCasa: (Int) -> Unit,
    onMirror: () -> Unit,
    onSuggestion: (Suggestion) -> Unit,
    onChannel: (Channel) -> Unit,
) {
    val focus = rememberInitialFocus()
    var heroIndex by remember { mutableIntStateOf(0) }
    val spots = suggestions.filter { it.backdropUrl != null }.ifEmpty { suggestions }
    val spot = if (spots.isEmpty()) null else spots[heroIndex % spots.size]
    val dock = shortcuts.map { s -> DockEntry(s.label, s.app.pkg, null, s.color) { onApp(s.app) } } +
        DockEntry("Tutte le app", null, Glyph.Grid, Neutral, onAllApps)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (spot != null) {
            item {
                Hero(
                    spot = spot,
                    watchLabel = watchLabel(spot),
                    focus = focus,
                    onWatch = { onSuggestion(spot) },
                    onNext = { heroIndex++ },
                )
            }
        } else {
            item {
                Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                    Text(greeting(), fontSize = 38.sp, fontWeight = FontWeight.ExtraLight, color = Ink)
                    Text(
                        remember { SimpleDateFormat("EEEE d MMMM", Locale.ITALY).format(Date()).replaceFirstChar { it.uppercase() } },
                        fontSize = 14.sp, color = InkDim,
                    )
                }
            }
        }
        // Le app: una riga sola, tutte visibili, come il dock di tvOS.
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 14.dp),
            ) {
                itemsIndexed(dock) { i, entry ->
                    DockItem(entry, if (spot == null && i == 0) focus else null)
                }
            }
        }
        if (live.isNotEmpty()) {
            item { SectionTitle("In diretta ora", "Jellyfin Live TV") }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                ) {
                    items(live.take(24)) { c -> LiveCard(c) { onChannel(c) } }
                }
            }
        }
        item { SectionTitle("Azioni rapide") }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
            ) {
                if (last != null) {
                    item { WideCard("Riprendi", last.name, Modifier.size(200.dp, 64.dp), glyph = Glyph.Play, compact = true, onClick = onLast) }
                }
                item { WideCard("Duplica schermo", "Dal tablet alla TV", Modifier.size(220.dp, 64.dp), glyph = Glyph.Cast, compact = true, onClick = onMirror) }
                itemsIndexed(casa) { i, name ->
                    WideCard(name, "Domotica", Modifier.size(200.dp, 64.dp), glyph = Glyph.House, tint = Amber, compact = true) { onCasa(i) }
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            item { SectionTitle("Consigliati per te", "Dati TMDB e JustWatch") }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                ) {
                    items(suggestions) { s -> PosterCard(s) { onSuggestion(s) } }
                }
            }
        }
    }
}

/** Sottotitolo di un canale: dice subito se parte al volo o se devi prima installare l'app. */
private fun subtitleOf(c: Channel, installed: List<InstalledApp>): String = when {
    c.jellyfinId != null -> c.nowPlaying ?: "In diretta"
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
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 6.dp)) {
                Text("TV Live", fontSize = 32.sp, fontWeight = FontWeight.ExtraLight, color = Ink)
                Text("Scegli un canale", fontSize = 13.sp, color = InkDim)
            }
        }
        groups.forEach { (group, list) ->
            item { SectionTitle(group) }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                ) {
                    items(list) { c ->
                        val pkg = c.app?.let { installed.findApp(listOf(it))?.pkg }
                        WideCard(
                            c.name,
                            subtitleOf(c, installed),
                            Modifier.size(240.dp, 84.dp),
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
    val entries = apps.map { a -> DockEntry(a.label, a.pkg, null, Neutral) { onApp(a) } } +
        DockEntry("Home Amazon", null, Glyph.House, Neutral, onAmazon)
    LazyVerticalGrid(
        GridCells.Adaptive(112.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(entries) { e ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DockItem(e, if (e === entries.first()) focus else null)
            }
        }
    }
}
