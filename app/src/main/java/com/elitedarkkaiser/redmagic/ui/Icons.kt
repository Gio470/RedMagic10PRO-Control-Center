package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.elitedarkkaiser.redmagic.R

/**
 * The app's icons, from Font Awesome Free (FortAwesome/Font-Awesome, solid style).
 *
 * Every row and section used to carry a Unicode dingbat picked for looking roughly like the thing
 * it stood for -- ❋ for the LEDs, ⎓ for charging, ☏ for calls. They were never icons, only
 * characters that resembled some, so they were drawn by whichever system font happened to have the
 * codepoint: different weights, different optical sizes, some of them missing on another ROM and
 * rendered as a box. A single icon font settles all of that at once.
 *
 * The codepoints are Font Awesome's private-use-area assignments, taken from the project's own
 * metadata/icons.json rather than typed by hand, and each one is named here after what it is for in
 * this app with the Font Awesome name beside it -- so a future change means looking up one constant
 * rather than searching for a character nobody can type.
 *
 * Icons are CC BY 4.0 and the font is SIL OFL 1.1; the full licence ships in the APK's assets.
 */
object Icons {

    /**
     * The solid face, loaded once. Null if the font resource cannot be read, in which case callers
     * leave the text as it is -- a missing typeface must not take the row down with it.
     */
    @Volatile private var cached: Typeface? = null

    fun typeface(context: Context): Typeface? {
        cached?.let { return it }
        val loaded = runCatching {
            ResourcesCompat.getFont(context, R.font.fa_solid_900)
        }.getOrNull()
        cached = loaded
        return loaded
    }

    // ---- Hardware ------------------------------------------------------------------------------

    /** `fan` */ const val FAN = "\uf863"
    /** `wand-magic-sparkles` */ const val AUTO_FAN = "\ue2ca"
    /** `wave-square` */ const val HAPTICS = "\uf83e"
    /** `play` */ const val TEST = "\uf04b"
    /** `toggle-on` */ const val MAGIC_KEY = "\uf205"
    /** `gamepad` */ const val TRIGGERS = "\uf11b"
    /** `sliders` */ const val CONFIGURE = "\uf1de"
    /** `arrow-rotate-left` */ const val UNLOCK = "\uf0e2"
    /** `power-off` */ const val AUTOSTART = "\uf011"
    /** `up-right-and-down-left-from-center` */ const val DENSITY = "\uf424"
    /** `check` */ const val CHECK = "\uf00c"
    /** `xmark` */ const val XMARK = "\uf00d"
    /** `arrow-left` -- the way out of a page. See PageHost. */ const val BACK = "\uf060"

    // ---- Lighting ------------------------------------------------------------------------------

    /** `lightbulb` */ const val LED = "\uf0eb"
    /** `eye` */ const val PREVIEW = "\uf06e"
    /** `star` */ const val LOGO = "\uf005"
    /** `gamepad` */ const val GAME_MODE = "\uf11b"
    /** `table-cells-large` */ const val APP_LIST = "\uf009"
    /** `wand-magic-sparkles` */ const val PROFILE = "\ue2ca"
    /** `phone` */ const val CALLS = "\uf095"
    /** `pause` */ const val PAUSE = "\uf04c"
    /** `phone-volume` */ const val CALL_INCOMING = "\uf2a0"
    /** `phone-flip` */ const val CALL_ACTIVE = "\uf879"
    /** `plug` */ const val CHARGING = "\uf1e6"

    // ---- Software: windows ---------------------------------------------------------------------

    /** `window-restore` */ const val WINDOW = "\uf2d2"
    /** `window-minimize` */ const val WINDOW_MIN = "\uf2d1"
    /** `thumbtack` */ const val DROP_POSITION = "\uf08d"
    /** `arrow-up-right-from-square` */ const val OFFSCREEN = "\uf08e"
    /** `table-columns` */ const val SPLIT = "\uf0db"
    /** `square-plus` */ const val ANY_APP = "\uf0fe"

    // ---- Software: icon pack, GameAssist -------------------------------------------------------

    /** `palette` */ const val ICON_PACK_MASTER = "\uf53f"
    /** `icons` */ const val ICON_PACK = "\uf86d"
    /** `clone` */ const val FALLBACK = "\uf24d"
    /** `maximize` */ const val SHORTCUTS = "\uf31e"
    /** `circle-half-stroke` */ const val MONOCHROME = "\uf042"
    /** `gauge-high` */ const val GAME_ASSIST = "\uf625"
    /** `shield-halved` */ const val SHIELD = "\uf3ed"
    /** `crown` */ const val GLOBAL_GAME = "\uf521"
    /** `eye-slash` */ const val HIDE = "\uf070"
    /** `expand` */ const val RESOLUTION = "\uf065"
    /** `pen` */ const val WATERMARK = "\uf304"
    /** `clone` */ const val SMALL_WINDOW = "\uf24d"

    // ---- Software: root tools ------------------------------------------------------------------

    /** `broom` */ const val DEBLOAT = "\uf51a"
    /** `trash-can` */ const val CACHE_CLEAN = "\uf2ed"
    /** `microchip` */ const val KERNEL = "\uf2db"
    /** `wind` */ const val VSYNC = "\uf72e"
    /** `tachometer-alt` */ const val PERF = "\uf3fd"
    /** `cloud` -- stands in for Google Play services, not the real brand mark. */ const val GMS = "\uf0c2"
    /** `recycle` */ const val RAM_TWEAK = "\uf1b8"
    /** `shield-halved` */ const val STARFLOW = "\uf3ed"
    /** `microchip` */ const val PROCESSES = "\uf2db"
    /** `terminal` */ const val NATIVE_PROCESS = "\uf120"
    /** `triangle-exclamation` */ const val WARNING = "\uf071"
    /** `magnifying-glass` -- a search bar's leading icon. */ const val SEARCH = "\uf002"
    /** `circle-info` -- an info block's leading icon. */ const val INFO = "\uf05a"
    /** `arrow-down-wide-short` -- a sort chip's leading icon. */ const val SORT = "\uf160"
    /** `circle-xmark` -- a search bar's clear button. */ const val CLEAR = "\uf057"
    /** `paintbrush` */ const val PAINTBRUSH = "\uf1fc"
    /** `gears` */ const val GEARS = "\uf085"
    /** `rotate` */ const val ROTATE = "\uf2f1"
    /** `cube` */ const val PACKAGE = "\uf1b2"
    /** `code` */ const val CODE = "\uf121"
    /** `bolt` */ const val BOLT = "\uf0e7"

    // ---- Navigation bar -----------------------------------------------------------------------

    /** `house` */ const val HOME = "\uf015"
    /** `microchip` */ const val HARDWARE = "\uf2db"
    /** `laptop-code` */ const val SOFTWARE = "\uf5fc"
    /** `gear` */ const val SETTINGS = "\uf013"

    // ---- Magic Key ----------------------------------------------------------------------------

    /** `camera` */ const val CAMERA = "\uf030"
    /** `gamepad` */ const val GAME_SPACE = "\uf11b"
    /** `volume-high` */ const val SOUND_MODE = "\uf028"
    /** `lightbulb` */ const val FLASHLIGHT = "\uf0eb"
    /** `microphone` */ const val RECORDER = "\uf130"
    /** `arrow-up-right-from-square` */ const val LAUNCH_APP = "\uf08e"
    /** `ban` */ const val DISABLED = "\uf05e"

    // ---- Settings ------------------------------------------------------------------------------

    /** `bug` */ const val CRASH_LOG = "\uf188"
    /** `list` */ const val WRITE_LOG = "\uf03a"
    /** `circle-half-stroke` */ const val DYNAMIC_COLOR = "\uf042"
    /** `palette` */ const val COLOR_PALETTE = "\uf53f"
    /** `table-columns` */ const val DOCKED_BAR = "\uf0db"
    /** `layer-group` */ const val GLASS = "\uf5fd"
    /** `droplet` */ const val PURE_BLACK = "\uf043"
    /** `droplet`, for ReSukiSU's Opacity icon on Card transparency */ const val OPACITY = "\uf043"
    /** `image` */ const val BLUR = "\uf03e"
    /** `temperature-half` */ const val FAHRENHEIT = "\uf2c9"
    /** `circle-half-stroke` */ const val THEME_MODE = "\uf042"
    /** `wand-magic-sparkles` */ const val BACKGROUND = "\ue2ca"
    /** `memory` */ const val MEMORY = "\uf538"
}
