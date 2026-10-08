package com.elitedarkkaiser.redmagic.gametrigger

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.elitedarkkaiser.redmagic.TriggerAccessibilityService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class TriggerSetupTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var component: String

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        component = ComponentName(context, TriggerAccessibilityService::class.java).flattenToString()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        context.getSharedPreferences("native_tgk_profiles", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Exercise the real generated shell script against isolated settings, without su or a phone. */
    private fun shell(initial: String, user: Int = 0): File {
        val dir = temporary.newFolder()
        File(dir, "$user.enabled_accessibility_services").writeText(initial)
        File(dir, "99.enabled_accessibility_services").writeText("untouched.user/Service")
        val bin = File(dir, "bin").apply { mkdir() }
        fun executable(name: String, body: String) {
            File(bin, name).apply { writeText("#!/bin/sh\n$body\n"); assertTrue(setExecutable(true)) }
        }
        executable("id", "printf '0\\n'")
        executable("cmd", "printf '%s\\n' \"\u0024*\" >> \"\u0024STORE/appops\"")
        executable("settings", """
            [ "\u00241" = --user ] || exit 1
            target="\u00242"
            shift 2
            [ "\u00242" = secure ] || exit 1
            path="\u0024STORE/\u0024target.\u00243"
            case "\u00241" in
                get) if [ -f "\u0024path" ]; then cat "\u0024path"; else printf 'null'; fi ;;
                put) printf '%s' "\u00244" > "\u0024path" ;;
                *) exit 1 ;;
            esac
        """.trimIndent().replace("\\u0024", "$"))
        val command = TriggerSetup.commands(context.packageName, component, user, TriggerSetup.State(false, false))
        repeat(2) {
            val child = ProcessBuilder("/bin/sh", "-c", command).redirectErrorStream(true).apply {
                environment()["PATH"] = bin.absolutePath + ":" + System.getenv("PATH")
                environment()["STORE"] = dir.absolutePath
            }.start()
            assertTrue(child.waitFor(5, TimeUnit.SECONDS))
            val output = child.inputStream.bufferedReader().readText()
            assertEquals(output, 0, child.exitValue())
        }
        return dir
    }

    @Test fun rootScriptPreservesOtherServicesAndIsIdempotent() {
        val other = "reader.app/.Reader:automation.app/automation.Service"
        val dir = shell(other)
        assertEquals("$other:$component", File(dir, "0.enabled_accessibility_services").readText())
        assertEquals("1", File(dir, "0.accessibility_enabled").readText())
        assertTrue(File(dir, "appops").readText().contains("appops set --user 0 ${context.packageName} SYSTEM_ALERT_WINDOW allow"))
        assertEquals("untouched.user/Service", File(dir, "99.enabled_accessibility_services").readText())
    }

    @Test fun rootScriptUsesTheAppUserAndHandlesAnEmptySetting() {
        val dir = shell("null", 10)
        assertEquals(component, File(dir, "10.enabled_accessibility_services").readText())
        assertFalse(File(dir, "0.enabled_accessibility_services").exists())
        assertTrue(File(dir, "appops").readText().contains("--user 10"))
    }

    @Test fun serviceListIsNotReplacedWhenOnlyGlobalAccessibilityIsDisabled() {
        val initial = "$component:reader.app/.Reader"
        val dir = shell(initial)
        assertEquals(initial, File(dir, "0.enabled_accessibility_services").readText())
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, initial)
        assertFalse(TriggerSetup.state(context).accessibility)
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        assertTrue(TriggerSetup.state(context).accessibility)
    }

    @Test fun completedSetupDoesNotInvokeRootOrEnableGameMapping() {
        val result = TriggerSetup.grant(context, { TriggerSetup.State(true, true) }, { error("Root must not run") })
        assertTrue(result.state.ready)
        assertFalse(NativeTgkStorage.enabled(context))
        assertTrue(NativeTgkStorage.readProfiles(context).isEmpty())
    }

    @Test fun commandSuccessIsVerifiedInsteadOfAssumingPermissionsWereGranted() {
        val result = TriggerSetup.grant(context, { TriggerSetup.State(false, false) }, { true })
        assertFalse(result.state.ready)
        assertTrue(result.message.contains("Firmware"))
        var calls = 0
        val partial = TriggerSetup.grant(context, { TriggerSetup.State(calls++ > 0, false) }, { true })
        assertTrue(partial.state.overlay)
        assertFalse(partial.state.accessibility)
        assertTrue(partial.message.contains("Accessibility"))
    }

    @Test fun deniedRootKeepsTheManualPathAndExistingPreferences() {
        val result = TriggerSetup.grant(context, { TriggerSetup.State(false, false) }, { false })
        assertFalse(result.state.ready)
        assertTrue(result.message.contains("manually"))
        assertFalse(NativeTgkStorage.enabled(context))
    }
}
