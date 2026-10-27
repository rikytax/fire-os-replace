package app.firehome

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager

/** Come sei collegato, letto dal sistema: "Wi-Fi 5 GHz", "Wi-Fi 2,4 GHz", "Ethernet" o "Offline". */
fun networkLabel(ctx: Context): String = runCatching {
    val connectivity = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    @Suppress("DEPRECATION")
    val info = connectivity.activeNetworkInfo
    when {
        info == null || !info.isConnected -> "Offline"
        info.type == ConnectivityManager.TYPE_ETHERNET -> "Ethernet"
        info.type == ConnectivityManager.TYPE_WIFI -> {
            val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val frequency = wifi.connectionInfo?.frequency ?: 0
            if (frequency >= 4900) "Wi-Fi 5 GHz" else if (frequency > 0) "Wi-Fi 2,4 GHz" else "Wi-Fi"
        }
        else -> "Online"
    }
}.getOrDefault("Rete")

/** Memoria libera in MB: sulle Fire Stick è poca, e vederla aiuta a capire quando conviene chiudere qualcosa. */
fun freeMemoryMb(ctx: Context): Int = runCatching {
    val info = ActivityManager.MemoryInfo()
    (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
    (info.availMem / (1024 * 1024)).toInt()
}.getOrDefault(0)
