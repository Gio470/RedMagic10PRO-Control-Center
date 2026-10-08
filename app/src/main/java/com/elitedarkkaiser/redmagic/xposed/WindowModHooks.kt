package com.elitedarkkaiser.redmagic.xposed

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.lang.reflect.Method

/**
 * The RedMagicOS floating-window fixes, ported from github.com/Gio470/FixRedMagicWindow.
 *
 * The original module applies all three patches unconditionally. Here each one is gated on the
 * setting the Xposed tab writes: the hooks are always installed (installing them later is not
 * possible -- system_server is hooked once at boot) but each returns early when its setting is off,
 * so a toggle takes effect immediately without a reboot.
 */
class WindowModHooks : IXposedHookLoadPackage {

    private val config = WindowModConfig.Reader()
    private val installed = linkedMapOf<String, String>()
    private var limitCounter = "not probed"
    private var limitDump: String? = null

    @Volatile
    private var counterProbed = false

    @Volatile
    private var counter: WindowLimitCounter.Counter? = null

    private fun bool(key: String, default: Boolean): Boolean = config.boolean(key, default)

    /**
     * The master switch, checked inside every callback rather than at install time: hooks are
     * installed once at boot and cannot be added later, so switching the feature off has to mean
     * the installed hooks stand down and let the original method run.
     */
    private fun enabled(): Boolean =
        bool(WindowModConfig.KEY_ENABLED, WindowModConfig.DEFAULT_ENABLED)

    private fun int(key: String, default: Int): Int = config.int(key, default)

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "android") return

        install("allow-any-app") { allowAnyApp(lpparam.classLoader) }
        install("keep-mini-interactive") { preventMiniToHangBubble(lpparam.classLoader) }
        install("keep-drop-position") { disableAutoHang(lpparam.classLoader) }
        install("allow-offscreen") { allowOffscreen(lpparam.classLoader) }
        install("no-drag-to-split") { disableDragToSplit(lpparam.classLoader) }
        install("window-limit") { applyWindowLimit(lpparam.classLoader) }
        writeStatus()
    }

    private inline fun install(name: String, block: () -> Unit) {
        runCatching(block)
            .onSuccess { installed[name] = "ok" }
            .onFailure {
                installed[name] = "FAILED: $it"
                XposedBridge.log("[RedMagic Control] $name hook failed to install: $it")
            }
    }

    /**
     * Reports what actually happened at boot, so a hook that failed to install is visible in the
     * app instead of only in the Xposed log, which rotates away. Written from system_server into
     * its own directory; the app reads it back as root.
     */
    private fun writeStatus() {
        val report = buildString {
            append("time=").append(System.currentTimeMillis()).append('\n')
            // Which boot this report is from. See WindowModConfig.KEY_BOOT_ID.
            append(WindowModConfig.KEY_BOOT_ID).append('=').append(
                runCatching { File(WindowModConfig.BOOT_ID_PATH).readText().trim() }
                    .getOrDefault("")
            ).append('\n')
            append("config_found=").append(File(WindowModConfig.CONFIG_PATH).isFile).append('\n')
            append("enabled=").append(enabled()).append('\n')
            append("allow_any_app=").append(
                bool(WindowModConfig.KEY_ALLOW_ANY_APP, WindowModConfig.DEFAULT_ALLOW_ANY_APP)
            ).append('\n')
            append("keep_mini=").append(
                bool(WindowModConfig.KEY_KEEP_MINI_INTERACTIVE,
                    WindowModConfig.DEFAULT_KEEP_MINI_INTERACTIVE)
            ).append('\n')
            append("keep_drop=").append(
                bool(WindowModConfig.KEY_KEEP_DROP_POSITION,
                    WindowModConfig.DEFAULT_KEEP_DROP_POSITION)
            ).append('\n')
            append("allow_offscreen=").append(
                bool(WindowModConfig.KEY_ALLOW_OFFSCREEN,
                    WindowModConfig.DEFAULT_ALLOW_OFFSCREEN)
            ).append('\n')
            append("no_drag_to_split=").append(
                bool(WindowModConfig.KEY_NO_DRAG_TO_SPLIT,
                    WindowModConfig.DEFAULT_NO_DRAG_TO_SPLIT)
            ).append('\n')
            append("window_limit=").append(
                int(WindowModConfig.KEY_WINDOW_LIMIT, WindowModConfig.DEFAULT_WINDOW_LIMIT)
            ).append('\n')
            installed.forEach { (name, state) -> append("hook.").append(name).append('=').append(state).append('\n') }
            // How the window cap fared. Without a counter it cannot be enforced, and the dump is
            // the ROM surface to work from rather than a dead end.
            append("limit_counter=").append(limitCounter).append('\n')
            limitDump?.let { append("limit_members=").append(it).append('\n') }
        }
        XposedBridge.log("[RedMagic Control] hooks installed:\n$report")
        runCatching {
            File(WindowModConfig.STATUS_PATH).writeText(report)
        }.onFailure {
            XposedBridge.log("[RedMagic Control] could not write status file: $it")
        }
    }

    /**
     * The ROM only offers floating mode for apps on a built-in whitelist. Reporting every package
     * as whitelisted opens it to all of them.
     */
    @SuppressLint("PrivateApi")
    private fun allowAnyApp(cl: ClassLoader) {
        XposedBridge.hookMethod(
            cl.loadClass("android.app.WindowReplyUtils")
                .getDeclaredMethod("isForceSupportWhiteListForWR", String::class.java),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (enabled() && bool(
                            WindowModConfig.KEY_ALLOW_ANY_APP,
                            WindowModConfig.DEFAULT_ALLOW_ANY_APP
                        )
                    ) {
                        param.result = true
                    }
                }
            }
        )
    }

    /**
     * Shrinking a small window past the "mini" threshold converts it into a tap-to-restore hang
     * bubble, which cannot be interacted with. No-oping the conversion leaves it a small but still
     * usable window.
     */
    @SuppressLint("PrivateApi")
    private fun preventMiniToHangBubble(cl: ClassLoader) {
        val taskClass = cl.loadClass("com.android.server.wm.Task")
        val activityRecordClass = cl.loadClass("com.android.server.wm.ActivityRecord")
        XposedBridge.hookMethod(
            taskClass.getDeclaredMethod("changeToHangInDragAnim", activityRecordClass),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (enabled() && bool(
                            WindowModConfig.KEY_KEEP_MINI_INTERACTIVE,
                            WindowModConfig.DEFAULT_KEEP_MINI_INTERACTIVE
                        )
                    ) {
                        param.result = null
                    }
                }
            }
        )
    }

    /**
     * Releasing a small-window drag docks it to the nearest edge as a hang bubble instead of
     * leaving it where it was dropped. Blocking the dock keeps the drop position.
     */
    @SuppressLint("PrivateApi")
    private fun disableAutoHang(cl: ClassLoader) {
        val displayContentClass = cl.loadClass("com.android.server.wm.DisplayContent")
        val activityRecordClass = cl.loadClass("com.android.server.wm.ActivityRecord")
        val windowManagerServiceClass = cl.loadClass("com.android.server.wm.WindowManagerService")
        val modifierClass = cl.loadClass("com.android.server.wm.TaskLaunchParamsModifierMifavor")

        XposedBridge.hookMethod(
            modifierClass.getDeclaredMethod(
                "performHangForWr",
                displayContentClass,
                Rect::class.java,
                activityRecordClass,
                windowManagerServiceClass,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (enabled() && bool(
                            WindowModConfig.KEY_KEEP_DROP_POSITION,
                            WindowModConfig.DEFAULT_KEEP_DROP_POSITION
                        )
                    ) {
                        param.result = false
                    }
                }
            }
        )
    }

    /**
     * Lets a dragged window hang off the edge of the screen.
     *
     * While a small window is being dragged near an edge, the ROM clamps its bounds fully back
     * on-screen every frame, so it can never be left partly cut off. Returning the rect it was
     * handed, unchanged, skips the clamp. Every other overload funnels into this one, so hooking it
     * alone covers all of them.
     *
     * Ported from Gio470/FixRedMagicWindow's DisableWrEdgeClamp, and gated on a setting the way the
     * rest of this file is: upstream applies it unconditionally.
     */
    @SuppressLint("PrivateApi")
    private fun allowOffscreen(cl: ClassLoader) {
        val displayContentClass = cl.loadClass("com.android.server.wm.DisplayContent")
        val modifierClass = cl.loadClass("com.android.server.wm.TaskLaunchParamsModifierMifavor")

        XposedBridge.hookMethod(
            modifierClass.getDeclaredMethod(
                "adjustPositionForWr",
                Context::class.java,
                displayContentClass,
                Rect::class.java,
                Float::class.javaPrimitiveType
            ),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (enabled() && bool(
                            WindowModConfig.KEY_ALLOW_OFFSCREEN,
                            WindowModConfig.DEFAULT_ALLOW_OFFSCREEN
                        )
                    ) {
                        // The bounds it was asked to adjust, handed straight back.
                        param.result = param.args[2]
                    }
                }
            }
        )
    }

    /**
     * Stops a drag to the screen edge flipping the window into split screen.
     *
     * Dragging a small window to the top or bottom edge in portrait, or the left or right in
     * landscape, toggles split-screen mode. Two checks decide that, one per orientation; forcing
     * both false disables the gesture without touching split screen entered any other way.
     *
     * Ported from Gio470/FixRedMagicWindow's DisableWrDragToSplit. The portrait method's name is
     * misspelled in the ROM ("Porirait"), and is spelled here exactly as it is there -- it is a
     * reflection lookup, so the typo is part of the address.
     */
    @SuppressLint("PrivateApi")
    private fun disableDragToSplit(cl: ClassLoader) {
        val taskPositionerClass = cl.loadClass("com.android.server.wm.TaskPositioner")

        val falseHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (enabled() && bool(
                        WindowModConfig.KEY_NO_DRAG_TO_SPLIT,
                        WindowModConfig.DEFAULT_NO_DRAG_TO_SPLIT
                    )
                ) {
                    param.result = false
                }
            }
        }

        XposedBridge.hookMethod(
            taskPositionerClass.getDeclaredMethod("checkNeedToggleFromWrToSplitForPorirait"),
            falseHook
        )
        XposedBridge.hookMethod(
            taskPositionerClass.getDeclaredMethod("checkWrToSplitForLand"),
            falseHook
        )
    }

    /**
     * The stock ROM caps how many apps may be open in floating mode, and refuses to launch another
     * once [isReachWrMaxSizeForMulti] says the cap is reached. The upstream module forces that to
     * false, which removes the cap entirely; this keeps the removal as the default but lets the
     * user set their own number instead.
     *
     * Enforcing a *chosen* number needs the current window count, which the stock method reads
     * internally and does not expose. [findWindowCounter] looks for the accessor by shape at hook
     * time rather than hard-coding a name that differs per ROM build. When no counter is found the
     * behaviour falls back to unlimited -- the upstream behaviour, and the safe direction to fail
     * in, since the alternative would be silently blocking windows the user asked to allow.
     */
    @SuppressLint("PrivateApi")
    private fun applyWindowLimit(cl: ClassLoader) {
        val atmsClass = cl.loadClass("com.android.server.wm.ActivityTaskManagerService")

        // Probed here only when a limit is actually set. The probe walks every declared method and
        // field of ActivityTaskManagerService -- one of the largest classes in system_server --
        // matching two regexes against each name, and it runs during system_server's startup. The
        // default is no limit, in which case the counter it finds is never read: the hook below
        // short-circuits to "cap removed" before it would look at one. So the common case now pays
        // nothing for it, and a limit set later resolves the counter on the first window that
        // needs it (see counterFor).
        val limitAtBoot = int(WindowModConfig.KEY_WINDOW_LIMIT, WindowModConfig.DEFAULT_WINDOW_LIMIT)
        if (limitAtBoot > 0) {
            probeCounter(atmsClass, cl)
        } else {
            limitCounter = "not probed (no limit set)"
        }

        XposedBridge.hookMethod(
            atmsClass.getDeclaredMethod("isReachWrMaxSizeForMulti"),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!enabled()) return
                    val limit = int(
                        WindowModConfig.KEY_WINDOW_LIMIT,
                        WindowModConfig.DEFAULT_WINDOW_LIMIT
                    )
                    // No limit set: remove the cap, which is what the module this was ported from
                    // does unconditionally.
                    if (limit <= 0) {
                        param.result = false
                        return
                    }
                    // No way to count on this ROM: same answer. Failing this way round is
                    // deliberate -- the alternative blocks windows the user asked to allow.
                    val counter = counterFor(atmsClass, cl)
                    if (counter == null) {
                        param.result = false
                        return
                    }
                    val open = counter.read(param.thisObject)
                    param.result = if (open == null) false else open >= limit
                }
            }
        )
    }

    /**
     * The window counter, resolved once -- at boot when a limit was already set, otherwise on the
     * first floating window launched after one is.
     */
    private fun counterFor(atmsClass: Class<*>, cl: ClassLoader): WindowLimitCounter.Counter? {
        if (counterProbed) return counter
        synchronized(this) {
            if (!counterProbed) {
                probeCounter(atmsClass, cl)
                // The status file was written at boot, before there was a limit to probe for, so
                // it still says the probe was skipped. Rewriting it is what keeps the Xposed tab's
                // "this ROM doesn't expose a window count" warning able to appear for a limit set
                // after boot. Once per boot, and off the path that matters -- this only runs on
                // the first floating window launched while a limit is in force.
                writeStatus()
            }
        }
        return counter
    }

    private fun probeCounter(atmsClass: Class<*>, cl: ClassLoader) {
        counter = WindowLimitCounter.find(atmsClass)
        counterProbed = true
        limitCounter = counter?.toString() ?: "none"
        if (counter == null) limitDump = WindowLimitCounter.dump(cl)
    }
}
