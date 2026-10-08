package com.elitedarkkaiser.redmagic.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.lang.reflect.Method

/**
 * GameAssist ("Game Helper") and GameSpace tweaks, ported from khanhnguyen9872/NubiaToolkit
 * (github.com/khanhnguyen9872/NubiaToolkit): No Kill, Global Game Mode, Hide Energy Cube, Super
 * Resolution, Watermark Length and Small Window.
 *
 * The upstream module ships its own [de.robv.android.xposed.XposedHelpers]-based
 * `findAndHookMethod`, which this app cannot call: [xposed-api] only vendors the handful of
 * classes [WindowModHooks] needs (see its README), not the full helper surface. [hookExact] and
 * [hookOrFallback] below do the same job with plain reflection, matching how [WindowModHooks]
 * already hooks `system_server` methods.
 *
 * cn.nubia.gameassist ships with R8/ProGuard method-name obfuscation on newer RedMagic OS builds
 * (confirmed on GameAssist 16.5.000.2604202119 / RedMagicOS 11.0.5MR_GB, RedMagic 10 Pro NX789J),
 * so several targets are hooked by original name first, falling back to the single-letter name
 * reverse-engineered from that build if the original isn't found -- the fallback names will need
 * re-deriving after the next obfuscated GameAssist release.
 */
class GameAssistHooks : IXposedHookLoadPackage {

    private val config = GameAssistConfig.Reader()
    private val installed = linkedMapOf<String, String>()

    private fun bool(key: String, default: Boolean): Boolean = config.boolean(key, default)

    /** The master switch, checked on every call: hooks install once and cannot be added later. */
    private fun enabled(): Boolean = bool(GameAssistConfig.KEY_ENABLED, GameAssistConfig.DEFAULT_ENABLED)

    private fun featureOn(key: String, default: Boolean): Boolean = enabled() && bool(key, default)

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            "cn.nubia.gameassist" -> {
                install("no-kill") { hookNoKill(lpparam.classLoader) }
                install("global-game-mode") { hookGlobalGameMode(lpparam.classLoader) }
                install("hide-energy-cube") { hookHideEnergyCube(lpparam.classLoader) }
                install("super-resolution") { hookSuperResolutionAssist(lpparam.classLoader) }
                install("small-window") { hookSmallWindow(lpparam.classLoader) }
                writeStatus()
            }
            "cn.nubia.gamelauncher" -> {
                install("watermark-length") { hookWatermarkLength(lpparam.classLoader) }
                install("super-resolution-launcher") { hookSuperResolutionLauncher(lpparam.classLoader) }
                writeStatus()
            }
        }
    }

    private inline fun install(name: String, block: () -> Unit) {
        runCatching(block)
            .onSuccess { installed[name] = "ok" }
            .onFailure {
                installed[name] = "FAILED: $it"
                XposedBridge.log("[RedMagic Control] $name hook failed to install: $it")
            }
    }

    /** Reports what actually happened at boot -- see [WindowModHooks.writeStatus] for why. */
    private fun writeStatus() {
        val report = buildString {
            append("time=").append(System.currentTimeMillis()).append('\n')
            append("config_found=").append(File(GameAssistConfig.CONFIG_PATH).isFile).append('\n')
            append("enabled=").append(enabled()).append('\n')
            installed.forEach { (name, state) -> append("hook.").append(name).append('=').append(state).append('\n') }
        }
        XposedBridge.log("[RedMagic Control] GameAssist hooks installed:\n$report")
        runCatching {
            File(GameAssistConfig.STATUS_PATH).writeText(report)
        }.onFailure {
            XposedBridge.log("[RedMagic Control] could not write GameAssist status file: $it")
        }
    }

    /** Hooks a method whose name and signature are unchanged on this build -- no fallback needed. */
    private fun hookExact(
        cl: ClassLoader, className: String, methodName: String, vararg params: Class<*>,
        callback: XC_MethodHook
    ) {
        val method: Method = cl.loadClass(className).getDeclaredMethod(methodName, *params)
        XposedBridge.hookMethod(method, callback)
    }

    private fun hookConstructor(
        cl: ClassLoader, className: String, vararg params: Class<*>, callback: XC_MethodHook
    ) {
        val ctor = cl.loadClass(className).getDeclaredConstructor(*params)
        XposedBridge.hookMethod(ctor, callback)
    }

    /** Hooks [methodName], falling back to [fallbackName] if the original isn't found on this build. */
    private fun hookOrFallback(
        cl: ClassLoader, className: String, methodName: String, fallbackName: String,
        vararg params: Class<*>, callback: XC_MethodHook
    ) {
        val clazz = cl.loadClass(className)
        val method: Method = try {
            clazz.getDeclaredMethod(methodName, *params)
        } catch (_: NoSuchMethodException) {
            clazz.getDeclaredMethod(fallbackName, *params)
        }
        XposedBridge.hookMethod(method, callback)
    }

    // ---- No Kill: prevent GameAssist's automatic background-app cleanup ----

    private fun hookNoKill(cl: ClassLoader) {
        fun on() = featureOn(GameAssistConfig.KEY_NO_KILL, GameAssistConfig.DEFAULT_NO_KILL)

        runCatching {
            // Obfuscated fallback "f": private synthetic void f() -- confirmed by its internal
            // "startClean,isAnimating" log message and its call into
            // MindSyncManager.startBgAppCleanupFromGameMode(List).
            hookOrFallback(
                cl, "cn.nubia.gameassist.dessert.policy.clean.CleanAnimationController",
                "startClean", "f",
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = null
                    }
                }
            )
        }.onFailure { XposedBridge.log("NoKill: CleanAnimationController hook failed: $it") }

        runCatching {
            hookExact(
                cl, "com.zte.performance.mindsync.MindSyncManager",
                "startBgAppCleanupFromGameMode", java.util.List::class.java,
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = null
                    }
                }
            )
        } // Optional hook -- may not exist on all variants.

        runCatching {
            // Obfuscated fallback "j": public Bundle j(String, Bundle) -- confirmed by its internal
            // "linkOMTProvider: e = " log message (return type changed from void to Bundle in this
            // build, but the (String, Bundle) parameter match is unaffected).
            hookOrFallback(
                cl, "cn.nubia.gameassist.onemorething.OneMoreThingManager",
                "linkOMTProvider", "j",
                String::class.java, android.os.Bundle::class.java,
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args[0] == "kill" && on()) param.result = null
                    }
                }
            )
        } // Optional hook -- may not exist on all variants.
    }

    // ---- Global Game Mode: treats every app as a game app ----

    private fun hookGlobalGameMode(cl: ClassLoader) {
        fun on() = featureOn(GameAssistConfig.KEY_GLOBAL_GAME_MODE, GameAssistConfig.DEFAULT_GLOBAL_GAME_MODE)

        runCatching {
            // Obfuscated fallback "h": public boolean h(String) -- unique match by signature
            // (GameCheck is fully method-obfuscated on newer RedMagic OS builds).
            hookOrFallback(
                cl, "com.zte.gameassist.common.GameCheck", "isGameSpaceListApp", "h",
                String::class.java,
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = true
                    }
                }
            )
        } // Silently fail -- method might not exist on all variants.

        runCatching {
            // Obfuscated fallback "i": public boolean i(String, int) -- unique match by signature.
            hookOrFallback(
                cl, "com.zte.gameassist.common.GameCheck", "isGameSpaceListApp", "i",
                String::class.java, Int::class.javaPrimitiveType!!,
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = true
                    }
                }
            )
        } // Silently fail -- method might not exist on all variants.
    }

    // ---- Hide Energy Cube: blocks the floating launch-tips overlay ----

    private fun hookHideEnergyCube(cl: ClassLoader) {
        runCatching {
            // Obfuscated fallback "k": the only static method returning GameAssistLaunchTips with
            // this exact 8-parameter list -- confirmed unique match.
            hookOrFallback(
                cl, "cn.nubia.gameassist.tips.GameAssistLaunchTips", "createAndShowTips", "k",
                android.content.Context::class.java,
                android.os.Handler::class.java,
                android.os.Handler::class.java,
                String::class.java,
                String::class.java,
                java.util.List::class.java,
                Runnable::class.java,
                String::class.java, // launchWay
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (featureOn(
                                GameAssistConfig.KEY_HIDE_ENERGY_CUBE,
                                GameAssistConfig.DEFAULT_HIDE_ENERGY_CUBE
                            )
                        ) {
                            param.result = null
                        }
                    }
                }
            )
        }.onFailure { XposedBridge.log("HideEnergyCube: createAndShowTips hook failed: $it") }
    }

    // ---- Small Window: allows every app to open in windowed mode ----

    private fun hookSmallWindow(cl: ClassLoader) {
        fun on() = featureOn(GameAssistConfig.KEY_SMALL_WINDOW, GameAssistConfig.DEFAULT_SMALL_WINDOW)

        // Each hook below is applied independently so that one missing/renamed method
        // (obfuscation, a future OTA) doesn't take the other three down with it.

        runCatching {
            hookExact(
                cl, "com.zte.shared.wrapper.ActivityManagerWrapper", "checkTaskSupportWr",
                String::class.java,
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = true
                    }
                }
            )
        }.onFailure { XposedBridge.log("SmallWindow: checkTaskSupportWr hook failed: $it") }

        runCatching {
            // Obfuscated fallback "k": public static ArrayList k(Context) -- confirmed by its
            // content://.../hide_apps_str provider read and "get hide app list error:" log message.
            hookOrFallback(
                cl, "cn.nubia.gameassist.utils.TilesUtil", "getHideAppList", "k",
                android.content.Context::class.java,
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = ArrayList<String>()
                    }
                }
            )
        }.onFailure { XposedBridge.log("SmallWindow: getHideAppList hook failed: $it") }

        runCatching {
            // Obfuscated fallback "Q": public boolean Q(Context) -- confirmed by reading the
            // "ss_multi_window_enabled" flag from Settings.System.
            hookOrFallback(
                cl, "cn.nubia.gameassist.utils.Utils", "isSmallWindowOpen", "Q",
                android.content.Context::class.java,
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = true
                    }
                }
            )
        }.onFailure { XposedBridge.log("SmallWindow: isSmallWindowOpen hook failed: $it") }

        runCatching {
            // Obfuscated fallback "c0": protected void c0(QSTile$State, Object) -- unique match by
            // signature within SmallWindowTile.
            hookOrFallback(
                cl, "cn.nubia.gameassist.dessert.tiles.SmallWindowTile", "handleUpdateState", "c0",
                cl.loadClass("cn.nubia.gameassist.common.QSTile\$State"), Any::class.java,
                callback = object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!on()) return
                        val state = param.args[0] ?: return
                        setBooleanFieldQuietly(state, "value", true)
                        setBooleanFieldQuietly(state, "visible", true)
                    }
                }
            )
        }.onFailure { XposedBridge.log("SmallWindow: handleUpdateState hook failed: $it") }
    }

    private fun setBooleanFieldQuietly(target: Any, fieldName: String, value: Boolean) {
        runCatching {
            val field = target.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.setBoolean(target, value)
        }
    }

    // ---- Watermark Length: removes the on-screen watermark's character limit ----

    private fun hookWatermarkLength(cl: ClassLoader) {
        runCatching {
            hookConstructor(
                cl, "cn.nubia.gamecenter.settings.watermark.WaterMarkWatcher",
                android.widget.EditText::class.java, Int::class.javaPrimitiveType!!,
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (featureOn(
                                GameAssistConfig.KEY_WATERMARK_LENGTH,
                                GameAssistConfig.DEFAULT_WATERMARK_LENGTH
                            )
                        ) {
                            param.args[1] = 1000
                        }
                    }
                }
            )
        }.onFailure { XposedBridge.log("WatermarkLength: WaterMarkWatcher hook failed: $it") }
    }

    // ---- Super Resolution: enables the feature on devices the ROM doesn't list as supported ----

    private fun hookSuperResolutionAssist(cl: ClassLoader) {
        fun on() = featureOn(GameAssistConfig.KEY_SUPER_RESOLUTION, GameAssistConfig.DEFAULT_SUPER_RESOLUTION)

        runCatching {
            // Obfuscated fallback "e": public int e(String) -- unique match by signature.
            hookOrFallback(
                cl, "cn.nubia.gameassist.plugin.PluginUtils", "getGfrcCapByPkg", "e",
                String::class.java,
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = 1 // 1 = Supported
                    }
                }
            )

            // Obfuscated fallback "h": public boolean h() -- delegates to
            // ZteFeature.isSupportSuperResolutionOld() unless a locally-cached field
            // short-circuits it first, so the method itself must be forced true.
            hookOrFallback(
                cl, "cn.nubia.gameassist.plugin.PluginUtils", "isSupportResolutionOld", "h",
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = true
                    }
                }
            )

            // Obfuscated fallback "i": public boolean i() -- same shape as "h" above, delegates to
            // ZteFeature.isSupportSuperResolutionSettings().
            hookOrFallback(
                cl, "cn.nubia.gameassist.plugin.PluginUtils", "isSupportResolutionSettingsInXml", "i",
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = true
                    }
                }
            )

            // Obfuscated fallback "p": public boolean p(String) -- unique match by signature.
            hookOrFallback(
                cl, "cn.nubia.gameassist.plugin.PluginUtils", "supportResolution", "p",
                String::class.java,
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = true
                    }
                }
            )
        }.onFailure { XposedBridge.log("SuperResolution: PluginUtils hook failed: $it") }

        runCatching {
            hookExact(
                cl, "cn.nubia.plugin.superresolution.SuperResolutionTypeDataManager", "getItem",
                String::class.java, String::class.java,
                callback = object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!on()) return
                        val type = param.args[1] as? String
                        val result = param.result as? String
                        // Override default/unsupported values with "1" (supported).
                        if (type == "imageQuality" && (result == "origin" || result == null)) {
                            param.result = "1"
                        } else if (type == "frameRate" && (result == "frameRate_origin" || result == null)) {
                            param.result = "1"
                        }
                    }
                }
            )
        }.onFailure { XposedBridge.log("SuperResolution: SuperResolutionTypeDataManager hook failed: $it") }

        runCatching {
            hookExact(
                cl, "com.zte.gameassist.config.ZteFeature", "isSupportSuperResolution",
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = true
                    }
                }
            )
            hookExact(
                cl, "com.zte.gameassist.config.ZteFeature", "isSupportSuperResolutionOld",
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = true
                    }
                }
            )
        }.onFailure { XposedBridge.log("SuperResolution: ZteFeature hook failed: $it") }

        runCatching {
            // Obfuscated fallback "k": public static boolean k(Context, String) -- confirmed by
            // reading "game_assist_enable_plugin_"+pluginName from Settings.Global.
            hookOrFallback(
                cl, "cn.nubia.gameassist.plugin.config.PluginConfig", "isPluginEnable", "k",
                android.content.Context::class.java, String::class.java,
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pluginName = param.args[1] as? String
                        if ((pluginName == "super_resolution" || pluginName == "super_resolution_old") && on()) {
                            param.result = true
                        }
                    }
                }
            )
        }.onFailure { XposedBridge.log("SuperResolution: PluginConfig hook failed: $it") }

        runCatching {
            // Obfuscated fallback "Q": public boolean Q(Context) -- confirmed by reading the
            // "ss_multi_window_enabled" flag from Settings.System. Forcing this false here prevents
            // the "Please close float window first" toast that otherwise blocks the feature.
            hookOrFallback(
                cl, "cn.nubia.gameassist.utils.Utils", "isSmallWindowOpen", "Q",
                android.content.Context::class.java,
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = false
                    }
                }
            )
        }.onFailure { XposedBridge.log("SuperResolution: Utils hook failed: $it") }
    }

    private fun hookSuperResolutionLauncher(cl: ClassLoader) {
        fun on() = featureOn(GameAssistConfig.KEY_SUPER_RESOLUTION, GameAssistConfig.DEFAULT_SUPER_RESOLUTION)

        runCatching {
            hookExact(
                cl,
                "cn.nubia.gamelauncher.gamecontrolpanel.superresolution.SuperResolutionHelper",
                "supportSuperResolutionByPkgName", String::class.java,
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = true
                    }
                }
            )
            hookExact(
                cl,
                "cn.nubia.gamelauncher.gamecontrolpanel.utils.ControlPanelFeatureHelper",
                "getZteFeatureMagicSuperResolution",
                callback = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (on()) param.result = true
                    }
                }
            )
        }.onFailure { XposedBridge.log("SuperResolution: GameLauncher hooks failed: $it") }
    }
}
