package com.guestvault.plan

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Pass 16 — the paid build's second wave: emergency code, daily budget,
 * six-digit PIN and the intruder photo.
 *
 * Runs on `com.guestvault.pro` in its own emulator group, because it changes the
 * owner PIN (to six digits) and replaces the recovery code. A later pass
 * expecting a four-digit PIN would be grading a state this pass created.
 */
@RunWith(AndroidJUnit4::class)
class ProSecurityPlanTest : StepHarness() {

    private val sixDigits = listOf("1", "3", "5", "7", "9", "2")

    /** Shares no digit in place with [sixDigits], so it cannot be a near miss. */
    private val wrongPinSix = listOf("4", "6", "8", "1", "3", "5")

    /** Saved by 16.1, typed back in 16.4. */
    private var recoveryCode: String = ""

    @Before
    fun setUp() {
        prepare()
    }

    @Test
    fun pass16() {
        arrange { ensureOwnerHome() }

        verify("16.1", "A recovery code is generated, shown once, and then stored") {
            openSettings()
            expectTrue(
                scrollToText("Emergency unlock code", maxSwipes = 10),
                "no Emergency unlock code row (text=${visibleText().take(200)})"
            )
            tapText("Emergency unlock code")
            expectTrue(
                waitFor("What this is", 12_000),
                "the recovery screen did not open (text=${visibleText().take(180)})"
            )
            expectTrue(
                !device.hasObject(By.text("This is the free build")),
                "the paid build showed the free-build lock panel"
            )

            tapText("Create a code")
            val shown = waitForCode(10_000)
            expectTrue(shown != null, "no code was shown (text=${visibleText().take(200)})")
            recoveryCode = shown!!
            note("Code shown: $recoveryCode")

            expectTrue(
                waitFor("I've written it down", 6_000),
                "the confirm button did not appear"
            )
            tapText("I've written it down")
            expectTrue(
                waitFor("A code is saved", 10_000),
                "saving the code did not update the screen (text=${visibleText().take(200)})"
            )
        }

        verify("16.2", "The daily limit starts unset, and setting it produces a caption") {
            backToDashboard()
            openSettings()
            expectTrue(scrollToText("Time limits", maxSwipes = 10), "no Time limits row")
            tapText("Time limits")
            expectTrue(
                waitFor("How a budget is counted", 12_000),
                "the time-limits screen did not open (text=${visibleText().take(180)})"
            )
            expectTrue(
                waitForContains("No daily limit", 8_000),
                "the daily limit is not shown as unset (text=${visibleText().take(200)})"
            )

            tapText("Set")
            expectTrue(
                waitFor("30 min", 8_000),
                "the daily choices did not appear (text=${visibleText().take(180)})"
            )
            tapText("30 min")
            expectTrue(
                waitForContains("used today of 30 min", 10_000),
                "the card does not report usage against the limit " +
                    "(text=${visibleText().take(200)})"
            )
            expectTrue(
                waitForContains("min left today", 8_000),
                "no countdown caption under the daily limit (text=${visibleText().take(200)})"
            )
            note("Daily limit set to 30 min with a live caption")
        }

        verify("16.3", "A six-digit PIN is accepted, and the lock screen draws six dots") {
            backToDashboard()
            openSettings()
            expectTrue(scrollToText("Change owner PIN", maxSwipes = 10), "no Change owner PIN row")
            tapText("Change owner PIN")
            expectTrue(waitFor("Enter your current PIN", 12_000), "the change flow did not open")

            enterPin(pinDigits)
            expectTrue(
                waitFor("Choose a new PIN", 12_000),
                "the current PIN was not accepted (text=${visibleText().take(200)})"
            )
            expectTrue(tapChip("6 digits"), "no 6-digit option on the new-PIN step")
            pause(600)

            enterPin(sixDigits)
            expectTrue(
                waitFor("Confirm your PIN", 10_000),
                "the six-digit entry did not advance (text=${visibleText().take(200)})"
            )
            enterPin(sixDigits)
            expectTrue(
                waitFor("Start Guest Session", 15_000),
                "the new six-digit PIN was not saved (text=${visibleText().take(200)})"
            )
            rememberPin(sixDigits)

            // Prove the lock draws six dots now: start a session, open the lock,
            // look, and back out again without entering anything.
            pickAppsAndStart()
            openLock()
            val dots = device.findObjects(By.descContains("of 6 PIN digits entered"))
            expectTrue(
                dots.isNotEmpty(),
                "the lock does not draw six dots after a six-digit PIN was set " +
                    "(text=${visibleText().take(200)})"
            )
            note("Lock draws six dots")
            tapText("Cancel")
            expectTrue(
                waitFor("Welcome to Guest Mode", 12_000),
                "Cancel did not return to the guest home"
            )
        }

        verify("16.4", "The emergency code unlocks, and lands on a new-PIN screen") {
            openLock()
            expectTrue(
                waitFor("Forgot PIN? Use your emergency code", 10_000),
                "no recovery link although a code is saved (text=${visibleText().take(200)})"
            )
            tapText("Forgot PIN? Use your emergency code")
            expectTrue(waitFor("Emergency unlock", 8_000), "the recovery dialog did not open")

            val field = device.findObject(By.clazz("android.widget.EditText"))
            expectTrue(field != null, "no code field in the dialog")
            field!!.click()
            pause(400)
            field.setText(recoveryCode.replace("-", ""))
            pause(500)

            tapText("Unlock")
            // The session ends and the app lands on the PIN screen, not the dashboard.
            expectTrue(
                waitForAny("Create your owner PIN", "Choose a new PIN", timeout = 25_000) != null,
                "the code did not lead to a new-PIN screen (screen: ${screen()}; " +
                    "text=${visibleText().take(200)})"
            )
            expectTrue(
                !device.hasObject(By.text("Start Guest Session")),
                "the emergency code dropped the owner on the dashboard with no usable PIN"
            )
            note("Emergency code ended the session and opened the new-PIN screen")
        }

        verify("16.5", "One still frame is stored on the phone, and nothing leaves it") {
            // 16.4 ended the session with the emergency code, so the app is on a
            // new-PIN screen and nothing else can be reached: the owner has no
            // usable PIN yet. Set the six digits again before going anywhere.
            expectTrue(
                waitForAny("Create your owner PIN", "Choose a new PIN", timeout = 12_000) != null,
                "no new-PIN screen after the emergency unlock (text=${visibleText().take(160)})"
            )
            // Pick six explicitly rather than trusting the loaded preference: the
            // screen defaults to four until the stored length arrives, and typing
            // before it does would submit a four-digit PIN.
            tapChip("6 digits")
            pause(600)
            enterPin(sixDigits)
            expectTrue(
                waitFor("Confirm your PIN", 10_000),
                "the six-digit entry did not reach the confirm step (text=${visibleText().take(160)})"
            )
            enterPin(sixDigits)
            expectTrue(
                waitFor("Start Guest Session", 15_000),
                "the re-set PIN was not saved (text=${visibleText().take(160)})"
            )

            // Anything a previous attempt left behind would make the assertion
            // below pass without the unlock in this step storing anything.
            intruderFiles().forEach { runCatching { it.delete() } }

            openSettings()
            expectTrue(
                scrollToText("Photograph failed unlocks", maxSwipes = 10),
                "no intruder-photo row in the paid build (text=${visibleText().take(200)})"
            )
            tapDesc("Intruder photo toggle")
            // First switch-on asks for CAMERA; a second run finds it granted and
            // toggles straight away. Both end in the same subtitle.
            allowCameraIfAsked()
            expectTrue(
                waitForContains("One photo is stored on this phone", 12_000),
                "the feature did not switch on after the permission was granted " +
                    "(text=${visibleText().take(220)})"
            )

            backToDashboard()
            pickAppsAndStart()
            openLock()
            enterPin(wrongPinSix)
            expectTrue(
                waitForContains("Incorrect PIN", 12_000),
                "the wrong PIN was not rejected (text=${visibleText().take(160)})"
            )

            val stored = waitForIntruderPhoto(30_000)
            expectTrue(
                stored != null,
                "no still frame was written to app storage " +
                    "(dir=${intruderFiles().map { it.name }}; log=${recentLog(6)})"
            )
            // A zero-byte file would prove the file plumbing and nothing else.
            // A JPEG starts FF D8, so the first two bytes say a frame was encoded.
            val header = stored!!.readBytes().take(2).map { it.toInt() and 0xFF }
            expectTrue(
                header == listOf(0xFF, 0xD8),
                "the stored file is not a JPEG (${stored.length()} bytes, header=$header)"
            )
            note(
                "Stored ${stored.name} (${stored.length()} bytes); " +
                    "newest first: ${intruderFiles().take(4).map { it.name }}"
            )

            // "Nothing is uploaded" is not something this test can watch for; it
            // is a permission the build does not hold. That is the stronger claim,
            // and it is the one the store listing makes.
            val requested = runCatching {
                context.packageManager
                    .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                    .requestedPermissions?.toList().orEmpty()
            }.getOrDefault(emptyList())
            expectTrue(
                requested.none { it == Manifest.permission.INTERNET },
                "the paid build declares INTERNET, so the photo could leave the phone"
            )
        }
    }

    // ------------------------------------------------------------- helpers

    /**
     * The generated code as rendered ("ABCD-EFGH").
     *
     * Found by shape rather than by position: it is the only text on the screen
     * that is exactly two groups of four symbols from the code alphabet.
     */
    private fun waitForCode(timeoutMs: Long): String? {
        val pattern = Regex("^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val found = runCatching {
                device.findObjects(By.textContains(""))
                    .mapNotNull { it.text }
                    .firstOrNull { pattern.matches(it.trim()) }
            }.getOrNull()
            if (found != null) return found.trim()
            Thread.sleep(400)
        }
        return null
    }

    /** Guest home → the owner PIN pad. */
    private fun openLock() {
        if (!device.hasObject(By.text("Return to Owner"))) {
            scrollToText("Return to Owner", maxSwipes = 6)
        }
        tapText("Return to Owner")
        expectTrue(
            waitFor("Owner only", 12_000),
            "the lock screen did not open (text=${visibleText().take(200)})"
        )
    }

    private fun openSettings() {
        backToDashboard()
        expectTrue(waitFor("Start Guest Session", 20_000), "the dashboard did not come back")
        tapDesc("Settings")
        expectTrue(waitFor("Owner-only controls", 15_000), "Settings did not open")
    }

    private fun backToDashboard() {
        wakeDevice()
        repeat(6) {
            if (runCatching { device.hasObject(By.text("Start Guest Session")) }.getOrDefault(false)) return
            dismissPinnedDialog(timeoutMs = 1_000)
            if (device.hasObject(By.desc("Back"))) tapBack() else device.pressBack()
            pause(800)
        }
    }

    /** Dashboard → allowed apps → tick three → confirm, then start a session. */
    private fun pickAppsAndStart() {
        // The card's action is "Choose apps" only while the allow-list is empty
        // and "Manage" once it is not — and this helper is used twice, the second
        // time with the three apps 16.3 already picked. Tapping the wrong label
        // timed out on the dashboard and cost 16.5 its run.
        if (device.hasObject(By.text("Choose apps"))) {
            tapText("Choose apps")
        } else {
            tapText("Manage")
        }
        expectTrue(
            waitFor("Search apps", 20_000),
            "the app picker did not open (text=${visibleText().take(160)})"
        )
        if (device.hasObject(By.text("Clear"))) {
            tapText("Clear")
            pause(600)
        }
        // Count what is *ticked*, not what is listed. The picker always shows
        // dozens of rows, so counting rows looked like a finished selection and
        // Continue was tapped with nothing chosen — and because the button is
        // disabled until one app is picked, it did nothing at all and the whole
        // pass sat on the picker.
        var guard = 0
        while ((selectedApps() ?: 0) < 3 && guard < 24) {
            guard++
            val row = appRows().getOrNull(guard - 1)
            if (row == null) {
                val x = device.displayWidth / 2
                device.swipe(
                    x, (device.displayHeight * 0.75f).toInt(),
                    x, (device.displayHeight * 0.45f).toInt(), 18
                )
                pause(500)
                continue
            }
            runCatching { row.click() }
            pause(450)
            if (device.hasObject(By.text("Allow anyway"))) {
                tapText("Allow anyway")
                pause(400)
            }
        }
        expectTrue(
            (selectedApps() ?: 0) >= 3,
            "only ${selectedApps() ?: 0} apps were selected (counter: ${selectionCounter() ?: "none"})"
        )
        hideKeyboard()
        tapText("Continue")
        expectTrue(
            waitFor("One last look before you hand over", 15_000),
            "the confirm screen did not open (counter: ${selectionCounter() ?: "none"}; " +
                "text=${visibleText().take(160)})"
        )
        tapText("Start Guest Mode")
        pause(1_500)
        repeat(14) {
            when {
                device.hasObject(By.text("Welcome to Guest Mode")) -> return
                pinnedDialogVisible() -> dismissPinnedDialog(timeoutMs = 5_000)
                device.hasObject(By.res("com.android.permissioncontroller:id/permission_allow_button")) -> {
                    runCatching {
                        device.findObject(
                            By.res("com.android.permissioncontroller:id/permission_allow_button")
                        )?.click()
                    }
                    pause(800)
                }
                else -> pause(900)
            }
        }
        expectTrue(
            waitFor("Welcome to Guest Mode", 20_000),
            "the session did not start (screen: ${screen()}; text=${visibleText().take(200)})"
        )
    }

    /** Everything in the app's own intruder folder, newest first. */
    private fun intruderFiles(): List<File> = runCatching {
        File(context.filesDir, "intruder").listFiles()
            ?.filter { it.isFile && it.name.endsWith(".jpg") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }.getOrDefault(emptyList())

    /**
     * Waits for a photo to land.
     *
     * The capture runs on Dispatchers.IO and opens a camera session, so the file
     * appears a second or two after the wrong PIN — never instantly.
     */
    private fun waitForIntruderPhoto(timeoutMs: Long): File? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            intruderFiles().firstOrNull { it.length() > 0 }?.let { return it }
            Thread.sleep(400)
        }
        return intruderFiles().firstOrNull()?.takeIf { it.length() > 0 }
    }

    /**
     * Accepts the CAMERA prompt if it appeared.
     *
     * The resource id has changed between releases and the buttons differ across
     * API levels, so this tries the ids first and the visible labels second. It
     * never fails: when the permission is already granted there is no dialog, and
     * the caller's assertion on the subtitle is what decides the step.
     */
    private fun allowCameraIfAsked() {
        val ids = listOf(
            "com.android.permissioncontroller:id/permission_allow_button",
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
            "com.android.permissioncontroller:id/permission_allow_one_time_button"
        )
        val labels = listOf("Allow", "While using the app", "Only this time")
        repeat(8) {
            val button = ids.firstNotNullOfOrNull { id ->
                runCatching { device.findObject(By.res(id)) }.getOrNull()
            } ?: labels.firstNotNullOfOrNull { label ->
                runCatching { device.findObject(By.text(label)) }.getOrNull()
            }
            if (button != null) {
                runCatching { button.click() }
                pause(900)
                return
            }
            pause(400)
        }
    }

    private fun appRows() = runCatching {
        device.findObjects(By.descContains("App row "))
    }.getOrDefault(emptyList())

    /** The picker's own tally, e.g. "3 apps selected". */
    private fun selectionCounter(): String? = runCatching {
        device.findObjects(By.textContains("apps selected")).firstOrNull()?.text
    }.getOrNull()

    private fun selectedApps(): Int? =
        Regex("(\\d+) apps selected").find(selectionCounter() ?: "")?.groupValues?.get(1)?.toIntOrNull()

    /** The IME covers the picker's bottom bar, Continue included. */
    private fun hideKeyboard() {
        repeat(2) {
            val shown = shell("dumpsys input_method").lineSequence().any { it.contains("mInputShown=true") }
            if (!shown) return
            device.pressBack()
            pause(700)
        }
    }
}
