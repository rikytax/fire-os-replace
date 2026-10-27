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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.lerp
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
import java.util.Date
import java.util.Locale

// Palette "ghiaccio": blu notte con azzurro. Il disegno è pensato per 960 x 540 dp e si scala da solo (vedi FireTheme).
private val BgDeep = Color(0xFF0A0E1A)
private val SurfaceDim = Color(0xFF0F1524)
private val ContainerLow = Color(0xFF111828)
private val ContainerHigh = Color(0xFF1A2438)
private val Primary = Color(0xFF7DD3FC)
private val PrimaryContainer = Color(0xFF0E4D6E)
private val PrimaryFixed = Color(0xFFC8EAFF)
private val OnPrimary = Color(0xFF001F2E)
private val OnSurface = Color(0xFFE0E8F0)
private val OnVariant = Color(0xFFA0B4C4)
private val OutlineVariant = Color(0xFF2A3A48)
private val Neutral = Color.Unspecified
private val Amber = Color(0xFFE5A00D)

private const val DESIGN_WIDTH_DP = 960f
private const val DESIGN_HEIGHT_DP = 540f

/**
 * Sfondo scuro con un alone azzurro in alto. Niente sfocature (costose su una Fire Stick): solo gradienti.
 * La scala si adatta alla risoluzione reale: se cambi TV, 720p o 4K, tutto si ridimensiona da solo
 * perché l'interfaccia occupa sempre lo stesso "spazio di progetto" di 960 x 540.
 */
@Composable
fun FireTheme(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val base = LocalDensity.current
    val factor = minOf(configuration.screenWidthDp / DESIGN_WIDTH_DP, configuration.screenHeightDp / DESIGN_HEIGHT_DP).coerceIn(0.6f, 2.5f)
    CompositionLocalProvider(LocalDensity provides Density(base.density * factor, base.fontScale)) {
        MaterialTheme(colorScheme = darkColorScheme(background = BgDeep, surface = SurfaceDim)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(BgDeep)
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                listOf(Primary.copy(alpha = 0.10f), Color.Transparent),
                                center = Offset(size.width * 0.30f, 0f),
                                radius = size.width * 0.55f,
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

// --- riquadro -----------------------------------------------------------------

/** Riquadro scuro con bordo azzurro tenue; al focus il bordo si accende, compare un bagliore e si ingrandisce un poco. */
@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    tint: Color = Neutral,
    corner: Dp = 16.dp,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(dampingRatio = 0.7f, stiffness = 420f), label = "panel-scale")
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "panel-lift")
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (lift > 0f) {
                    drawCircle(
                        Brush.radialGradient(listOf(Primary.copy(alpha = 0.30f * lift), Color.Transparent), center = center, radius = size.maxDimension * 0.75f),
                        radius = size.maxDimension * 0.75f,
                    )
                }
            }
            .border((1f + lift).dp, lerp(Primary.copy(alpha = 0.16f), Primary, lift), shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(ContainerHigh.copy(alpha = 0.62f), ContainerLow.copy(alpha = 0.88f))))
            .then(if (tint != Neutral) Modifier.background(tint.copy(alpha = 0.16f)) else Modifier)
            .clickable(onClick = onClick),
        content = content,
    )
}

// --- icone --------------------------------------------------------------------

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
        Modifier.size(badge).clip(RoundedCornerShape(badge * 0.28f)).background(PrimaryContainer.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, badge * 0.55f, Primary)
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
        Box(Modifier.size(s).clip(RoundedCornerShape(s * 0.22f)).background(ContainerHigh), contentAlignment = Alignment.Center) {
            Text(pkg.substringAfterLast('.').take(1).uppercase(), fontSize = 20.sp, color = OnSurface)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color = OnVariant, filled: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (filled) color.copy(alpha = 0.18f) else ContainerHigh.copy(alpha = 0.8f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

// --- immagini di rete -----------------------------------------------------------

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

// --- schede ---------------------------------------------------------------------

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
    Panel(modifier, tint, focusRequester = focusRequester, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                iconPkg != null -> AppIcon(iconPkg, iconSize)
                glyph != null -> GlyphBadge(glyph, iconSize)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (desc != null) {
                    Text(desc, fontSize = if (compact) 11.sp else 12.sp, color = OnVariant, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
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
    big: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val iconSize = if (big) 52.dp else 44.dp
    Panel(modifier, tint, focusRequester = focusRequester, onClick = onClick) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                iconPkg != null -> AppIcon(iconPkg, iconSize)
                glyph != null -> GlyphBadge(glyph, iconSize)
            }
            Spacer(Modifier.height(if (big) 8.dp else 6.dp))
            Text(title, fontSize = if (big) 15.sp else 13.sp, fontWeight = FontWeight.SemiBold, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (desc != null) {
                Text(desc, fontSize = if (big) 11.sp else 10.sp, color = OnVariant, maxLines = if (big) 1 else 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Una voce della griglia delle app nella Home. */
private class GridItem(val title: String, val desc: String?, val pkg: String?, val glyph: Glyph?, val tint: Color, val onClick: () -> Unit)

@Composable
private fun PosterCard(s: Suggestion, onClick: () -> Unit) {
    val poster = rememberRemoteImage(s.posterUrl)
    Panel(Modifier.size(104.dp, 156.dp), corner = 14.dp, onClick = onClick) {
        if (poster != null) {
            Image(poster, s.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.40f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f))))
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Text(s.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val where = s.providers.firstOrNull()?.let(::shortProvider) ?: ""
            Text("$where · ★ ${String.format(Locale.ITALY, "%.1f", s.rating)}", fontSize = 10.sp, color = OnVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// --- banner in primo piano ----------------------------------------------------------

@Composable
private fun HeroButton(label: String, glyph: Glyph?, primary: Boolean, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(dampingRatio = 0.7f, stiffness = 420f), label = "hero-button")
    val shape = RoundedCornerShape(12.dp)
    val foreground = if (primary) OnPrimary else OnSurface
    Row(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else OutlineVariant, shape)
            .clip(shape)
            .background(if (primary) Primary else ContainerHigh.copy(alpha = 0.85f))
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

@Composable
private fun Hero(
    spot: Suggestion,
    watchLabel: String,
    focus: FocusRequester?,
    onWatch: () -> Unit,
    onNext: () -> Unit,
) {
    val backdrop = rememberRemoteImage(spot.backdropUrl)
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(184.dp)
            .border(1.dp, Primary.copy(alpha = 0.22f), shape)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(SurfaceDim, ContainerLow, SurfaceDim))),
    ) {
        if (backdrop != null) {
            Image(backdrop, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.42f)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, SurfaceDim))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(SurfaceDim, SurfaceDim.copy(alpha = 0.6f), Color.Transparent))))
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp).widthIn(max = 520.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip("In primo piano", Primary, filled = true)
                Chip(spot.kind)
                if (spot.year.isNotEmpty()) Chip(spot.year)
                Chip("★ ${String.format(Locale.ITALY, "%.1f", spot.rating)}")
            }
            Spacer(Modifier.height(8.dp))
            Text(spot.title, fontSize = 28.sp, fontWeight = FontWeight.Black, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (spot.overview.isNotEmpty()) {
                Text(spot.overview, fontSize = 12.sp, color = OnVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroButton(watchLabel, Glyph.Play, primary = true, focusRequester = focus, onClick = onWatch)
                HeroButton("Altro titolo", null, primary = false, onClick = onNext)
            }
        }
    }
}

// --- barra laterale e intestazione -------------------------------------------------------

enum class Section { Home, Live, Apps }

@Composable
private fun RailItem(label: String, glyph: Glyph, selected: Boolean, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth().height(62.dp), corner = 14.dp, onClick = onClick) {
        if (selected) Box(Modifier.fillMaxSize().background(PrimaryContainer.copy(alpha = 0.9f)))
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            GlyphIcon(glyph, 22.dp, if (selected) PrimaryFixed else OnVariant)
            Spacer(Modifier.height(3.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (selected) PrimaryFixed else OnVariant, maxLines = 1)
        }
    }
}

/** Barra laterale con le sezioni, sempre con icona e nome, e il contenuto accanto. */
@Composable
fun Shell(
    selected: Section?,
    onHome: () -> Unit,
    onLive: () -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(84.dp).fillMaxHeight().background(SurfaceDim.copy(alpha = 0.92f)).padding(horizontal = 8.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RailItem("Home", Glyph.House, selected == Section.Home, onHome)
            RailItem("TV Live", Glyph.Tv, selected == Section.Live, onLive)
            RailItem("App", Glyph.Grid, selected == Section.Apps, onApps)
            Spacer(Modifier.weight(1f))
            RailItem("Impostazioni", Glyph.Gear, false, onSettings)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
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
    Column(horizontalAlignment = Alignment.End) {
        Text(time, fontSize = 30.sp, fontWeight = FontWeight.Medium, color = OnSurface)
        Text(date, fontSize = 12.sp, color = OnVariant)
    }
}

@Composable
private fun StatusBar() {
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(network, Primary, filled = true)
            Chip("RAM libera $memory MB")
        }
        Clock()
    }
}

@Composable
private fun SectionTitle(text: String, note: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
        if (note != null) Chip(note)
    }
}

// --- schermate ----------------------------------------------------------------------------

@Composable
fun HomeScreen(
    last: Channel?,
    shortcuts: List<Shortcut>,
    casa: List<String>,
    suggestions: List<Suggestion>,
    watchLabel: (Suggestion) -> String,
    onLast: () -> Unit,
    onApp: (InstalledApp) -> Unit,
    onAllApps: () -> Unit,
    onCasa: (Int) -> Unit,
    onMirror: () -> Unit,
    onSuggestion: (Suggestion) -> Unit,
) {
    val focus = rememberInitialFocus()
    var heroIndex by remember { mutableIntStateOf(0) }
    val spots = suggestions.filter { it.backdropUrl != null }.ifEmpty { suggestions }
    val spot = if (spots.isEmpty()) null else spots[heroIndex % spots.size]
    val gridItems = shortcuts.map { s -> GridItem(s.label, null, s.app.pkg, null, s.color) { onApp(s.app) } } +
        GridItem("Tutte le app", null, null, Glyph.Grid, Neutral, onAllApps)
    val columns = 4
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusBar() }
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
        }
        item { SectionTitle("Le tue app", "Avvio rapido") }
        // Griglia a 4 colonne: righe fisse dentro la colonna che scorre, con tessere ampie.
        gridItems.chunked(columns).forEach { row ->
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { g ->
                        AppCard(
                            g.title, g.desc, Modifier.weight(1f).height(112.dp),
                            glyph = g.glyph, iconPkg = g.pkg, tint = g.tint, big = true,
                            focusRequester = if (spot == null && g === gridItems.first()) focus else null,
                            onClick = g.onClick,
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        item { SectionTitle("Controlli rapidi") }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
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
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("TV Live", fontSize = 30.sp, fontWeight = FontWeight.Black, color = OnSurface)
                Text("Scegli un canale", fontSize = 13.sp, color = OnVariant)
            }
        }
        groups.forEach { (group, list) ->
            item { SectionTitle(group) }
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
    LazyVerticalGrid(
        GridCells.Adaptive(124.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(apps) { a ->
            AppCard(
                a.label,
                null,
                Modifier.fillMaxWidth().height(100.dp),
                iconPkg = a.pkg,
                focusRequester = if (a === apps.first()) focus else null,
            ) { onApp(a) }
        }
        item {
            AppCard("Home Amazon", "Resta finché apri un'app", Modifier.fillMaxWidth().height(100.dp), glyph = Glyph.House, onClick = onAmazon)
        }
    }
}
