package com.elitedarkkaiser.redmagic

import android.graphics.ImageDecoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elitedarkkaiser.redmagic.ui.GlassPrefs
import com.elitedarkkaiser.redmagic.ui.GlassTint
import com.elitedarkkaiser.redmagic.ui.Icons
import com.elitedarkkaiser.redmagic.ui.M3
import com.elitedarkkaiser.redmagic.ui.composeColorScheme
import com.elitedarkkaiser.redmagic.ui.ThemePrefs
import com.elitedarkkaiser.redmagic.ui.glassbar.GlassBottomBarColors
import com.elitedarkkaiser.redmagic.ui.glassbar.GlassBottomBarPreview
import com.elitedarkkaiser.redmagic.ui.glassbar.GlassBottomBarTab
import com.elitedarkkaiser.redmagic.ui.glassbar.isGlassBlurSupported
import com.google.android.material.color.MaterialColors
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Glass playground, ported from Kyant0/AndroidLiquidGlass's catalog
 * (GlassPlaygroundContent) -- the same library this app already draws its bottom bar and loading
 * screen with, so this is that library's own demo of what the effects it ships actually do.
 *
 * A pane of glass over a backdrop, draggable, pinchable and rotatable, with the values that define
 * the effect on controls: corner radius, blur, refraction height, refraction amount, dispersion
 * and depth.
 *
 * Its own Activity rather than a screen in the tabs. The gestures want the whole window (a pinch
 * inside a scrolling tab fights the scroll), and the backdrop has to record a layer of its own to
 * refract, which is exactly what the main Activity's backdrop is already doing for the bar.
 *
 * ## Why the backdrop is a test card
 *
 * The first version of this screen put a four-colour gradient behind the glass and read as the
 * effects doing nothing at all. That was the backdrop's fault rather than the effects': refraction
 * is implemented by moving *where* the shader samples the layer, and a smooth gradient sampled a
 * few pixels to one side is the same smooth gradient. Nothing moves because nothing in the image
 * marks a position.
 *
 * Upstream sidesteps this by putting a photograph back there. This draws a test card instead --
 * hard-edged colour bands, a fine grid, concentric rings and large type -- because every one of
 * those is a feature the eye can track as it bends, and because a pattern generated in code ships
 * without a wallpaper asset. The colours are the classic saturated test-card set rather than this
 * app's palette: dispersion splits a hard edge into red and blue fringes, and a pastel edge has
 * no fringe to give. [PhotoBackdrop] is still there for anyone who wants upstream's version --
 * pick any image on the phone.
 */
class GlassPlaygroundActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val m3 = M3(this)
            MaterialTheme(colorScheme = m3.composeColorScheme()) {
                GlassPlayground(m3, onClose = { finish() })
            }
        }
    }
}

/** What is behind the glass. */
private enum class Backdrop { PATTERN, PHOTO, PLAIN }

/** What the glass is drawn as. */
private enum class Preview { PANE, CARDS, NAV_BAR }

@Composable
private fun GlassPlayground(m3: M3, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val zoom = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }

    // The real settings, loaded once and written back on every change. There is no Apply button:
    // the previews below are the real bar composables drawing with these values, so "apply" would
    // only mean "do to the bar what you can already see happening here", and MainActivity picks
    // the change up when this screen closes (see its onResume).
    var effects by remember { mutableStateOf(GlassPrefs.effects(context)) }
    fun update(block: (GlassPrefs.Effects) -> GlassPrefs.Effects) {
        effects = block(effects).also { GlassPrefs.setEffects(context, it) }
    }

    // The demo pane's own shape, fixed -- the floating bar is a capsule and the docked one has its
    // own corner slider, so there is no bar setting behind this.
    val paneCornerFrac = 0.5f

    var preview by remember { mutableStateOf(Preview.PANE) }
    var panelExpanded by remember { mutableStateOf(true) }

    var backdropKind by remember { mutableStateOf(Backdrop.PATTERN) }
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) { decodeSampled(context, uri) }
                if (loaded != null) {
                    photo = loaded
                    backdropKind = Backdrop.PHOTO
                }
            }
        }
    }

    val backdrop = rememberLayerBackdrop()

    // The wash the app's own glass is drawn with at this Tint, so the previews are the real
    // thing rather than a flattering version of it -- for a long while nothing here was tinted
    // at all, which is why Tint looked like it worked in the playground and nowhere else.
    val isLight = MaterialColors.isColorLight(m3.surface)
    val tintWash = Color(GlassTint.bar(m3.surface, isLight, effects.tintAlpha))

    Box(Modifier.fillMaxSize()) {
        // What the glass has to bend. The content is a *child* of the recording node, not that
        // node's own background: layerBackdrop records what is drawn inside it, so painting the
        // node itself left the layer empty and the glass with nothing to refract -- which looks
        // exactly like the effects doing nothing. Same shape as GlassBarScaffold's own recording.
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            when (backdropKind) {
                Backdrop.PATTERN -> TestCardBackdrop()
                Backdrop.PLAIN -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(m3.surfaceContainerHigh))
                )
                Backdrop.PHOTO -> {
                    val bitmap = photo
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        TestCardBackdrop()
                    }
                }
            }
        }

        val onSurface = Color(m3.onSurface)
        val accent = Color(m3.primary)

        when (preview) {
            Preview.NAV_BAR -> {
                // The app's own bars, drawn by the app's own composables off the same settings
                // this screen is editing. Both are shown at once because the question "does this
                // look like glass" has a different answer for a floating capsule and a bar seated
                // on the screen edge, and only one of them is on at a time in the app.
                val barColors = remember(m3.primaryVivid, m3.onSurface, m3.surface) {
                    GlassBottomBarColors(
                        background = Color(m3.surface),
                        container = Color(m3.surfaceContainerLow),
                        accent = Color(m3.primaryVivid),
                        onSurface = Color(m3.onSurface),
                        onSurfaceVariant = Color(m3.onSurfaceVariant),
                        isLightTheme = com.google.android.material.color.MaterialColors
                            .isColorLight(m3.surface),
                        secondaryAccent = Color(m3.secondary),
                        tertiaryAccent = Color(m3.tertiary)
                    )
                }
                val tabs = remember {
                    listOf(
                        GlassBottomBarTab(Icons.HOME, "Home"),
                        GlassBottomBarTab(Icons.HARDWARE, "Hardware"),
                        GlassBottomBarTab(Icons.SOFTWARE, "Software")
                    )
                }
                var previewTab by remember { mutableIntStateOf(0) }

                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    PreviewCaption("Floating bar", onSurface)
                    GlassBottomBarPreview(
                        tabs = tabs,
                        selectedIndex = { previewTab },
                        onSelected = { previewTab = it },
                        colors = barColors,
                        effects = effects,
                        backdrop = backdrop,
                        docked = false,
                        isBlurEnabled = isGlassBlurSupported()
                    )
                    PreviewCaption("Docked bar", onSurface)
                    GlassBottomBarPreview(
                        tabs = tabs,
                        selectedIndex = { previewTab },
                        onSelected = { previewTab = it },
                        colors = barColors,
                        effects = effects,
                        backdrop = backdrop,
                        docked = true,
                        isBlurEnabled = isGlassBlurSupported(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // The third thing the bar can be. Every panel in the app opens as a page now,
                    // and for as long as one is open the bar is the way out of it rather than a
                    // tab row -- drawn from the same settings as the two above, so this screen has
                    // to show it or it would be the one piece of glass the playground cannot set.
                    PreviewCaption("Back bar (while a page is open)", onSurface)
                    GlassBottomBarPreview(
                        tabs = tabs,
                        selectedIndex = { previewTab },
                        onSelected = { previewTab = it },
                        colors = barColors,
                        effects = effects,
                        backdrop = backdrop,
                        docked = false,
                        isBlurEnabled = isGlassBlurSupported(),
                        backTitle = "Fan LED",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    )
                }
            }

            Preview.CARDS -> {
                // ScrollContainerContent, from the same catalog: a column of glass panels
                // refracting whatever passes behind them as it scrolls.
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(16.dp)
                        .padding(bottom = 320.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(8) {
                        Box(
                            Modifier
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { RoundedCornerShape(48.dp * paneCornerFrac) },
                                    effects = { applyEffects(effects) },
                                    highlight = { Highlight.Plain },
                                    onDrawSurface = { drawRect(tintWash) }
                                )
                                .height(150.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }

            Preview.PANE -> Box(
                Modifier
                    .padding(top = 40.dp)
                    .statusBarsPadding()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(256.dp / 2f * paneCornerFrac) },
                        effects = { applyEffects(effects) },
                        highlight = { Highlight.Plain },
                        onDrawSurface = { drawRect(tintWash) },
                        layerBlock = {
                            translationX = offset.value.x
                            translationY = offset.value.y
                            scaleX = zoom.value
                            scaleY = zoom.value
                            rotationZ = rotation.value
                        }
                    )
                    .pointerInput(scope) {
                        fun Offset.rotateBy(angle: Float): Offset {
                            val radians = angle * (PI / 180)
                            val c = cos(radians)
                            val s = sin(radians)
                            return Offset((x * c - y * s).toFloat(), (x * s + y * c).toFloat())
                        }

                        detectTransformGestures { _, pan, gestureZoom, gestureRotate ->
                            val targetZoom = zoom.value * gestureZoom
                            val targetRotation = rotation.value + gestureRotate
                            val targetOffset =
                                offset.value + pan.rotateBy(targetRotation) * targetZoom
                            scope.launch {
                                offset.snapTo(targetOffset)
                                zoom.snapTo(targetZoom)
                                rotation.snapTo(targetRotation)
                            }
                        }
                    }
                    .size(256.dp)
                    .align(Alignment.TopCenter)
            )
        }

        // The control panel: Material 3 styling (app palette), a plain surface rather than the
        // glass the previews above demonstrate -- a control you cannot read is not a control.
        MaterialTheme(colorScheme = m3.composeColorScheme()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Glass playground",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                when (preview) {
                                    Preview.PANE -> "Drag, pinch and rotate the pane"
                                    Preview.CARDS -> "Scroll the cards over the backdrop"
                                    Preview.NAV_BAR -> "These are the app's own bars, live"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { panelExpanded = !panelExpanded }) {
                            Text(if (panelExpanded) "Hide" else "Controls")
                        }
                        TextButton(onClick = onClose) { Text("Done") }
                    }

                    if (panelExpanded) {
                        Column(
                            Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (!ThemePrefs.useGlassBlur(context)) {
                                Text(
                                    "Liquid glass bottom bar is off in Settings, so the bar will " +
                                        "not use these until you turn it back on.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            SectionLabel("Preview")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                M3Chip("Pane", preview == Preview.PANE) { preview = Preview.PANE }
                                M3Chip("Cards", preview == Preview.CARDS) { preview = Preview.CARDS }
                                M3Chip("Navigation bar", preview == Preview.NAV_BAR) {
                                    preview = Preview.NAV_BAR
                                }
                            }

                            SectionLabel("Backdrop")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                M3Chip("Test card", backdropKind == Backdrop.PATTERN) {
                                    backdropKind = Backdrop.PATTERN
                                }
                                M3Chip("Photo", backdropKind == Backdrop.PHOTO) {
                                    if (photo != null) {
                                        backdropKind = Backdrop.PHOTO
                                    } else {
                                        picker.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                                }
                                M3Chip("Flat", backdropKind == Backdrop.PLAIN) {
                                    backdropKind = Backdrop.PLAIN
                                }
                            }
                            if (backdropKind == Backdrop.PHOTO) {
                                TextButton(onClick = {
                                    picker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }) { Text("Choose another image") }
                            }

                            SectionLabel("Preset")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val active = GlassPrefs.presetName(effects)
                                GlassPrefs.PRESETS.forEach { (name, preset) ->
                                    M3Chip(name, active == name) {
                                        // The corner is the bar's shape rather than its material,
                                        // so a preset leaves whatever was set there alone.
                                        update { preset.copy(dockedCornerDp = it.dockedCornerDp) }
                                    }
                                }
                            }

                            SectionLabel("Material \u2014 used by both bars")
                            M3Slider(
                                "Blur radius",
                                "${effects.blurRadiusDp.roundToInt()} dp",
                                effects.blurRadiusDp / GlassPrefs.MAX_BLUR_DP
                            ) { v -> update { it.copy(blurRadiusDp = v * GlassPrefs.MAX_BLUR_DP) } }
                            M3Slider(
                                "Refraction height",
                                "${effects.refractionHeightDp.roundToInt()} dp",
                                effects.refractionHeightDp / GlassPrefs.MAX_REFRACTION_HEIGHT_DP
                            ) { v ->
                                update { it.copy(refractionHeightDp = v * GlassPrefs.MAX_REFRACTION_HEIGHT_DP) }
                            }
                            M3Slider(
                                "Refraction amount",
                                "${effects.refractionAmountDp.roundToInt()} dp",
                                effects.refractionAmountDp / GlassPrefs.MAX_REFRACTION_AMOUNT_DP
                            ) { v ->
                                update { it.copy(refractionAmountDp = v * GlassPrefs.MAX_REFRACTION_AMOUNT_DP) }
                            }
                            M3Slider(
                                "Tint",
                                "${(effects.tintAlpha * 100f).roundToInt()}%",
                                effects.tintAlpha / GlassPrefs.MAX_TINT
                            ) { v -> update { it.copy(tintAlpha = v * GlassPrefs.MAX_TINT) } }

                            // Both of these are booleans in the library (see
                            // BackdropEffectScope.lens), so they are toggles, not sliders.
                            M3Toggle("Dispersion", effects.dispersion) { v ->
                                update { it.copy(dispersion = v) }
                            }
                            M3Toggle("Depth effect", effects.depthEffect) { v ->
                                update { it.copy(depthEffect = v) }
                            }

                            if (preview == Preview.NAV_BAR) {
                                M3Slider(
                                    "Docked bar corner",
                                    "${effects.dockedCornerDp.roundToInt()} dp",
                                    effects.dockedCornerDp / GlassPrefs.MAX_DOCKED_CORNER_DP
                                ) { v ->
                                    update { it.copy(dockedCornerDp = v * GlassPrefs.MAX_DOCKED_CORNER_DP) }
                                }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    scope.launch {
                                        launch { offset.animateTo(Offset.Zero) }
                                        launch { zoom.animateTo(1f) }
                                        launch { rotation.animateTo(0f) }
                                    }
                                    update { GlassPrefs.DEFAULT }
                                }) { Text("Reset") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The one place the effect stack is written, so the pane and the cards cannot drift apart. */
private fun BackdropEffectScope.applyEffects(effects: GlassPrefs.Effects) {
    vibrancy()
    blur(effects.blurRadiusDp.dp.toPx())
    lens(
        refractionHeight = effects.refractionHeightDp.dp.toPx(),
        refractionAmount = effects.refractionAmountDp.dp.toPx(),
        depthEffect = effects.depthEffect,
        chromaticAberration = effects.dispersion
    )
}

@Composable
private fun PreviewCaption(text: String, color: Color) {
    Text(
        text,
        color = color.copy(alpha = 0.75f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * The backdrop the effects are demonstrated against: hard-edged colour bands, a fine grid,
 * concentric rings and large type.
 *
 * Every element here is chosen for what it shows. The bands give refraction a straight hard edge
 * to bow and dispersion a saturated boundary to fringe. The grid is the one that makes the lens
 * legible at a glance -- parallel lines pinching toward the pane's rim is unmistakable in a way
 * that a shifted gradient is not. The rings show the distortion's own curvature, and the type is
 * the highest-frequency thing on the screen, so it is where blur reads first.
 */
@Composable
private fun TestCardBackdrop() {
    // Test-card colours rather than the app's palette. A pastel edge has no fringe to split, and
    // dispersion is the effect that most needs one.
    val bands = listOf(
        Color(0xFFE53935),
        Color(0xFFFB8C00),
        Color(0xFFFDD835),
        Color(0xFF43A047),
        Color(0xFF00ACC1),
        Color(0xFF3949AB),
        Color(0xFF8E24AA)
    )

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val skew = h * 0.28f

            drawRect(Color(0xFF101216))

            // Sheared bands, so the edges run diagonally and a pane dragged in any direction
            // crosses one.
            val bandWidth = (w + skew) / bands.size
            bands.forEachIndexed { index, color ->
                val x = index * bandWidth
                drawPath(
                    Path().apply {
                        moveTo(x, 0f)
                        lineTo(x + bandWidth, 0f)
                        lineTo(x + bandWidth - skew, h)
                        lineTo(x - skew, h)
                        close()
                    },
                    color
                )
            }

            val step = 26.dp.toPx()
            val hair = 1.5.dp.toPx()
            val gridColor = Color.White.copy(alpha = 0.55f)
            var x = 0f
            while (x <= w) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), hair)
                x += step
            }
            var y = 0f
            while (y <= h) {
                drawLine(gridColor, Offset(0f, y), Offset(w, y), hair)
                y += step
            }

            val ringCenter = Offset(w / 2f, h * 0.24f)
            val ringColor = Color.Black.copy(alpha = 0.75f)
            repeat(10) { index ->
                drawCircle(
                    color = ringColor,
                    radius = (index + 1) * 16.dp.toPx(),
                    center = ringCenter,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Hard dots in black and white: the highest-contrast edges on the card, which is what
            // dispersion needs to show a coloured fringe rather than a slightly softer edge.
            repeat(7) { row ->
                repeat(5) { col ->
                    val cx = w * (col + 0.5f) / 5f
                    val cy = h * (row + 0.5f) / 7f
                    drawCircle(
                        color = if ((row + col) % 2 == 0) Color.White else Color.Black,
                        radius = 7.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }
        }

        // Type, which is where blur shows first and where a bent baseline is obvious.
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(6) { index ->
                Text(
                    if (index % 2 == 0) "REFRACT" else "0123456789",
                    color = if (index % 2 == 0) Color.White else Color.Black,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

/** Loads a picked image at no more than [MAX_PHOTO_DIMENSION] on its long edge. */
private fun decodeSampled(
    context: android.content.Context,
    uri: android.net.Uri
): ImageBitmap? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        // A phone wallpaper decoded at full size is tens of megabytes, and this one is only ever
        // drawn at screen size.
        var sample = 1
        val longest = max(info.size.width, info.size.height)
        while (longest / sample > MAX_PHOTO_DIMENSION) sample *= 2
        decoder.setTargetSampleSize(sample)
        // The backdrop layer samples this bitmap through a shader, so it has to be readable
        // rather than living in graphics memory.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }.asImageBitmap()
}.getOrNull()

private const val MAX_PHOTO_DIMENSION = 2048

/** A Material 3 section header: the group label in the primary colour, as the app's screens use. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
    )
}

/** A Material 3 filter chip, for the playground's single-choice rows. */
@Composable
private fun M3Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) }
    )
}

/** A labelled Material 3 slider that says what it is set to. */
@Composable
private fun M3Slider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            valueText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = 0f..1f)
}

/** A Material 3 row with a switch, the whole row tappable. */
@Composable
private fun M3Toggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
