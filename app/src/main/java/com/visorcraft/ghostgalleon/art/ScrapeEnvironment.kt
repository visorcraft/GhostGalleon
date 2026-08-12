package com.visorcraft.ghostgalleon.art

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.visorcraft.ghostgalleon.settings.Settings

/**
 * Android-side readings for [ScrapePolicy]. Keeps ConnectivityManager /
 * BatteryManager out of pure policy + host tests.
 */
object ScrapeEnvironment {

    fun preferences(settings: Settings): ScrapePolicy.Preferences =
        ScrapePolicy.Preferences(
            wifiOnly = settings.scrapeWifiOnly,
            pauseBelowPercent = settings.scrapePauseBelowBattery,
        )

    fun network(context: Context): ScrapePolicy.NetworkSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return ScrapePolicy.NetworkSnapshot(
                connected = false,
                onWifi = false,
                metered = true,
            )
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val connected = caps != null && (
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
        val onWifi = caps != null && (
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
        val metered = try {
            cm.isActiveNetworkMetered
        } catch (_: Exception) {
            !onWifi
        }
        return ScrapePolicy.NetworkSnapshot(
            connected = connected,
            onWifi = onWifi,
            metered = metered,
        )
    }

    fun battery(context: Context): ScrapePolicy.BatterySnapshot {
        val sticky = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            null
        }
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return ScrapePolicy.BatterySnapshot(percent = pct, charging = charging)
    }

    fun decision(context: Context, settings: Settings): ScrapePolicy.Decision =
        ScrapePolicy.mayRun(network(context), battery(context), preferences(settings))
}
