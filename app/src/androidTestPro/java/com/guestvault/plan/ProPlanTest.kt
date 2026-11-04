package com.guestvault.plan

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import com.guestvault.domain.policy.SchedulePolicy
import java.time.LocalDate
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pass 15 — the paid build.
 *
 * The free plan (passes 1–14) grades `com.guestvault`; this runs on
 * `com.guestvault.pro`, where the time-limits screen is not behind a lock. It
 * exists because the two features this pass covers — per-app budgets and
 * schedules — are exactly the ones the free build is supposed to *refuse*, so
 * grading them there would only ever prove the lock works.
 *
 * Everything is driven through the UI, the way an owner would, and every
 * assertion is about something the guest or owner can actually see.
 */
@RunWith(AndroidJUnit4::class)
class ProPlanTest : StepHarness() {

    private val budgetMinutes = 5

    /** The app the picker gave us; filled in by [allowOneApp]. */
    private var allowedLabel = ""

    @Before
    fun setUp() {
        prepare()
    }

    @Test
    fun pass15() {
        arrange { ensureOwnerHome() }
        // Clock, not Camera: the plan's emulator is started with
        // `-camera-back none -camera-front none`, so the camera finishes on its
        // own and the guest is back on the guest home before a budget can count.
        arrange { allowOneApp(search = "clock") }
        arrange { backToDashboard() }

        verify("15.1", "The real screen: \"How a budget is counted\", a PER APP list naming the allowed app") {
            openTimeRules()
            expectTrue(
                waitFor("How a budget is counted", 12_000),
                "the time-limits screen did not open (text=${visibleText().take(160)})"
            )
            expectTrue(
                runCatching { device.hasObject(By.text("PER APP")) }.getOrDefault(false),
                "no PER APP section (text=${visibleText().take(160)})"
            )
            // The one thing that must not be here: the free build's locked panel.
            expectTrue(
                !runCatching { device.hasObject(By.text("This is the free build")) }.getOrDefault(false),
                "the paid build showed the free-build lock panel"
            )
            expectTrue(
                runCatching { device.hasObject(By.text(allowedLabel)) }.getOrDefault(false),
                "the allowed app ($allowedLabel) is not listed (text=${visibleText().take(180)})"
            )
            note("Paid build shows the real limits screen for $allowedLabel")
        }

        verify("15.2", "The schedule switch and its window appear, and the subtitle stops saying \"No schedule\"") {
            expectTrue(
                scrollToText("Only allow guest mode at set times", maxSwipes = 8),
                "the schedule row is not on the screen (text=${visibleText().take(180)})"
            )
            expectTrue(
                waitForContains("No schedule", 6_000),
                "the subtitle did not start from \"No schedule\" (text=${visibleText().take(180)})"
            )
            val found = device.findObject(By.desc("Schedule toggle"))
            expectTrue(found != null, "no schedule toggle")
            val scheduleSwitch = found!!
            expectTrue(
                !scheduleSwitch.isChecked,
                "the schedule was already on before this step touched it"
            )
            scheduleSwitch.click()
            expectTrue(
                waitFor("Guest mode opens at", 10_000),
                "turning the schedule on showed no time window (text=${visibleText().take(180)})"
            )
            expectTrue(
                device.findObject(By.desc("Schedule toggle"))?.isChecked == true,
                "the switch did not stay on"
            )
            note("Window ${SchedulePolicy.formatMinute(7 * 60)}–${SchedulePolicy.formatMinute(22 * 60)} exposed by default")
        }

        verify("15.3", "Unticking today names the pause and says when guest mode opens again") {
            val today = SchedulePolicy.shortDay(LocalDate.now().dayOfWeek.value)
            expectTrue(
                scrollToText("Days", maxSwipes = 8),
                "the day picker is not on the screen (text=${visibleText().take(180)})"
            )
            expectTrue(tapDayChip(today), "the day chip \"$today\" could not be reached")
            expectTrue(
                waitForContains("paused until", 10_000),
                "unticking $today did not pause guest mode (text=${visibleText().take(200)})"
            )
            note("Subtitle: ${device.findObjects(By.textContains("paused until")).firstOrNull()?.text}")
        }

        verify("15.4", "The dashboard and the confirm screen both state the pause; no session starts") {
            backToDashboard()
            expectTrue(
                waitForContains("paused until", 10_000),
                "the dashboard shows no schedule banner (text=${visibleText().take(180)})"
            )
            tapText("Start Guest Session")
            expectTrue(
                waitFor("One last look before you hand over", 15_000),
                "the confirm screen did not open"
            )
            tapText("Start Guest Mode")
            expectTrue(
                waitForContains("paused until", 10_000),
                "the confirm screen did not explain why it refused (text=${visibleText().take(200)})"
            )
            expectTrue(
                !runCatching { device.hasObject(By.text("Welcome to Guest Mode")) }.getOrDefault(false),
                "a guest session started even though the schedule is shut"
            )
        }

        verify("15.5", "Schedule off, budget $budgetMinutes min on $allowedLabel, and the row says so") {
            backToDashboard()
            openTimeRules()
            expectTrue(waitFor("How a budget is counted", 12_000), "the limits screen did not reopen")

            expectTrue(
                scrollToText("Only allow guest mode at set times", maxSwipes = 8),
                "the schedule row is gone (text=${visibleText().take(180)})"
            )
            val scheduleSwitch = device.findObject(By.desc("Schedule toggle"))
            expectTrue(
                scheduleSwitch != null && scheduleSwitch.isChecked,
                "the schedule switch is not on any more"
            )
            scheduleSwitch!!.click()
            expectTrue(
                waitForContains("No schedule", 10_000),
                "switching the schedule off did not restore the open state (text=${visibleText().take(180)})"
            )

            expectTrue(
                scrollToText(allowedLabel, maxSwipes = 8),
                "$allowedLabel is not listed under PER APP (text=${visibleText().take(180)})"
            )
            tapText(allowedLabel)
            val choice = "$budgetMinutes min"
            expectTrue(
                waitForContains("How long may a guest use this app", 8_000) &&
                    waitFor(choice, 8_000),
                "the budget dialog did not offer $choice (text=${visibleText().take(180)})"
            )
            tapText(choice)
            pause(900)
            expectTrue(
                waitForContains("Budgeted", 10_000),
                "the row still says No limit after picking $choice (text=${visibleText().take(200)})"
            )
        }

        verify("15.6", "The budget is counted: the tile said \"$budgetMinutes min left\", then \"Time's up\", and the app cannot reopen") {
            backToDashboard()
            tapText("Start Guest Session")
            expectTrue(
                waitFor("One last look before you hand over", 15_000),
                "the confirm screen did not open after the schedule was switched off"
            )
            startSessionAndWait()

            expectTrue(
                waitForContains("min left", 10_000),
                "the guest tile shows no budget caption (text=${visibleText().take(200)})"
            )

            // Open the budgeted app the way a guest would: tap its tile. The app
            // itself does not matter — the clock runs while the guest home is not
            // in front, which is the rule this step is grading.
            val found = device.findObjects(By.descContains("Open ")).firstOrNull()
            expectTrue(found != null, "no app tile on the guest home (text=${visibleText().take(200)})")
            val tile = found!!
            val tileLabel = runCatching { tile.contentDescription }.getOrNull() ?: "the tile"
            tile.click()
            // A cold first launch on a fresh emulator takes seconds, so this
            // waits instead of sampling once.
            expectTrue(
                waitGone("Welcome to Guest Mode", 30_000),
                "tapping \"$tileLabel\" did not open the app, so no budget could be counted " +
                    "(text=${visibleText().take(200)})"
            )
            note("opened $tileLabel")

            // An app that bounces straight back to the guest home never counts
            // down. Say so in a minute rather than in seven.
            pause(60_000)
            if (runCatching { device.hasObject(By.text("Welcome to Guest Mode")) }.getOrDefault(false)) {
                throw AssertionError(
                    "$tileLabel closed immediately, so the budget could not be counted " +
                        "(text=${visibleText().take(200)})"
                )
            }

            val deadline = System.currentTimeMillis() + (budgetMinutes + 2) * 60_000L
            var backHome = false
            var lastWake = System.currentTimeMillis()
            while (System.currentTimeMillis() < deadline) {
                if (runCatching { device.hasObject(By.text("Welcome to Guest Mode")) }.getOrDefault(false)) {
                    backHome = true
                    break
                }
                // The emulator dozes off during a wait this long, and a locked
                // screen would hide the very screen this step is waiting for.
                if (System.currentTimeMillis() - lastWake > 30_000) {
                    wakeDevice()
                    lastWake = System.currentTimeMillis()
                }
                Thread.sleep(3_000)
            }
            expectTrue(
                backHome,
                "the guest was not returned after $budgetMinutes minutes " +
                    "(screen: ${screen()}; text=${visibleText().take(200)})"
            )
            expectTrue(
                waitForContains("Time's up", 15_000),
                "the tile does not say \"Time's up\" (text=${visibleText().take(200)})"
            )

            val exhaustedTile = device.findObjects(By.descContains("Open ")).firstOrNull()
            exhaustedTile?.click()
            pause(3_000)
            expectTrue(
                runCatching { device.hasObject(By.text("Welcome to Guest Mode")) }.getOrDefault(false),
                "the app reopened after its budget was spent — the limit is decoration, not enforcement"
            )
            note("Guest was returned to the guest home after ${budgetMinutes}m; the tile reads \"Time's up\"")
        }
    }

    // ------------------------------------------------------------- helpers

    /**
     * Allows exactly one app, by searching for it.
     *
     * One app rather than three: the limits screen sorts its rows by label and
     * the guest home sorts its tiles its own way, so a single allowed app is the
     * only way to be certain the tile this pass budgets is the tile it taps.
     */
    private fun allowOneApp(search: String) {
        expectTrue(waitFor("Start Guest Session", 25_000), "not on the dashboard")
        if (device.hasObject(By.text("Choose apps"))) tapText("Choose apps") else tapText("Manage")
        expectTrue(waitFor("Search apps", 20_000), "the app picker did not open")
        if (device.hasObject(By.text("Clear"))) {
            tapText("Clear")
            pause(600)
        }

        val found = device.findObject(By.clazz("android.widget.EditText"))
        expectTrue(found != null, "no search field in the picker")
        val field = found!!
        field.click()
        pause(400)
        field.setText(search)
        pause(900)
        dismissKeyboard()

        var rows = appRows()
        if (rows.isEmpty()) {
            // The accessibility set-text can be swallowed on a cold start.
            field.click()
            pause(300)
            shell("input text $search")
            pause(1_200)
            dismissKeyboard()
            rows = appRows()
        }
        expectTrue(rows.isNotEmpty(), "searching \"$search\" matched nothing (text=${visibleText().take(180)})")

        val row = rows.first()
        allowedLabel = (row.contentDescription ?: "").removePrefix("App row ").trim()
        row.click()
        pause(700)
        // A sensitive app asks for confirmation before it can be allowed.
        if (device.hasObject(By.text("Allow anyway"))) {
            tapText("Allow anyway")
            pause(600)
        }
        dismissKeyboard()
        tapText("Continue")
        expectTrue(
            waitFor("One last look before you hand over", 20_000),
            "the confirm screen did not open after choosing $allowedLabel"
        )
        expectTrue(allowedLabel.isNotEmpty(), "the chosen app has no label")
    }

    /** True once [text] is no longer on screen — a launch that never lands must not pass. */
    private fun waitGone(text: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!runCatching { device.hasObject(By.text(text)) }.getOrDefault(false)) return true
            Thread.sleep(500)
        }
        return false
    }

    private fun appRows(): List<UiObject2> =
        runCatching { device.findObjects(By.descContains("App row ")) }.getOrDefault(emptyList())

    private fun dismissKeyboard() {
        repeat(2) {
            val shown = shell("dumpsys input_method")
                .lineSequence().any { it.contains("mInputShown=true") }
            if (!shown) return
            device.pressBack()
            pause(700)
        }
    }

    /**
     * Taps a day chip, scrolling the chip strip sideways if it is off-screen.
     *
     * Seven chips do not fit a phone: "Sun" starts outside the viewport, and the
     * strip only scrolls horizontally, so scrolling the page never reaches it.
     */
    private fun tapDayChip(name: String): Boolean {
        repeat(4) { attempt ->
            val chip = runCatching { device.findObject(By.text(name)) }.getOrNull()
            if (chip != null) {
                val bounds = runCatching { chip.visibleBounds }.getOrNull()
                val onScreen = bounds != null && bounds.width() > 0 &&
                    bounds.left >= 0 && bounds.right <= device.displayWidth
                if (onScreen) {
                    chip.click()
                    pause(700)
                    return true
                }
            }
            val y = runCatching {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    .firstNotNullOfOrNull { day ->
                        device.findObject(By.text(day))?.visibleBounds?.centerY()
                    }
            }.getOrNull() ?: (device.displayHeight * 0.62f).toInt()
            if (attempt == 3) {
                // Last try: tap it even if its bounds say it is off-screen.
                if (chip != null) {
                    chip.click()
                    pause(700)
                    return true
                }
            }
            device.swipe(
                (device.displayWidth * 0.9f).toInt(), y,
                (device.displayWidth * 0.15f).toInt(), y, 20
            )
            pause(700)
        }
        return false
    }

    private fun openTimeRules() {
        openSettings()
        expectTrue(
            reachRow("Time limits"),
            "the Time limits row is not in Settings (text=${visibleText().take(200)})"
        )
        tapText("Time limits")
        pause(1_500)
    }

    private fun openSettings() {
        backToDashboard()
        expectTrue(waitFor("Start Guest Session", 20_000), "the dashboard did not come back")
        tapDesc("Settings")
        expectTrue(waitFor("Owner-only controls", 15_000), "Settings did not open")
    }

    /** Backs out of whatever is open until the dashboard is in front. */
    private fun backToDashboard() {
        wakeDevice()
        repeat(6) {
            if (runCatching { device.hasObject(By.text("Start Guest Session")) }.getOrDefault(false)) return
            dismissPinnedDialog(timeoutMs = 1_000)
            if (device.hasObject(By.desc("Back"))) tapBack() else device.pressBack()
            pause(800)
        }
    }

    private fun reachRow(row: String, timeoutMs: Long = 15_000): Boolean {
        if (runCatching { device.hasObject(By.text(row)) }.getOrDefault(false)) return true
        if (scrollUntil(timeoutMs) { device.hasObject(By.text(row)) }) return true
        // One rebuild from a cold start rather than failing on a page that has
        // stopped moving.
        backToDashboard()
        openSettingsWithoutBack()
        return runCatching { device.hasObject(By.text(row)) }.getOrDefault(false)
    }

    private fun openSettingsWithoutBack() {
        expectTrue(waitFor("Start Guest Session", 20_000), "the dashboard did not come back")
        tapDesc("Settings")
        expectTrue(waitFor("Owner-only controls", 15_000), "Settings did not open")
    }

    /** Taps Start Guest Mode and clears the notification and pinning prompts. */
    private fun startSessionAndWait() {
        tapText("Start Guest Mode")
        pause(1_500)
        repeat(14) {
            when {
                runCatching { device.hasObject(By.text("Welcome to Guest Mode")) }.getOrDefault(false) -> return
                pinnedDialogVisible() -> dismissPinnedDialog(timeoutMs = 5_000)
                device.hasObject(By.res("com.android.permissioncontroller:id/permission_allow_button")) -> {
                    runCatching {
                        device.findObject(By.res("com.android.permissioncontroller:id/permission_allow_button"))?.click()
                    }
                    pause(800)
                }
                device.hasObject(By.text("Allow")) -> {
                    tapText("Allow")
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
}
