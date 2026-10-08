package com.elitedarkkaiser.redmagic.gametrigger

import android.content.Context
import com.elitedarkkaiser.redmagic.RootShell

/** Transaction IDs belong to the installed framework, not to a particular phone generation. */
object NativeTgkAbi {
    private data class Binding(
        val name: String,
        val parameters: List<String>,
        val aliases: List<String> = listOf(name),
        val result: String = "void",
        val required: Boolean = true
    )
    // InputManager's public wrappers and IInputManager's Binder methods have different names.
    // Keep the bridge's wrapper names as keys, but resolve the actual AIDL name AND signature
    // before reading its transaction field. Numbers are never borrowed from another firmware.
    private val bindings = listOf(
        Binding("setTgkPoint", listOf("[I", "[I", "int")),
        Binding("setTgkMode", listOf("int", "int")),
        Binding("setTgkVersion", listOf("int")),
        Binding("enableTgkDrive", listOf("boolean")),
        Binding("setConsumeTgkKey", listOf("boolean")),
        Binding("setTouchHapticFeedbackEnable", listOf("boolean")),
        Binding("setLeftTgkEnable", listOf("boolean"), listOf("setLeftGameKeyEnable", "setLeftTgkEnable")),
        Binding("setRightTgkEnable", listOf("boolean"), listOf("setRightGameKeyEnable", "setRightTgkEnable")),
        Binding("setGameKeyEnable", listOf("boolean"), listOf("setGlobalKeyEnable", "setGameKeyEnable")),
        Binding("isGameKeyEnable", emptyList(), listOf("isGlobalKeyEnable", "isGameKeyEnable"), "boolean"),
        Binding("isLeftGameKeyEnable", emptyList(), result = "boolean"),
        Binding("isRightGameKeyEnable", emptyList(), result = "boolean"),
        Binding("setTgkRapidFireCount", listOf("int", "int"), required = false),
        Binding("setTgkTopEffectEnable", listOf("boolean"), required = false),
        Binding("setTgkCenterEffectEnable", listOf("boolean"), required = false),
        Binding("setTgkTransparency", listOf("int"), required = false),
        Binding("isTouchHapticFeedbackEnable", emptyList(), result = "boolean", required = false)
    )
    private val required = bindings.filter { it.required }.map { it.name }.toSet()
    @Volatile private var cached: Map<String, Int>? = null
    @Volatile var diagnostics: String = "Native trigger API has not been checked"
        private set

    fun discover(): Map<String, Int> = discover(
        Class.forName("android.hardware.input.IInputManager"),
        Class.forName("android.hardware.input.IInputManager\$Stub")
    )

    internal fun discover(api: Class<*>, stub: Class<*>): Map<String, Int> {
        val methods = api.declaredMethods
        val missing = mutableListOf<String>()
        val transactions = linkedMapOf<String, Int>()
        bindings.forEach { binding ->
            val id = binding.aliases.firstNotNullOfOrNull { name ->
                val matches = methods.any { it.name == name &&
                    it.parameterTypes.map { type -> type.name } == binding.parameters &&
                    it.returnType.name == binding.result }
                if (!matches) null else runCatching {
                    stub.getDeclaredField("TRANSACTION_$name").apply { isAccessible = true }
                        .getInt(null).takeIf { it > 0 }
                }.getOrNull()
            }
            if (id != null) transactions[binding.name] = id
            else if (binding.required) missing += binding.aliases.joinToString("/")
        }
        check(missing.isEmpty()) { "Missing or incompatible Binder methods: ${missing.joinToString(", ")}" }
        return transactions
    }

    @Synchronized fun transactions(context: Context): Map<String, Int> {
        cached?.let { return it }
        val direct = runCatching { discover() }
        // app_process uses the same hidden-API access as the app's existing root theme helper.
        val result = direct.getOrNull() ?: run {
            val apk = context.applicationInfo.sourceDir.replace("'", "'\\''")
            val output = RootShell.execForOutput("CLASSPATH='$apk' /system/bin/app_process /system/bin " +
                "com.elitedarkkaiser.redmagic.gametrigger.NativeTgkAbiCli").orEmpty()
            diagnostics = "App probe: ${direct.exceptionOrNull()?.message}\nRoot probe: " +
                output.take(12_000).ifBlank { "No reply. Check root access." }
            val parsed = parse(output)
            check(parsed.keys.containsAll(required)) {
                "Could not resolve native trigger API. Root probe: " +
                    (output.lineSequence().firstOrNull { it.startsWith("TGK_UNAVAILABLE ") }
                        ?.removePrefix("TGK_UNAVAILABLE ") ?: output.take(500).ifBlank { "No reply; check root access" })
            }
            parsed
        }
        diagnostics = "Installed firmware transactions: " + result.entries.joinToString("; ") { "${it.key}=${it.value}" }
        cached = result
        return result
    }

    internal fun describe(): String = Class.forName("android.hardware.input.IInputManager").declaredMethods
        .filter { method -> bindings.any { method.name in it.aliases } ||
            method.name.contains("tgk", true) || method.name.contains("gamekey", true) ||
            method.name == "setKeyTouchPoint" }
        .sortedBy { it.name }
        .joinToString("; ") { "${it.name}(${it.parameterTypes.joinToString(",") { type -> type.name }}):${it.returnType.name}" }
        .ifBlank { "No visible vendor trigger methods" }

    internal fun parse(output: String): Map<String, Int> = output.lineSequence()
        .firstOrNull { it.startsWith("TGK_ABI ") }?.removePrefix("TGK_ABI ")
        ?.split(';')?.mapNotNull {
            val pair = it.split('=', limit = 2)
            val number = pair.getOrNull(1)?.toIntOrNull()?.takeIf { n -> n > 0 }
            if (pair.size == 2 && number != null) pair[0] to number else null
        }?.toMap().orEmpty()
}

object NativeTgkAbiCli {
    @JvmStatic fun main(args: Array<String>) {
        runCatching { NativeTgkAbi.discover() }
            .onSuccess { println("TGK_ABI " + it.entries.joinToString(";") { entry -> "${entry.key}=${entry.value}" }) }
            .onFailure {
                println("TGK_UNAVAILABLE ${it.message}")
                println("TGK_METHODS " + runCatching { NativeTgkAbi.describe() }.getOrElse { error -> error.toString() })
            }
        kotlin.system.exitProcess(0)
    }
}

/** Reject exceptions, unknown transactions and non-Boolean replies instead of treating them as OFF. */
object TgkParcelReply {
    private val header = Regex("""Parcel\(\s*(?:0x[0-9a-fA-F]+:\s*)?([0-9a-fA-F]{8})(?:\s+([0-9a-fA-F]{8}))?""")
    fun boolean(output: String): Boolean {
        val match = header.find(output) ?: error("TGK getter returned no Boolean parcel")
        check(match.groupValues[1] == "00000000") { "TGK getter was rejected: $output" }
        return when (match.groupValues[2]) {
            "00000000" -> false
            "00000001" -> true
            else -> error("TGK getter returned an invalid Boolean: $output")
        }
    }
    fun checkSetter(output: String) {
        check(!output.contains("Exception", true) && !output.contains("Permission Denial", true) &&
            (output.contains("Parcel(NULL)") || header.find(output)?.groupValues?.get(1) == "00000000")) {
            "TGK setter was rejected: $output"
        }
    }
}
