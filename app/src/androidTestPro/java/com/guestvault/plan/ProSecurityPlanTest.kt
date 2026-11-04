package com.guestvault.plan

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
        tapText("Choose apps")
        expectTrue(waitFor("Search apps", 20_000), "the app picker did not open")
        if (device.hasObject(By.text("Clear"))) {
            tapText("Clear")
            pause(600)
        }
        var guard = 0
        while (appRows().size < 3 && guard < 24) {
            guard++
            val row = appRows().getOrNull(guard - 1) ?: run {
                device.swipe(
                    device.displayWidth / 2, (device.displayHeight * 0.75f).toInt(),
                    device.displayWidth / 2, (device.displayHeight * 0.45f).toInt(), 18
                )
                pause(500)
                null
            } ?: continue
            runCatching { row.click() }
            pause(450)
            if (device.hasObject(By.text("Allow anyway"))) {
                tapText("Allow anyway")
                pause(400)
            }
        }
        expectTrue(appRows().size >= 3, "could not select three apps")
        tapText("Continue")
        expectTrue(
            waitFor("One last look before you hand over", 15_000),
            "the confirm screen did not open"
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

    private fun appRows() = runCatching {
        device.findObjects(By.descContains("App row "))
    }.getOrDefault(emptyList())
}
