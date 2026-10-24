package com.nexguard.app.util

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val NAME = "nexguard_prefs"

    const val ONBOARDING_DONE = "onboarding_done"
    const val MONITOR_ON = "monitor_on"
    const val AUTO_QUARANTINE = "auto_quarantine"
    const val NOTIFY_THREATS = "notify_threats"
    const val DEFAULT_MODE = "default_mode"
    const val USE_CACHE = "use_cache"
    const val CLOUD_LOOKUP = "cloud_lookup"
    const val SCAN_INSTALLED_APPS = "scan_installed_apps"
    const val BLOCKED_SITES = "blocked_sites"
    const val RECENT_CHECKS = "recent_url_checks"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun onboardingDone(context: Context): Boolean =
        get(context).getBoolean(ONBOARDING_DONE, false)

    fun monitorOn(context: Context): Boolean =
        get(context).getBoolean(MONITOR_ON, true)

    fun autoQuarantine(context: Context): Boolean =
        get(context).getBoolean(AUTO_QUARANTINE, false)

    fun notifyThreats(context: Context): Boolean =
        get(context).getBoolean(NOTIFY_THREATS, true)

    fun defaultMode(context: Context): String =
        get(context).getString(DEFAULT_MODE, "full") ?: "full"

    fun useCache(context: Context): Boolean =
        get(context).getBoolean(USE_CACHE, true)

    fun cloudLookup(context: Context): Boolean =
        get(context).getBoolean(CLOUD_LOOKUP, false)

    fun scanInstalledApps(context: Context): Boolean =
        get(context).getBoolean(SCAN_INSTALLED_APPS, true)

    fun blockedSites(context: Context): List<String> =
        (get(context).getString(BLOCKED_SITES, "") ?: "")
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    fun saveBlockedSites(context: Context, sites: List<String>) {
        get(context).edit().putString(BLOCKED_SITES, sites.joinToString("\n")).apply()
    }
}
