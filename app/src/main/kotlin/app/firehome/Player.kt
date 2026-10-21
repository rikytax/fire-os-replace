@file:OptIn(UnstableApi::class)

package app.firehome

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private fun Channel.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(stream)
        .setMimeType(if (dash) MimeTypes.APPLICATION_MPD else MimeTypes.APPLICATION_M3U8)
        .apply {
            license?.let {
                setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(it).build())
            }
        }
        .build()

/**
 * Su/Giù (o Canale +/-) cambiano canale, OK mostra il nome, Indietro esce.
 * [zap] riceve +1 o -1.
 */
@Composable
fun PlayerScreen(channel: Channel, zap: (Int) -> Unit) {
    val ctx = LocalContext.current
    val exo = remember {
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http)))
            .build()
    }
    var buffering by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var infoTick by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(true) }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    LaunchedEffect(channel) {
        failed = false
        exo.setMediaItem(channel.toMediaItem())
        exo.prepare()
        exo.playWhenReady = true
        infoTick++
    }

    LaunchedEffect(infoTick) {
        showInfo = true
        delay(3_000)
        showInfo = false
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                val step = when (e.key) {
                    Key.DirectionUp, Key.ChannelUp, Key.PageUp -> 1
                    Key.DirectionDown, Key.ChannelDown, Key.PageDown -> -1
                    else -> 0
                }
                when {
                    step != 0 -> {
                        if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) zap(step)
                        true
                    }
                    e.key == Key.DirectionCenter || e.key == Key.Enter -> {
                        if (e.type == KeyEventType.KeyDown) infoTick++
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    keepScreenOn = true
                    this.player = exo
                }
            },
        )

        if (failed || buffering) {
            Text(
                if (failed) "Canale non disponibile" else "Caricamento…",
                Modifier.align(Alignment.Center),
                fontSize = 28.sp,
            )
        }

        if (showInfo) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 64.dp, vertical = 28.dp),
            ) {
                Text(channel.name, fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
                Text("▲▼ cambia canale   ·   Indietro per uscire", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
