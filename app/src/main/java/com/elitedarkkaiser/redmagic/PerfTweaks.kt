package com.elitedarkkaiser.redmagic

import android.content.Context

/** Root implementations of Cache Cleaner and GMS Optimizer. Call off the main thread. */
object PerfTweaks {

    // ---- Cache Cleaner ------------------------------------------------------------------------

    /**
     * One-shot: fstrim every partition, clear every app's cache/code_cache, and the handful of
     * `cmd` resets the original's action.sh ran. Ordinary maintenance -- the same kind of thing
     * Android's own JobScheduler does on a cadence, just run on demand.
     */
    fun cleanCache(): Boolean = RootShell.exec(
        """
        for p in system vendor data cache metadata odm system_ext product; do fstrim -v "/${'$'}p" >/dev/null 2>&1; done
        find /data/data/*/cache/* -delete 2>/dev/null
        find /data/data/*/code_cache/* -delete 2>/dev/null
        find /data/user_de/*/*/cache/* -delete 2>/dev/null
        find /data/user_de/*/*/code_cache/* -delete 2>/dev/null
        find /sdcard/Android/data/*/cache/* -delete 2>/dev/null
        pm trim-caches 1024G >/dev/null 2>&1
        cmd stats clear-puller-cache
        cmd activity clear-debug-app
        cmd activity clear-watch-heap -a
        cmd activity clear-exit-info
        cmd content reset-today-stats
        cmd blob_store clear-all-blobs
        cmd blob_store clear-all-sessions
        dumpsys procstats --clear
        cmd package art cleanup
        sync
        """.trimIndent()
    )

    // ---- GMS Optimizer --------------------------------------------------------------------------

    /**
     * The appops, component and doze/hibernation restrictions from the original's service.sh,
     * applied to every installed package whose name contains "gms". This is the module that will
     * visibly change how the phone behaves -- background sync, push notifications and Find My
     * Device all lean on the access this switches off -- which is stated in the row's own
     * supporting text, not just here.
     *
     * [on] false reverses the state-based restrictions (appops back to allow, the disabled
     * components re-enabled, GMS taken back off the hibernation/restricted paths). It does not
     * re-run the one-shot, stateless actions from the original (dexopt cleanup, ART profile
     * clearing) because there is nothing there to undo -- they are not a setting sitting in the
     * "on" position, they already ran once and are done.
     */
    fun setGmsOptimizer(context: Context, on: Boolean): Boolean {
        val packages = gmsPackages(context)
        if (packages.isEmpty()) return true

        val script = buildString {
            packages.forEach { gms ->
                if (on) {
                    listOf(
                        "ACCESS_RESTRICTED_SETTINGS" to "ignore",
                        "ACTIVITY_RECOGNITION" to "deny",
                        "ACTIVITY_RECOGNITION_SOURCE" to "deny",
                        "ASSIST_STRUCTURE" to "ignore",
                        "AUTO_REVOKE_PERMISSIONS_IF_UNUSED" to "ignore",
                        "BODY_SENSORS" to "deny",
                        "GET_ACCOUNTS" to "deny",
                        "GET_USAGE_STATS" to "deny",
                        "SCHEDULE_EXACT_ALARM" to "deny",
                        "MONITOR_HIGH_POWER_LOCATION" to "deny",
                        "MONITOR_LOCATION" to "ignore",
                        "RUN_USER_INITIATED_JOBS" to "deny",
                        "INTERACT_ACROSS_PROFILES" to "deny",
                        "START_FOREGROUND" to "ignore",
                        "SYSTEM_EXEMPT_FROM_HIBERNATION" to "deny",
                        "SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS" to "deny",
                        "WAKE_LOCK" to "ignore",
                        "LOADER_USAGE_STATS" to "deny",
                        "READ_WRITE_HEALTH_DATA" to "deny",
                        "READ_CALL_LOG" to "deny",
                        "WRITE_CALL_LOG" to "deny",
                        "QUERY_ALL_PACKAGES" to "deny",
                        "RUN_ANY_IN_BACKGROUND" to "ignore",
                        "RUN_IN_BACKGROUND" to "ignore",
                        "FOREGROUND_SERVICE_SPECIAL_USE" to "ignore",
                        "INSTANT_APP_START_FOREGROUND" to "ignore"
                    ).forEach { (op, mode) -> append("appops set $gms $op $mode\n") }
                } else {
                    // "allow" is every one of the ops above's own default.
                    setOf(
                        "ACCESS_RESTRICTED_SETTINGS", "ACTIVITY_RECOGNITION", "ACTIVITY_RECOGNITION_SOURCE",
                        "ASSIST_STRUCTURE", "AUTO_REVOKE_PERMISSIONS_IF_UNUSED", "BODY_SENSORS",
                        "GET_ACCOUNTS", "GET_USAGE_STATS", "SCHEDULE_EXACT_ALARM",
                        "MONITOR_HIGH_POWER_LOCATION", "MONITOR_LOCATION", "RUN_USER_INITIATED_JOBS",
                        "INTERACT_ACROSS_PROFILES", "START_FOREGROUND", "SYSTEM_EXEMPT_FROM_HIBERNATION",
                        "SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS", "WAKE_LOCK", "LOADER_USAGE_STATS",
                        "READ_WRITE_HEALTH_DATA", "READ_CALL_LOG", "WRITE_CALL_LOG", "QUERY_ALL_PACKAGES",
                        "RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND", "FOREGROUND_SERVICE_SPECIAL_USE",
                        "INSTANT_APP_START_FOREGROUND"
                    ).forEach { op -> append("appops set $gms $op allow\n") }
                }

                if (gms == "com.google.android.gms") {
                    val verb = if (on) "disable" else "enable"
                    append("pm $verb com.google.android.gms/.auth.managed.admin.DeviceAdminReceiver\n")
                    append("pm $verb com.google.android.gms/.mdm.receivers.MdmDeviceAdminReceiver\n")
                    append("pm $verb com.google.android.gms/.chimera.GmsIntentOperationService\n")
                }

                if (on) {
                    append("am force-stop $gms; am kill $gms; am kill-all $gms\n")
                    append("dumpsys deviceidle whitelist -$gms\n")
                    append("dumpsys deviceidle sys-whitelist -$gms\n")
                    append("cmd app_hibernation set-state $gms true\n")
                    append("am set-inactive --user 0 $gms true\n")
                    append("am set-standby-bucket --user 0 $gms restricted\n")
                    append("am set-bg-restriction-level --user 0 $gms hibernation\n")
                    append("cmd connectivity set-background-networking-enabled-for-uid $gms false\n")
                    append("cmd netpolicy remove restrict-background-whitelist $gms\n")
                    append("cmd netpolicy add restrict-background-blacklist $gms\n")
                    append("cmd netpolicy remove app-idle-whitelist $gms\n")
                    append("cmd connectivity set-package-networking-enabled false $gms\n")
                } else {
                    append("dumpsys deviceidle whitelist +$gms\n")
                    append("dumpsys deviceidle sys-whitelist +$gms\n")
                    append("cmd app_hibernation set-state $gms false\n")
                    append("am set-inactive --user 0 $gms false\n")
                    append("am set-standby-bucket --user 0 $gms active\n")
                    append("am set-bg-restriction-level --user 0 $gms none\n")
                    append("cmd connectivity set-background-networking-enabled-for-uid $gms true\n")
                    append("cmd netpolicy remove restrict-background-blacklist $gms\n")
                    append("cmd netpolicy add app-idle-whitelist $gms\n")
                    append("cmd connectivity set-package-networking-enabled true $gms\n")
                }
            }

            if (on) {
                append("settings put global gms_checkin_timeout_min 120\n")
                append("settings put secure assistant 0\n")
                append("settings put secure smartspace 0\n")
                append("settings put global google_core_control 0\n")
                append("settings put global hotword_detection_enabled 0\n")
                append("settings put secure systemui.google.opa_enabled 0\n")
                append("settings put secure adaptive_connectivity_enabled 0\n")
            } else {
                append("settings delete global gms_checkin_timeout_min\n")
                append("settings put secure assistant 1\n")
                append("settings put secure smartspace 1\n")
                append("settings put global google_core_control 1\n")
                append("settings put global hotword_detection_enabled 1\n")
                append("settings put secure systemui.google.opa_enabled 1\n")
                append("settings put secure adaptive_connectivity_enabled 1\n")
            }
        }
        return RootShell.exec(script)
    }

    /** Every installed package whose name suggests it is part of Google Play Services. */
    private fun gmsPackages(context: Context): List<String> {
        val output = RootShell.execForOutput("cmd package list packages -a") ?: return emptyList()
        return output.lineSequence()
            .mapNotNull { line -> line.substringAfter("package:", "").takeIf { it.isNotBlank() } }
            .filter { it.contains("gms") && !it.contains("revanced") }
            .toList()
            .ifEmpty {
                // The `cmd package` form needs a live activity-manager binder call, which some
                // ROMs refuse from a plain root shell. `pm` is the older, always-available path.
                RootShell.execForOutput("pm list packages")
                    ?.lineSequence()
                    ?.mapNotNull { it.substringAfter("package:", "").takeIf { p -> p.isNotBlank() } }
                    ?.filter { it.contains("gms") && !it.contains("revanced") }
                    ?.toList()
                    .orEmpty()
            }
    }

}
