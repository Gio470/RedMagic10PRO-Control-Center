package com.elitedarkkaiser.redmagic.xposed

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Finds how many floating windows are currently open.
 *
 * The ROM does not expose this. `isReachWrMaxSizeForMulti()` compares the count to a built-in
 * maximum internally and returns only the verdict, so enforcing a *chosen* maximum means locating
 * whatever it compares. Neither FixRedMagicWindow nor WooBoxForRedmagicOS -- the module this was
 * ported from and the one it was ported from in turn -- ever needed it: both only force the verdict
 * to false, which removes the cap entirely.
 *
 * So it is searched for by shape rather than by a hard-coded name that would differ per ROM build:
 * an int getter, an int field, or a collection field, whose name mentions the ROM's "WR" /
 * "windowReply" vocabulary. Reads only -- no-argument getters and field reads cannot change state,
 * which matters when probing a live system_server.
 *
 * [describe] reports what was found, and [dump] lists what was there to find, so a ROM this misses
 * leaves something actionable in the app rather than a silently ignored setting.
 */
internal object WindowLimitCounter {

    private val NAME_HINT = Regex("(?i)(wr|windowreply)")
    private val COUNT_HINT = Regex("(?i)(size|count|num)")

    private val PROBE_CLASSES = listOf(
        "com.android.server.wm.ActivityTaskManagerService",
        "com.android.server.wm.TaskDisplayAreaMifavor",
        "com.android.server.wm.TaskMifavor",
        "android.app.WindowReplyUtils"
    )

    class Counter(private val describe: String, private val read: (Any) -> Int?) {
        override fun toString() = describe
        fun read(target: Any): Int? = runCatching { read.invoke(target) }.getOrNull()
    }

    /** Best-effort counter for [owner], or null when nothing on it looks like one. */
    fun find(owner: Class<*>): Counter? {
        methodCounter(owner)?.let { return it }
        fieldCounter(owner)?.let { return it }
        return null
    }

    private fun methodCounter(owner: Class<*>): Counter? {
        val method: Method = owner.declaredMethods.firstOrNull {
            it.parameterCount == 0 &&
                (it.returnType == Int::class.javaPrimitiveType || it.returnType == Integer::class.java) &&
                NAME_HINT.containsMatchIn(it.name) && COUNT_HINT.containsMatchIn(it.name)
        } ?: return null
        method.isAccessible = true
        return Counter("method ${method.name}()") { target -> method.invoke(target) as? Int }
    }

    private fun fieldCounter(owner: Class<*>): Counter? {
        val fields = owner.declaredFields.filter { NAME_HINT.containsMatchIn(it.name) }

        fields.firstOrNull {
            (it.type == Int::class.javaPrimitiveType || it.type == Integer::class.java) &&
                COUNT_HINT.containsMatchIn(it.name)
        }?.let { field: Field ->
            field.isAccessible = true
            return Counter("field ${field.name}") { target -> field.get(target) as? Int }
        }

        fields.firstOrNull { Collection::class.java.isAssignableFrom(it.type) }?.let { field ->
            field.isAccessible = true
            return Counter("collection ${field.name}.size") { target ->
                (field.get(target) as? Collection<*>)?.size
            }
        }

        return null
    }

    /**
     * The ROM's floating-window surface, for reporting back when [find] comes up empty. Bounded,
     * since it ends up in a status file and then on screen.
     */
    fun dump(cl: ClassLoader): String {
        val parts = mutableListOf<String>()
        PROBE_CLASSES.forEach { name ->
            val klass = runCatching { cl.loadClass(name) }.getOrNull() ?: return@forEach
            val short = name.substringAfterLast('.')
            klass.declaredMethods
                .filter { NAME_HINT.containsMatchIn(it.name) }
                .take(24)
                .forEach { m ->
                    parts += "$short.${m.name}(${m.parameterTypes.joinToString(",") {
                        it.simpleName
                    }}):${m.returnType.simpleName}"
                }
            klass.declaredFields
                .filter { NAME_HINT.containsMatchIn(it.name) }
                .take(24)
                .forEach { f -> parts += "$short#${f.name}:${f.type.simpleName}" }
        }
        return if (parts.isEmpty()) "none" else parts.joinToString(" ").take(3000)
    }
}
