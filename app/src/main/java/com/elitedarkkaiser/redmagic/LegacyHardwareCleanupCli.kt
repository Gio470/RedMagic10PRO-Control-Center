package com.elitedarkkaiser.redmagic

import android.os.IBinder

/** Clears a legacy GameAssist override as root. The only rate this helper can send is zero. */
object LegacyHardwareCleanupCli {
    @JvmStatic fun main(args: Array<String>) {
        runCatching {
            require(args.size == 1 && Regex("""[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+""").matches(args[0]))
            val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, "ZteScreenRefreshRate") as? IBinder
            if (binder != null) {
                val descriptor = binder.interfaceDescriptor ?: error("Missing vendor descriptor")
                val proxy = Class.forName(descriptor + "\$Stub").getMethod("asInterface", IBinder::class.java)
                    .invoke(null, binder) ?: error("Installed vendor proxy is unavailable")
                val method = Class.forName(descriptor).getMethod("setRefreshRateByGameAssist",
                    String::class.java, String::class.java, Integer.TYPE).apply { isAccessible = true }
                val result = method.invoke(proxy, "GameAssist", args[0], 0)
                if (method.returnType == java.lang.Boolean.TYPE) check(result == true)
            }
            println("LEGACY_NATIVE_CLEARED")
        }.onFailure { println("LEGACY_CLEANUP_ERROR ${it.cause?.message ?: it.message}") }
        kotlin.system.exitProcess(0)
    }
}
