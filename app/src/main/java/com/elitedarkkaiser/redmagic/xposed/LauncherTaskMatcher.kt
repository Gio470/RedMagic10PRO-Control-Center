package com.elitedarkkaiser.redmagic.xposed

import android.content.ComponentName
import android.content.Intent
import java.util.IdentityHashMap

/** Recognizes HOME tasks, without hiding the launcher's settings or unrelated grouped apps. */
object LauncherTaskMatcher {
    fun matches(task: Any?, selectedPackage: String, selectedComponent: String = ""): Boolean {
        val pkg = SoftwareControlsConfig.validLauncher(selectedPackage)
        if (task == null || pkg.isEmpty()) return false
        val home = ComponentName.unflattenFromString(selectedComponent)?.takeIf { it.packageName == pkg }
        val visited = IdentityHashMap<Any, Boolean>()
        fun classify(value: Any?, depth: Int): List<Boolean> {
            if (value == null || visited.put(value, true) != null) return emptyList()
            if (depth > 4) return listOf(false)
            if (value is Iterable<*>) return value.flatMap { classify(it, depth + 1) }
            if (value.javaClass.isArray && value is Array<*>) return value.flatMap { classify(it, depth + 1) }
            val intent = if (value is Intent) value else
                field(value, "baseIntent") as? Intent ?: field(value, "intent") as? Intent
            // OEM gesture tasks often omit HOME categories, or expose only TaskInfo components.
            val components = listOfNotNull(intent?.component) +
                listOf("baseActivity", "topActivity", "origActivity", "realActivity")
                    .mapNotNull { field(value, it) as? ComponentName }
            if (components.isNotEmpty() || intent != null) {
                val belongs = components.any { it.packageName == pkg } || intent?.`package` == pkg
                val isHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true ||
                    (home != null && home in components) || method(value, "getActivityType") == 2
                // Without an Intent, older ROM TaskInfo wrappers only identify the package.
                return listOf(belongs && (isHome || (intent == null && home == null)))
            }
            val children = listOf("getTasks", "getTaskInfo1", "getTaskInfo2", "getFirstTask", "E")
                .mapNotNull { method(value, it) } +
                listOf("tasks", "mTasks", "taskInfo", "taskInfo1", "taskInfo2", "mTaskInfo1", "mTaskInfo2", "task1", "task2", "key")
                    .mapNotNull { field(value, it) }
            return if (children.isEmpty()) listOf(false) else children.flatMap { classify(it, depth + 1) }
        }
        val leaves = classify(task, 0)
        return leaves.isNotEmpty() && leaves.all { it }
    }

    internal fun field(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val current = type
            val result = runCatching { current.getDeclaredField(name).apply { isAccessible = true }.get(target) }
            if (result.isSuccess) return result.getOrNull()
            type = current.superclass
        }
        return null
    }

    internal fun method(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val current = type
            val result = runCatching { current.getDeclaredMethod(name).apply { isAccessible = true }.invoke(target) }
            if (result.isSuccess) return result.getOrNull()
            type = current.superclass
        }
        return null
    }

    /** Copy only when a launcher card is removed; never mutate the launcher's cached list. */
    fun filter(tasks: List<*>, state: SoftwareControlsConfig.LauncherState): List<*> {
        if (!state.active) return tasks
        val retained = tasks.filterNot { matches(it, state.packageName, state.component) }
        return if (retained.size == tasks.size) tasks else java.util.ArrayList(retained)
    }
}
