package com.elitedarkkaiser.redmagic.xposed

/**
 * Whether the Xposed module is actually running, in one place.
 *
 * Two screens need this answer and they must not disagree: the Home tab's compatibility checklist
 * reports it, and the Software tab refuses to let anything that depends on it be switched on. A
 * second opinion between those two is how a user ends up with a control that says it is on and a
 * ROM that never heard about it.
 *
 * "Running" means the module's hooks reported in on *this* boot -- see [WindowModConfig.KEY_BOOT_ID]
 * for why the report alone is not enough.
 */
object ModuleStatus {

    data class Summary(
        /** Hooks reported in this boot. */
        val loaded: Boolean,
        /** Loaded, and every hook installed. Anything that needs the module needs this. */
        val healthy: Boolean,
        /** One line for a checklist subtitle. */
        val detail: String,
        /** Which hooks failed, if any, for the longer explanation on the Software tab. */
        val failed: Map<String, String>
    )

    /** Blocking: reads a file over root. */
    fun read(): Summary {
        val status = WindowModSettings.readModuleStatus()
        val hooks = status.orEmpty().filterKeys { it.startsWith("hook.") }
        val failed = hooks.filterValues { it != "ok" }
        val loaded = status != null && hooks.isNotEmpty()
        val healthy = loaded && failed.isEmpty()

        return Summary(
            loaded = loaded,
            healthy = healthy,
            detail = when {
                !loaded -> "Not loaded — enable it in LSPosed and reboot"
                !healthy -> "Loaded, but ${failed.size} hook(s) failed"
                else -> "Active"
            },
            failed = failed.mapKeys { it.key.removePrefix("hook.") }
        )
    }
}
