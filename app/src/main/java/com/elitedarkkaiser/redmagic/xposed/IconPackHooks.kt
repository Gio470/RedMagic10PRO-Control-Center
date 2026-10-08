package com.elitedarkkaiser.redmagic.xposed

import android.app.Application
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageItemInfo
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Member

/**
 * Applies an icon pack to every app's icons, ported from
 * github.com/RichardLuo0/global-icon-pack-android.
 *
 * The trick is upstream's, and it is worth stating because nothing else here makes sense without
 * it. An icon is a resource id, passed around as an int and only turned into a drawable much
 * later, so replacing icons means replacing ids: every [PackageItemInfo] this process builds gets
 * its `icon` field rewritten to a fake id carrying the icon pack entry's index ([IN_PACK]) or a
 * marker saying the pack has nothing for it ([NOT_IN_PACK]). Real ids always start with 0x7f, so
 * the fake ones cannot collide with a genuine resource. [Resources.getDrawableForDensity] is then
 * hooked to recognise those ids and hand back the pack's drawable -- or, for [NOT_IN_PACK] with
 * "force monochrome" on, the app's own icon rendered as a themed monochrome one.
 *
 * Only the constructors are hooked, not upstream's Parcel and ParceledListSlice paths: an info
 * object arriving over Binder is built by a constructor too, which covers the launcher and the
 * other icon surfaces without reaching into framework internals that move between releases.
 */
class IconPackHooks : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            OWN_PACKAGE -> return
            "android" -> installSystemServerHooks(lpparam.classLoader)
            // Once per process, not once per package: a process that hosts several packages
            // (a shared uid, or an app with more than one) gets handleLoadPackage for each of
            // them, and a second copy of these would hook everything twice over.
            else -> if (!hookedApp) {
                hookedApp = true
                install("app-start") { hookAppStart() }
                install("resources") { hookResources() }
                install("item-infos") { hookItemInfos() }
                install("shortcuts") { hookShortcuts() }
            }
        }
    }

    /**
     * system_server's share, which is one hook -- and only when a pack is actually selected.
     *
     * `AppsFilterBase.shouldFilterApplication` is called in tight loops while the package manager
     * scans packages and builds its intent resolver caches, which is a large part of what a boot
     * is. Hooking it does more than add the callback's own cost: a hooked method is deoptimised,
     * so it can no longer be inlined and every call goes through the bridge. That is the one
     * genuinely hot framework method this module touches, and it was being hooked on every boot
     * whether or not there was a pack to make visible.
     *
     * The file is read rather than `Settings.Global` because this runs before there is a settings
     * provider to ask -- the same constraint [WindowModConfig] is built around. A missing file
     * means the app has not run since this version was installed, and the hook goes in as it
     * always did: skipping it on a guess would break an icon pack that was already working.
     */
    private fun installSystemServerHooks(cl: ClassLoader) {
        val packs = IconPackConfig.SystemReader()
        if (packs.knownEmpty()) return
        install("package-visibility") { bypassPackageVisibility(cl, packs) }
    }

    /**
     * Stashes the Context the lazy load needs, and nothing else.
     *
     * ## Why the hooks go in at load time and not from here
     *
     * They were moved here in 2.6.4, behind a settings read, on the theory that installing the
     * icon pipeline's hooks in every app in scope whether or not a pack was selected was what made
     * boot slow. It was not: boot was unchanged, and the change cost two ANRs. The settings read
     * was one (see below). The other was installing the hooks *late*, and it is why the launcher
     * kept failing after SystemUI was fixed.
     *
     * Hooking a method makes ART deoptimise it, which suspends every thread in the process. Done
     * from `handleLoadPackage` that is free -- the app has not started yet, there is nothing to
     * suspend. Done from a background thread minutes into the process's life it lands on whatever
     * the app is doing, and what the launcher is doing at boot is constructing an [ActivityInfo]
     * and an [ApplicationInfo] for every app on the device to build its icon cache. Those are
     * precisely the constructors being hooked. SystemUI barely touches them, which is why it
     * recovered and the launcher did not.
     *
     * So the hooks are installed up front again, as they were before 2.6.4, and what is left here
     * is the part that was always right: a Context, from a method the framework always calls
     * exactly once per process (unlike `Application.onCreate`, which depends on an app remembering
     * to call `super`).
     */
    private fun hookAppStart() {
        Instrumentation::class.java.getDeclaredMethod(
            "callApplicationOnCreate", Application::class.java
        ).hook(
            after = { param ->
                val app = param.args.getOrNull(0) as? Context ?: return@hook
                appContext = app
                if (prepared) return@hook
                prepared = true
                prepare(app)
            }
        )
    }

    /**
     * Reads the settings and loads the pack, once per process, on a thread of its own.
     *
     * Nothing on a hooked path is allowed to do this work, and that is the whole point.
     * [IconPackConfig] lives in `Settings.Global`, and the first read in a process is a
     * `ContentResolver` acquire -- a Binder call into the activity manager which, if the settings
     * provider is not up yet, blocks until it is. Loading the pack itself then asks the package
     * manager for another app's resources and parses its `appfilter.xml`, which on a large pack is
     * thousands of entries.
     *
     * Both of those used to happen on whichever thread drew the first icon, which at boot is the
     * main thread of an app that has only just started -- the launcher building its icon cache,
     * or SystemUI. That is an "isn't responding" dialog waiting to happen, and it is what both of
     * them were showing. Doing it here costs icons drawn before it finishes staying stock, which
     * is a cosmetic price for a class of hang.
     */
    private fun prepare(context: Context) {
        Thread({
            val config = runCatching { IconPackConfig.read(context) }.getOrNull() ?: return@Thread
            state = config
            if (!config.active) return@Thread
            busy.set(true)
            try {
                // getResourcesForApplication() builds ApplicationInfo objects, which would land
                // straight back in the constructor hook and recurse into this. The flag is read
                // there; it is thread-local, so only this thread stands down.
                source = runCatching { IconPackSource(context, config.pack, config.asFallback) }
                    .onFailure {
                        XposedBridge.log("[RedMagic Control] icon pack ${config.pack} failed to load: $it")
                    }
                    .getOrNull()
            } finally {
                busy.set(false)
            }
        }, "rmcc-icon-pack").apply { isDaemon = true }.start()
    }

    private inline fun install(name: String, block: () -> Unit) {
        runCatching(block).onFailure {
            XposedBridge.log("[RedMagic Control] icon pack $name hook failed to install: $it")
        }
    }

    // ---- Per-process state: loaded once, by prepare(), never by a hooked call ----

    /**
     * "Local mode": the pack is read by each hooked app itself, once per process, and kept for
     * that process's life. Nothing watches for changes, which is why the app force-stops the
     * launcher after a settings change rather than expecting it to notice.
     *
     * Both of these are plain field reads, and no hooked path may turn them into anything more.
     * [prepare] is the only thing that loads, it runs on its own thread, and until it has finished
     * these answer null -- which every caller already handles as "leave this icon alone".
     */
    private fun source(): IconPackSource? = source

    private fun state(): IconPackConfig.State? = state

    // ---- Rewriting icon ids ----

    private fun hookItemInfos() {
        listOf(
            ApplicationInfo::class.java,
            ActivityInfo::class.java,
            ServiceInfo::class.java,
            ProviderInfo::class.java,
        ).forEach { clazz ->
            clazz.declaredConstructors.forEach { ctor ->
                ctor.hook(after = { param -> replaceIconId(param.thisObject as? PackageItemInfo) })
            }
        }
    }

    private fun replaceIconId(info: PackageItemInfo?) {
        if (info == null || busy.get() == true) return
        if (info.packageName == null) return
        // A quick settings tile draws its own icon from this field; upstream leaves those alone and
        // so does this, or every tile turns into an app icon.
        if (info is ServiceInfo &&
            info.permission == android.Manifest.permission.BIND_QUICK_SETTINGS_TILE
        ) return

        val id = source()?.getId(componentOf(info))
        if (id != null) {
            info.icon = id.withHighByte(IN_PACK)
            return
        }
        if (state()?.forceMonochrome == true && info.icon.hasHighByte(ANDROID_DEFAULT))
            info.icon = info.icon.withHighByte(NOT_IN_PACK)
    }

    private fun componentOf(info: PackageItemInfo): ComponentName =
        if (info is ApplicationInfo) IconPackSource.packageEntry(info.packageName)
        else ComponentName(info.packageName, info.fullClassName)

    private val PackageItemInfo.fullClassName: String
        get() = name?.let { if (it.startsWith(".")) packageName + it else it } ?: ""

    // ---- Turning the rewritten ids back into drawables ----

    private fun hookResources() {
        Resources::class.java.getDeclaredMethod(
            "getDrawableForDensity",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Resources.Theme::class.java,
        ).hook(
            before = { param ->
                var monochrome = false
                // Pushed in a finally so that after() always has exactly one entry to take, however
                // this branch ends -- an unbalanced deque would misapply the treatment elsewhere.
                try {
                    val resId = param.args[0] as? Int
                    val density = param.args[1] as? Int ?: 0
                    when {
                        resId == null -> {}
                        resId.hasHighByte(IN_PACK) -> {
                            val icon = source()?.getIcon(resId.lowBits(), density)
                            // A pack entry whose drawable went missing would otherwise return null
                            // and leave the caller with no icon at all; the stock placeholder is
                            // kinder.
                            if (icon != null) param.result = icon
                            else param.args[0] = android.R.drawable.sym_def_app_icon
                        }
                        resId.hasHighByte(NOT_IN_PACK) -> {
                            param.args[0] = resId.withHighByte(ANDROID_DEFAULT)
                            monochrome = true
                        }
                    }
                } finally {
                    pendingMonochrome.get()!!.addLast(monochrome)
                }
            },
            after = { param ->
                if (pendingMonochrome.get()!!.removeLastOrNull() != true) return@hook
                val res = param.thisObject as? Resources ?: return@hook
                (param.result as? Drawable)?.let { param.result = monochrome(res, it) }
            },
        )
    }

    /**
     * Renders an app's own icon as a themed monochrome one: its monochrome layer, tinted with the
     * wallpaper palette over a matching background, which is what the launcher would draw if the
     * app shipped a themed icon and the user had themed icons on.
     *
     * The extra inset matches upstream: a monochrome layer is authored at foreground size, but the
     * system's own themed-icon treatment draws the glyph smaller than that inside the circle.
     */
    private fun monochrome(res: Resources, drawable: Drawable): Drawable {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return drawable
        val mono = (drawable as? AdaptiveIconDrawable)?.monochrome ?: return drawable
        return runCatching {
            val dark = res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            val background = ColorDrawable(
                res.getColor(
                    if (dark) android.R.color.system_accent2_800
                    else android.R.color.system_accent1_100,
                    null
                )
            )
            val tint = res.getColor(
                if (dark) android.R.color.system_accent1_200
                else android.R.color.system_accent1_700,
                null
            )
            val foreground = mono.mutate().apply {
                colorFilter = BlendModeColorFilter(tint, BlendMode.SRC_IN)
            }
            AdaptiveIconDrawable(background, InsetDrawable(foreground, MONO_INSET))
        }.getOrDefault(drawable)
    }

    // ---- Shortcuts ----

    /**
     * A pack has no entries for shortcuts, so a shortcut keeps its stock icon while the app it
     * belongs to gets a themed one. This gives it the pack's icon for its owning app instead.
     */
    private fun hookShortcuts() {
        LauncherApps::class.java.declaredMethods
            .filter { it.name == "getShortcutIconDrawable" }
            .forEach { method ->
                method.hook(before = { param ->
                    if (state()?.shortcut != true) return@hook
                    val shortcut = param.args.getOrNull(0) as? ShortcutInfo ?: return@hook
                    val density = param.args.getOrNull(1) as? Int ?: 0
                    val source = source() ?: return@hook
                    val id = source.getId(IconPackSource.packageEntry(shortcut.`package`))
                        ?: return@hook
                    source.getIcon(id, density)?.let { param.result = it }
                })
            }
    }

    // ---- system_server ----

    /**
     * An app may only read the resources of a package it can see, and since Android 11 it can see
     * only what it declares in `<queries>` -- which no app declares about an icon pack the user
     * picked today. This makes the chosen pack visible to everything, which is the least this can
     * do and still let a hooked app open it.
     *
     * `shouldFilterApplication` is one of the busiest methods in system_server while packages are
     * being scanned at boot, so what this callback costs per call is what the module costs at
     * boot. Two things keep it near nothing: the early return on a call that was not filtering
     * anything, and [IconPackConfig.SystemReader], which answers "which pack" from a cached field
     * rather than the way this used to -- through the same lazy loader the app-side hooks use,
     * whose Context lookup is a reflective `ActivityThread.currentApplication()` call that returns
     * null for the whole early part of boot and so ran again on every single call.
     *
     * A file rather than `Settings.Global`, for the reason [WindowModConfig] spells out: there is
     * no settings provider to ask this early, and /data/system is system_server's own directory.
     */
    private fun bypassPackageVisibility(cl: ClassLoader, packs: IconPackConfig.SystemReader) {
        val packageState = cl.loadClass("com.android.server.pm.pkg.PackageState")
        val getPackageName = packageState.getMethod("getPackageName")
        cl.loadClass("com.android.server.pm.AppsFilterBase")
            .declaredMethods
            .filter { it.name == "shouldFilterApplication" }
            .forEach { method ->
                method.hook(after = { param ->
                    if (param.result != true) return@hook
                    val pack = packs.pack().takeIf { it.isNotEmpty() } ?: return@hook
                    // The target is the last PackageState-shaped argument in every signature this
                    // method has had; the caller's own state comes before it.
                    val target = param.args.lastOrNull { packageState.isInstance(it) } ?: return@hook
                    if (getPackageName.invoke(target) == pack) param.result = false
                })
            }
    }

    private fun Member.hook(
        before: ((XC_MethodHook.MethodHookParam) -> Unit)? = null,
        after: ((XC_MethodHook.MethodHookParam) -> Unit)? = null,
    ) {
        XposedBridge.hookMethod(this, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                before?.let { runCatching { it(param) } }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                after?.let { runCatching { it(param) } }
            }
        })
    }

    private companion object {
        /**
         * This app's own id, to skip theming its own icons.
         *
         * From BuildConfig rather than written out: these hooks run inside other apps, so there is
         * no context to ask, and a literal here is a copy of the application id that a rename
         * leaves silently stale -- which is exactly what happened to it once already.
         */
        val OWN_PACKAGE: String = com.elitedarkkaiser.redmagic.BuildConfig.APPLICATION_ID

        /** Resource ids start with 0x7f, so these two cannot be mistaken for real ones. */
        const val IN_PACK = 0x6f000000
        const val NOT_IN_PACK = 0x6e000000
        const val ANDROID_DEFAULT = 0x7f000000
        const val HIGH_BYTE_MASK = 0xff000000.toInt()
        const val LOW_BITS_MASK = 0x00ffffff

        val MONO_INSET =
            (1f - 1f / (1f + 2f * AdaptiveIconDrawable.getExtraInsetFraction())) / 2f

        fun Int.hasHighByte(highByte: Int) = this and HIGH_BYTE_MASK == highByte

        fun Int.withHighByte(highByte: Int) = this and LOW_BITS_MASK or highByte

        fun Int.lowBits() = this and LOW_BITS_MASK

        @Volatile
        var appContext: Context? = null

        /** Whether this process has been hooked yet. See [handleLoadPackage]. */
        @Volatile
        var hookedApp = false

        /** Whether [prepare] has been kicked off in this process. */
        @Volatile
        var prepared = false

        @Volatile
        var state: IconPackConfig.State? = null

        @Volatile
        var source: IconPackSource? = null

        val busy: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

        val pendingMonochrome: ThreadLocal<ArrayDeque<Boolean>> =
            ThreadLocal.withInitial { ArrayDeque() }
    }
}
