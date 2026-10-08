package com.elitedarkkaiser.redmagic

import android.app.Application

class RedMagicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // First thing, so a crash anywhere after this point leaves a stack trace behind for
        // Settings to hand back. It only records and then delegates, so Android's own handling
        // is unchanged.
        CrashLog.install(this)
        // Colour is generated in process from a seed rather than applied to the Activity theme
        // -- see Palette. A theme overlay resolves once, on the way into an Activity, which is
        // exactly why changing the palette used to mean recreating one.
        com.elitedarkkaiser.redmagic.ui.AppTheme.appContext = this
        LegacyHardwareCleanup.schedule(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                LegacyHardwareCleanup.schedule(activity)
                com.elitedarkkaiser.redmagic.gametrigger.TriggerRuntimeService.sync(activity)
            }
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
    }
}
