package com.elitedarkkaiser.redmagic.ui

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import com.elitedarkkaiser.redmagic.ui.backgrounds.BackgroundType
import com.elitedarkkaiser.redmagic.ui.glassbar.GlassBarScaffold
import com.elitedarkkaiser.redmagic.ui.glassbar.GlassBottomBarColors
import com.elitedarkkaiser.redmagic.ui.glassbar.GlassBottomBarTab
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Debug-only fixture: exercises actual hardware drawing, without touching device hardware. */
class CardBlurProbeActivity : ComponentActivity() {
    val cards = mutableListOf<View>()
    var changedPattern by mutableStateOf(false)
    private var settingsScroll: ScrollView? = null
    private var settingsContent: LinearLayout? = null

    override fun attachBaseContext(newBase: android.content.Context) {
        val configuration = Configuration(newBase.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CardStyle.setBlur(this, 0.25f)
        CardStyle.setConnected(this, true)
        ThemePrefs.setBackgroundAnimation(this, BackgroundType.CIRCLES.name)
        val m3 = M3(this)
        if (intent.getBooleanExtra("settings", false)) {
            val content = SettingsTabUi.create(SettingsTabDeps(
                scrollTabContainer = { LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } },
                activity = { this }, sectionPanel = { m3.block() },
                restartForTheme = {}, onGlassBlurChanged = {}, onBarStyleChanged = {},
                onBackgroundAnimationChanged = {}, onPureBlackChanged = {}, onPaletteChanged = {},
                getUseFahrenheit = { false }, setUseFahrenheit = {}, saveUseFahrenheit = {},
                refreshStatus = {}, onRefreshIntervalsChanged = {}
            )).view
            settingsContent = content
            val scroll = ScrollView(this).apply { addView(content) }
            settingsScroll = scroll
            setContent {
                GlassBarScaffold(
                    contentView = scroll, isLoading = { false }, onSettings = {},
                    tabs = listOf(GlassBottomBarTab(Icons.HOME, "Home")),
                    selectedIndex = { 0 }, onSelected = {}, isBlurEnabled = true,
                    backgroundType = BackgroundType.CIRCLES, dockedBar = { true },
                    colors = GlassBottomBarColors(
                        Color(m3.surface), Color(m3.rowSurface), Color(m3.primary),
                        Color(m3.onSurface), Color(m3.onSurfaceVariant), false,
                        Color(m3.secondary), Color(m3.tertiary)
                    )
                )
            }
            return
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val inset = m3.dp(16)
            setPadding(inset, inset, inset, inset)
            repeat(3) { index ->
                val fill = BackdropFill().apply {
                    setColor(AndroidColor.GREEN)
                    cornerRadius = m3.dpF(16f)
                }
                val ripple = RippleDrawable(
                    ColorStateList.valueOf(AndroidColor.WHITE), fill,
                    GradientDrawable().apply {
                        setColor(AndroidColor.WHITE)
                        cornerRadius = m3.dpF(16f)
                    }
                )
                val card = View(this@CardBlurProbeActivity).apply {
                    background = when (index) {
                        0 -> fill
                        1 -> ripple
                        else -> InsetDrawable(LayerDrawable(arrayOf(ripple)), m3.dp(4))
                    }
                }
                cards.add(card)
                addView(card, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, m3.dp(80)
                ).apply { bottomMargin = m3.dp(8) })
            }
        }

        setContent {
            val source = rememberGraphicsLayer()
            val host = LocalView.current
            val navigationBackdrop = rememberLayerBackdrop { drawContent() }
            DisposableEffect(source) { onDispose { CardBackdrop.clear(source) } }
            Box(Modifier.fillMaxSize().layerBackdrop(navigationBackdrop)) {
                Canvas(Modifier.fillMaxSize().drawWithContent {
                    source.record { this@drawWithContent.drawContent() }
                    drawLayer(source)
                    CardBackdrop.update(source, host, this)
                }) {
                    drawRect(if (changedPattern) Color.Yellow else Color.Red,
                        size = Size(size.width / 2f, size.height))
                    drawRect(if (changedPattern) Color.Cyan else Color.Blue,
                        topLeft = Offset(size.width / 2f, 0f),
                        size = Size(size.width / 2f, size.height))
                }
                AndroidView(factory = { content }, modifier = Modifier.fillMaxSize())
            }
        }
    }

    fun showBackgroundSettings() {
        val content = settingsContent ?: return
        val header = (0 until content.childCount).map { content.getChildAt(it) }
            .filterIsInstance<TextView>().firstOrNull { it.text.toString() == "Background" }
        settingsScroll?.scrollTo(0, header?.top ?: 0)
    }
}
