package com.elitedarkkaiser.redmagic.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CardBlurGpuTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun actualHardwareBlurSamplesBackgroundAcrossDrawableWrappersAndUpdates() {
        ActivityScenario.launch(CardBlurProbeActivity::class.java).use { scenario ->
            SystemClock.sleep(1500)
            val first = screenshot("pattern-25-percent")
            scenario.onActivity { activity ->
                activity.cards.forEachIndexed { index, card ->
                    assertTrue("Card $index must draw on a hardware canvas", card.isHardwareAccelerated)
                    val left = pixel(first, card, 0.25f)
                    val right = pixel(first, card, 0.75f)
                    val edge = pixel(first, card, 0.5f)
                    assertTrue("Card $index lost the RED source: $left", Color.red(left) > 100)
                    assertTrue("Card $index lost the BLUE source: $right", Color.blue(right) > 100)
                    assertTrue("Card $index has no blur at the source edge: $edge",
                        Color.red(edge) > 35 && Color.blue(edge) > 35)
                }
                activity.changedPattern = true
            }
            SystemClock.sleep(1000)
            val updated = screenshot("pattern-updated")
            scenario.onActivity { activity ->
                activity.cards.forEachIndexed { index, card ->
                    val left = pixel(updated, card, 0.25f)
                    assertTrue("Card $index retained a stale background: $left",
                        Color.red(left) > 100 && Color.green(left) > 230)
                }
                CardStyle.setBlur(activity, 0f)
            }
            SystemClock.sleep(1000)
            val solid = screenshot("pattern-zero-percent")
            scenario.onActivity { activity ->
                activity.cards.forEachIndexed { index, card ->
                    assertEquals("Card $index should be solid at zero", Color.GREEN,
                        pixel(solid, card, 0.25f))
                }
            }
        }
    }

    @Test fun captureActualSettingsWithConnectedCardsAt25Percent() {
        val intent = Intent(instrumentation.targetContext, CardBlurProbeActivity::class.java)
            .putExtra("settings", true)
        ActivityScenario.launch<CardBlurProbeActivity>(intent).use { scenario ->
            SystemClock.sleep(1500)
            scenario.onActivity { it.showBackgroundSettings() }
            SystemClock.sleep(1200)
            screenshot("settings-connected-25-percent")
        }
    }

    private fun screenshot(name: String): Bitmap {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Hardware screenshot unavailable", bitmap)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "card-blur-gpu")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    private fun pixel(bitmap: Bitmap, view: View, horizontal: Float): Int {
        val position = IntArray(2)
        view.getLocationOnScreen(position)
        return bitmap.getPixel(position[0] + (view.width * horizontal).toInt(),
            position[1] + view.height / 2)
    }
}
