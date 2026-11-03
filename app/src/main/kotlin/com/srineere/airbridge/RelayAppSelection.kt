package com.srineere.airbridge

import android.content.Context
import android.content.pm.PackageManager
import android.telecom.TelecomManager

/** Host-app preferences, deliberately separate from the device-control package. */
internal object RelayAppSelection {
    private const val DEFAULT_APP = "com.google.android.calendar"
    private const val FALLBACK_DIALER = "com.google.android.dialer"

    /** Compiled once; it is checked against every rule key on every write. */
    private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")

    /** A name for an app that is gone, and so has no label left to load. */
    internal fun labelFor(packageName: String): String =
        if (packageName == DEFAULT_APP) "Google Calendar" else packageName

    /**
     * Whatever actually answers the phone here. Hard-coding Google's dialer
     * was right on one phone and wrong on every Samsung, Xiaomi and OnePlus.
     */
    fun dialer(context: Context): String = runCatching {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: FALLBACK_DIALER

    /**
     * What a fresh install relays. The phone is in here because a relay whose
     * headline is "ring until answered" that ignores calls out of the box is
     * not a default, it is a trap: nothing on screen says the one alert you
     * most wanted is the one not selected.
     */
    fun selected(context: Context): Set<String> = context
        .getSharedPreferences("calendar_relay", Context.MODE_PRIVATE)
        .getStringSet("selected_apps", setOf(dialer(context), DEFAULT_APP))!!.toSet()

    fun save(context: Context, packages: Set<String>) {
        require(packages.size <= 64 && packages.all {
            it != context.packageName && it.matches(PACKAGE_NAME)
        }) { "Select up to 64 notification apps, excluding this demo." }
        context.getSharedPreferences("calendar_relay", Context.MODE_PRIVATE)
            .edit().putStringSet("selected_apps", packages).apply()
    }

    /** "double": two pulses; "single": one tap. Both keep the wearer's strength. */
    fun pulseStyle(context: Context): String = context
        .getSharedPreferences("calendar_relay", Context.MODE_PRIVATE)
        .getString("pulse_style", "single").takeIf { it in PULSE_STYLES } ?: "single"

    fun savePulseStyle(context: Context, style: String) {
        require(style in PULSE_STYLES) { "Unknown pulse style." }
        context.getSharedPreferences("calendar_relay", Context.MODE_PRIVATE)
            .edit().putString("pulse_style", style).apply()
    }

    private val PULSE_STYLES = setOf("double", "single")

    /**
     * Whether this app can receive calls, so only apps that can are offered a
     * call rule.
     *
     * `MANAGE_OWN_CALLS` is what an app needs to put a call through the
     * telecom framework, and messaging apps that call declare it. `CALL_PHONE`
     * is no use as a signal: Calendar and Messages hold it so a tapped number
     * dials out, and neither ever rings.
     */
    internal fun canReceiveCalls(context: Context, packageName: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.contains("android.permission.MANAGE_OWN_CALLS") == true
    }.getOrDefault(false)

    /**
     * Apps known to say who is calling, so only they are offered a list of
     * people. An app that rings without naming the caller would give a list
     * that never matches anything, which is worse than not offering one.
     */
    internal fun namesCallers(dialer: String) =
        setOf(dialer, FALLBACK_DIALER) + RelayContacts.NAMED_CALLERS

    /** An app that has actually named a caller has earned its list. */
    fun rememberNamesCallers(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences("calendar_relay", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("naming_apps", emptySet())!!
        if (packageName in seen || seen.size >= 64) return
        prefs.edit().putStringSet("naming_apps", seen + packageName).apply()
    }

    internal fun seenNaming(context: Context): Set<String> = context
        .getSharedPreferences("calendar_relay", Context.MODE_PRIVATE)
        .getStringSet("naming_apps", emptySet())!!

    /**
     * Apps actually seen ringing, in case one calls without declaring it.
     * Observation beats inference: an app that has rung is a calling app
     * whatever its manifest says.
     */
    fun rememberCalling(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences("calendar_relay", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("calling_apps", emptySet())!!
        if (packageName in seen || seen.size >= 64) return
        prefs.edit().putStringSet("calling_apps", seen + packageName).apply()
    }

    internal fun seenCalling(context: Context): Set<String> = context
        .getSharedPreferences("calendar_relay", Context.MODE_PRIVATE)
        .getStringSet("calling_apps", emptySet())!!

}
