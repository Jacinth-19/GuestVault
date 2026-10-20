package com.guestvault

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end walkthrough of the owner → guest → owner flow.
 *
 * Besides guarding the critical path, this test captures the screenshots used
 * in the README (and is recorded to a video by the CI workflow).
 *
 * The test is idempotent: it detects whether onboarding has already happened,
 * and the PIN it uses is always the same, so reruns on a warm emulator work.
 */
@RunWith(AndroidJUnit4::class)
class GuestVaultFlowTest {

    private lateinit var device: UiDevice
    private lateinit var context: Context
    private lateinit var screenshots: File

    private val pin = "4726"
    private val pinDigits = pin.map(Char::toString)

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        context = ApplicationProvider.getApplicationContext()

        screenshots = File(context.filesDir, "screenshots").apply { mkdirs() }
        screenshots.listFiles()?.forEach { it.delete() }

        // Best-effort environment prep; all of these run as the shell user.
        shell("wm dismiss-keyguard")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("svc power stayon true")
        shell("settings put global window_animation_scale 0")
        shell("settings put global transition_animation_scale 0")
        shell("settings put global animator_duration_scale 0")
        shell("cmd uimode night no")
        device.pressHome()
        Thread.sleep(800)
    }

    @Test
    fun ownerLendsPhoneToGuestAndReturns() {
        step("launch")
        launchApp()

        val firstScreen = waitForAny(
            "Skip",
            "Create your owner PIN",
            "Start Guest Session",
            "Welcome to Guest Mode",
            timeout = 45_000
        )
        assertNotNull("App never reached a known screen", firstScreen)

        when (firstScreen) {
            "Skip" -> {
                onboardingWalkthrough()
                createPin()
            }

            "Create your owner PIN" -> createPin()

            "Welcome to Guest Mode" -> {
                // A previous run left a session open, so the app opened
                // straight into the restricted guest home: unlock it first.
                tapText("Return to Owner")
                unlockWithPin()
                assertTrue(waitFor("Start Guest Session", 30_000))
            }
        }

        assertTrue("Dashboard did not load", waitFor("Start Guest Session", 30_000))
        Thread.sleep(1_200)
        shot("06-dashboard-light.png")

        // ---------------------------------------------------------- settings --
        tapDesc("Settings")
        assertTrue(waitFor("Owner-only controls", 15_000))
        Thread.sleep(600)
        shot("07-settings.png")

        tapText("Allowed apps")
        assertTrue(waitFor("Guests only see what you pick", 15_000))
        Thread.sleep(800)
        shot("08-select-apps.png")
        assertTrue(waitForContains("apps selected", 10_000))

        tapText("Save")
        assertTrue(waitFor("Owner-only controls", 15_000))
        tapDesc("Back")
        assertTrue(waitFor("Start Guest Session", 15_000))

        // ----------------------------------------------------------- confirm --
        tapText("Start Guest Session")
        assertTrue(waitFor("Start guest session", 15_000))
        Thread.sleep(600)
        shot("09-confirm-session.png")

        // Screen pinning triggers a system confirmation dialog. Turn it off so
        // the walkthrough stays deterministic; the feature itself is covered by
        // the Settings toggle and the README.
        if (device.hasObject(By.desc("Screen pinning toggle"))) {
            device.findObject(By.desc("Screen pinning toggle")).click()
            Thread.sleep(400)
        }
        tapText("15 min")
        Thread.sleep(400)
        shot("10-confirm-options.png")

        tapText("Start Guest Mode")
        assertTrue("Guest home did not appear", waitFor("Welcome to Guest Mode", 30_000))
        assertFalse("Owner dashboard leaked into guest mode", device.hasObject(By.text("Start Guest Session")))
        Thread.sleep(2_000) // let app icons resolve
        shot("11-guest-home.png")

        // -------------------------------------------------------------- exit --
        tapText("Return to Owner")
        assertTrue(
            "Owner lock screen did not appear",
            waitFor("Enter your PIN or use your fingerprint to end the guest session.", 15_000)
        )
        Thread.sleep(600)
        shot("12-lock-screen.png")

        enterPin(listOf("1", "1", "1", "1"))
        assertTrue("Wrong PIN should be rejected", waitFor("Incorrect PIN. 4 attempts left.", 15_000))
        Thread.sleep(500)
        shot("13-lock-wrong-pin.png")

        unlockWithPin()
        assertTrue("Did not return to the owner dashboard", waitFor("Start Guest Session", 40_000))
        Thread.sleep(1_400)
        shot("14-dashboard-history.png")

        // --------------------------------------------------------- dark mode --
        shell("cmd uimode night yes")
        Thread.sleep(2_800)
        shot("15-dashboard-dark.png")

        step("open about")
        if (scrollToText("Learn more")) {
            tapText("Learn more")
            if (waitFor("What needs system access", 12_000)) {
                Thread.sleep(900)
                shot("16-about-dark.png")
                tapDesc("Back")
                Thread.sleep(800)
            } else {
                step("about screen not reached")
            }
        } else {
            step("learn more not found")
        }
        shell("cmd uimode night no")
        Thread.sleep(1_500)

        step("done")
    }

    // ------------------------------------------------------------------ steps

    private fun launchApp() {
        val started = shell("am start -W -n ${context.packageName}/.MainActivity")
        if (!started.contains("Status: ok") && !started.contains("Warning: Activity not started")) {
            runCatching {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    private fun onboardingWalkthrough() {
        assertTrue(waitFor("Lend your phone safely", 15_000))
        Thread.sleep(700)
        shot("01-onboarding-welcome.png")

        tapText("Next")
        assertTrue(waitFor("Only the apps you allow", 12_000))
        Thread.sleep(600)
        shot("02-onboarding-apps.png")

        tapText("Next")
        assertTrue(waitFor("You hold the keys", 12_000))
        Thread.sleep(600)
        shot("03-onboarding-keys.png")

        tapText("Get Started")
        Thread.sleep(800)
    }

    private fun createPin() {
        assertTrue("PIN creation screen missing", waitFor("Create your owner PIN", 20_000))
        Thread.sleep(500)
        shot("04-pin-create.png")

        enterPin(pinDigits)
        assertTrue("PIN confirmation screen missing", waitFor("Confirm your PIN", 12_000))
        Thread.sleep(400)
        shot("05-pin-confirm.png")

        enterPin(pinDigits)
    }

    private fun unlockWithPin() {
        assertTrue(
            "Owner lock screen missing",
            device.wait(Until.hasObject(By.desc("PIN key 1")), 20_000)
        )
        enterPin(pinDigits)
    }

    private fun enterPin(digits: List<String>) {
        digits.forEach { digit ->
            val selector = By.desc("PIN key $digit")
            assertTrue("PIN key $digit not found", device.wait(Until.hasObject(selector), 6_000))
            device.findObject(selector)?.click()
            Thread.sleep(140)
        }
        Thread.sleep(700)
    }

    // ---------------------------------------------------------------- helpers

    /** Reports progress so CI logs show exactly which step ran. */
    private fun step(name: String) {
        runCatching {
            InstrumentationRegistry.getInstrumentation().sendStatus(
                2,
                Bundle().apply { putString("guestvault-step", name) }
            )
        }
    }

    private fun shell(command: String): String =
        runCatching { device.executeShellCommand(command) }.getOrDefault("")

    private fun waitFor(text: String, timeout: Long = 15_000): Boolean =
        device.wait(Until.hasObject(By.text(text)), timeout)

    private fun waitForDesc(desc: String, timeout: Long = 15_000): Boolean =
        device.wait(Until.hasObject(By.desc(desc)), timeout)

    private fun waitForContains(text: String, timeout: Long = 15_000): Boolean =
        device.wait(Until.hasObject(By.textContains(text)), timeout)

    /** Swipes up until [text] is on screen (LazyColumn pages need this). */
    private fun scrollToText(text: String, maxSwipes: Int = 4): Boolean {
        repeat(maxSwipes) {
            if (device.hasObject(By.text(text))) return true
            val centerX = device.displayWidth / 2
            device.swipe(
                centerX,
                (device.displayHeight * 0.78f).toInt(),
                centerX,
                (device.displayHeight * 0.32f).toInt(),
                18
            )
            Thread.sleep(450)
        }
        return device.hasObject(By.text(text))
    }

    private fun waitForAny(vararg candidates: String, timeout: Long = 20_000): String? {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            candidates.firstOrNull { device.hasObject(By.text(it)) }?.let { return it }
            Thread.sleep(250)
        }
        return null
    }

    private fun tapText(text: String, timeout: Long = 15_000) {
        assertTrue("Timed out waiting for \"$text\"", waitFor(text, timeout))
        device.findObject(By.text(text))?.click()
        Thread.sleep(400)
    }

    private fun tapDesc(desc: String, timeout: Long = 15_000) {
        assertTrue("Timed out waiting for description \"$desc\"", waitForDesc(desc, timeout))
        device.findObject(By.desc(desc))?.click()
        Thread.sleep(400)
    }

    private fun shot(name: String) {
        Thread.sleep(400)
        val file = File(screenshots, name)
        runCatching {
            device.takeScreenshot(file)
            publishToGallery(file, name)
        }
    }

    /**
     * Also publishes every screenshot to /sdcard/Pictures/GuestVault so CI can
     * pull it with plain `adb pull` (app-internal storage is not readable by
     * adb on modern Android).
     */
    private fun publishToGallery(file: File, name: String) {
        runCatching {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GuestVault")
            }
            val uri = context.contentResolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: return
            context.contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
        }
    }
}
