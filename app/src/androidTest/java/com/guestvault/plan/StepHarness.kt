package com.guestvault.plan

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File

/**
 * Shared plumbing for the numbered manual test plan.
 *
 * Every plan step is wrapped in [verify], which records PASS/FAIL instead of
 * throwing, so a broken step cannot hide the verdicts that come after it.
 * Assertions inside a step use the `*OrFail` helpers, which throw and are
 * caught by [verify].
 */
abstract class StepHarness {

    protected lateinit var device: UiDevice
    protected lateinit var context: Context
    protected lateinit var screenshots: File

    protected val pin = "4726"
    protected val pinDigits: List<String> = pin.map(Char::toString)

    /** Call from `@Before`. Leaves the device awake, unlocked and on the launcher. */
    open fun prepare() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        context = ApplicationProvider.getApplicationContext()

        StepResults.initialise(context)

        screenshots = File(context.filesDir, "plan-shots").apply { mkdirs() }

        shell("wm dismiss-keyguard")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("svc power stayon true")
        shell("settings put global window_animation_scale 0")
        shell("settings put global transition_animation_scale 0")
        shell("settings put global animator_duration_scale 0")
        shell("cmd uimode night no")
        // A previous pass may have left a notification shade or a dialog open.
        runCatching { device.pressHome() }
        pause(600)
    }

    // ------------------------------------------------------------- verdicts

    /**
     * Runs one numbered step and records its verdict.
     *
     * [expect] is copied verbatim from the plan: it is the acceptance
     * criterion, not a description of what the code happens to do.
     */
    protected fun verify(
        id: String,
        expect: String,
        note: String = "",
        body: () -> Unit
    ) {
        val started = System.currentTimeMillis()
        try {
            body()
            StepResults.pass(id, expect, note.ifBlank { "met" }, System.currentTimeMillis() - started)
        } catch (t: Throwable) {
            val message = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
            StepResults.fail(id, expect, message, System.currentTimeMillis() - started)
        }
    }

    /** Records a step that cannot be executed on an emulator, with the reason. */
    protected fun notAutomatable(id: String, expect: String, reason: String) =
        StepResults.skip(id, expect, reason)

    protected fun expectTrue(condition: Boolean, message: String) {
        if (!condition) throw AssertionError(message)
    }

    protected fun expectContains(haystack: String?, needle: String, message: String) {
        if (haystack == null || !haystack.contains(needle)) {
            throw AssertionError("$message (got: ${haystack ?: "nothing"})")
        }
    }

    // ---------------------------------------------------------- app control

    protected fun launchApp() {
        device.pressHome()
        pause(500)
        runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        }
        pause(800)
    }

    /** Force-stops and relaunches — a real cold start of the owner app. */
    protected fun coldStartApp(holdSplashMs: Int? = null) {
        // The instrumentation lives in the same process as the app, so a
        // force-stop would kill the test. `am start -S` restarts only the
        // activity stack; the host script performs the process-kill steps.
        shell("am force-stop ${context.packageName}")
        pause(300)
        val hold = holdSplashMs?.let { " --ei com.guestvault.extra.SPLASH_HOLD_MS $it" } ?: ""
        shell("am start -n ${context.packageName}/.MainActivity$hold")
        pause(1200)
    }

    /** Walks whatever screen is showing to the owner dashboard. */
    protected fun ensureOwnerHome() {
        launchApp()
        when (waitForAny("Skip", "Create your owner PIN", "Start Guest Session", "Welcome to Guest Mode", "Owner lock", timeout = 45_000)) {
            "Skip" -> {
                repeat(3) {
                    if (device.hasObject(By.text("Next"))) tapText("Next")
                    pause(500)
                }
                if (device.hasObject(By.text("Get Started"))) tapText("Get Started")
            }
        }

        when {
            device.hasObject(By.text("Create your owner PIN")) -> createPin(pinDigits)
            device.hasObject(By.desc("PIN key 1")) -> unlockWithPin()
        }

        // A guest session can outlive the owner activity; leave it first.
        if (device.hasObject(By.text("Return to Owner"))) {
            tapText("Return to Owner")
            if (device.hasObject(By.desc("PIN key 1"))) unlockWithPin()
        }

        expectTrue(
            waitForAny("Start Guest Session", "Create your owner PIN", "Owner lock", timeout = 20_000) != null,
            "owner app never reached a known screen"
        )
        if (device.hasObject(By.text("Create your owner PIN"))) createPin(pinDigits)
        if (device.hasObject(By.desc("PIN key 1"))) unlockWithPin()
        expectTrue(waitFor("Start Guest Session", 20_000), "dashboard did not appear")
    }

    /** Completes onboarding if it is showing. Returns true when it did. */
    protected fun onboardingIfShown(): Boolean {
        if (!device.hasObject(By.text("Skip")) && !device.hasObject(By.text("Lend your phone safely"))) return false
        repeat(4) {
            if (device.hasObject(By.text("Next"))) {
                tapText("Next")
                pause(600)
            }
        }
        if (device.hasObject(By.text("Get Started"))) tapText("Get Started")
        pause(800)
        return true
    }

    protected fun createPin(digits: List<String> = pinDigits) {
        assertWait("Create your owner PIN", 20_000)
        enterPin(digits)
        assertWait("Confirm your PIN", 15_000)
        enterPin(digits)
        pause(800)
    }

    protected fun unlockWithPin(digits: List<String> = pinDigits) {
        expectTrue(waitForDesc("PIN key 1", 20_000), "PIN pad did not appear")
        enterPin(digits)
        pause(800)
    }

    protected fun enterPin(digits: List<String>) {
        digits.forEach { digit ->
            val selector = By.desc("PIN key $digit")
            expectTrue(device.wait(Until.hasObject(selector), 8_000), "PIN key $digit not found")
            device.findObject(selector)?.click()
            pause(150)
        }
        pause(700)
    }

    // ------------------------------------------------------------- finders

    protected fun pause(ms: Long) = Thread.sleep(ms)

    protected fun shell(command: String): String =
        runCatching { device.executeShellCommand(command) }.getOrDefault("")

    protected fun waitFor(text: String, timeout: Long = 15_000): Boolean =
        device.wait(Until.hasObject(By.text(text)), timeout)

    protected fun waitForDesc(desc: String, timeout: Long = 15_000): Boolean =
        device.wait(Until.hasObject(By.desc(desc)), timeout)

    protected fun waitForContains(text: String, timeout: Long = 15_000): Boolean =
        device.wait(Until.hasObject(By.textContains(text)), timeout)

    protected fun assertWait(text: String, timeout: Long = 15_000) {
        expectTrue(waitFor(text, timeout), "\"$text\" never appeared")
    }

    protected fun assertWaitDesc(desc: String, timeout: Long = 15_000) {
        expectTrue(waitForDesc(desc, timeout), "description \"$desc\" never appeared")
    }

    /** First visible string out of [candidates], or null on timeout. */
    protected fun waitForAny(vararg candidates: String, timeout: Long = 20_000): String? {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            candidates.firstOrNull { device.hasObject(By.text(it)) }?.let { return it }
            Thread.sleep(250)
        }
        return null
    }

    protected fun tapText(text: String, timeout: Long = 15_000) {
        expectTrue(waitFor(text, timeout), "Timed out waiting for \"$text\"")
        device.findObject(By.text(text))?.click()
        pause(450)
    }

    protected fun tapDesc(desc: String, timeout: Long = 15_000) {
        expectTrue(waitForDesc(desc, timeout), "Timed out waiting for description \"$desc\"")
        device.findObject(By.desc(desc))?.click()
        pause(450)
    }

    protected fun tapTextContains(text: String, timeout: Long = 15_000) {
        expectTrue(waitForContains(text, timeout), "Timed out waiting for text containing \"$text\"")
        device.findObject(By.textContains(text))?.click()
        pause(450)
    }

    /** Swipes up until [text] is on screen (LazyColumn pages need this). */
    protected fun scrollToText(text: String, maxSwipes: Int = 5): Boolean {
        repeat(maxSwipes) {
            if (device.hasObject(By.text(text))) return true
            val centerX = device.displayWidth / 2
            device.swipe(
                centerX,
                (device.displayHeight * 0.78f).toInt(),
                centerX,
                (device.displayHeight * 0.30f).toInt(),
                18
            )
            pause(500)
        }
        return device.hasObject(By.text(text))
    }

    protected fun scrollToDesc(desc: String, maxSwipes: Int = 5): Boolean {
        repeat(maxSwipes) {
            if (device.hasObject(By.desc(desc))) return true
            val centerX = device.displayWidth / 2
            device.swipe(
                centerX,
                (device.displayHeight * 0.78f).toInt(),
                centerX,
                (device.displayHeight * 0.30f).toInt(),
                18
            )
            pause(500)
        }
        return device.hasObject(By.desc(desc))
    }

    protected fun scrollUp(maxSwipes: Int = 4) {
        repeat(maxSwipes) {
            val centerX = device.displayWidth / 2
            device.swipe(
                centerX,
                (device.displayHeight * 0.30f).toInt(),
                centerX,
                (device.displayHeight * 0.80f).toInt(),
                18
            )
            pause(400)
        }
    }

    /** Every visible text on screen — used to prove a string is *absent*. */
    protected fun visibleText(): String =
        device.findObjects(By.textContains("")).joinToString(" | ") { it.text ?: "" }

    protected fun shot(name: String) {
        pause(350)
        runCatching { device.takeScreenshot(File(screenshots, name)) }
    }
}
