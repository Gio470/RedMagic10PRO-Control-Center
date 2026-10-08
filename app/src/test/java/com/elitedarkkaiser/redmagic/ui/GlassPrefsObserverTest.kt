package com.elitedarkkaiser.redmagic.ui

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class GlassPrefsObserverTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("glass", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun savedPlaygroundPresetsReachTheObserverAsOneCompleteMaterial() {
        val seen = mutableListOf<GlassPrefs.Effects>()
        val subscription = GlassPrefs.observe(context, seen::add)
        try {
            assertEquals(listOf(GlassPrefs.DEFAULT), seen)
            GlassPrefs.setEffects(context, GlassPrefs.FROSTED)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(GlassPrefs.DEFAULT, GlassPrefs.FROSTED), seen)
            GlassPrefs.setEffects(context, GlassPrefs.CLEAR)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(GlassPrefs.CLEAR, seen.last())
        } finally { subscription.close() }
    }

    @Test fun overlayContextReceivesEverySavedCustomEffectFromTheAppContext() {
        val overlay = context.createConfigurationContext(Configuration(context.resources.configuration))
        val seen = mutableListOf<GlassPrefs.Effects>()
        val subscription = GlassPrefs.observe(overlay, seen::add)
        try {
            val custom = GlassPrefs.Effects(12f, 19f, 37f, 0.31f, true, true, 30f)
            GlassPrefs.setEffects(context, custom)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(custom, seen.last())
            assertEquals(2, seen.size)
            assertEquals(custom, GlassPrefs.effects(overlay))
        } finally { subscription.close() }
    }

    @Test fun closingOneWindowStopsItsUpdatesWhileOtherWindowsContinue() {
        val closed = mutableListOf<GlassPrefs.Effects>()
        val live = mutableListOf<GlassPrefs.Effects>()
        val first = GlassPrefs.observe(context, closed::add)
        val second = GlassPrefs.observe(context, live::add)
        try {
            // The notification is queued before close; releasing the window cancels that delivery too.
            val writer = Thread { GlassPrefs.setEffects(context, GlassPrefs.FROSTED) }.apply { start() }
            writer.join(5_000)
            assertFalse(writer.isAlive)
            first.close()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(GlassPrefs.DEFAULT), closed)
            assertEquals(listOf(GlassPrefs.DEFAULT, GlassPrefs.FROSTED), live)
            GlassPrefs.setEffects(context, GlassPrefs.FROSTED)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2, live.size)
        } finally { second.close() }
    }
}
