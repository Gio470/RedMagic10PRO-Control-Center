package com.elitedarkkaiser.redmagic

import kotlin.math.abs
import kotlin.math.round
import java.util.concurrent.ConcurrentHashMap

object HardwareController {

    private val recentHardwareWrites = ConcurrentHashMap<String, Long>()
    private const val DUPLICATE_WRITE_SKIP_MS = 2_000L

    /**
     * The last few hundred hardware writes, kept in memory so they can be read back from inside the
     * app -- Settings has a button that copies this out.
     *
     * logcat would do the same job, but only for someone with adb or a terminal on the phone. This
     * needs no tooling at all: reproduce whatever the hardware is doing, tap the button, paste.
     */
    private val writeLog = ArrayDeque<String>()
    private const val WRITE_LOG_MAX = 400

    private fun note(line: String) {
        synchronized(writeLog) {
            writeLog.addLast(line)
            while (writeLog.size > WRITE_LOG_MAX) writeLog.removeFirst()
        }
    }

    /** The log as text, oldest first, for the clipboard. */
    fun writeLogSnapshot(): String = synchronized(writeLog) {
        if (writeLog.isEmpty()) "(no hardware writes recorded yet)"
        else writeLog.joinToString("\n")
    }

    fun clearWriteLog() = synchronized(writeLog) { writeLog.clear() }

    private fun stamp(): String =
        java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.ENGLISH)
            .format(java.util.Date())

    /**
     * How many fan-node writes are in flight, and when the last one finished.
     *
     * A status pass reads the fan nodes in one shell and then applies what it read to the switch
     * and the slider. If a write lands while that read is in the air, what comes back is the state
     * from *before* the user's tap -- and applying it moves the control back, which is a second
     * instruction to the hardware and the start of the loop this app has fought twice already.
     *
     * Ordering the write before the refresh (as the fan switch does) only covers the refresh that
     * tap starts. It does nothing about the independent poll that was already running when the tap
     * happened. At the fifteen-second default those almost never overlap; at the half-second floor
     * the tab allows they overlap constantly, which is why this only ever showed up for someone who
     * had turned the interval down.
     *
     * So a reader records when it started and asks [fanStateSettledSince] whether anything moved
     * underneath it. See MainActivity.refreshStatus.
     */
    private val fanWritesInFlight = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile private var lastFanWriteAt = 0L

    /**
     * True when no fan-node write has overlapped a read that began at [readStartedAt], so what that
     * read returned still describes the fan now.
     */
    fun fanStateSettledSince(readStartedAt: Long): Boolean =
        fanWritesInFlight.get() == 0 && lastFanWriteAt < readStartedAt

    /**
     * @param group writes that land on the same node, and so overwrite each other. Repeating a
     *   write is only pointless while nothing else has touched that node since — if something has,
     *   the repeat is what puts the value back, and skipping it leaves whatever overwrote it. The
     *   fan LED save did exactly that: it applied a preset, then the service turned the LED "on"
     *   (which is a green write, there being no separate enable), then re-applied the preset — and
     *   that last write, seeing itself as a duplicate, was dropped, leaving green.
     */
    private fun execHardwareWrite(key: String, command: String, group: String? = null): Boolean {
        val fanNode = group == FAN_NODE_GROUP
        if (fanNode) fanWritesInFlight.incrementAndGet()
        try {
            val now = System.currentTimeMillis()
            val last = recentHardwareWrites[key]
            if (last != null && (now - last) < DUPLICATE_WRITE_SKIP_MS) {
                val line = "${stamp()} SKIP dup   key=$key  from=${callerTrace()}"
                note(line)
                android.util.Log.i("RedmagicWrite", line)
                return true
            }

            // Every write, recorded with what asked for it. Reading the code was not enough to find
            // what keeps switching the fan, so this says so outright.
            val ok = RootShell.exec(command)
            val line = "${stamp()} ${if (ok) "WROTE" else "FAILED"}     key=$key  cmd=[$command]  " +
                "from=${callerTrace()}"
            note(line)
            android.util.Log.i("RedmagicWrite", line)
            if (ok) {
                if (group != null) {
                    recentHardwareWrites.keys.removeAll { it != key && it.startsWith(group) }
                }
                recentHardwareWrites[key] = now
            }
            return ok
        } finally {
            if (fanNode) {
                // Stamped even for a skipped duplicate: a skip means the node was written to this
                // value moments ago, which a read already in the air may still predate.
                lastFanWriteAt = android.os.SystemClock.uptimeMillis()
                fanWritesInFlight.decrementAndGet()
            }
        }
    }

    /**
     * The first few frames of the call stack outside this object -- who asked for a write. Cheap
     * enough at the rate these run, and the only way to tell a tap apart from a service tick when
     * both end up in the same one-line shell command.
     */
    private fun callerTrace(): String =
        Throwable().stackTrace
            .drop(1)
            .filterNot { it.className.endsWith("HardwareController") }
            .take(4)
            .joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}" }

    /** Every fan LED write goes to the one effect node, so they all share a group. */
    private const val FAN_LED_GROUP = "fan_led"

    /**
     * enableFan, setFanLevel and setFanPwm all write fan_enable, and two of them the level node,
     * so each can undo the others -- which is exactly what [execHardwareWrite]'s group is for.
     *
     * Ungrouped, each was deduplicated on its own key: turn the fan off, then set a level, then
     * turn it off again inside the two-second window and that last write was dropped as a
     * "duplicate" of the first, leaving the fan running with the app believing it had stopped it.
     *
     * The prefix is deliberately not "fan": that would also match the fan_led keys and clear a
     * group it has nothing to do with.
     */
    private const val FAN_NODE_GROUP = "fannode"

    private const val FAN_ENABLE = "/sys/kernel/fan/fan_enable"
    private const val FAN_LEVEL = "/sys/kernel/fan/fan_speed_level"
    private const val FAN_PWM = "/sys/kernel/fan/fan_speed_pwm"
    private const val FAN_RPM = "/sys/kernel/fan/fan_speed_count"

    private const val PUMP_ENABLE = "/proc/driver/micropump/enable"
    private const val PUMP_FREQ = "/proc/driver/micropump/freq"
    private const val PUMP_SPEED = "/proc/driver/micropump/speed"

    private const val LED_EFFECT = "/sys/class/leds/aw22xxx_led/effect"
    private const val LED_CFG = "/sys/class/leds/aw22xxx_led/cfg"

    private const val SAR0_MODE = "/sys/class/leds/sar0/mode_operation"
    private const val SAR1_MODE = "/sys/class/leds/sar1/mode_operation"

    private const val HAPTIC_DURATION = "/sys/class/leds/vibrator/duration"
    private const val HAPTIC_GAIN = "/sys/class/leds/vibrator/gain"
    private const val HAPTIC_ACTIVATE = "/sys/class/leds/vibrator/activate"

    fun enableFan(enabled: Boolean): Boolean {
        return execHardwareWrite(
            "fannode_enable:$enabled",
            "echo ${if (enabled) 1 else 0} > $FAN_ENABLE",
            FAN_NODE_GROUP
        )
    }

    fun isFanEnabled(): Boolean {
        return RootShell.execForOutput("cat $FAN_ENABLE")?.trim() == "1"
    }

    fun setFanLevel(level: Int): Boolean {
        val safe = level.coerceIn(0, 5)
        val cmds = if (safe == 0) {
            "echo 0 > $FAN_LEVEL; echo 0 > $FAN_ENABLE"
        } else {
            "echo 1 > $FAN_ENABLE; echo $safe > $FAN_LEVEL"
        }
        return execHardwareWrite("fannode_level:$safe", cmds, FAN_NODE_GROUP)
    }

    fun setFanPwm(value: Int): Boolean {
        val safe = value.coerceIn(0, 255)
        val cmds = if (safe == 0) {
            "echo 0 > $FAN_PWM; echo 0 > $FAN_ENABLE"
        } else {
            "echo 1 > $FAN_ENABLE; echo $safe > $FAN_PWM"
        }
        return execHardwareWrite("fannode_pwm:$safe", cmds, FAN_NODE_GROUP)
    }

    fun readFanRpm(): Int? {
        return RootShell.execForOutput("cat $FAN_RPM")?.trim()?.toIntOrNull()
    }

    fun readFanLevel(): Int? {
        return RootShell.execForOutput("cat $FAN_LEVEL")?.trim()?.toIntOrNull()?.coerceIn(0, 5)
    }

    fun enablePump(enabled: Boolean): Boolean {
        return execHardwareWrite("pump_enable:$enabled", "echo ${if (enabled) 1 else 0} > $PUMP_ENABLE")
    }

    fun setPumpProfile(profile: String): Boolean {
        val cmd = when (profile.lowercase()) {
            "slow" -> "echo 1 > $PUMP_ENABLE; echo 4 > $PUMP_FREQ; echo 40 > $PUMP_SPEED"
            "medium" -> "echo 1 > $PUMP_ENABLE; echo 4 > $PUMP_FREQ; echo 60 > $PUMP_SPEED"
            "quick" -> "echo 1 > $PUMP_ENABLE; echo 4 > $PUMP_FREQ; echo 80 > $PUMP_SPEED"
            "experimental" -> "echo 1 > $PUMP_ENABLE; echo 4 > $PUMP_FREQ; echo 90 > $PUMP_SPEED"
            "off" -> "echo 0 > $PUMP_ENABLE"
            else -> "echo 1 > $PUMP_ENABLE; echo 4 > $PUMP_FREQ; echo 80 > $PUMP_SPEED"
        }
        return execHardwareWrite("led_effect:$cmd", cmd)
    }

    fun readPumpEnabled(): String? = RootShell.execForOutput("cat $PUMP_ENABLE")
    fun readPumpFreq(): String? = RootShell.execForOutput("cat $PUMP_FREQ")
    fun readPumpSpeed(): String? = RootShell.execForOutput("cat $PUMP_SPEED")

    // LED effect encoding on NX789J: OEM LightOldData protocol.
    // Kernel only accepts ≤3-digit hex values; rejects 8-digit zone-prefixed values.
    // Only the FAN zone (aw22xxx_led on a single matrix) is wired up here; on
    // RM 10 Pro the logo + shoulder strip aren't exposed via this chip.
    private const val FAN_LED_OFF_VALUE = "2"
    /**
     * The low nibble is the colour and the rest is the effect, so a colour works under any of them.
     *
     * Colours 0-7 are the solid ones. Watching the effect node while tapping through the stock
     * app's own "presets" showed those to be colours 8-15 under one effect (0x48..0x4f, directly
     * above the solid 0x40..0x47) — so they are colours, not animations, and take any effect. They
     * are not solid ones though: see [FAN_LED_PATTERNS].
     */
    private val FAN_LED_COLOR_CODES = mapOf(
        // color index → (steady, breathe, flashing, flow, burst) hex strings
        1 to LedEffectSet("100", "30", "20", "40", "110"),  // red
        9 to LedEffectSet("101", "31", "21", "41", "111"),  // rose
        3 to LedEffectSet("107", "37", "27", "47", "117"),  // orange
        4 to LedEffectSet("102", "32", "22", "42", "112"),  // yellow
        5 to LedEffectSet("103", "33", "23", "43", "113"),  // green
        6 to LedEffectSet("105", "35", "25", "45", "115"),  // cyan
        7 to LedEffectSet("104", "34", "24", "44", "114"),  // blue
        8 to LedEffectSet("106", "36", "26", "46", "116"),  // purple
        // The upper half. Patterns rather than solids -- see [FAN_LED_PATTERNS]. Note the jump from
        // d to f: the stock row has seven, and the capture of it skipped e rather than missing it.
        101 to LedEffectSet("108", "38", "28", "48", "118"),  // pattern 1
        102 to LedEffectSet("109", "39", "29", "49", "119"),  // pattern 2
        103 to LedEffectSet("10a", "3a", "2a", "4a", "11a"),  // pattern 3
        104 to LedEffectSet("10b", "3b", "2b", "4b", "11b"),  // pattern 4
        105 to LedEffectSet("10c", "3c", "2c", "4c", "11c"),  // pattern 5
        106 to LedEffectSet("10d", "3d", "2d", "4d", "11d"),  // pattern 6
        107 to LedEffectSet("10f", "3f", "2f", "4f", "11f")   // pattern 7
    )

    /**
     * These are not single colours: the fan is a matrix, and each of them lights it in several
     * colours at once.
     *
     * Read off the stock app's own preset row, quarter by quarter, rather than guessed — the
     * bubbles this app used to draw were copied from some other build of that row and matched
     * nothing the fan did. Four palette ids each, in the order they are drawn: top left, top right,
     * bottom left, bottom right. The last three are two colours on the diagonal.
     */
    val FAN_LED_PATTERNS: List<Pair<Int, List<Int>>> = listOf(
        101 to listOf(9, 3, 7, 5),  // pink, orange / blue, green
        102 to listOf(8, 4, 6, 9),  // purple, yellow / cyan, pink
        103 to listOf(8, 6, 7, 9),  // purple, cyan / blue, pink
        104 to listOf(9, 7, 7, 9),  // pink and blue
        105 to listOf(8, 6, 6, 8),  // purple and cyan
        106 to listOf(6, 3, 3, 6),  // cyan and orange
        107 to listOf(4, 5, 5, 4)   // yellow and green
    )

    private data class LedEffectSet(
        val steady: String,
        val breathe: String,
        val flashing: String,
        val flow: String,
        val burst: String
    ) {
        fun pick(effectName: String): String = when (effectName.lowercase()) {
            "breathe" -> breathe
            "flashing" -> flashing
            "flow" -> flow
            "burst" -> burst
            else -> steady
        }
    }

    private fun fanLedValueFor(effectName: String, color: Int): String {
        val set = FAN_LED_COLOR_CODES[color] ?: FAN_LED_COLOR_CODES.getValue(1)
        return set.pick(effectName)
    }

    fun setFanLedEnabled(enabled: Boolean): Boolean {
        return if (enabled) {
            val on = fanLedValueFor("steady", 5)
            execHardwareWrite("fan_led_enabled:true", "echo $on > $LED_EFFECT; echo 1 > $LED_CFG", FAN_LED_GROUP)
        } else {
            execHardwareWrite("fan_led_enabled:false", "echo $FAN_LED_OFF_VALUE > $LED_EFFECT; echo 1 > $LED_CFG", FAN_LED_GROUP)
        }
    }

    /**
     * The old presets, now that they are known to be the soft colours: kept only so that what is
     * already saved still lights something. Saved profiles, Game Mode, charging and call lighting
     * all store these as "preset:0x300…" strings, from back when they were written to the node
     * verbatim — which this kernel rejects outright, and is why presets never lit anything.
     */
    private val FAN_LED_PRESET_COLORS = mapOf(
        "0x3002101" to 101,
        "0x3002102" to 102,
        "0x3002103" to 103,
        "0x3002104" to 104,
        "0x3002105" to 105,
        "0x3002106" to 106,
        "0x3002107" to 107,
        // There is no eighth: the stock row holds seven. Anything saved against this one lights
        // nothing rather than lighting the wrong thing.
    )

    fun setFanLedStockPreset(effectValue: String): Boolean {
        val color = FAN_LED_PRESET_COLORS[effectValue] ?: return false
        return setFanLedEffect("flow", color)
    }

    /**
     * Note what this does NOT do: write the fan's enable node.
     *
     * It used to open with `echo 1 > fan_enable`, inherited from before the LED protocol was
     * understood. The ring is on the aw22xxx chip under /sys/class/leds; the fan motor is a
     * different subsystem under /sys/kernel/fan, and lighting one never needed the other -- every
     * other LED write here (setFanLedEnabled, the back LED, all-off) has always managed without it.
     *
     * What it did do was switch the fan on behind the user's back on every LED write. With the
     * colour rotation running that is once a second, so the fan came back on a second after being
     * switched off, over and over -- an audible on/off cycle, and a master switch that flipped
     * itself as the status refresh read the node back.
     */
    fun setFanLedEffect(effectName: String, color: Int): Boolean {
        val value = fanLedValueFor(effectName, color)
        return execHardwareWrite("fan_led:$effectName:$color", "echo $value > $LED_EFFECT; echo 1 > $LED_CFG", FAN_LED_GROUP)
    }

    // Back logo + X emblem on RM 10 Pro live on the aw22xxx chip too, but at a
    // different value range than the fan matrix. Values 0x60..0x67 each load a
    // dedicated firmware preset (aw_cfg0_1..aw_cfg0_8) — observed sequence is
    // red / orange / yellow / green / cyan / blue / purple / rose.
    private const val BACK_LED_OFF_VALUE = "0"  // loads m_led_off.bin (all-zone blank)
    private fun backLedValueFor(color: Int): String = when (color) {
        1 -> "0x60"  // red
        3 -> "0x61"  // orange
        4 -> "0x62"  // yellow
        5 -> "0x63"  // green
        6 -> "0x64"  // cyan
        7 -> "0x65"  // blue
        8 -> "0x66"  // purple
        9 -> "0x67"  // rose
        else -> "0x63"
    }

    fun setLogoLedEnabled(enabled: Boolean): Boolean {
        return if (enabled) {
            execHardwareWrite("back_led_enabled:true", "echo ${backLedValueFor(5)} > $LED_EFFECT; echo 1 > $LED_CFG")
        } else {
            execHardwareWrite("back_led_enabled:false", "echo $BACK_LED_OFF_VALUE > $LED_EFFECT; echo 1 > $LED_CFG")
        }
    }

    fun setLogoLedEffect(effectName: String, color: Int): Boolean {
        // Back logo is solid-color only; effectName is ignored, the chip drives
        // its own breathing/idle behavior baked into the firmware preset.
        val value = backLedValueFor(color)
        return execHardwareWrite("back_led:$color", "echo $value > $LED_EFFECT; echo 1 > $LED_CFG")
    }

    fun setShoulderLedEnabled(enabled: Boolean): Boolean {
        // No dedicated shoulder LED strip on RM 10 Pro. No-op.
        return true
    }

    fun setShoulderLedEffect(effectName: String, color: Int): Boolean = true

    fun turnOffAllLeds(): Boolean {
        return execHardwareWrite("led_all_off", "echo $FAN_LED_OFF_VALUE > $LED_EFFECT; echo 1 > $LED_CFG")
    }

    fun enableTriggers(): Boolean {
        return execHardwareWrite("triggers_enabled:true", "echo 1 > $SAR0_MODE; echo 1 > $SAR1_MODE")
    }

    fun disableTriggers(): Boolean {
        return execHardwareWrite("triggers_enabled:false", "echo 0 > $SAR0_MODE; echo 0 > $SAR1_MODE")
    }

    fun isTriggersEnabled(): Boolean {
        // sar0/sar1 mode_operation reads back as: "mode : 1, REG_WST(0x1a14) :0x1000000"
        // Parse the first "mode : N" digit rather than the whole string.
        val pattern = Regex("""mode\s*:\s*(\d)""")
        val sar0 = RootShell.execForOutput("cat $SAR0_MODE")?.let { pattern.find(it)?.groupValues?.get(1) }
        val sar1 = RootShell.execForOutput("cat $SAR1_MODE")?.let { pattern.find(it)?.groupValues?.get(1) }
        return sar0 == "1" || sar1 == "1"
    }

    fun injectTap(x: Int, y: Int): Boolean {
        return RootShell.exec("input tap $x $y")
    }

    private fun setSliderStockFunction(value: Int): Boolean {
        val cmd = "settings put system fourth_physical_key_function_value $value; " +
            "settings put system physical_key_function_app_value cn.nubia.gamelauncher"
        return execHardwareWrite("slider_stock_function:$value", cmd)
    }

    fun setSliderOpenCamera(): Boolean = setSliderStockFunction(1)

    fun setSliderOpenGameSpace(): Boolean = setSliderStockFunction(2)

    fun setSliderSoundMode(): Boolean = setSliderStockFunction(3)

    fun setSliderFlashlight(): Boolean = setSliderStockFunction(4)

    fun setSliderVoiceRecorder(): Boolean = setSliderStockFunction(5)

    fun setSliderLaunchApp(pkg: String): Boolean {
        val cmd = "settings put system fourth_physical_key_function_value 16; " +
            "settings put system physical_key_function_app_value $pkg"
        return execHardwareWrite("slider_launch_app:$pkg", cmd)
    }

    fun disableSliderSystemHandling(): Boolean {
        return execHardwareWrite("slider_system_handling:false", "settings put system fourth_physical_key_function_value 0")
    }

    fun readSliderState(): String? {
        return RootShell.execForOutput("settings get global zte_keypad_slide_on_or_off")?.trim()
    }

    fun vibrate(durationMs: Int, gain: Int): Boolean {
        val d = durationMs.coerceIn(1, 5000)
        val g = gain.coerceIn(0, 255)
        val cmd = "echo $d > $HAPTIC_DURATION; echo $g > $HAPTIC_GAIN; echo 1 > $HAPTIC_ACTIVATE"
        return RootShell.exec(cmd)
    }

    /** The thermal zone that answered last time. Resolving it costs a shell per candidate tried. */
    @Volatile private var cachedThermalPath: String? = null

    /**
     * Fan enable, level, RPM and temperature in ONE root shell.
     *
     * Read separately, these were four calls to RootShell -- and readTemperatureC walks up to eight
     * candidate thermal zones, each its own shell. Every one of those spawns an `su` process, so a
     * single status pass could fork a dozen of them. At the old fifteen-second floor that merely
     * wasted work; at the half-second one this tab now allows it is what makes it stutter.
     *
     * One `cat` per line, with `|| echo x` so a missing node still emits a line and the values stay
     * on the indices they were asked for.
     */
    fun readFanStatus(): FanStatus {
        val thermal = cachedThermalPath ?: return FanStatus(
            enabled = isFanEnabled(),
            level = readFanLevel(),
            rpm = readFanRpm(),
            // Resolves and caches the zone, so every later pass takes the one-shell path above.
            tempC = readTemperatureC()
        )

        val lines = RootShell.execForOutput(
            "cat $FAN_ENABLE 2>/dev/null || echo x; " +
                "cat $FAN_LEVEL 2>/dev/null || echo x; " +
                "cat $FAN_RPM 2>/dev/null || echo x; " +
                "cat $thermal 2>/dev/null || echo x"
        )?.lines()?.map { it.trim() } ?: return FanStatus(false, null, null, null)

        fun at(index: Int): String? = lines.getOrNull(index)?.takeIf { it.isNotBlank() && it != "x" }

        val rawTemp = at(3)?.toFloatOrNull()
        return FanStatus(
            enabled = at(0) == "1",
            level = at(1)?.toIntOrNull()?.coerceIn(0, 5),
            rpm = at(2)?.toIntOrNull(),
            tempC = when {
                rawTemp == null -> null
                rawTemp > 1000f && rawTemp < 200000f -> rawTemp / 1000f
                rawTemp > 0f && rawTemp < 200f -> rawTemp
                else -> null
            }
        )
    }

    data class FanStatus(
        val enabled: Boolean,
        val level: Int?,
        val rpm: Int?,
        val tempC: Float?
    ) {
        val tempF: Float? get() = tempC?.let { (it * 9f / 5f) + 32f }
    }

    fun readTemperatureC(): Float? {
        val candidates = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp",
            "/sys/class/thermal/thermal_zone3/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone2/temp",
            "/sys/devices/virtual/thermal/thermal_zone3/temp"
        )

        for (path in candidates) {
            val raw = RootShell.execForOutput("cat $path 2>/dev/null")?.trim()?.toFloatOrNull() ?: continue
            if (raw > 1000f && raw < 200000f) {
                cachedThermalPath = path
                return raw / 1000f
            }
            if (raw > 0f && raw < 200f) {
                cachedThermalPath = path
                return raw
            }
        }

        return null
    }

    fun readTemperatureF(): Float? {
        val c = readTemperatureC() ?: return null
        return (c * 9f / 5f) + 32f
    }

    fun chooseFanLevelForTempF(tempF: Float, curve: String): Int {
        return when (curve.lowercase()) {
            "quiet" -> when {
                tempF < 95f -> 0
                else -> 1
            }
            "turbo" -> when {
                tempF < 100f -> 4
                else -> 5
            }
            else -> when {
                tempF < 100f -> 2
                else -> 3
            }
        }
    }

    fun chooseAutoFanLevelForTempF(tempF: Float): Int {
        return when {
            tempF < 95f -> 0
            tempF < 104f -> 1
            tempF < 113f -> 2
            tempF < 122f -> 3
            tempF < 131f -> 4
            else -> 5
        }
    }

    fun applyFanCurve(curve: String): Int? {
        val tempF = readTemperatureF() ?: return null
        val level = chooseFanLevelForTempF(tempF, curve)
        setFanLevel(level)
        return level
    }

    fun applyAutoFanCurve(): Int? {
        val tempF = readTemperatureF() ?: return null
        val level = chooseAutoFanLevelForTempF(tempF)
        setFanLevel(level)
        return level
    }

    fun readCpuModel(): String {
        val socModel = RootShell.execForOutput("getprop ro.soc.model")?.trim().orEmpty()
        if (socModel.isNotBlank()) return socModel

        val board = RootShell.execForOutput("getprop ro.board.platform")?.trim().orEmpty()
        if (board.isNotBlank()) return board

        val hardware = RootShell.execForOutput("getprop ro.hardware")?.trim().orEmpty()
        if (hardware.isNotBlank()) return hardware

        return "Unknown"
    }

    fun readRamInfo(): String {
        val memInfo = RootShell.execForOutput("cat /proc/meminfo 2>/dev/null") ?: return "Unknown"
        val totalLine = memInfo.lines().firstOrNull { it.startsWith("MemTotal:") } ?: return "Unknown"
        val kb = totalLine.substringAfter("MemTotal:").trim().substringBefore(" ").toLongOrNull() ?: return "Unknown"

        val gb = kb / 1024.0 / 1024.0
        val rounded = round(gb).toInt()
        val supported = listOf(12, 16, 24)
        val nearest = supported.minByOrNull { abs(it - rounded) } ?: rounded

        return "$nearest GB"
    }

    fun applyHardwareProfile(profile: HardwareProfile): Boolean {
        enableFan(profile.fanEnabled)
        if (profile.fanEnabled) {
            setFanLevel(profile.fanLevel)
        }

        if (profile.pumpEnabled) {
            setPumpProfile(profile.pumpProfile)
        } else {
            enablePump(false)
        }

        if (profile.fanLedEnabled) {
            setFanLedEffect(profile.fanLedEffect, profile.fanLedColor)
        } else {
            setFanLedEnabled(false)
        }

        if (profile.logoLedEnabled) {
            setLogoLedEffect(profile.logoLedEffect, profile.logoLedColor)
        } else {
            setLogoLedEnabled(false)
        }

        if (profile.shoulderLedEnabled) {
            setShoulderLedEffect(profile.shoulderLedEffect, profile.shoulderLedColor)
        } else {
            setShoulderLedEnabled(false)
        }

        return true
    }

}
