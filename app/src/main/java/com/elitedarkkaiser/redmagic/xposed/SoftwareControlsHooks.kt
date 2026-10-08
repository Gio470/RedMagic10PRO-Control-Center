package com.elitedarkkaiser.redmagic.xposed

import android.content.Context
import android.media.AudioManager
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.function.Consumer

/** Feature behaviour reference: https://github.com/XPRAMT/RedMagicX (independently implemented). */
class SoftwareControlsHooks : IXposedHookLoadPackage {
    private val volume = SoftwareControlsConfig.VolumeReader()
    private val settingVolume = ThreadLocal<Boolean>()

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            "android" -> {
                install("volume step") { hookVolume(lpparam.classLoader) }
                install("system recents") { hookSystemRecents(lpparam.classLoader) }
            }
            SoftwareControlsConfig.STOCK_LAUNCHER -> {
                install("launcher gesture task") { hookGestureTask(lpparam.classLoader) }
                install("launcher recent list") { hookRecentList(lpparam.classLoader) }
            }
        }
    }

    private fun install(name: String, action: () -> Unit) {
        runCatching(action).onSuccess {
            XposedBridge.log("[RedMagic Control] $name installed")
        }.onFailure { XposedBridge.log("[RedMagic Control] $name unavailable: $it") }
    }

    private fun hookVolume(loader: ClassLoader) {
        val service = loader.loadClass("com.android.server.audio.AudioService")
        // Suggested-volume routing chooses media vs ring/call first. Hook the resolved stream,
        // not the suggested method, whose first two arguments are in the opposite order.
        val methods = service.declaredMethods.filter {
            it.name == "adjustStreamVolume" && it.returnType == Void.TYPE &&
                it.parameterTypes.size >= 3 && it.parameterTypes.take(3).all { type -> type == Int::class.javaPrimitiveType }
        }
        check(methods.isNotEmpty()) { "No supported AudioService volume method" }
        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (settingVolume.get() == true) return
                val state = volume.state()
                if (!state.enabled || state.step == 1) return
                val args = param.args ?: return
                val stream = args.getOrNull(0) as? Int ?: return
                val direction = args.getOrNull(1) as? Int ?: return
                if (stream != AudioManager.STREAM_MUSIC || direction !in listOf(-1, 1)) return
                val context = LauncherTaskMatcher.field(param.thisObject ?: return, "mContext") as? Context ?: return
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                settingVolume.set(true)
                try {
                    val target = state.target(stream, direction, audio.getStreamVolume(stream),
                        audio.getStreamMinVolume(stream), audio.getStreamMaxVolume(stream)) ?: return
                    audio.setStreamVolume(stream, target, args.getOrNull(2) as? Int ?: 0)
                    param.result = null
                } catch (_: Throwable) {
                    // Let the original adjust run when the ROM refuses the custom write.
                } finally {
                    settingVolume.remove()
                }
            }
        }
        methods.forEach { XposedBridge.hookMethod(it, callback) }
    }

    private fun hookGestureTask(loader: ClassLoader) {
        val view = loader.loadClass("com.android.quickstep.views.RecentsView")
        val methods = view.declaredMethods.filter {
            it.name == "onGestureAnimationStart" && it.parameterTypes.isNotEmpty() && !it.parameterTypes[0].isPrimitive
        }
        check(methods.isNotEmpty()) { "Gesture task method not found" }
        methods.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    runCatching {
                        val context = launcherContext(param.thisObject) ?: return
                        val state = SoftwareControlsConfig.launcherState(context)
                        if (state.active && LauncherTaskMatcher.matches(param.args?.getOrNull(0), state.packageName, state.component)) {
                            // A null running task leaves the gesture animation intact but prevents
                            // the selected HOME from being inserted as a synthetic app card.
                            param.args[0] = null
                        }
                    }
                }
            })
        }
    }

    private fun hookRecentList(loader: ClassLoader) {
        val list = loader.loadClass("com.android.quickstep.RecentTasksList")
        val methods = list.declaredMethods.filter { it.name in listOf("getTasks", "getTaskKeys", "loadTasksInBackground") }
        check(methods.isNotEmpty()) { "Recent list method not found" }
        methods.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val args = param.args ?: return
                    for (index in args.indices) {
                        if (!method.parameterTypes[index].isAssignableFrom(Consumer::class.java)) continue
                        @Suppress("UNCHECKED_CAST")
                        val original = args[index] as? Consumer<Any?> ?: continue
                        // getTasks may deliver a cached result asynchronously without loading tasks.
                        // Read settings at delivery time, so switching the control off stays live.
                        args[index] = Consumer<Any?> { result ->
                            val filtered = runCatching {
                                val context = launcherContext(param.thisObject)
                                if (context != null && result is List<*>) {
                                    LauncherTaskMatcher.filter(result, SoftwareControlsConfig.launcherState(context))
                                } else result
                            }.getOrDefault(result)
                            original.accept(filtered)
                        }
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    runCatching {
                        val context = launcherContext(param.thisObject) ?: return
                        val state = SoftwareControlsConfig.launcherState(context)
                        replaceRecentResult(param, state, method.returnType)
                    }
                }
            })
        }
    }

    private fun hookSystemRecents(loader: ClassLoader) {
        var installed = 0
        for (name in listOf("com.android.server.wm.ActivityTaskManagerService", "com.android.server.wm.RecentTasks")) {
            val type = runCatching { loader.loadClass(name) }.getOrNull() ?: continue
            type.declaredMethods.filter { it.name in listOf("getRecentTasks", "getRecentTasksImpl") }
                .forEach { method ->
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            runCatching {
                                val context = launcherContext(param.thisObject) ?: return
                                replaceRecentResult(param, SoftwareControlsConfig.launcherState(context), method.returnType)
                            }
                        }
                    })
                    installed++
                }
        }
        check(installed > 0) { "No supported system recent-task method" }
    }

    private fun replaceRecentResult(param: XC_MethodHook.MethodHookParam,
        state: SoftwareControlsConfig.LauncherState, returnType: Class<*>) {
        if (!state.active) return
        val original = param.result ?: return
        if (original is List<*>) {
            val filtered = LauncherTaskMatcher.filter(original, state)
            if (filtered !== original && returnType.isInstance(filtered)) param.result = filtered
        } else {
            // Framework Binder APIs use ParceledListSlice rather than a raw List.
            val tasks = LauncherTaskMatcher.method(original, "getList") as? List<*> ?: return
            val filtered = LauncherTaskMatcher.filter(tasks, state)
            if (filtered !== tasks) {
                val replacement = original.javaClass.getConstructor(List::class.java).newInstance(filtered)
                if (returnType.isInstance(replacement)) param.result = replacement
            }
        }
    }

    private fun launcherContext(target: Any?): Context? =
        (target as? View)?.context ?: (target as? Context) ?:
            target?.let { LauncherTaskMatcher.field(it, "mContext") as? Context } ?:
            runCatching {
                val thread = Class.forName("android.app.ActivityThread")
                (thread.getDeclaredMethod("currentApplication").apply { isAccessible = true }
                    .invoke(null) as? Context) ?: thread.getDeclaredMethod("currentActivityThread")
                    .apply { isAccessible = true }.invoke(null)?.let {
                        LauncherTaskMatcher.method(it, "getSystemContext") as? Context
                    }
            }.getOrNull()
}
