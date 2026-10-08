package com.elitedarkkaiser.redmagic.ui

import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class CardBlurTest {
    private lateinit var context: ContextThemeWrapper

    @Before fun setup() {
        context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
        CardStyle.setBlur(context, 0.5f)
        CardStyle.setConnected(context, false)
    }

    @Test fun resolvesViewThroughRippleInsetAndLayerWrappers() {
        val fill = BackdropFill()
        val ripple = RippleDrawable(
            ColorStateList.valueOf(Color.BLACK), fill,
            GradientDrawable().apply { setColor(Color.WHITE) }
        )
        val view = View(context)
        view.background = InsetDrawable(LayerDrawable(arrayOf(ripple)), 8)
        assertSame(view, fill.owningView())
        view.background = null
        assertNull(fill.owningView())
    }

    @Test fun clickableAndConnectedSettingsRowsKeepBlurAndJoinedCorners() {
        val m3 = M3(context)
        val palette = m3.row("Color Palette", onClick = {})
        val connect = m3.row("Connect cards")
        val parent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(palette)
            addView(connect)
        }
        CardStyle.setConnected(context, true)
        m3.connectRows(parent)
        val first = backdrop(palette.background)!!
        val last = backdrop(connect.background)!!
        assertSame(palette, first.owningView())
        assertSame(connect, last.owningView())
        assertEquals(0f, first.cornerRadii!![4], 0f)
        assertEquals(0f, last.cornerRadii!![0], 0f)
        assertEquals(0, (connect.layoutParams as LinearLayout.LayoutParams).topMargin)

        CardStyle.setConnected(context, false)
        m3.connectRows(parent)
        assertTrue(backdrop(palette.background)!!.cornerRadii!![4] > 0f)
        assertTrue(backdrop(connect.background)!!.cornerRadii!![0] > 0f)
    }

    @Test fun dashboardAndMasterCardTintsUseBlurWhileSwatchesKeepExactColors() {
        val m3 = M3(context)
        for (tint in M3.HomeTint.entries) {
            val color = m3.homeCardColors(tint).first
            assertTrue(m3.cardShape(color, 28) is BackdropFill)
            assertNotNull(backdrop(m3.filled(color, 28, m3.onSurface, card = true)))
        }
        assertTrue(m3.cardShape(m3.primaryContainer, 24) is BackdropFill)
        assertTrue(m3.insetGroup().background is BackdropFill)
        assertFalse(m3.surfaceShape(Color.MAGENTA, 24) is BackdropFill)
        assertFalse(m3.surfaceShape(m3.rowSurface, 24) is BackdropFill)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun softwareRenderingFallsBackToSolidCard() {
        val base = Color.rgb(20, 40, 60)
        val fill = BackdropFill().apply {
            setColor(base)
            setBounds(0, 0, 24, 24)
        }
        val view = View(context).apply {
            background = fill
            layout(0, 0, 24, 24)
        }
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        assertEquals(base, bitmap.getPixel(12, 12))
    }

    @Test @Config(sdk = [28])
    fun olderAndroidKeepsOpaqueCards() {
        CardStyle.setBlur(context, 1f)
        assertFalse(CardStyle.blurSupported)
        assertEquals(1f, CardStyle.alpha, 0f)
    }

    private fun backdrop(drawable: Drawable): BackdropFill? {
        if (drawable is BackdropFill) return drawable
        if (drawable is InsetDrawable) return backdrop(drawable.drawable!!)
        if (drawable is LayerDrawable) {
            for (i in 0 until drawable.numberOfLayers) {
                backdrop(drawable.getDrawable(i))?.let { return it }
            }
        }
        return null
    }
}
