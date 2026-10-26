package app.firehome

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Fire OS riserva il tasto Home al launcher Amazon (pacchetto protetto) e lo gestisce prima che
 * qualunque filtro di accessibilità lo veda. Questo servizio aspetta quindi che compaia la finestra
 * Home di Amazon, la copre e lancia FireHome.
 *
 * L'idea e i dettagli vengono da dumbtv (github.com/humblemuzzu/dumbtv, licenza MIT):
 *  - un startActivity subito dopo Home è ritardato di 5 secondi da Android; un PendingIntent lanciato
 *    da un allarme parte da system_server ed è esente, ma subito solo con min_futurity=0
 *    (lo imposta lo script di installazione);
 *  - Android 11 rifiuta l'avvio da un'app senza finestra visibile: la copertura è quella finestra,
 *    quindi il lancio parte dopo il suo primo disegno.
 *
 * In più c'è un freno: se FireHome non compare dopo alcuni lanci di fila, il servizio si ferma
 * finché non si apre un'altra app, così un guasto non diventa un ciclo infinito.
 */
class HomeGuard : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val uncoverTask = Runnable { uncover() }
    private val launchTask = Runnable { launchHome(this) }
    private var cover: View? = null
    private var lastLaunch = 0L
    private var onAmazonHome = false

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!paused && onAmazonHome) launchWhenCovered()
        }
    }

    override fun onServiceConnected() {
        running = this
        val view = View(this).apply { visibility = View.GONE }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        params.windowAnimations = 0
        (getSystemService(WINDOW_SERVICE) as WindowManager).addView(view, params)
        cover = view
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    override fun onDestroy() {
        handler.removeCallbacks(uncoverTask)
        handler.removeCallbacks(launchTask)
        try {
            unregisterReceiver(screenOn)
        } catch (e: IllegalArgumentException) {
        }
        cover?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        if (running === this) running = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != AMAZON) {
            if (pkg == "com.android.systemui" || pkg == "android") return
            onAmazonHome = false
            paused = false
            failedLaunches = 0
            if (pkg != packageName) arrived(this)
            return
        }
        onAmazonHome = event.className?.toString() == AMAZON_HOME
        if (paused || !onAmazonHome) return
        val now = SystemClock.uptimeMillis()
        if (now - lastLaunch < REPEAT_MS) return
        lastLaunch = now
        launchWhenCovered()
    }

    /** Mostra la copertura e, quando il window manager la vede, lancia FireHome. */
    private fun launchWhenCovered() {
        if (failedLaunches >= MAX_FAILED_LAUNCHES) {
            paused = true
            return
        }
        failedLaunches++
        handler.removeCallbacks(launchTask)
        val view = cover
        if (view == null) {
            launchHome(this)
            return
        }
        val alreadyShown = view.visibility == View.VISIBLE
        cover()
        if (alreadyShown) {
            launchHome(this)
            return
        }
        view.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                view.viewTreeObserver.removeOnPreDrawListener(this)
                handler.removeCallbacks(launchTask)
                launchHome(this@HomeGuard)
                return true
            }
        })
        handler.postDelayed(launchTask, LAUNCH_FALLBACK_MS)
    }

    private fun cover() {
        cover?.setBackgroundColor(COVER_COLOR)
        cover?.visibility = View.VISIBLE
        handler.removeCallbacks(uncoverTask)
        handler.postDelayed(uncoverTask, COVER_MAX_MS)
    }

    private fun uncover() {
        handler.removeCallbacks(uncoverTask)
        cover?.visibility = View.GONE
    }

    override fun onInterrupt() {}

    companion object {
        const val AMAZON = "com.amazon.tv.launcher"
        private const val AMAZON_HOME = "com.amazon.tv.launcher.ui.HomeActivity_vNext"
        private const val COVER_MAX_MS = 1500L
        private const val REPEAT_MS = 1000L
        private const val LAUNCH_FALLBACK_MS = 150L
        private const val MAX_FAILED_LAUNCHES = 4
        private val COVER_COLOR = Color.parseColor("#0B0D12")

        @Volatile private var paused = false
        @Volatile private var failedLaunches = 0
        private var running: HomeGuard? = null

        private fun pendingLaunch(context: Context): PendingIntent {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle(),
            )
        }

        private fun launchHome(context: Context) {
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try {
                alarms.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), pendingLaunch(context))
            } catch (e: SecurityException) {
                alarms.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), pendingLaunch(context))
            }
        }

        /** Annulla un lancio in coda, così non ti riporta alla Home dopo che hai aperto un'app. */
        fun arrived(context: Context) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingLaunch(context))
        }

        /** FireHome è sul display: toglie la copertura e azzera il freno. */
        fun shown() {
            failedLaunches = 0
            running?.uncover()
        }

        /** Lascia la Home di Amazon sullo schermo finché non apri un'altra app. */
        fun openAmazon(context: Context) {
            paused = true
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(AMAZON)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                paused = false
            }
        }
    }
}
