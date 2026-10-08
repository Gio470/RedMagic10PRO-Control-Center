package com.elitedarkkaiser.redmagic.systemtheme

import android.os.Build
import java.io.File
import java.lang.reflect.Method

/**
 * Registers and enables fabricated runtime resource overlays, from inside a root process.
 *
 * ## Why this is a `main` and not a class the app calls
 *
 * Overriding a system colour resource means talking to `IOverlayManager`, and every piece of that
 * conversation -- `FabricatedOverlay.Builder`, `OverlayManagerTransaction`, the service handle
 * itself -- is hidden platform API that a normal app is both unprivileged for and blocked from
 * reflecting into. ColorBlendr solves this with libsu's RootService: a second copy of the app's
 * code launched as root through `app_process`, bound over AIDL.
 *
 * This is the same trick with the binding taken out. The app writes a spec file, runs
 *
 *     su -c "CLASSPATH=<apk> app_process /system/bin <this class> apply <spec>"
 *
 * and reads what it prints. The process runs as root, so the permission checks pass; it was not
 * started by the zygote, so the hidden-API policy that would block the reflection is never applied
 * to it.
 *
 * ## Register, then enable
 *
 * These are two transactions, and the first without the second is the shape of bug that looks
 * exactly like the feature not existing: a registered overlay is a file on disk that nothing is
 * reading. `setEnabled` is what puts it in front of the resources it targets, and it needs the
 * overlay's identifier -- owner package plus name -- rather than the overlay itself.
 *
 * The owner is `android`, which is not a decorative choice: the overlay manager validates that an
 * overlay targeting the framework is owned by it, and an overlay owned by anything else is
 * accepted and then ignored.
 *
 * ## The spec file
 *
 * One entry per line, `targetPackage|overlayName|resourceName|#AARRGGBB`. Lines are grouped by
 * (target, name) into one overlay each, because a fabricated overlay is registered whole: register
 * it twice and the second registration replaces the first rather than adding to it.
 *
 * Every step prints what it did. Theming a ROM means asking it to accept something it may refuse,
 * and the difference between "this does nothing" and a named exception from a named method is the
 * whole of whether the feature can be debugged at all.
 */
object OverlayCli {

    private const val OK = "OK"
    private const val FAIL = "FAIL"

    /**
     * Who the overlays belong to.
     *
     * `android` -- the framework itself -- because that is the package being overlaid, and
     * OverlayManagerService will not honour a framework overlay owned by anything else. This app
     * is not a valid owner however much it would tidy things up: an overlay owned by an app also
     * dies with that app's data, and a theme that vanishes on an app update is not a theme.
     */
    const val OWNER = "android"

    /** TYPE_INT_COLOR_ARGB8 from android.util.TypedValue. */
    private const val TYPE_INT_COLOR_ARGB8 = 0x1c

    private val log = StringBuilder()

    private fun step(message: String) {
        log.append("· ").append(message).append('\n')
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val result = try {
            when (args.getOrNull(0)) {
                "apply" -> apply(File(args[1]))
                "remove" -> remove(args.drop(1))
                "diagnose" -> diagnose()
                else -> "$FAIL unknown command ${args.getOrNull(0)}"
            }
        } catch (t: Throwable) {
            "$FAIL ${t.javaClass.name}: ${t.message}"
        }
        print(log)
        println(result)
        // Without this the process hangs: binder threads started by the reflection above are not
        // daemons, and a root process left running holds the su session open with it.
        System.exit(0)
    }

    // ---- commands -----------------------------------------------------------------------------

    private fun apply(spec: File): String {
        if (!spec.isFile) return "$FAIL spec file missing: ${spec.absolutePath}"

        val overlays = LinkedHashMap<Pair<String, String>, MutableList<Pair<String, Int>>>()
        spec.forEachLine { line ->
            val parts = line.split('|')
            if (parts.size != 4) return@forEachLine
            val color = parseArgb(parts[3]) ?: return@forEachLine
            overlays.getOrPut(parts[0] to parts[1]) { mutableListOf() }.add(parts[2] to color)
        }
        if (overlays.isEmpty()) return "$FAIL spec file had no usable entries"
        step("read ${overlays.values.sumOf { it.size }} resources in ${overlays.size} overlay(s)")

        val register = TransactionBuilder()
        overlays.forEach { (key, entries) ->
            register.register(buildOverlay(key.first, key.second, entries))
        }
        register.commit()
        step("registered")

        val enable = TransactionBuilder()
        overlays.keys.forEach { (_, name) -> enable.setEnabled(name, true) }
        enable.commit()
        step("enabled")

        return "$OK ${overlays.values.sumOf { it.size }} resources in ${overlays.size} overlay(s)"
    }

    private fun remove(names: List<String>): String {
        if (names.isEmpty()) return "$FAIL nothing to remove"
        // Disabling first: unregistering an enabled overlay leaves some versions holding a
        // reference to a file that is no longer there, and the resources stay overridden.
        runCatching {
            val disable = TransactionBuilder()
            names.forEach { disable.setEnabled(it, false) }
            disable.commit()
            step("disabled")
        }.onFailure { step("disable failed (continuing): ${it.javaClass.simpleName}") }

        val transaction = TransactionBuilder()
        names.forEach { transaction.unregister(it) }
        transaction.commit()
        return "$OK removed ${names.size} overlay(s)"
    }

    /** Reports what of the hidden API is actually present on this ROM, and nothing else. */
    private fun diagnose(): String {
        listOf(
            "android.content.om.FabricatedOverlay",
            "android.content.om.FabricatedOverlay\$Builder",
            "android.content.om.OverlayManagerTransaction",
            "android.content.om.OverlayManagerTransaction\$Builder",
            "android.content.om.OverlayIdentifier",
            "android.content.om.IOverlayManager\$Stub"
        ).forEach { name ->
            step(
                runCatching { Class.forName(name) }
                    .fold({ "found $name" }, { "MISSING $name" })
            )
        }
        step(
            runCatching { service() }
                .fold({ "overlay service reachable" }, { "overlay service: ${it.message}" })
        )
        step("uid=" + android.os.Process.myUid() + " sdk=" + Build.VERSION.SDK_INT)
        return "$OK diagnose"
    }

    // ---- reflection ---------------------------------------------------------------------------

    private val builderClass by lazy { Class.forName("android.content.om.FabricatedOverlay\$Builder") }
    private val overlayClass by lazy { Class.forName("android.content.om.FabricatedOverlay") }
    private val transactionClass by lazy { Class.forName("android.content.om.OverlayManagerTransaction") }
    private val transactionBuilderClass by lazy {
        Class.forName("android.content.om.OverlayManagerTransaction\$Builder")
    }
    private val identifierClass by lazy { Class.forName("android.content.om.OverlayIdentifier") }

    private fun buildOverlay(target: String, name: String, entries: List<Pair<String, Int>>): Any {
        val builder = builderClass
            .getConstructor(String::class.java, String::class.java, String::class.java)
            .newInstance(OWNER, name, target)

        // Android 14 added a configuration argument and kept the old signature, but not on every
        // vendor build -- so take whichever is actually there rather than deciding from SDK_INT.
        val withConfig: Method? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                builderClass.getMethod(
                    "setResourceValue",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java
                )
            }.getOrNull()
        } else {
            null
        }
        val plain: Method? = runCatching {
            builderClass.getMethod(
                "setResourceValue",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
        }.getOrNull()
        if (withConfig == null && plain == null) {
            throw NoSuchMethodException("FabricatedOverlay.Builder.setResourceValue")
        }
        step("setResourceValue: " + if (withConfig != null) "4-arg" else "3-arg")

        entries.forEach { (resource, color) ->
            // The overlay manager wants fully qualified names. Upstream formats them the same way
            // (FabricatedOverlayResource.formatName) and it is not optional -- a bare name is
            // accepted and then matches nothing.
            val qualified = if (resource.contains(':')) resource else "$target:color/$resource"
            if (withConfig != null) {
                withConfig.invoke(builder, qualified, TYPE_INT_COLOR_ARGB8, color, null)
            } else {
                plain!!.invoke(builder, qualified, TYPE_INT_COLOR_ARGB8, color)
            }
        }

        return builderClass.getMethod("build").invoke(builder)!!
    }

    private fun identifier(name: String): Any =
        identifierClass
            .getConstructor(String::class.java, String::class.java)
            .newInstance(OWNER, name)

    private fun service(): Any {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "overlay")
            ?: throw IllegalStateException("no overlay service in ServiceManager")
        return Class.forName("android.content.om.IOverlayManager\$Stub")
            .getMethod("asInterface", Class.forName("android.os.IBinder"))
            .invoke(null, binder)!!
    }

    private class TransactionBuilder {
        private val builder: Any = transactionBuilderClass.getConstructor().newInstance()

        fun register(overlay: Any) {
            transactionBuilderClass
                .getMethod("registerFabricatedOverlay", overlayClass)
                .invoke(builder, overlay)
        }

        fun unregister(name: String) {
            transactionBuilderClass
                .getMethod("unregisterFabricatedOverlay", identifierClass)
                .invoke(builder, identifier(name))
        }

        /**
         * User 0 only. Upstream walks every profile of the current user; this is a phone with one
         * user on it, and enumerating profiles means another two hidden services to reflect into
         * for a case that does not arise here.
         */
        fun setEnabled(name: String, enabled: Boolean) {
            transactionBuilderClass
                .getMethod(
                    "setEnabled",
                    identifierClass,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                .invoke(builder, identifier(name), enabled, 0)
        }

        fun commit() {
            val transaction = transactionBuilderClass.getMethod("build").invoke(builder)
            val manager = service()
            manager.javaClass
                .getMethod("commit", transactionClass)
                .invoke(manager, transaction)
        }
    }

    /** `#AARRGGBB` without pulling android.graphics.Color into a process that has no resources. */
    private fun parseArgb(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (hex.length != 8) return null
        return runCatching { hex.toLong(16).toInt() }.getOrNull()
    }
}
