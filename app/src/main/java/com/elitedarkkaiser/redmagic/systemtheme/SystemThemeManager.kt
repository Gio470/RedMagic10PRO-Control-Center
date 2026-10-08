package com.elitedarkkaiser.redmagic.systemtheme

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import com.elitedarkkaiser.redmagic.RootShell
import org.json.JSONObject
import java.io.File

/**
 * Applying a system-wide Material You theme, ported from Mahmud0808/ColorBlendr.
 *
 * One mechanism: a fabricated runtime overlay over the `android` package's palette resources (see
 * [OverlayCli]). Every ramp the system publishes is overridden directly, so the seed, the style and
 * all six fine-tuning settings arrive by the same route.
 *
 * ## Why not the settings key as well
 *
 * There is a second, gentler mechanism -- `Settings.Secure.theme_customization_overlay_packages`,
 * the key the platform's own Wallpaper & style picker writes -- and an earlier version of this
 * wrote both, on the reasoning that the supported path should carry what it can and the overlay
 * should carry the rest. That was wrong, and it crashed the phone.
 *
 * Changing that key wakes SystemUI's ThemeOverlayController, which generates *its own* fabricated
 * overlays over the very same resources and commits them a moment after this one is enabled. Two
 * overlays racing for the framework's colour table is not a state the system is built to be in.
 * ColorBlendr writes that key only in its Shizuku and wireless-ADB modes, where it has no way to
 * register an overlay at all; in root mode it never goes near it. Neither does this.
 *
 * [clear] still strips the key, because builds that wrote it are out there and this is the only
 * thing that will take it back off.
 *
 * ## Only resources that exist
 *
 * Every name is checked against the framework before it goes in the spec. A fabricated overlay
 * naming a resource its target does not have is not politely ignored -- it is a bad idmap over the
 * package every process on the phone links against. Upstream guards the same way (its `ifExists`
 * flag); this applies the guard to everything rather than to the handful of names it was unsure of.
 */
object SystemThemeManager {

    private const val FRAMEWORK = "android"
    private const val OVERLAY_NAME = "redmagic_control_system_colors"
    private const val SETTINGS_KEY = "theme_customization_overlay_packages"

    private const val KEY_STYLE = "android.theme.customization.theme_style"
    private const val KEY_SOURCE = "android.theme.customization.color_source"
    private const val KEY_PALETTE = "android.theme.customization.system_palette"
    private const val KEY_ACCENT = "android.theme.customization.accent_color"
    private const val KEY_TIMESTAMP = "_applied_timestamp"

    /**
     * What to run if this ever leaves the phone in a state its own UI cannot fix. Shown in the
     * sheet rather than kept here, because the moment it is needed is the moment the app may not
     * be openable.
     */
    const val RECOVERY_COMMAND =
        "su -c 'cmd overlay disable --user 0 android:$OVERLAY_NAME'"

    /** What an apply or a clear did, in words the Software tab can put on screen. */
    data class Result(val ok: Boolean, val detail: String)

    /**
     * The colours the wallpaper suggests, best first.
     *
     * [WallpaperManager.getWallpaperColors] is the same source the system's own picker offers, and
     * it needs no permission for the *system* wallpaper only from Android 8.1 up. It can legitimately
     * return nothing -- a live wallpaper that publishes no colours, or a wallpaper set before the
     * API existed -- so callers fall back to the basic set.
     */
    fun wallpaperColors(context: Context): List<Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return emptyList()
        val colors = runCatching {
            WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull() ?: return emptyList()

        return listOfNotNull(
            colors.primaryColor.toArgb(),
            colors.secondaryColor?.toArgb(),
            colors.tertiaryColor?.toArgb()
        ).distinct()
    }

    /** ColorBlendr's own nine, verbatim from its `basic_color_codes` array. */
    val BASIC_COLORS = intArrayOf(
        0xFFFA5F49.toInt(), 0xFFFF9800.toInt(), 0xFF59B4BC.toInt(),
        0xFFD8F79A.toInt(), 0xFF95B8F6.toInt(), 0xFF7FF9C7.toInt(),
        0xFFF3EDC8.toInt(), 0xFFF7CAE4.toInt(), 0xFFB186F1.toInt()
    )

    fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * The seed in use: the saved one, or -- when following the wallpaper -- the wallpaper colour
     * picked, falling back to its first when the wallpaper has since changed and no longer offers
     * it. Always taking the first made the second and third wallpaper colours do nothing.
     */
    fun seed(context: Context): Int {
        val saved = SystemThemePrefs.seed(context)
        if (SystemThemePrefs.followWallpaper(context)) {
            val colours = wallpaperColors(context)
            if (saved in colours) return saved
            colours.firstOrNull()?.let { return it }
        }
        return saved
    }

    /** Everything an apply writes, as one string: equal means the phone already shows it. */
    fun signature(context: Context): String = listOf(
        seed(context),
        SystemThemePrefs.style(context).name,
        SystemThemePrefs.accentSaturation(context),
        SystemThemePrefs.backgroundSaturation(context),
        SystemThemePrefs.backgroundLightness(context),
        SystemThemePrefs.accurateShades(context),
        SystemThemePrefs.pitchBlack(context),
        SystemThemePrefs.tintText(context)
    ).joinToString("|")

    /** Whether the saved settings differ from what the last successful apply wrote. */
    fun hasUnapplied(context: Context): Boolean =
        context.getSharedPreferences("system_theme", Context.MODE_PRIVATE)
            .getString(KEY_APPLIED, null) != signature(context)

    private const val KEY_APPLIED = "applied_signature"

    /**
     * Writes the theme to the system, as one overlay.
     *
     * Synchronized with [clear]: the screen's Apply, the card's switch and the light/dark reapply
     * all run on their own threads, and two overlay writes interleaving leave the phone in
     * whichever state lost the race.
     */
    @Synchronized
    fun apply(context: Context): Result {
        if (!SystemThemePrefs.enabled(context)) return clear(context)

        val seed = seed(context)
        val style = SystemThemePrefs.style(context)

        context.getSharedPreferences("system_theme", Context.MODE_PRIVATE)
            .edit().putBoolean("last_applied_dark", isDark(context)).apply()

        val spec = buildSpec(context, seed, style)
        if (spec.isEmpty()) {
            return Result(false, "This ROM publishes none of the palette resources to override.")
        }

        val label = "#${hexNoHash(seed)} · ${style.label}"
        val output = runOverlayCli(context, "apply", spec)
            ?: return Result(false, "The overlay step produced no output. Is root granted?")

        return if (output.contains("OK")) {
            context.getSharedPreferences("system_theme", Context.MODE_PRIVATE)
                .edit().putString(KEY_APPLIED, signature(context)).apply()
            Result(true, "Applied $label. ${output.substringAfter("OK").trim()}")
        } else {
            Result(false, "$label failed: $output")
        }
    }

    /** Puts the system back the way it was: the platform's own key cleared, the overlay gone. */
    @Synchronized
    fun clear(context: Context): Result {
        context.getSharedPreferences("system_theme", Context.MODE_PRIVATE)
            .edit().remove(KEY_APPLIED).apply()
        val removed = removeOverlay()
        val current = RootShell.execForOutput("settings get secure $SETTINGS_KEY")?.trim()
        val stripped = stripOurKeys(current)
        val wrote = RootShell.exec(
            if (stripped == null) {
                "settings delete secure $SETTINGS_KEY"
            } else {
                "settings put secure $SETTINGS_KEY '${shellQuote(stripped)}'"
            }
        )
        return if (wrote) {
            Result(true, "System theme reset." + if (removed) " Overlay removed." else "")
        } else {
            Result(false, "Could not clear the theme setting. Is root granted?")
        }
    }

    // ---- the platform's own key ---------------------------------------------------------------

    private fun stripOurKeys(current: String?): String? {
        if (current.isNullOrBlank() || current == "null") return null
        val json = runCatching { JSONObject(current) }.getOrNull() ?: return null
        listOf(KEY_SOURCE, KEY_PALETTE, KEY_STYLE, KEY_ACCENT, KEY_TIMESTAMP).forEach(json::remove)
        return if (json.length() == 0) null else json.toString()
    }

    // ---- the overlay --------------------------------------------------------------------------

    /**
     * Both halves of the ramp set, written as the spec [OverlayCli] reads.
     *
     * Only the half matching the current light/dark mode is written: a fabricated overlay holds one
     * value per resource, and the system's own light and dark resources are the same names. Which
     * is why the theme is reapplied when the mode changes (see SoftwareTabUi).
     */
    private fun buildSpec(context: Context, seed: Int, style: MonetStyle): List<String> {
        val dark = isDark(context)
        // See the class doc: a name the framework does not define makes a bad idmap over the one
        // package everything on the phone links against.
        fun exists(name: String): Boolean =
            runCatching {
                context.resources.getIdentifier(name, "color", FRAMEWORK) != 0
            }.getOrDefault(false)

        val ramps = generate(context, seed, style, dark)

        // Both halves, because the roles below name their own mode and can hold both at once.
        // The ramps cannot -- their names say nothing about light or dark -- which is the whole
        // reason the overlay has to be rewritten when the phone changes mode.
        val rampsFor = mapOf(
            false to generate(context, seed, style, dark = false),
            true to generate(context, seed, style, dark = true)
        )

        val lines = mutableListOf<String>()
        ThemeEngine.PALETTE_NAMES.forEachIndexed { palette, name ->
            ThemeEngine.TONE_NAMES.forEachIndexed { tone, suffix ->
                val resource = "${name}_$suffix"
                if (exists(resource)) lines += entry(resource, ramps[palette][tone])
            }
        }

        // The derived roles, which is what modern system UI actually reads. See [SystemRoles]:
        // overriding the ramps alone was leaving every one of these still holding the colour the
        // system computed for itself, so the theme applied and almost nothing moved.
        val monochrome = style == MonetStyle.MONOCHROME
        listOf(false, true).forEach { forDark ->
            val set = rampsFor.getValue(forDark)

            SystemRoles.ROLES.forEach { role ->
                val tone = if (forDark) role.darkTone else role.lightTone
                val color = set[role.palette][tone]
                SystemRoles.names(role.name, forDark).forEach { resource ->
                    if (exists(resource)) lines += entry(resource, color)
                }
            }

            SystemRoles.ERROR_ROLES.forEach { role ->
                val color = SystemRoles.errorColor(role, forDark, monochrome)
                SystemRoles.names(role.name, forDark).forEach { resource ->
                    if (exists(resource)) lines += entry(resource, color)
                }
            }

            val highlight = if (forDark) {
                SystemRoles.CONTROL_HIGHLIGHT_DARK
            } else {
                SystemRoles.CONTROL_HIGHLIGHT_LIGHT
            }
            SystemRoles.names("control_highlight", forDark).forEach { resource ->
                if (exists(resource)) lines += entry(resource, highlight)
            }
        }

        // M3's reference palette: the same tones under the naming Material's own components use.
        val refSet = rampsFor.getValue(dark)
        SystemRoles.REF_PALETTES.forEach { (name, palette) ->
            SystemRoles.REF_TONES.forEach { (shade, tone) ->
                val color = refSet[palette][tone]
                listOf(
                    "m3_ref_palette_$name$shade",
                    "m3_ref_palette_dynamic_$name$shade",
                    "gm3_ref_palette_$name$shade",
                    "gm3_ref_palette_dynamic_$name$shade"
                ).forEach { resource ->
                    if (exists(resource)) lines += entry(resource, color)
                }
            }
        }

        if (SystemThemePrefs.pitchBlack(context) && exists("background_dark")) {
            lines += entry("background_dark", Color.BLACK)
        }

        // Untinted text is an override rather than a generated value: the tinted version is what
        // the ramps already produce, so switching it off means writing the plain neutrals back.
        if (!SystemThemePrefs.tintText(context)) {
            listOf(
                "text_color_primary_device_default_dark" to Color.WHITE,
                "text_color_secondary_device_default_dark" to 0xB3FFFFFF.toInt(),
                "text_color_primary_device_default_light" to Color.BLACK,
                "text_color_secondary_device_default_light" to 0xB3000000.toInt()
            ).forEach { (resource, color) ->
                if (exists(resource)) lines += entry(resource, color)
            }
        }

        return lines
    }

    /** One full set of ramps with the user's fine-tuning applied. */
    private fun generate(
        context: Context,
        seed: Int,
        style: MonetStyle,
        dark: Boolean
    ): Array<IntArray> = ThemeEngine.generate(
        seed = seed,
        style = style,
        dark = dark,
        accentSaturation = SystemThemePrefs.accentSaturation(context),
        backgroundSaturation = SystemThemePrefs.backgroundSaturation(context),
        backgroundLightness = SystemThemePrefs.backgroundLightness(context),
        pitchBlack = SystemThemePrefs.pitchBlack(context),
        accurateShades = SystemThemePrefs.accurateShades(context)
    )

    private fun entry(resource: String, color: Int) =
        "$FRAMEWORK|$OVERLAY_NAME|$resource|${ThemeEngine.hex(color)}"

    /**
     * Runs [OverlayCli] as root out of this app's own APK.
     *
     * The spec goes through a file rather than the command line: 78 colours is well past what an
     * argument list wants to carry, and it lands in the app's own cache so nothing world-readable
     * is involved. CLASSPATH is the installed APK, which is what gives the root process this app's
     * classes without installing anything anywhere.
     */
    private fun runOverlayCli(context: Context, command: String, spec: List<String>?): String? {
        val apk = context.applicationInfo.sourceDir
        val file = File(context.cacheDir, "overlay-spec.txt")
        return try {
            if (spec != null) file.writeText(spec.joinToString("\n"))
            val argument = if (spec != null) " ${file.absolutePath}" else " $OVERLAY_NAME"
            RootShell.execForOutput(
                "CLASSPATH=$apk app_process /system/bin " +
                    "com.elitedarkkaiser.redmagic.systemtheme.OverlayCli $command$argument"
            )?.trim()
        } catch (t: Throwable) {
            "FAIL ${t.javaClass.simpleName}: ${t.message}"
        } finally {
            file.delete()
        }
    }

    private fun removeOverlay(): Boolean {
        val apk = lastApkPath ?: return false
        val output = RootShell.execForOutput(
            "CLASSPATH=$apk app_process /system/bin " +
                "com.elitedarkkaiser.redmagic.systemtheme.OverlayCli remove $OVERLAY_NAME"
        )
        return output?.contains("OK") == true
    }

    /** Set on the first apply so [removeOverlay] can run without a Context to hand. */
    private var lastApkPath: String? = null

    fun rememberApk(context: Context) {
        lastApkPath = context.applicationInfo.sourceDir
    }

    /**
     * Rewrites the overlay if the phone has changed between light and dark since the last apply.
     *
     * A fabricated overlay holds one value per resource, and the system's light and dark palettes
     * are the same resource names -- so an overlay written in dark mode is still the dark ramp
     * after the phone turns light, and the phone comes out wrong. The settings-key half has no
     * such problem (the system regenerates both halves itself), which is why this only runs when
     * the overlay is actually in use.
     *
     * Called from the Activity's onResume: the mode can change while the app is not running, and
     * coming back to it is the first moment this app can do anything about that.
     */
    fun reapplyIfModeChanged(context: Context): Result? {
        if (!SystemThemePrefs.enabled(context)) return null
        val dark = isDark(context)
        val prefs = context.getSharedPreferences("system_theme", Context.MODE_PRIVATE)
        if (prefs.getBoolean("last_applied_dark", dark) == dark &&
            prefs.contains("last_applied_dark")
        ) {
            return null
        }
        prefs.edit().putBoolean("last_applied_dark", dark).apply()
        rememberApk(context)
        return apply(context)
    }

    private fun hexNoHash(color: Int): String = String.format("%06X", color and 0xFFFFFF)

    /** Single quotes are what wraps the JSON, so the JSON must not contain one unescaped. */
    private fun shellQuote(value: String): String = value.replace("'", "'\\''")
}
