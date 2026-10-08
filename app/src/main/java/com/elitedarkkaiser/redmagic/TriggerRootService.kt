package com.elitedarkkaiser.redmagic

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class TriggerRootService : Service() {

    
    private var rightTriggerUnlockedUntil: Long = 0L
    private var running = true

    private var leftUnlockArmedAt = 0L
    private var leftUnlockedUntil = 0L
    private var leftUnlockTapCount = 0
    private val held = ConcurrentHashMap<String, AtomicBoolean>()
    private val repeatThreads = ConcurrentHashMap<String, Thread>()

    private var rightUnlockArmedAt = 0L
    private var rightUnlockTapCount = 0
    private var rightUnlockedUntil = 0L

    private val RIGHT_UNLOCK_TAP_WINDOW_MS = 450L
    private val RIGHT_UNLOCK_ACTIVE_MS = 2500L
    private val HOLD_REPEAT_START_MS = 350L
    private val HOLD_REPEAT_INTERVAL_MS = 110L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        HardwareController.enableTriggers()
        android.util.Log.d("TRIGGER", "TriggerRootService onCreate")
        startReader(findTriggerEvent("nubia_tgk_aw_sar0_ch0"), "left_trigger")
        startReader(findTriggerEvent("nubia_tgk_aw_sar1_ch0"), "right_trigger")
    }


    private fun findTriggerEvent(triggerName: String): String {
        val cmd = "for ev in /sys/class/input/event*; do name=\$(cat \"\$ev/device/name\" 2>/dev/null); if [ \"\$name\" = \"$triggerName\" ]; then basename \"\$ev\"; exit 0; fi; done"
        val eventName = RootShell.execForOutput(cmd)?.trim()?.lineSequence()?.firstOrNull()?.trim()

        return if (!eventName.isNullOrBlank()) {
            "/dev/input/$eventName"
        } else {
            android.util.Log.e("TRIGGER", "failed to resolve trigger event for $triggerName")
            "/dev/input/event0"
        }
    }

    private fun prefs() = getSharedPreferences("triggers", MODE_PRIVATE)

    private fun isScreenInteractive(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        return powerManager.isInteractive
    }


    private fun getAction(key: String): String {
        val value = prefs().getString(key, "NONE") ?: "NONE"
        android.util.Log.d("TRIGGER", "getAction(" + key + ")=" + value)
        return value
    }

    private fun hapticsEnabled(): Boolean {
        return prefs().getBoolean("haptics_enabled", false)
    }

    /**
     * Fires one haptic at the configured strength, or not at all.
     *
     * [baseGain] is what this particular haptic wants at full strength -- the strength setting
     * scales every one of them by the same fraction, so a tap stays lighter than an unlock at any
     * setting. See [HapticStrength].
     */
    private fun haptic(durationMs: Int, baseGain: Int) {
        if (!hapticsEnabled()) return
        val strength = HapticStrength.get(this)
        if (strength <= HapticStrength.OFF) return
        HardwareController.vibrate(
            durationMs = durationMs,
            gain = HapticStrength.scale(baseGain, strength)
        )
    }

    private fun runRoot(cmd: String) {
        try {
            android.util.Log.d("TRIGGER", "runRoot=" + cmd)
            Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        } catch (t: Throwable) {
            android.util.Log.e("TRIGGER", "runRoot failed: " + t)
        }
    }

    private fun hapticTap() {
        try {
            haptic(durationMs = 20, baseGain = 180)
        } catch (t: Throwable) {
            android.util.Log.e("TRIGGER", "hapticTap failed: " + t)
        }
    }

    private fun hapticUnlock() {
        try {
            haptic(durationMs = 40, baseGain = 255)
        } catch (t: Throwable) {
            android.util.Log.e("TRIGGER", "hapticUnlock failed: " + t)
        }
    }

    private fun hapticHoldStart() {
        try {
            haptic(durationMs = 35, baseGain = 255)
        } catch (t: Throwable) {
            android.util.Log.e("TRIGGER", "hapticHoldStart failed: " + t)
        }
    }

    private fun performAction(action: String) {
        if (com.elitedarkkaiser.redmagic.gametrigger.GameTrigger.ownsTriggers()) return
        android.util.Log.d("TRIGGER", "performAction=" + action)

        when (action) {
            "VOL_UP" -> runRoot("input keyevent 24")
            "VOL_DOWN" -> runRoot("input keyevent 25")
            "MEDIA_PLAY_PAUSE" -> runRoot("input keyevent 85")
            "MEDIA_NEXT" -> runRoot("input keyevent 87")
            "MEDIA_PREVIOUS" -> runRoot("input keyevent 88")
            "NONE" -> Unit
            else -> Unit
        }
    }

    private fun isRepeatable(action: String): Boolean {
        return when (action) {
            "VOL_UP", "VOL_DOWN" -> true
            else -> false
        }
    }

    private fun startRepeater(prefKey: String) {
        stopRepeater(prefKey)

        val action = getAction(prefKey)
        if (!isRepeatable(action)) return

        val flag = AtomicBoolean(true)
        held[prefKey] = flag

        val thread = Thread {
            try {
                Thread.sleep(HOLD_REPEAT_START_MS)

                if (running && flag.get()) {
                    hapticHoldStart()
                }

                while (running && flag.get() && !com.elitedarkkaiser.redmagic.gametrigger.GameTrigger.ownsTriggers()) {
                    if (prefKey == "right_trigger" && !isRightUnlocked()) {
                        break
                    }
                    performAction(getAction(prefKey))
                    if (prefKey == "right_trigger") {
                        extendRightUnlock()
                    }
                    Thread.sleep(HOLD_REPEAT_INTERVAL_MS)
                }
            } catch (_: InterruptedException) {
            } catch (t: Throwable) {
                android.util.Log.e("TRIGGER", "startRepeater failed for " + prefKey + ": " + t)
            }
        }

        repeatThreads[prefKey] = thread
        thread.start()
    }

    private fun stopRepeater(prefKey: String) {
        held[prefKey]?.set(false)
        held.remove(prefKey)

        repeatThreads[prefKey]?.interrupt()
        repeatThreads.remove(prefKey)
    }

    private fun now() = System.currentTimeMillis()

    private fun isRightUnlocked(): Boolean {
        val unlocked = now() <= rightUnlockedUntil
        if (!unlocked && rightUnlockedUntil != 0L) {
            android.util.Log.d("TRIGGER", "right trigger locked by timeout")
        }
        return unlocked
    }

    private fun extendRightUnlock() {
        rightUnlockedUntil = now() + RIGHT_UNLOCK_ACTIVE_MS
        android.util.Log.d("TRIGGER", "right unlock extended until=" + rightUnlockedUntil)
    }

    private fun rightIntentUnlockTapCountRequired(): Int {
        return prefs().getInt("intent_unlock_tap_count", 2).coerceIn(2, 4)
    }

    private fun leftIntentUnlockTapCountRequired(): Int {
        return prefs().getInt("left_intent_unlock_tap_count", 1).coerceIn(1, 4)
    }

    private fun handleRightIntentUnlock(): Boolean {
        if (!prefs().getBoolean("intent_unlock_right_trigger", false)) {
            return true
        }

        val current = now()
        val requiredTaps = rightIntentUnlockTapCountRequired()

        if (current <= rightUnlockedUntil) {
            extendRightUnlock()
            return true
        }

        if (rightUnlockArmedAt == 0L || (current - rightUnlockArmedAt) > RIGHT_UNLOCK_TAP_WINDOW_MS) {
            rightUnlockArmedAt = current
            rightUnlockTapCount = 1
        } else {
            rightUnlockTapCount += 1
        }

        if (rightUnlockTapCount >= requiredTaps) {
            rightUnlockArmedAt = 0L
            rightUnlockTapCount = 0
            rightUnlockedUntil = current + RIGHT_UNLOCK_ACTIVE_MS
            android.util.Log.d("TRIGGER", "right trigger UNLOCKED taps=$requiredTaps")
            hapticUnlock()
            return true
        }

        android.util.Log.d(
            "TRIGGER",
            "right trigger unlock tap $rightUnlockTapCount/$requiredTaps ignored"
        )
        return false
    }


    private fun handleLeftIntentUnlock(): Boolean {
        val requiredTaps = leftIntentUnlockTapCountRequired()
        if (requiredTaps <= 1) {
            return true
        }

        val current = now()

        if (current <= leftUnlockedUntil) {
            leftUnlockedUntil = current + RIGHT_UNLOCK_ACTIVE_MS
            return true
        }

        if (leftUnlockArmedAt == 0L || (current - leftUnlockArmedAt) > RIGHT_UNLOCK_TAP_WINDOW_MS) {
            leftUnlockArmedAt = current
            leftUnlockTapCount = 1
        } else {
            leftUnlockTapCount += 1
        }

        if (leftUnlockTapCount >= requiredTaps) {
            leftUnlockArmedAt = 0L
            leftUnlockTapCount = 0
            leftUnlockedUntil = current + RIGHT_UNLOCK_ACTIVE_MS
            android.util.Log.d("TRIGGER", "left trigger UNLOCKED taps=$requiredTaps")
            hapticUnlock()
            return true
        }

        android.util.Log.d(
            "TRIGGER",
            "left trigger unlock tap $leftUnlockTapCount/$requiredTaps ignored"
        )
        return false
    }

    private fun handleLeftDown(device: String, line: String) {
        if (com.elitedarkkaiser.redmagic.gametrigger.GameTrigger.ownsTriggers()) {
            stopRepeater("left_trigger")
            return
        }
        android.util.Log.d("TRIGGER", "LEFT DOWN device=" + device + " line=" + line)

        if (!isScreenInteractive()) {
            android.util.Log.d("TRIGGER", "LEFT ignored because screen is off")
            return
        }

        if (!handleLeftIntentUnlock()) {
            stopRepeater("left_trigger")
            return
        }

        hapticTap()
        rightTriggerUnlockedUntil = now() + RIGHT_UNLOCK_ACTIVE_MS
        rightUnlockedUntil = now() + RIGHT_UNLOCK_ACTIVE_MS
        rightUnlockArmedAt = 0L
        rightUnlockTapCount = 0
        android.util.Log.d("TRIGGER", "LEFT temporarily unlocked right trigger until=" + rightUnlockedUntil)
        performAction(getAction("left_trigger"))
        startRepeater("left_trigger")
    }

    private fun handleRightDown(device: String, line: String) {
        if (com.elitedarkkaiser.redmagic.gametrigger.GameTrigger.ownsTriggers()) {
            stopRepeater("right_trigger")
            return
        }
        android.util.Log.d("TRIGGER", "RIGHT DOWN device=" + device + " line=" + line)

        if (!isScreenInteractive()) {
            android.util.Log.d("TRIGGER", "RIGHT ignored because screen is off")
            return
        }

        if (now() <= rightTriggerUnlockedUntil) {
            extendRightUnlock()
            hapticTap()
            performAction(getAction("right_trigger"))
            startRepeater("right_trigger")
            return
        }

        if (!handleRightIntentUnlock()) {
            stopRepeater("right_trigger")
            return
        }

        hapticTap()
        performAction(getAction("right_trigger"))
        startRepeater("right_trigger")
    }

    private fun handleUp(prefKey: String, device: String, line: String) {
        android.util.Log.d("TRIGGER", "UP device=" + device + " key=" + prefKey + " line=" + line)
        stopRepeater(prefKey)
    }


    private fun isDownLine(line: String): Boolean {
        return line.contains(" DOWN") ||
            line.endsWith(" 00000001") ||
            line.contains(" value 1")
    }

    private fun isUpLine(line: String): Boolean {
        return line.contains(" UP") ||
            line.endsWith(" 00000000") ||
            line.contains(" value 0")
    }


    private fun startReader(device: String, prefKey: String) {
        Thread {
            try {
                android.util.Log.d("TRIGGER", "startReader device=" + device + " key=" + prefKey)
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "getevent -l " + device))
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                while (running) {
                    val line = reader.readLine() ?: break

                    android.util.Log.d("TRIGGER", "raw device=$device key=$prefKey line=$line")

                    if (isDownLine(line)) {
                        if (prefKey == "left_trigger") {
                            handleLeftDown(device, line)
                        } else {
                            handleRightDown(device, line)
                        }
                    } else if (isUpLine(line)) {
                        handleUp(prefKey, device, line)
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("TRIGGER", "startReader failed for " + device + ": " + t)
            }
        }.start()
    }

    override fun onDestroy() {
        running = false
        stopRepeater("left_trigger")
        stopRepeater("right_trigger")
        android.util.Log.d("TRIGGER", "TriggerRootService onDestroy")
        super.onDestroy()
    }
}
