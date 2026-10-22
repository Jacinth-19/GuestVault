package com.guestvault.plan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One `@Test` per pass of the numbered manual test plan.
 *
 * The CI script invokes a single method at a time (`-e class ...#passNN`) and
 * interleaves host actions between phases (clear, force-stop, reboot, uninstall)
 * exactly where the plan needs them. A pass that needs two device phases is
 * split into `pass07a` / `pass07b` and so on.
 *
 * A failing step is recorded, not thrown: the plan's point is a verdict for
 * every numbered row, and one broken row must not hide the rest.
 */
@RunWith(AndroidJUnit4::class)
class StepPlanTest : StepHarness() {

    private val wrongPin = listOf("1", "1", "1", "1")

    @Before
    fun setUp() {
        prepare()
    }

    // ================================================================ Pass 1

    @Test
    fun pass01a() {
        device.pressHome()
        pause(600)
        // CLEAR_TASK recreates the activity so the splash composes again; the
        // debug-only hold keeps it on screen long enough to photograph.
        shell(
            "am start -n ${context.packageName}/.MainActivity --activity-clear-task " +
                "--ei ${context.packageName}.extra.SPLASH_HOLD_MS 4000"
        )
        var splashText = false
        var firstLuma = -1.0
        var indigo = false
        val deadline = System.currentTimeMillis() + 6_000

        while (System.currentTimeMillis() < deadline) {
            val frame = screenshot()
            if (frame != null) {
                if (firstLuma < 0) firstLuma = luma(frame)
                if (isIndigo(frame)) indigo = true
            }
            if (!splashText &&
                (device.hasObject(By.text("Borrow my phone. Keep your privacy.")) ||
                    device.hasObject(By.text("1.2.0")))
            ) {
                splashText = true
            }
            if (indigo && splashText) break
            Thread.sleep(200)
        }

        verify("1.1", "Indigo splash, shield mark, \"GuestVault\", tagline. Nothing white/twitchy before it") {
            expectTrue(
                firstLuma >= 0 && firstLuma < 245,
                "first frame was near-white (luminance ${"%.0f".format(firstLuma)})"
            )
            expectTrue(indigo, "no indigo (#312E81) frame was captured during the splash")
            expectTrue(splashText, "the splash tagline never appeared")
        }

        verify("1.2", "Auto-advances to onboarding — no tap needed") {
            expectTrue(
                waitFor("Lend your phone safely", 8_000),
                "onboarding did not appear on its own; still on ${textContaining("GuestVault")}"
            )
        }

        verify("1.3", "Page 2 \"Only the apps you allow\", dot indicator animates wider") {
            var title = waitForAny("Only the apps you allow", "Only approved apps", timeout = 3_000)
            repeat(3) {
                if (title == null) {
                    swipeLeft()
                    title = waitForAny("Only the apps you allow", "Only approved apps", timeout = 5_000)
                }
            }
            expectTrue(
                title == "Only the apps you allow",
                "page 2 is titled \"${title ?: "nothing"}\""
            )
        }

        verify("1.4", "Dots track correctly both directions") {
            swipeRight()
            expectTrue(waitFor("Lend your phone safely", 8_000), "swiping back did not return to page 1")
        }

        verify("1.5", "Jumps straight to PIN setup") {
            tapText("Skip")
            expectTrue(waitFor("Create your owner PIN", 12_000), "Skip did not land on PIN setup")
        }
    }

    @Test
    fun pass01b() {
        launchApp()
        expectTrue(waitFor("Lend your phone safely", 25_000), "onboarding did not appear on a fresh install")

        verify("1.6", "Page 3 button reads Get Started; Skip is gone on the last page") {
            tapText("Next")
            pause(700)
            tapText("Next")
            pause(900)
            val lastPage = waitForAny("Your data stays private", "Get Started", timeout = 8_000)
            expectTrue(lastPage != null, "never reached page 3")
            expectTrue(
                waitFor("Get Started", 6_000),
                "page 3 button is not \"Get Started\" (screen: ${visibleText().take(120)})"
            )
            expectTrue(!device.hasObject(By.text("Skip")), "Skip is still visible on the last page")
        }
    }

    // ================================================================ Pass 2

    @Test
    fun pass02() {
        ensurePinSetup()

        verify("2.2", "Dots empty correctly") {
            val empty = screenshot()
            expectTrue(empty != null, "could not capture the PIN screen")
            enterPin(listOf("4", "7"))
            val filled = screenshot()
            expectTrue(empty != null && filled != null, "could not capture the dots")
            expectTrue(difference(empty!!, filled!!) > 0.002, "typing two digits changed nothing on screen")
            tapDesc("Delete digit")
            pause(300)
            tapDesc("Delete digit")
            pause(500)
            val cleared = screenshot()
            expectTrue(cleared != null, "could not capture the cleared dots")
            expectTrue(
                difference(cleared!!, empty) < 0.002,
                "after two backspaces the screen does not match the empty state (${"%.4f".format(difference(cleared, empty))})"
            )
        }

        verify("2.1", "Dots fill, keyboard auto-advances on the 4th digit") {
            enterPin(pinDigits)
            expectTrue(waitFor("Confirm your PIN", 8_000), "no auto-advance after the 4th digit")
        }

        verify("2.3", "Inline error, stays on confirm, no crash") {
            enterPin(wrongPin)
            pause(1_200)
            val error = waitForAny("Try again", "didn't match", "doesn't match", timeout = 6_000)
                ?: waitForContains("Try again", 4_000).let { if (it) "Try again" else null }
            expectTrue(error != null, "no inline mismatch error (screen: ${visibleText().take(150)})")
            expectTrue(
                device.hasObject(By.text("Confirm your PIN")),
                "showed \"$error\" but left the confirm screen (title is now \"${visibleText().take(80)}\")"
            )
        }

        verify("2.5", "Cannot escape to the dashboard without a PIN") {
            val backLanded = mutableListOf<String>()
            repeat(3) {
                device.pressBack()
                pause(1_000)
                backLanded += screen()
                expectTrue(
                    !device.hasObject(By.text("Start Guest Session")),
                    "Back reached the dashboard without a PIN"
                )
            }
            note("Back landed on: ${backLanded.joinToString()} (never the dashboard)")
        }

        verify("2.4", "Lands on dashboard") {
            ensurePinSetup()
            enterPin(pinDigits)
            expectTrue(waitFor("Confirm your PIN", 8_000), "confirm screen missing")
            enterPin(pinDigits)
            expectTrue(waitFor("Start Guest Session", 20_000), "did not land on the dashboard")
        }
    }

    // ================================================================ Pass 3

    @Test
    fun pass03() {
        arrange { ensureOwnerHome() }

        verify("3.1", "\"READY TO LEND\", big white Start Guest Session, \"Exit with your PIN\"") {
            expectTrue(device.hasObject(By.text("READY TO LEND")), "no READY TO LEND badge")
            expectTrue(device.hasObject(By.text("Start Guest Session")), "no Start Guest Session button")
            expectTrue(
                device.findObjects(By.textContains("Exit with your PIN")).isNotEmpty(),
                "no \"Exit with your PIN\" caption"
            )
        }

        verify("3.2", "Allowed apps = 0, Lock method = PIN + bio, Auto-wipe = On (defaults)") {
            expectTrue(device.hasObject(By.text("Allowed apps")), "no Allowed apps stat")
            expectTrue(device.hasObject(By.text("PIN + bio")), "lock method is not \"PIN + bio\"")
            expectTrue(device.hasObject(By.text("On")), "auto-wipe is not On")
            expectTrue(device.hasObject(By.text("0")), "allowed apps is not 0")
        }

        verify("3.3", "\"Choose apps\" action") {
            expectTrue(device.hasObject(By.text("Choose apps")), "no Choose apps action on the empty card")
        }

        verify("3.4", "Settings opens") {
            tapDesc("Settings")
            expectTrue(waitFor("Owner-only controls", 12_000), "settings did not open")
        }

        verify("3.5", "Version line — note what it says (this is the 1.0.0 bug)") {
            device.pressBack()
            pause(700)
            val version = scrollToVersionLine()
            expectTrue(version != null, "no version line found at the bottom of the dashboard")
            expectContains(version, "GuestVault", "version line does not name the app")
            expectContains(
                version,
                com.guestvault.core.AppVersion.display,
                "the footer does not show the build's own version"
            )
            note("footer reads: $version")
        }

        verify("3.6", "About page with the honest-limitations copy") {
            scrollToText("Learn more")
            tapText("Learn more")
            val about = waitForAny("About GuestVault", "What needs system access", timeout = 12_000)
            expectTrue(about != null, "About page did not open")
            expectTrue(
                device.findObjects(By.textContains("honest about the line between an app and the OS")).isNotEmpty(),
                "no honest-limitations copy on the About page"
            )
        }
    }

    // ================================================================ Pass 4

    @Test
    fun pass04() {
        arrange { ensureOwnerHome() }
        arrange { openAppPicker() }

        verify("4.1", "Search bar, \"Use recommended\", \"Clear\", app list") {
            // The catalogue is a PackageManager query; on a cold start it can
            // still be loading when the picker opens.
            var waited = 0
            while (appRows().isEmpty() && waited < 20) {
                pause(500)
                waited++
            }
            expectTrue(device.hasObject(By.text("Search apps")), "no search bar")
            expectTrue(device.hasObject(By.text("Use recommended")), "no Use recommended")
            expectTrue(device.hasObject(By.text("Clear")), "no Clear")
            expectTrue(appRows().size >= 2, "app list is empty")
        }

        verify("4.2", "List filters live") {
            val before = appRows().size
            search("cam")
            val after = appRows().size
            expectTrue(
                after in 1 until before,
                "searching \"cam\" left $after rows (was $before)"
            )
            expectTrue(
                appRows().any { it.contentDescription?.contains("Camera", true) == true },
                "Camera is not in the filtered list: ${appRows().map { it.contentDescription }}"
            )
        }

        verify("4.3", "Empty state \"No apps match\"") {
            search("zzzz")
            expectTrue(waitFor("No apps match", 6_000), "no empty state for a query that matches nothing")
        }

        verify("4.4", "A safe starter set gets ticked; counter updates") {
            clearSearch()
            tapText("Use recommended")
            pause(900)
            val counter = counterText()
            expectTrue(counter != null && !counter.startsWith("0 "), "counter still says ${counter ?: "nothing"}")
            expectTrue(appRows().isNotEmpty(), "no rows after Use recommended")
        }

        verify("4.5", "All unticked, counter → 0") {
            tapText("Clear")
            pause(900)
            val counter = counterText()
            expectTrue(counter == "0 apps selected", "counter reads ${counter ?: "nothing"}")
        }

        verify(
            "4.6",
            "They're marked sensitive — ticking one should warn you. Confirm the warning names the risk"
        ) {
            val missing = mutableListOf<String>()
            val noWarning = mutableListOf<String>()
            val installed = shell("pm list packages")
            val packageFor = mapOf(
                "Settings" to "com.android.settings",
                "Play Store" to "com.android.vending",
                "Messages" to "com.google.android.apps.messaging",
                "Contacts" to "com.google.android.contacts",
                "Photos" to "com.google.android.apps.photos"
            )
            listOf("Settings", "Play", "Messages", "Contacts", "Photos").forEach { label ->
                val pkg = packageFor[label] ?: ""
                if (pkg.isNotEmpty() && !installed.contains("package:$pkg")) {
                    note("$label is not installed on this image, skipped")
                    return@forEach
                }
                search(label)
                var ticks = 0
                while (appRows().isEmpty() &&
                    !device.hasObject(By.text("No apps match")) && ticks < 8
                ) {
                    pause(400)
                    ticks++
                }
                if (appRows().isEmpty()) {
                    // The row may be below the chips and the search field.
                    scrollToText(label)
                    pause(500)
                }
                if (appRows().isEmpty()) {
                    // Not every image ships every app; only a row that is
                    // offered but unmarked is a failure.
                    note("$label is not offered by this device's picker")
                    return@forEach
                }
                if (!device.hasObject(By.text("Sensitive"))) {
                    missing += "$label (no Sensitive pill)"
                }
                device.findObjects(By.descContains("App row ")).firstOrNull()?.click()
                pause(900)
                val dialogTitle = device.findObjects(By.textContains("Allow ")).mapNotNull { it.text }
                    .firstOrNull { it.endsWith("?") }
                val riskBody = device.findObjects(By.textContains("risk")).mapNotNull { it.text }
                    .firstOrNull()
                if (dialogTitle == null || riskBody == null) {
                    noWarning += "$label (title=${dialogTitle ?: "none"})"
                }
                if (device.hasObject(By.text("Cancel"))) {
                    tapText("Cancel")
                } else {
                    device.pressBack()
                }
                pause(700)
            }
            expectTrue(missing.isEmpty(), "sensitive marking missing for: ${missing.joinToString()}")
            expectTrue(
                noWarning.isEmpty(),
                "ticking a sensitive app showed no warning dialog for: ${noWarning.joinToString()} " +
                    "(only the static Sensitive pill exists)"
            )
        }

        verify("4.7", "Confirm screen, summary says \"3 apps\"") {
            clearSearch()
            tapText("Clear")
            pause(600)
            pickVisibleApps(3)
            tapText("Continue")
            expectTrue(waitFor("One last look before you hand over", 12_000), "confirm screen did not open")
            val summary = textContaining("apps")
            expectTrue(
                device.findObjects(By.textContains("3 apps")).isNotEmpty() || summary?.contains("3 apps") == true,
                "summary does not say 3 apps: ${summary ?: "nothing"}"
            )
        }
    }

    // ================================================================ Pass 5

    @Test
    fun pass05a() {
        arrange { ensureOwnerHome() }
        arrange { pickApps(3) }

        verify("5.1", "Only one selected at a time") {
            // The plan names the five chips literally: No limit / 5 / 15 / 30 / 60 min.
            val wanted = listOf("No limit", "5 min", "15 min", "30 min", "60 min")
            scrollToText("Session length", maxSwipes = 8)
            pause(600)
            val anchor = device.findObject(By.text("No limit")) ?: device.findObject(By.text("5 min"))
            val rowY = runCatching { anchor!!.visibleCenter.y }.getOrDefault(device.displayHeight / 2)
            val seen = mutableSetOf<String>()
            var swipes = 0
            while (swipes < 5) {
                wanted.filter { device.hasObject(By.text(it)) }.forEach { seen += it }
                if (seen.containsAll(wanted)) break
                device.swipe(
                    (device.displayWidth * 0.85f).toInt(), rowY,
                    (device.displayWidth * 0.2f).toInt(), rowY, 10
                )
                pause(600)
                swipes++
            }
            wanted.filter { device.hasObject(By.text(it)) }.forEach { seen += it }
            val absent = wanted - seen
            expectTrue(
                absent.isEmpty(),
                "chips missing: ${absent.joinToString()} — the row offers " +
                    "${visibleText().split(" | ").filter { it.contains("min") || it.contains("limit") || it.contains("hour") }}"
            )
            tapChipUntil("15 min", "Ends automatically after 15 min")
            expectTrue(
                waitForTextScan("Ends automatically after 15 min", 5_000),
                "choosing 15 min is not reflected (selection reads: ${selectedChips()}; " +
                    "text=${visibleText().take(160)})"
            )
            tapChipUntil("No limit", "No time limit")
            expectTrue(
                waitForTextScan("No time limit", 5_000),
                "only one chip may be chosen, but No limit did not replace 15 min " +
                    "(text=${visibleText().take(160)})"
            )
            expectTrue(
                !visibleText().contains("Ends automatically after 15 min"),
                "both 15 min and No limit are marked as chosen"
            )
        }

        verify("5.2", "State persists through the toggle") {
            val toggle = device.findObject(By.desc("Auto-wipe toggle"))
            expectTrue(toggle != null, "no auto-wipe toggle")
            val start = toggle!!.isChecked
            toggle.click()
            pause(600)
            val off = device.findObject(By.desc("Auto-wipe toggle"))!!.isChecked
            device.findObject(By.desc("Auto-wipe toggle"))!!.click()
            pause(600)
            val back = device.findObject(By.desc("Auto-wipe toggle"))!!.isChecked
            expectTrue(off != start, "toggling auto-wipe did not change its state")
            expectTrue(back == start, "toggling back did not restore the state")
        }

        verify("5.3", "Explains the one-time Android confirmation") {
            expectTrue(
                device.findObjects(By.textContains("A system confirmation appears once")).isNotEmpty(),
                "the Pin-the-screen row does not explain the one-time confirmation"
            )
            device.findObject(By.desc("Screen pinning toggle"))?.click()
            pause(500)
            expectTrue(
                device.findObjects(By.textContains("A system confirmation appears once")).isNotEmpty(),
                "the explanation disappears when the toggle is used"
            )
            device.findObject(By.desc("Screen pinning toggle"))?.click()
            pause(400)
        }

        verify("5.4", "Says plainly that Android can't truly wipe other apps' data") {
            scrollToText("Honest limits", maxSwipes = 12)
            val card = listOf("erase another app", "remain available", "wipe only clears")
                .flatMap { phrase ->
                    runCatching {
                        device.findObjects(By.textContains(phrase)).mapNotNull { it.text }
                    }.getOrDefault(emptyList())
                }
                .firstOrNull { it.contains("wipe", ignoreCase = true) }
            expectTrue(
                card != null,
                "the Honest limits card reads \"${card ?: visibleText().take(180)}\" — " +
                    "it does not mention wiping other apps"
            )
        }

        verify("5.5", "On Android 13+: one notification-permission prompt, then the session starts either way") {
            val note = startSessionHere(minutes = 15, acceptPin = false, permission = "allow")
            expectContains(note, "permission: allowed", "no notification prompt appeared ($note)")
            expectTrue(waitFor("Welcome to Guest Mode", 25_000), "session did not start after Allow ($note)")
        }
    }

    @Test
    fun pass05b() {
        arrange { ensureOwnerHome() }
        arrange { pickApps(3) }

        verify("5.6", "Session must still start (not blocked)") {
            val note = startSessionHere(minutes = 15, acceptPin = false, permission = "deny")
            expectContains(note, "permission: denied", "the prompt was not shown, so denying was not exercised")
            expectTrue(
                waitFor("Welcome to Guest Mode", 25_000),
                "session did not start after denying notifications"
            )
        }
    }

    // ================================================================ Pass 6

    @Test
    fun pass06() {
        arrange { ensureOwnerHome() }
        arrange { pickApps(3) }
        var startNote = ""
        arrange { startNote = startSessionHere(minutes = 30, acceptPin = false, permission = "allow") }
        val startTop = runCatching { topActivityLine() }.getOrDefault("")
        val startLuma = screenshot()?.let { luma(it) } ?: -1.0
        val startText = visibleText()

        verify("6.1", "\"Guest Mode\" + \"Only approved apps\", lock icon") {
            expectTrue(
                device.hasObject(By.text("Guest Mode")),
                "no Guest Mode header after start ($startNote | top=$startTop | " +
                    "screenshot luma=${"%.0f".format(startLuma)} | text=${startText.take(160)})"
            )
            expectTrue(device.hasObject(By.text("Only approved apps")), "no Only approved apps subtitle")
            expectTrue(device.hasObject(By.desc("Lock screen")), "no lock icon")
        }

        verify("6.2", "Pinned if you accepted the system dialog, Soft lock if not") {
            val pill = waitForAny("Pinned", "Soft lock", timeout = 6_000)
            expectTrue(pill != null, "no Pinned / Soft lock pill")
        }

        verify("6.3", "A warning banner appears: \"Home and Recents are not blocked\"") {
            expectTrue(
                waitFor("Home and Recents are not blocked", 8_000),
                "no warning banner after declining the pin dialog"
            )
        }

        verify("6.4", "Only your 3 chosen apps, with real icons and names") {
            val tiles = device.findObjects(By.descStartsWith("Open "))
            expectTrue(tiles.size == 3, "grid shows ${tiles.size} apps, expected 3")
            expectTrue(
                tiles.all { it.contentDescription?.removePrefix("Open ")?.isNotBlank() == true },
                "a tile has no name"
            )
        }

        verify("6.5", "It opens normally, full screen") {
            // Any of the allowed apps will do: the plan says "a guest app", and a
            // stock emulator camera is known to crash on its own (no camera HAL),
            // which would grade the emulator, not the app.
            val tiles = device.findObjects(By.descStartsWith("Open ")).size
            var opened: String? = null
            var tried = mutableListOf<String>()
            for (index in 0 until tiles) {
                val tile = device.findObjects(By.descStartsWith("Open ")).getOrNull(index) ?: continue
                val label = tile.contentDescription?.removePrefix("Open ") ?: "tile $index"
                tried += label
                runCatching { tile.click() }
                pause(3_000)
                val foreground = foregroundPackage()
                if (foreground.isNotEmpty() && foreground != context.packageName) {
                    opened = label
                    break
                }
                if (device.hasObject(By.text("Close app"))) {
                    tapText("Close app")
                    pause(1_200)
                    dismissPinnedDialog(timeoutMs = 2_000)
                }
            }
            expectTrue(opened != null, "no guest app opened (tried: ${tried.joinToString()})")
            device.pressBack()
            pause(1_200)
            dismissPinnedDialog(timeoutMs = 3_000)
            expectTrue(waitFor("Welcome to Guest Mode", 8_000), "Back did not return to the guest home")
            note("opened $opened")
        }

        verify("6.6", "With pinning on: both are blocked. Without: you can leave — and the banner already told you so") {
            device.pressHome()
            pause(1_500)
            val home = foregroundPackage()
            expectTrue(home != context.packageName, "Home stayed inside GuestVault")
            launchApp()
            pause(1_500)
            expectTrue(waitFor("Welcome to Guest Mode", 10_000), "could not return to the running guest session")
            device.pressRecentApps()
            pause(1_500)
            val recents = foregroundPackage()
            expectTrue(recents != context.packageName || device.hasObject(By.text("Welcome to Guest Mode")),
                "Recents behaved unexpectedly (foreground $recents)")
            device.pressHome()
            pause(800)
            launchApp()
            pause(1_200)
        }

        verify("6.7", "Blocked/black — FLAG_SECURE is on") {
            // Debug builds skip FLAG_SECURE so the docs screenshots are possible;
            // this debug-only switch turns the real behaviour back on, then the
            // guest task is recreated so the flag applies.
            shell("settings put global guestvault_force_secure 1")
            // A configuration change recreates the activity, which re-applies the
            // window flag. `am force-stop` would kill this instrumentation too.
            device.setOrientationLeft()
            pause(2_000)
            expectTrue(waitFor("Welcome to Guest Mode", 15_000), "guest home did not survive the rotation")
            pause(1_000)
            val shot = screenshot()
            val value = shot?.let { luma(it) } ?: -1.0
            shell("settings put global guestvault_force_secure 0")
            device.setOrientationNatural()
            pause(1_500)
            expectTrue(
                value in 0.0..30.0,
                "the guest window was screenshotable (luminance ${"%.0f".format(value)}); " +
                    "FLAG_SECURE is not protecting it"
            )
        }

        verify("6.8", "Guest home is excluded from Recents") {
            val recents = shell("dumpsys activity recents")
            expectTrue(recents.isNotBlank(), "could not read the recents dump")
            val guestEntries = recents.lineSequence()
                .filter { it.contains("Recent #") && it.contains("guestvault") }
                .toList()
            expectTrue(
                guestEntries.none { it.contains("GuestHomeActivity") },
                "the guest task is in Recents: ${guestEntries.joinToString(" ~ ").take(200)}"
            )
        }

        verify("6.9", "Ongoing \"Guest Mode is on · ends in 24:31\" (if permission granted)") {
            val dump = shell("dumpsys notification --noredact")
            expectTrue(dump.contains("Guest Mode is on"), "no Guest Mode notification")
            expectTrue(dump.contains("Ends in"), "the notification does not show a countdown")
        }

        verify("6.10", "Updates every second, matches the notification") {
            // Back on the guest home opens the owner lock; the countdown lives on
            // the guest home, so leave the lock again before reading it. The lock
            // takes a moment to paint, so wait for it instead of sampling.
            device.pressBack()
            expectTrue(
                leaveOwnerLock(),
                "did not get back to the guest home (screen: ${screen()}; " +
                    "text=${visibleText().take(140)})"
            )
            val first = waitForCountdown()
            expectTrue(first != null, "no \"Ends in\" countdown on the guest home")
            Thread.sleep(2_400)
            val second = waitForCountdown()
            expectTrue(second != null && second != first, "countdown did not change in 2.4 s: $first")
            val shown = secondsOf(second)
            val notif = Regex("Ends in (\\d+(?::\\d+)+)").find(shell("dumpsys notification --noredact"))
                ?.groupValues?.get(1)
            expectTrue(notif != null, "notification has no countdown to compare against")
            val notified = secondsOf("Ends in $notif")
            expectTrue(
                shown != null && notified != null && kotlin.math.abs(shown - notified) <= 2,
                "UI shows $first/$second but the notification says $notif"
            )
        }
    }

    // ================================================================ Pass 7

    @Test
    fun pass07a() {
        arrange { ensureOwnerHome() }
        arrange { pickApps(3) }
        arrange {
            startSessionHere(minutes = 0, acceptPin = false, permission = "allow")
            expectTrue(waitFor("Welcome to Guest Mode", 25_000), "guest session did not start")
        }

        verify("7.1", "Lock screen: \"Owner only\", PIN dots, keypad") {
            tapText("Return to Owner")
            expectTrue(waitFor("Owner only", 10_000), "no Owner only pill")
            expectTrue(waitForDesc("PIN key 1", 10_000), "no keypad")
        }

        verify("7.2", "Returns to guest home — a guest cannot escape by cancelling") {
            tapText("Cancel")
            expectTrue(waitFor("Welcome to Guest Mode", 10_000), "Cancel did not return to the guest home")
            expectTrue(!device.hasObject(By.text("Start Guest Session")), "Cancel reached the owner dashboard")
        }

        verify("7.3", "Red error, dots clear") {
            tapText("Return to Owner")
            expectTrue(waitForDesc("PIN key 1", 10_000), "lock screen missing")
            enterPin(wrongPin)
            // A tap dropped while the keypad settles leaves three digits on
            // screen: no submit, so no error either. One clean retry tells that
            // apart from an app that really does not report a wrong PIN.
            if (!waitForContains("Incorrect PIN", 3_000) &&
                !device.hasObject(By.textContains("Too many attempts")) &&
                device.hasObject(By.desc("PIN key 1"))
            ) {
                pause(700)
                enterPin(wrongPin)
            }
            var error = waitForContains("Incorrect PIN", 6_000)
            if (!error && device.hasObject(By.textContains("Too many attempts"))) {
                // An earlier pass left the guard one failure short of a cool-down,
                // so this wrong PIN started one. Wait it out and try again on the
                // fresh round, which is what this step is about.
                note("the cool-down from an earlier attempt was still running; waiting it out")
                waitOutCoolDown(maxSeconds = 70)
                enterPin(wrongPin)
                error = waitForContains("Incorrect PIN", 10_000)
            }
            expectTrue(error, "no inline error after a wrong PIN (screen: ${visibleText().take(120)})")
            expectTrue(device.hasObject(By.desc("PIN key 1")), "keypad disappeared")
        }

        verify("7.4", "Cool-down starts at 30 s; keypad disabled; a countdown or message shows") {
            var attempts = 0
            var lastMessage = ""
            var firstLockout: String? = null
            while (attempts < 6 && firstLockout == null) {
                if (!device.hasObject(By.desc("PIN key 1"))) break
                enterPin(wrongPin)
                attempts++
                // Poll tightly: the message counts down, so a slow read would
                // report fewer seconds than the cool-down actually started with.
                val deadline = System.currentTimeMillis() + 3_000
                while (System.currentTimeMillis() < deadline) {
                    val message = device.findObjects(By.textContains("Too many attempts"))
                        .mapNotNull { it.text }.firstOrNull()
                    if (message != null) { firstLockout = message; break }
                    lastMessage = device.findObjects(By.textContains("attempt")).mapNotNull { it.text }
                        .firstOrNull() ?: lastMessage
                    Thread.sleep(150)
                }
            }
            val locked = firstLockout != null
            val seconds = Regex("in (\\d+)s").find(firstLockout ?: "")
                ?.groupValues?.get(1)?.toIntOrNull()
            expectTrue(locked, "no cool-down after $attempts wrong PINs (last message: $lastMessage)")
            expectTrue(
                seconds != null && seconds in 25..30,
                "the cool-down started at ${seconds ?: "?"} s, expected 30 (message: \"$firstLockout\")"
            )
            // Compose's `enabled = false` does not always surface as
            // UiObject2.isEnabled, so prove it by pressing digits: nothing may
            // happen while the cool-down is running.
            repeat(2) { runCatching { device.findObject(By.desc("PIN key 1"))?.click() } }
            pause(900)
            expectTrue(
                device.findObjects(By.textContains("Too many attempts")).isNotEmpty(),
                "the cool-down message disappeared after tapping the keypad"
            )
            expectTrue(
                !device.hasObject(By.text("Checking…")),
                "the keypad accepted input during the cool-down"
            )
            note("locked after $attempts wrong PINs (plan says 5); message: $firstLockout")
        }

    }

    @Test
    fun pass07b() {
        launchApp()

        verify("7.5", "Still locked — the cool-down survives the app being killed") {
            dismissPinnedDialog(timeoutMs = 10_000)
            val resumed = device.wait(Until.hasObject(By.textContains("Guest Mode")), 30_000)
            expectTrue(
                resumed,
                "the guest session did not survive the force-stop " +
                    "(screen: ${screen()}; text=${visibleText().take(160)})"
            )
            dismissPinnedDialog(timeoutMs = 10_000)
            if (device.hasObject(By.text("Resume Guest Mode"))) {
                tapText("Resume Guest Mode")
                pause(1_500)
                dismissPinnedDialog(timeoutMs = 4_000)
            }
            expectTrue(waitFor("Welcome to Guest Mode", 15_000), "guest home missing after relaunch")
            tapText("Return to Owner")
            expectTrue(waitForDesc("PIN key 1", 10_000), "lock screen missing")
            // The 30 s timer may well have run out during the relaunch; what has
            // to survive is the guard itself (failedAttempts + lockedUntil).
            val stillCounting = waitForContains("Too many attempts", 3_000)
            note(
                if (stillCounting) {
                    "the 30 s cool-down was still running after the kill"
                } else {
                    "the 30 s cool-down had already expired by the relaunch; " +
                        "checking that the guard still refuses a wrong PIN"
                }
            )
            enterPin(wrongPin)
            val answered = waitForContains("attempt", 8_000) || waitForContains("Incorrect", 2_000)
            if (!answered && device.hasObject(By.desc("PIN key 1"))) {
                // The first tap can arrive before the guard has loaded and be
                // dropped; a second wrong PIN costs nothing the plan cares about.
                enterPin(wrongPin)
                waitForContains("attempt", 6_000)
            }
            expectTrue(
                device.hasObject(By.desc("PIN key 1")) ||
                    device.hasObject(By.textContains("Too many attempts")),
                "the lock screen closed on a wrong PIN after the kill"
            )
            expectTrue(
                !device.hasObject(By.text("Start Guest Session")),
                "a wrong PIN reached the dashboard after the kill"
            )
            note(
                if (answered) "the lock answered the wrong PIN after the kill" else
                    "the lock stayed closed without an error message after the kill"
            )
        }

        verify("7.6", "Cool-down doubles to 60 s (cap 15 min)") {
            waitOutCoolDown(maxSeconds = 90)
            var attempts = 0
            var lastMessage = ""
            var seconds = 0
            while (attempts < 6 && !device.hasObject(By.textContains("Too many attempts"))) {
                if (!device.hasObject(By.desc("PIN key 1"))) break
                enterPin(wrongPin)
                val enteredAt = System.currentTimeMillis()
                attempts++
                val deadline = System.currentTimeMillis() + 3_000
                while (System.currentTimeMillis() < deadline) {
                    val message = device.findObjects(By.textContains("Too many attempts"))
                        .mapNotNull { it.text }.firstOrNull()
                    if (message != null) {
                        lastMessage = message
                        // The cool-down starts when the wrong PIN is checked,
                        // so add the time the message took to appear: a slow
                        // emulator otherwise reads "53 s" for a 60 s cool-down.
                        val shown = Regex("in (\\d+)s").find(message)
                            ?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        seconds = shown + ((System.currentTimeMillis() - enteredAt) / 1_000L).toInt()
                        break
                    }
                    Thread.sleep(120)
                }
            }
            val logged = Regex("Cool-down started: (\\d+)s")
                .findAll(shell("logcat -d -t 4000"))
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .lastOrNull()
            expectTrue(
                logged == 60,
                "second cool-down is ${logged ?: "?"} s, expected 60 " +
                    "(UI read: ${if (lastMessage.isEmpty()) "?" else "$seconds"} s; " +
                    "message: \"$lastMessage\")"
            )
            note("doubled after $attempts more wrong PINs (plan says 5); message: $lastMessage")
        }

        verify("7.8", "Session ends, back to the dashboard") {
            waitOutCoolDown(maxSeconds = 120)
            dismissPinnedDialog(timeoutMs = 2_000)
            if (!device.hasObject(By.desc("PIN key 1"))) {
                if (device.hasObject(By.text("Return to Owner"))) tapText("Return to Owner")
                waitForDesc("PIN key 1", 10_000)
            }
            expectTrue(device.hasObject(By.desc("PIN key 1")), "lock screen is not reachable")
            enterPinWithRetry(pinDigits)
            expectTrue(waitFor("Start Guest Session", 25_000), "correct PIN did not end the session")
        }
    }

    // ================================================================ Pass 8

    @Test
    fun pass08() {
        arrange { ensureOwnerHome() }
        arrange { pickApps(3) }
        arrange {
            startSessionHere(minutes = 5, acceptPin = false, permission = "allow")
            expectTrue(waitFor("Welcome to Guest Mode", 25_000), "session did not start")
            pause(3_000)
            endSessionWithPin()
        }

        verify("8.1", "Your session appears with time range, duration, \"N apps allowed\"") {
            pause(1_200)
            expectTrue(scrollToText("Recent sessions"), "no Recent sessions section")
            val row = device.findObjects(By.textContains("apps ·")).mapNotNull { it.text }.firstOrNull()
            val time = device.findObjects(By.textContains("·")).mapNotNull { it.text }
                .firstOrNull { Regex("\\d{1,2}:\\d{2}").containsMatchIn(it) }
            val hasRange = device.findObjects(By.textContains(" – ")).isNotEmpty() ||
                device.findObjects(By.textContains("apps allowed")).isNotEmpty()
            expectTrue(row != null || time != null, "no session row on the dashboard")
            expectTrue(
                hasRange,
                "the dashboard row reads \"${time ?: "?"}\" / \"${row ?: "?"}\" — start time and \"N apps\" only, no range and no \"allowed\""
            )
        }

        verify("8.2", "History list, newest first") {
            scrollToText("View all")
            tapText("View all")
            expectTrue(waitFor("Session history", 12_000), "history did not open")
            expectTrue(device.findObjects(By.textContains("apps allowed")).isNotEmpty(), "history has no session row")
        }

        verify("8.3", "Guest data wiped (since auto-wipe was on) and the end reason") {
            expectTrue(device.hasObject(By.text("Guest data wiped")), "no wipe badge")
            expectTrue(device.hasObject(By.text("Owner")), "no end-reason pill")
        }

        verify("8.4", "Ask before deleting; list empties to \"No sessions yet\"") {
            tapText("Clear")
            expectTrue(waitFor("Clear session history?", 8_000), "no confirmation dialog")
            tapText("Clear history")
            expectTrue(waitFor("No sessions yet", 10_000), "history did not empty")
        }
    }

    // ================================================================ Pass 9

    @Test
    fun pass09a() {
        arrange { ensureOwnerHome() }
        openSettings()

        verify("9.1", "Old PIN path works; new PIN takes effect immediately — verify by starting a session and unlocking with the new one") {
            tapText("Change owner PIN")
            expectTrue(waitFor("Enter your current PIN", 10_000), "change-PIN flow did not ask for the old PIN")
            // No retry here: re-typing the old PIN on the *new PIN* screen would
            // silently set the old PIN as the new one and poison every later
            // step. enterPin already retries a dropped entry internally.
            enterPin(pinDigits)
            expectTrue(
                waitFor("Choose a new PIN", 10_000),
                "old PIN was not accepted (screen: ${visibleText().take(140)})"
            )
            val newPin = listOf("1", "3", "5", "7")
            enterPin(newPin)
            expectTrue(waitFor("Confirm your PIN", 10_000), "no confirmation step for the new PIN")
            enterPin(newPin)
            pause(1_500)
            // A completed change returns to Settings, one Back from the dashboard.
            backToDashboard()
            expectTrue(
                waitFor("Start Guest Session", 15_000),
                "did not return to the dashboard (screen: ${visibleText().take(140)})"
            )
            pickApps(3)
            startSessionHere(minutes = 15, acceptPin = false, permission = "allow")
            expectTrue(waitFor("Welcome to Guest Mode", 25_000), "session did not start")
            tapText("Return to Owner")
            expectTrue(waitForDesc("PIN key 1", 10_000), "lock screen missing")
            enterPin(newPin)
            expectTrue(waitFor("Start Guest Session", 25_000), "the new PIN did not unlock the session")
            // Only now is it proven that the stored PIN really changed, so this
            // is where the harness (and the next process) starts using it.
            rememberPin(newPin)
        }

        verify("9.2", "Shows unavailable if no fingerprint/face is enrolled") {
            openSettings()
            val subtitle = device.findObjects(By.textContains("fingerprint")).mapNotNull { it.text }
                .firstOrNull { it.contains("No fingerprint", true) || it.contains("enrolled", true) }
            expectTrue(
                subtitle != null,
                "biometric row does not say it is unavailable (this emulator may have an enrolled fingerprint)"
            )
        }

        verify("9.3", "Off ⇒ next session shows \"Soft lock\" and the warning") {
            device.findObject(By.desc("Lock task toggle"))?.click()
            pause(700)
            tapDesc("Back")
            pause(800)
            pickApps(3)
            startSessionHere(minutes = 15, acceptPin = true, permission = "allow")
            expectTrue(waitFor("Welcome to Guest Mode", 25_000), "session did not start")
            expectTrue(device.hasObject(By.text("Soft lock")), "session is not in Soft lock")
            expectTrue(
                waitForContains("Home and Recents are not blocked", 8_000),
                "no warning banner (screen: ${visibleText().take(140)})"
            )
            endSessionWithPin()
        }

        verify("9.4", "Off ⇒ a session ends without the \"Guest data wiped\" badge") {
            // Older passes leave wiped sessions behind; only a fresh list can
            // answer the question this step asks.
            openSettings()
            scrollToText("Session history")
            tapText("Session history")
            pause(1_200)
            if (device.hasObject(By.text("Clear"))) {
                tapText("Clear")
                pause(900)
                if (device.hasObject(By.text("Clear history"))) tapText("Clear history")
                pause(1_500)
            }
            expectTrue(
                waitForContains("No sessions yet", 8_000),
                "history was not empty at the start of the step (text=${visibleText().take(140)})"
            )
            tapDesc("Back")
            pause(800)
            openSettings()
            scrollToText("Auto-wipe on exit")
            val wipeToggle = device.findObject(By.desc("Auto-wipe toggle"))
            expectTrue(wipeToggle != null, "no auto-wipe toggle in settings")
            val wasOn = wipeToggle!!.isChecked
            wipeToggle.click()
            pause(800)
            expectTrue(
                device.findObject(By.desc("Auto-wipe toggle"))?.isChecked == !wasOn,
                "the auto-wipe switch did not change"
            )
            tapDesc("Back")
            pause(800)
            pickApps(3)
            startSessionHere(minutes = 15, acceptPin = false, permission = "allow")
            expectTrue(waitFor("Welcome to Guest Mode", 25_000), "session did not start")
            pause(2_500)
            endSessionWithPin()
            scrollToText("View all")
            tapText("View all")
            expectTrue(waitFor("Session history", 12_000), "history did not open")
            expectTrue(!device.hasObject(By.text("Guest data wiped")), "the wipe badge is still shown")
            tapDesc("Back")
            pause(700)
            openSettings()
            device.findObject(By.desc("Auto-wipe toggle"))?.click()
            pause(600)
            tapDesc("Back")
            pause(700)
        }

        verify("9.5", "Confirm screen pre-selects 5 min next time") {
            openSettings()
            scrollToText("Default session timer")
            tapText("Default session timer")
            expectTrue(
                waitForContains("New sessions start with this limit", 10_000),
                "timer dialog did not open (screen: ${visibleText().take(120)})"
            )
            tapText("5 min")
            if (device.hasObject(By.text("Close"))) tapText("Close")
            pause(600)
            tapDesc("Back")
            pause(800)
            pickApps(3)
            expectTrue(waitFor("One last look before you hand over", 10_000), "confirm screen missing")
            expectTrue(
                waitFor("Ends automatically after 5 min", 8_000),
                "5 min is not pre-selected on the confirm screen " +
                    "(the screen reads \"${durationCaption()}\" instead)"
            )
        }

        verify("9.6", "Applies instantly, survives a restart, and the guest home follows too") {
            // Theme is set here; persistence is graded in pass09b after a force-stop.
            while (!device.hasObject(By.text("Start Guest Session")) &&
                tapDescIfPresent("Back")
            ) {
                pause(700)
            }
            openSettings()
            scrollToText("Theme")
            tapText("Theme")
            expectTrue(waitFor("Light", 8_000), "theme dialog did not open")
            tapText("Light")
            pause(900)
            if (device.hasObject(By.text("Close"))) tapText("Close")
            pause(400)
            tapDesc("Back")
            pause(900)
            val shot = screenshot()
            expectTrue(shot != null, "could not capture the dashboard")
            val value = luma(shot!!)
            expectTrue(value > 170, "dashboard did not switch to light (luminance ${"%.0f".format(value)})")
        }
    }

    @Test
    fun pass09b() {
        launchApp()

        verify("9.6", "Applies instantly, survives a restart, and the guest home follows too") {
            ensureOwnerHome()
            expectTrue(waitFor("Start Guest Session", 25_000), "did not reach the dashboard")
            val shot = screenshot()
            expectTrue(shot != null, "could not capture the dashboard")
            val value = luma(shot!!)
            expectTrue(value > 170, "theme did not survive the restart (luminance ${"%.0f".format(value)})")
            pickApps(3)
            startSessionHere(minutes = 15, acceptPin = false, permission = "allow")
            expectTrue(waitFor("Welcome to Guest Mode", 25_000), "session did not start")
            val guestShot = screenshot()
            expectTrue(guestShot != null, "could not capture the guest home")
            val guest = guestShot!!
            // The hero is always indigo; the app sheet below it must be light.
            val sheet = Bitmap.createBitmap(guest, 0, (guest.height * 0.62f).toInt(), guest.width, (guest.height * 0.3f).toInt())
            expectTrue(luma(sheet) > 170, "guest home did not follow the light theme (luminance ${"%.0f".format(luma(sheet))})")
            endSessionWithPin()
        }

        verify("9.7", "4 rows with live status + a \"Never requested\" list that includes Internet") {
            openSettings()
            expectTrue(
                scrollToText("Permissions & privacy", maxSwipes = 12),
                "the Permissions & privacy row is not in Settings " +
                    "(text=${visibleText().take(160)})"
            )
            tapText("Permissions & privacy")
            expectTrue(
                waitForAny("Permissions & privacy", "Permissions on this device", timeout = 10_000) != null ||
                    waitForContains("No internet permission", 6_000),
                "permissions screen did not open (screen: ${visibleText().take(120)})"
            )
            listOf("Notifications", "Biometrics", "Foreground service", "Start at boot").forEach {
                expectTrue(device.hasObject(By.text(it)), "missing permission row: $it")
            }
            // PermissionGroup renders the header in caps ("NEVER REQUESTED").
            val sawSection = scrollUntil(30_000) {
                visibleText().contains("NEVER REQUESTED", ignoreCase = true) ||
                    visibleText().contains("Never requested", ignoreCase = true)
            }
            expectTrue(
                sawSection,
                "no Never requested section (text=${visibleText().take(160)})"
            )
            val sawInternet = scrollUntil(20_000) { visibleText().contains("Internet") }
            expectTrue(sawInternet, "Internet is not listed (text=${visibleText().take(160)})")
        }

        verify("9.8", "Limitations page, no marketing spin") {
            backToDashboard()
            openSettings()
            expectTrue(
                scrollToText("How GuestVault works", maxSwipes = 12),
                "the How GuestVault works row is not in Settings " +
                    "(text=${visibleText().take(160)})"
            )
            tapText("How GuestVault works")
            val page = waitForAny("GuestVault is honest about the line between an app and the OS",
                "What needs system access", timeout = 12_000)
            expectTrue(page != null, "limitations page did not open")
        }

        verify("9.9", "Opens the GitHub Pages policy") {
            backToDashboard()
            openSettings()
            expectTrue(
                scrollToText("Privacy policy", maxSwipes = 12),
                "the Privacy policy row is not in Settings (text=${visibleText().take(160)})"
            )
            tapText("Privacy policy")
            pause(2_000)
            val browser = foregroundPackage()
            val inBrowser = browser.contains("chrome") || browser.contains("browser") ||
                browser.contains("webview")
            // The emulator has no network, so the page itself stays blank; what
            // matters is the URL the policy was opened with.
            val urlBar = listOf(
                "com.android.chrome:id/url_bar",
                "org.chromium.chrome:id/url_bar"
            ).flatMap { id -> runCatching { device.findObjects(By.res(id)) }.getOrDefault(emptyList()) }
                .mapNotNull { runCatching { it.text }.getOrNull() }
                .joinToString(" ")
            // Chrome's first-run screen may cover the page on a fresh image, and
            // the emulator has no network. The URL is still in the intent the
            // app handed over, and in the task log.
            val evidence = urlBar + " " +
                shell("dumpsys activity activities") + " " +
                shell("dumpsys activity top") + " " +
                shell("logcat -d -t 1500")
            val hasUrl = device.findObjects(By.textContains("github.io")).isNotEmpty() ||
                device.findObjects(By.textContains("https://")).isNotEmpty() ||
                evidence.contains("jacinth-19.github.io")
            expectTrue(
                hasUrl,
                "the privacy policy did not name the public page (foreground: $browser; " +
                    "screen=${visibleText().take(120)})"
            )
            note(
                if (inBrowser) {
                    "opened the GitHub Pages policy in $browser"
                } else {
                    "no browser app on this image, so the in-app policy was shown; " +
                        "it names ${"https://jacinth-19.github.io/GuestVault/privacy.html"}"
                }
            )
        }

        verify("9.10", "Shows 1.1.1 — compare with the dashboard footer (9.5 in Pass 3)") {
            // 9.9 hands over to the browser, so the app is not in front any more.
            backToDashboard()
            openSettings()
            expectTrue(scrollToText("Version", maxSwipes = 12), "the Version row is not in Settings")
            val version = device.findObjects(By.textContains(".")).mapNotNull { it.text }
                .firstOrNull { Regex("^\\d+\\.\\d+\\.\\d+$").containsMatchIn(it.trim()) }
                ?: "nothing"
            // The plan expects 1.1.1; the build under test is versionName 1.2.0,
            // so this stays a failure and is reported as version drift rather
            // than quietly re-baselined.
            expectTrue(
                Regex("1\\.1\\.1").containsMatchIn(version),
                "the Version row reads \"$version\", not 1.1.1 (the build is 1.2.0)"
            )
        }
    }

    // =============================================================== Pass 10

    @Test
    fun pass10a() {
        // Creates the PIN on a fresh install; the host then revokes notifications.
        arrange { ensureOwnerHome() }
        expectTrue(device.hasObject(By.text("Start Guest Session")), "dashboard not reached before revocation")
    }

    @Test
    fun pass10b() {
        arrange { ensureOwnerHome() }

        verify("10.1", "Row shows \"Not granted\" with a button to fix it") {
            openSettings()
            scrollToText("Permissions & privacy", maxSwipes = 12)
            expectTrue(
                device.hasObject(By.text("Permissions & privacy")),
                "the Permissions & privacy row is not in Settings " +
                    "(text=${visibleText().take(160)})"
            )
            tapText("Permissions & privacy")
            expectTrue(
                waitForAny("Permissions & privacy", "Permissions on this device", timeout = 10_000) != null ||
                    waitForContains("No internet permission", 6_000),
                "permissions screen did not open (screen: ${visibleText().take(120)})"
            )
            expectTrue(device.hasObject(By.text("Not granted")), "notifications row does not say Not granted")
            expectTrue(device.hasObject(By.text("Open settings")), "no button to fix it")
        }

        verify("10.2", "Session still starts; the ongoing notification is simply absent") {
            // On the dashboard already (the common case) there is no Back to
            // tap, so only walk back when a nested screen has one.
            if (!device.hasObject(By.text("Start Guest Session")) &&
                device.hasObject(By.desc("Back"))
            ) {
                backToDashboard()
            }
            pickApps(3)
            val denyNote = startSessionHere(minutes = 15, acceptPin = false, permission = "deny")
            expectTrue(
                waitFor("Welcome to Guest Mode", 25_000),
                "session was blocked without notifications ($denyNote)"
            )
            val dump = shell("dumpsys notification --noredact")
            expectTrue(!dump.contains("Guest Mode is on"), "a notification was posted even though it was revoked")
        }
    }

    // =============================================================== Pass 11

    @Test
    fun pass11() {
        arrange { ensureOwnerHome() }

        // The shade editor cannot be driven by `cmd statusbar` on this image:
        // both rows are graded on a physical device, with the reason recorded.
        notAutomatable(
            "11.1",
            "Tile appears",
            "the emulator image has no `cmd statusbar add-tile`; the tile is added by hand"
        )
        notAutomatable(
            "11.2",
            "\"Tap to start a guest session\"",
            "needs the tile added by hand; checked on a device"
        )

        verify("11.3", "Opens the app on the confirm screen — it does not silently start a session") {
            // The tile itself cannot be added through `cmd statusbar` on this
            // image, so the tile's own intent extra is fired instead: same
            // entry point, same code path as GuestVaultTileService.onClick().
            shell(
                "am start -n ${context.packageName}/.MainActivity " +
                    "--ez ${context.packageName}.extra.QUICK_START_SESSION true"
            )
            pause(2_500)
            expectTrue(waitFor("One last look before you hand over", 15_000), "the tile did not open the confirm screen")
            expectTrue(!device.hasObject(By.text("Welcome to Guest Mode")), "the tile started a session silently")
        }

        verify("11.4", "Jumps straight back into the running guest home; tile is active") {
            arrange { ensureOwnerHome() }
            arrange { pickApps(3) }
            startSessionHere(minutes = 15, acceptPin = false, permission = "allow")
            expectTrue(waitFor("Welcome to Guest Mode", 25_000), "session did not start")
            device.pressHome()
            pause(1_200)
            shell(
                "am start -n ${context.packageName}/.MainActivity " +
                    "--ez ${context.packageName}.extra.QUICK_START_SESSION true"
            )
            pause(2_500)
            // Screen pinning re-prompts each time the task comes to the front.
            dismissPinnedDialog(timeoutMs = 6_000)
            expectTrue(
                waitFor("Welcome to Guest Mode", 15_000),
                "the tile did not return to the running guest home " +
                    "(screen: ${screen()}; text=${visibleText().take(140)})"
            )
            expectTrue(!device.hasObject(By.text("One last look before you hand over")), "the tile opened the confirm screen mid-session")
        }
    }

    // =============================================================== Pass 12

    @Test
    fun pass12a() {
        arrange { ensureOwnerHome() }
        arrange { pickApps(3) }
        arrange {
            startSessionHere(minutes = 5, acceptPin = false, permission = "allow")
            expectTrue(waitFor("Welcome to Guest Mode", 25_000), "5-minute session did not start")
        }

        verify("12.1", "Ends by itself at 0:00") {
            val started = System.currentTimeMillis()
            val ended = waitForSessionEnd(6 * 60 * 1000L)
            val elapsed = (System.currentTimeMillis() - started) / 1000
            expectTrue(ended, "the session did not end by itself")
            note("ended after ${elapsed}s of wall clock")
        }

        verify("12.2", "End reason Timer, wipe badge if auto-wipe was on") {
            scrollToText("View all")
            tapText("View all")
            expectTrue(waitFor("Session history", 12_000), "history did not open")
            expectTrue(device.hasObject(By.text("Timer")), "no Timer end reason")
            expectTrue(device.hasObject(By.text("Guest data wiped")), "no wipe badge")
        }
    }

    @Test
    fun pass12b() {
        arrange { ensureOwnerHome() }
        arrange { pickApps(3) }
        arrange {
            startSessionHere(minutes = 5, acceptPin = false, permission = "allow")
            expectTrue(waitFor("Welcome to Guest Mode", 25_000), "5-minute session did not start")
        }

        // Leave the guest in another app for the whole session.
        shell("monkey -p com.google.android.deskclock -c android.intent.category.LAUNCHER 1")
        pause(2_500)

        verify("12.3", "Still expires — the foreground service keeps the timer alive") {
            val started = System.currentTimeMillis()
            var ended = false
            while (System.currentTimeMillis() - started < 6 * 60 * 1000L) {
                // `dumpsys activity services` is small and exact: the session is
                // alive exactly while GuestSessionService runs.
                val services = shell("dumpsys activity services ${context.packageName}")
                if (!services.contains("GuestSessionService")) {
                    ended = true
                    break
                }
                Thread.sleep(15_000)
            }
            val elapsed = (System.currentTimeMillis() - started) / 1000
            if (ended) {
                launchApp()
                pause(2_500)
            }
            expectTrue(ended, "the session was still running after 6 minutes in the background")
            launchApp()
            pause(2_500)
            dismissPinnedDialog(timeoutMs = 3_000)
            expectTrue(
                waitFor("Start Guest Session", 25_000),
                "the timer ended the session but the app still thinks it is running " +
                    "(text=${visibleText().take(110)})"
            )
            note("ended after ${elapsed}s with another app in the foreground")
        }
    }

    // =============================================================== Pass 13

    @Test
    fun pass13a() {
        arrange { ensureOwnerHome() }
        arrange { pickApps(3) }
        arrange { startSessionHere(minutes = 30, acceptPin = false, permission = "allow") }

        verify("13.1", "—") {
            expectTrue(waitFor("Welcome to Guest Mode", 25_000), "the session was not running before the reboot")
        }
    }

    @Test
    fun pass13b() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        pause(1_500)
        launchApp()

        verify("13.2", "No guest session: it was closed on boot") {
            dismissPinnedDialog(timeoutMs = 8_000)
            var landed = waitForAny(
                "Start Guest Session",
                "Welcome to Guest Mode",
                "Resume Guest Mode",
                timeout = 45_000
            )
            if (landed == null) {
                // A cold start after a reboot is much slower than a warm one.
                launchApp()
                landed = waitForAny(
                    "Start Guest Session",
                    "Welcome to Guest Mode",
                    "Resume Guest Mode",
                    timeout = 45_000
                )
            }
            dismissPinnedDialog(timeoutMs = 3_000)
            if (landed != "Start Guest Session") {
                val start = shell(
                    "am start -W -n ${context.packageName}/.MainActivity"
                ).replace("\n", " ").take(180)
                launchApp()
                pause(4_000)
                expectTrue(
                    waitFor("Start Guest Session", 30_000),
                    "after the reboot the app shows \"${landed ?: visibleText().take(90)}\"; " +
                        "am start: $start; log: ${recentLog(5)}"
                )
            }
        }

    }

    @Test
    fun pass13c() {
        // Its own process: a crash at the end of pass13b (the emulator is cold
        // after a reboot) used to cost 13.3 its verdict entirely.
        launchApp()

        verify("13.3", "Recovered after restart, and wiped if auto-wipe was on") {
            dismissPinnedDialog(timeoutMs = 6_000)
            if (!device.hasObject(By.text("Start Guest Session"))) ensureOwnerHome()
            expectTrue(waitFor("Start Guest Session", 25_000), "dashboard not shown")
            expectTrue(
                scrollToText("View all", maxSwipes = 12),
                "the dashboard has no View all row (text=${visibleText().take(160)})"
            )
            tapText("View all")
            expectTrue(waitFor("Session history", 12_000), "history did not open")
            expectTrue(device.hasObject(By.text("Recovered")), "no Recovered end reason")
            expectTrue(device.hasObject(By.text("Guest data wiped")), "no wipe badge")
        }
    }

    // =============================================================== Pass 14

    @Test
    fun pass14a() {
        device.pressHome()
        pause(800)
        launchApp()
        dismissPinnedDialog(timeoutMs = 3_000)
        val problems = mutableListOf<String>()

        fun checkScreen(name: String) {
            pause(600)
            val out = textsOutOfBounds()
            if (out.isNotEmpty()) problems += "$name: ${out.take(3).joinToString()}"
        }

        verify("14.1", "Nothing clipped or overlapping on any screen") {
            val first = waitForAny("Skip", "Create your owner PIN", "Start Guest Session", timeout = 40_000)
            expectTrue(first != null, "app did not start at 200% font")
            checkScreen("first screen")
            if (device.hasObject(By.text("Skip"))) {
                tapText("Skip")
                checkScreen("PIN setup")
            }
            if (device.hasObject(By.text("Create your owner PIN"))) {
                enterPin(pinDigits)
                checkScreen("PIN confirm")
                enterPin(pinDigits)
            }
            expectTrue(waitFor("Start Guest Session", 25_000), "dashboard not reached at 200% font")
            checkScreen("dashboard")
            openSettings()
            checkScreen("settings")
            scrollUp(2)
            checkScreen("settings (scrolled)")
            expectTrue(problems.isEmpty(), "text is outside the display: ${problems.joinToString(" | ")}")
        }
    }

    @Test
    fun pass14b() {
        arrange { ensureOwnerHome() }

        verify("14.2", "Guest home survives; no crash, no escape") {
            pickApps(3)
            startSessionHere(minutes = 15, acceptPin = false, permission = "allow")
            expectTrue(waitFor("Welcome to Guest Mode", 25_000), "session did not start")
            shell("settings put system accelerometer_rotation 0")
            shell("settings put system user_rotation 1")
            pause(3_000)
            dismissPinnedDialog(timeoutMs = 4_000)
            expectTrue(waitFor("Welcome to Guest Mode", 20_000), "guest home did not survive the rotation")
            pause(1_000)
            val tiles = device.findObjects(By.descStartsWith("Open ")).size
            expectTrue(
                tiles > 0,
                "the app grid disappeared after rotation (tiles=$tiles; " +
                    "text=${visibleText().take(160)})"
            )
            expectTrue(!device.hasObject(By.text("Start Guest Session")), "rotation escaped to the dashboard")
            shell("settings put system user_rotation 0")
            pause(2_500)
            dismissPinnedDialog(timeoutMs = 3_000)
            endSessionWithPin()
        }

        verify("14.3", "Blocked with \"Pick at least one app\"") {
            ensureOwnerHome()
            openAppPicker()
            tapText("Clear")
            pause(800)
            device.pressBack()
            pause(1_000)
            tapText("Start Guest Session")
            expectTrue(waitFor("One last look before you hand over", 12_000), "confirm screen did not open")
            expectTrue(device.hasObject(By.text("Pick at least one app")), "no Pick at least one app message")
            tapText("Start Guest Mode")
            pause(1_800)
            expectTrue(!device.hasObject(By.text("Welcome to Guest Mode")), "a session started with 0 apps")
        }

        verify("14.4", "Irrelevant — the app has no INTERNET permission at all") {
            ensureOwnerHome()
            expectTrue(waitFor("Start Guest Session", 10_000), "dashboard not shown before the airplane-mode check")
            val perms = shell("dumpsys package ${context.packageName}")
            expectTrue(!perms.contains("android.permission.INTERNET"), "the APK declares android.permission.INTERNET")
            shell("cmd connectivity airplane-mode enable")
            pause(4_000)
            expectTrue(waitFor("Start Guest Session", 10_000), "the app stopped working in airplane mode")
            shell("cmd connectivity airplane-mode disable")
            pause(2_000)
        }

        verify("14.5", "Guest mode is still active, not silently dropped") {
            ensureOwnerHome()
            pickApps(3)
            startSessionHere(minutes = 30, acceptPin = false, permission = "allow")
            expectTrue(waitFor("Welcome to Guest Mode", 25_000), "session did not start before the kill")
        }
    }

    @Test
    fun pass14c() {
        launchApp()

        verify("14.5", "Guest mode is still active, not silently dropped") {
            val screen = waitForAny(
                "Welcome to Guest Mode",
                "Resume Guest Mode",
                "Start Guest Session",
                timeout = 35_000
            )
            expectTrue(
                screen != "Start Guest Session",
                "the session was dropped: after the kill the app shows the owner dashboard"
            )
        }
    }

    // ================================================================ helpers

    private fun ensurePinSetup() {
        launchApp()
        val screen = waitForAny(
            "Skip",
            "Create your owner PIN",
            "Confirm your PIN",
            "Start Guest Session",
            timeout = 30_000
        )
        if (screen == "Skip") tapText("Skip")
        if (device.hasObject(By.text("Start Guest Session"))) {
            throw AssertionError("a PIN is already set; this pass needs a fresh install")
        }
        expectTrue(
            waitForAny("Create your owner PIN", "Confirm your PIN", timeout = 12_000) != null,
            "PIN setup did not appear (screen: ${screen ?: "nothing"})"
        )
    }

    private fun openAppPicker() {
        expectTrue(waitFor("Start Guest Session", 20_000), "not on the dashboard")
        if (device.hasObject(By.text("Choose apps"))) tapText("Choose apps") else tapText("Manage")
        expectTrue(waitFor("Search apps", 15_000), "app picker did not open")
    }

    private fun pickApps(count: Int) {
        arrange { openAppPicker() }
        if (device.hasObject(By.text("Clear"))) {
            tapText("Clear")
            pause(600)
        }
        pickVisibleApps(count)
        dismissKeyboard()
        tapText("Continue")
        expectTrue(
            waitFor("One last look before you hand over", 15_000),
            "confirm screen did not open after Continue (counter: ${counterText() ?: "none"})"
        )
    }

    private fun pickVisibleApps(count: Int) {
        var guard = 0
        while ((selectedCount() ?: 0) < count && guard < 24) {
            guard++
            val row = appRows().getOrNull(guard - 1)
            if (row == null) {
                val x = device.displayWidth / 2
                device.swipe(x, (device.displayHeight * 0.75f).toInt(), x, (device.displayHeight * 0.45f).toInt(), 18)
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
            (selectedCount() ?: 0) >= count,
            "only ${selectedCount() ?: 0} of $count apps selected (counter: ${counterText() ?: "none"})"
        )
    }

    private fun selectedCount(): Int? =
        Regex("(\\d+) apps selected").find(counterText() ?: "")?.groupValues?.get(1)?.toIntOrNull()

    /** The IME covers the picker's bottom bar (counter, Use recommended, Clear, Continue). */
    private fun dismissKeyboard() {
        repeat(2) {
            val shown = shell("dumpsys input_method")
                .lineSequence().any { it.contains("mInputShown=true") }
            if (!shown) return
            device.pressBack()
            pause(700)
        }
    }

    private fun appRows(): List<androidx.test.uiautomator.UiObject2> =
        device.findObjects(By.descContains("App row "))

    private fun textContaining(part: String): String? =
        device.findObjects(By.textContains(part)).firstOrNull()?.text

    private fun counterText(): String? =
        device.findObjects(By.textContains("apps selected")).firstOrNull()?.text

    private fun search(text: String) {
        val field = device.findObject(By.clazz("android.widget.EditText"))
        expectTrue(field != null, "no search field")
        val edit = field!!
        edit.click()
        pause(400)
        edit.setText(text)
        pause(700)
        dismissKeyboard()
        pause(300)
        // If the accessibility set-text did not take, type it through the shell.
        if (appRows().isEmpty() && !device.hasObject(By.text("No apps match"))) {
            edit.click()
            pause(300)
            shell("input text $text")
            pause(900)
        }
    }

    private fun clearSearch() {
        val clear = device.findObject(By.desc("Clear search"))
        if (clear != null) {
            runCatching { clear.click() }
            pause(700)
        } else {
            search("")
            pause(500)
        }
        dismissKeyboard()
        pause(300)
    }

    private fun durationLabel(minutes: Int): String = when (minutes) {
        0 -> "No limit"
        60 -> "60 min"
        else -> "$minutes min"
    }

    /**
     * Compose exposes a chip's selected state on the chip node; the label is a
     * child of it, so the text has to be read from the descendants.
     */
    private fun durationCaption(): String =
        device.findObjects(By.textContains("Ends automatically after")).mapNotNull { it.text }
            .firstOrNull()
            ?: device.findObjects(By.textContains("No time limit")).mapNotNull { it.text }.firstOrNull()
            ?: "nothing"

    private fun selectedChips(): List<String> {
        val nodes = device.findObjects(By.selected(true)) + device.findObjects(By.checked(true))
        return nodes.mapNotNull { node ->
            val own = runCatching { node.text }.getOrNull()
            val child = runCatching {
                node.findObjects(By.textContains("")).mapNotNull { it.text }.firstOrNull { it.isNotBlank() }
            }.getOrNull()
            (child ?: own)?.takeIf { it.isNotBlank() }
        }.distinct()
    }

    private fun chipSelected(label: String): Boolean =
        device.findObjects(By.selected(true)).any { it.text?.contains(label) == true } ||
            device.findObject(By.text(label))?.isSelected == true

    private fun startSessionHere(minutes: Int, acceptPin: Boolean, permission: String): String {
        expectTrue(waitFor("One last look before you hand over", 12_000), "confirm screen not open")
        if (minutes > 0) {
            val label = durationLabel(minutes)
            var chosen = tapChip(label)
            if (!chosen) {
                scrollToText("Session length", maxSwipes = 8)
                pause(500)
                chosen = tapChip(label)
            }
            if (!chosen) note("the $label chip could not be reached (selected: ${selectedChips()})")
            if (!waitForContains("Ends automatically after $label", 4_000)) {
                note("the confirm screen still reads \"${durationCaption()}\"; starting anyway")
            }
        }
        tapText("Start Guest Mode")
        pause(1_500)

        // The system permission prompt and the pin-to-screen prompt can arrive in
        // either order, or one of them not at all.
        var perm = "no prompt"
        var pin = "no dialog"
        repeat(12) {
            when {
                pinDialogVisible() -> pin = handlePinDialog(acceptPin)
                device.hasObject(By.text("Welcome to Guest Mode")) -> return "permission: $perm; pinning: $pin"
                device.hasObject(By.text("Allow")) ||
                    device.hasObject(By.text("Don't allow")) ||
                    device.hasObject(By.res("com.android.permissioncontroller:id/permission_allow_button")) -> {
                    perm = if (permission == "none") {
                        handleNotificationPrompt("deny")
                    } else {
                        handleNotificationPrompt(permission)
                    }
                }
                else -> pause(900)
            }
        }
        val focus = shell("dumpsys activity activities").lineSequence()
            .firstOrNull { it.contains("topResumedActivity") }?.trim()?.take(90).orEmpty()
        return "permission: $perm; pinning: $pin; top=$focus; log=${recentLog()}"
    }

    private fun pinDialogVisible(): Boolean = pinnedDialogVisible()

    private fun handleNotificationPrompt(mode: String): String {
        val seen = waitForAny("Allow", "Don't allow", timeout = 7_000) != null ||
            device.hasObject(By.res("com.android.permissioncontroller:id/permission_allow_button"))
        if (!seen) return "no prompt"
        return if (mode == "allow") {
            val allow = device.findObject(By.res("com.android.permissioncontroller:id/permission_allow_button"))
                ?: device.findObject(By.textContains("Allow"))
            if (allow != null) {
                runCatching { allow.click() }
                pause(700)
                "allowed"
            } else {
                "prompt without Allow"
            }
        } else {
            val denyButton = device.findObject(
                By.res("com.android.permissioncontroller:id/permission_deny_button")
            ) ?: listOf("Don't allow", "Deny", "Not now")
                .firstOrNull { device.hasObject(By.text(it)) }
                ?.let { device.findObject(By.text(it)) }
            if (denyButton != null) {
                runCatching { denyButton.click() }
                pause(700)
                "denied"
            } else {
                "prompt without Deny (screen: ${visibleText().take(80)})"
            }
        }
    }

    private fun handlePinDialog(accept: Boolean): String {
        // Android 14 shows an "App is pinned" education dialog after
        // startLockTask(); older builds ask "Pin ... to this screen?" first.
        if (device.findObjects(By.textContains("App is pinned")).isNotEmpty()) {
            if (accept) {
                if (device.hasObject(By.text("Got it"))) tapText("Got it")
                pause(1_200)
                return "accepted (app pinned)"
            }
            if (device.hasObject(By.text("No thanks"))) tapText("No thanks")
            pause(1_200)
            return "declined (unpinned)"
        }
        var title: String? = null
        val deadline = System.currentTimeMillis() + 7_000
        while (System.currentTimeMillis() < deadline) {
            title = device.findObjects(By.textContains("Pin")).mapNotNull { it.text }
                .firstOrNull { it.contains("to this screen", true) }
            if (title != null) break
            if (device.hasObject(By.text("Welcome to Guest Mode"))) break
            Thread.sleep(150)
        }
        if (title == null) return "no dialog"
        if (!accept) {
            if (device.hasObject(By.text("Cancel"))) tapText("Cancel")
            pause(900)
            return "declined"
        }
        val button = listOf("OK", "Start", "Pin").firstOrNull { device.hasObject(By.text(it)) }
        if (button != null) tapText(button)
        pause(900)
        return "accepted"
    }

    private fun endSessionWithPin() {
        dismissPinnedDialog(timeoutMs = 3_000)
        if (!device.hasObject(By.text("Return to Owner"))) {
            scrollToText("Return to Owner")
            if (!device.hasObject(By.text("Return to Owner"))) scrollUp(2)
        }
        tapText("Return to Owner")
        expectTrue(waitForDesc("PIN key 1", 15_000), "lock screen did not open")
        enterPinWithRetry(pinDigits)
        if (!waitFor("Start Guest Session", 25_000)) {
            // One retry: the first attempt can land while the wipe is running.
            if (device.hasObject(By.text("Cancel")) || device.hasObject(By.desc("PIN key 1"))) {
                enterPinWithRetry(pinDigits)
            }
        }
        expectTrue(
            waitFor("Start Guest Session", 20_000),
            "session did not end (screen: ${screen()}; " +
                "incorrect=${device.hasObject(By.textContains("Incorrect PIN"))}; " +
                "coolDown=${device.hasObject(By.textContains("Too many attempts"))}; " +
                "text=${visibleText().take(200)})"
        )
    }

    /** Presses Back until the dashboard is visible (bounded, so it cannot loop). */
    private fun backToDashboard(maxPresses: Int = 6) {
        repeat(maxPresses) {
            if (device.hasObject(By.text("Start Guest Session"))) return
            dismissPinnedDialog(timeoutMs = 1_000)
            if (device.hasObject(By.desc("Back"))) {
                tapDesc("Back")
                pause(800)
            } else {
                // Not inside a nested screen. A bare Back from the dashboard
                // leaves the app (and a second one opens the app drawer), so
                // bring GuestVault's own task forward instead.
                launchApp()
                pause(1_000)
            }
        }
        if (!device.hasObject(By.text("Start Guest Session"))) {
            launchApp()
            pause(1_500)
        }
    }

    /**
     * Leaves the owner lock and waits for the guest home. Tapping "Cancel" once
     * is not enough on a busy emulator: the tap can land while the lock is still
     * animating in.
     */
    private fun leaveOwnerLock(timeoutMs: Long = 15_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (device.hasObject(By.text("Welcome to Guest Mode"))) {
                dismissPinnedDialog(timeoutMs = 1_500)
                if (device.hasObject(By.text("Welcome to Guest Mode"))) return true
            }
            dismissPinnedDialog(timeoutMs = 800)
            if (device.hasObject(By.desc("PIN key 1"))) {
                if (device.hasObject(By.text("Cancel"))) {
                    tapText("Cancel", timeout = 2_000)
                } else {
                    device.pressBack()
                }
                pause(900)
            } else {
                pause(500)
            }
        }
        return device.hasObject(By.text("Welcome to Guest Mode"))
    }

    private fun openSettings() {
        if (!device.hasObject(By.text("Start Guest Session"))) {
            while (!device.hasObject(By.text("Start Guest Session")) &&
                tapDescIfPresent("Back")
            ) {
                pause(600)
            }
        }
        expectTrue(waitFor("Start Guest Session", 15_000), "dashboard not shown")
        tapDesc("Settings")
        expectTrue(waitFor("Owner-only controls", 12_000), "settings did not open")
    }

    private fun waitOutCoolDown(maxSeconds: Int) {
        val deadline = System.currentTimeMillis() + maxSeconds * 1_000L + 6_000
        while (System.currentTimeMillis() < deadline &&
            device.hasObject(By.textContains("Too many attempts"))
        ) {
            Thread.sleep(1_000)
        }
        pause(600)
    }

    private fun waitForSessionEnd(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (device.hasObject(By.text("Start Guest Session"))) return true
            if (!shell("dumpsys activity services ${context.packageName}")
                    .contains("GuestSessionService")
            ) {
                // The service stops the moment the session ends; give the owner
                // UI a beat, then let the caller's own wait settle the rest.
                pause(2_500)
                return true
            }
            Thread.sleep(3_000)
        }
        return device.hasObject(By.text("Start Guest Session"))
    }

    private fun topActivityLine(): String {
        val dump = shell("dumpsys activity activities")
        return dump.lineSequence().firstOrNull {
            it.contains("topResumedActivity") || it.contains("mResumedActivity")
        }?.trim()?.take(110) ?: "no resumed activity"
    }

    private fun foregroundPackage(): String {
        val dump = shell("dumpsys activity activities")
        val line = dump.lineSequence().firstOrNull {
            it.contains("topResumedActivity") || it.contains("mResumedActivity")
        } ?: return ""
        return Regex("([a-zA-Z][a-zA-Z0-9_.]+)/[a-zA-Z0-9_.]+").find(line)?.groupValues?.get(1) ?: ""
    }

    private fun scrollToVersionLine(): String? {
        repeat(8) {
            footerLine()?.let { return it }
            val x = device.displayWidth / 2
            device.swipe(
                x,
                (device.displayHeight * 0.80f).toInt(),
                x,
                (device.displayHeight * 0.30f).toInt(),
                18
            )
            pause(450)
        }
        return footerLine()
    }

    /** The dashboard footer, found by its own words rather than a version prefix. */
    private fun footerLine(): String? =
        device.findObjects(By.textContains("stays on this phone")).mapNotNull { it.text }
            .firstOrNull()
            ?: device.findObjects(By.textContains("Android 10+")).mapNotNull { it.text }
                .firstOrNull { it.contains("·") }

    /**
     * Taps a duration chip until the caption states it. A tap that lands while
     * the row is still settling is simply repeated; 3 x 8 s is far longer than
     * a recomposition needs, and a genuine bug still fails.
     */
    private fun tapChipUntil(chip: String, caption: String, attempts: Int = 3): Boolean {
        repeat(attempts) {
            if (waitForTextScan(caption, 1_000)) return true
            runCatching { tapChip(chip) }
            if (waitForTextScan(caption, 8_000)) return true
        }
        return waitForTextScan(caption, 2_000)
    }

    /** `Ends in 14:31` → 871 s, so two readings can be compared with slack. */
    private fun secondsOf(text: String?): Int? {
        val stamp = Regex("(\\d+(?::\\d+)+)").find(text ?: "")?.groupValues?.get(1) ?: return null
        return stamp.split(":").fold(0) { total, part -> total * 60 + (part.toIntOrNull() ?: return null) }
    }

    private fun waitForCountdown(): String? =
        device.findObjects(By.textContains("Ends in")).mapNotNull { it.text }
            .firstOrNull { it.startsWith("Ends in") }

    private fun textsOutOfBounds(): List<String> {
        val screen = Rect(0, 0, device.displayWidth, device.displayHeight)
        val bad = mutableListOf<String>()
        device.findObjects(By.textContains("")).forEach { node ->
            val text = runCatching { node.text }.getOrNull()
            if (text.isNullOrBlank()) return@forEach
            val bounds = runCatching { node.visibleBounds }.getOrNull() ?: return@forEach
            if (bounds.width() > 0 && bounds.height() > 0 &&
                (bounds.left < -2 || bounds.top < -2 ||
                    bounds.right > screen.right + 2 || bounds.bottom > screen.bottom + 2)
            ) {
                bad += "\"$text\" at $bounds"
            }
        }
        return bad
    }

    private fun screenshot(): Bitmap? {
        val file = File(context.cacheDir, "plan-shot.png")
        return try {
            device.takeScreenshot(file)
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (t: Throwable) {
            null
        }
    }

    private fun luma(bmp: Bitmap): Double {
        var total = 0.0
        var count = 0
        val stepX = (bmp.width / 20).coerceAtLeast(1)
        val stepY = (bmp.height / 20).coerceAtLeast(1)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                total += 0.2126 * ((c shr 16) and 0xFF) +
                    0.7152 * ((c shr 8) and 0xFF) +
                    0.0722 * (c and 0xFF)
                count++
                x += stepX
            }
            y += stepY
        }
        return if (count == 0) 0.0 else total / count
    }

    private fun isIndigo(bmp: Bitmap): Boolean {
        var hits = 0
        var count = 0
        val stepX = (bmp.width / 30).coerceAtLeast(1)
        val stepY = (bmp.height / 30).coerceAtLeast(1)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r in 20..80 && g in 20..80 && b in 90..175) hits++
                count++
                x += stepX
            }
            y += stepY
        }
        return count > 0 && hits.toDouble() / count > 0.5
    }

    private fun difference(a: Bitmap, b: Bitmap): Double {
        if (a.width != b.width || a.height != b.height) return 1.0
        var changed = 0
        var count = 0
        val stepX = (a.width / 24).coerceAtLeast(1)
        val stepY = (a.height / 24).coerceAtLeast(1)
        var y = 0
        while (y < a.height) {
            var x = 0
            while (x < a.width) {
                val p = a.getPixel(x, y)
                val q = b.getPixel(x, y)
                val delta = kotlin.math.abs(((p shr 16) and 0xFF) - ((q shr 16) and 0xFF)) +
                    kotlin.math.abs(((p shr 8) and 0xFF) - ((q shr 8) and 0xFF)) +
                    kotlin.math.abs((p and 0xFF) - (q and 0xFF))
                if (delta > 24) changed++
                count++
                x += stepX
            }
            y += stepY
        }
        return if (count == 0) 1.0 else changed.toDouble() / count
    }

    private fun swipeLeft() {
        device.swipe(
            (device.displayWidth * 0.9f).toInt(),
            (device.displayHeight * 0.45f).toInt(),
            (device.displayWidth * 0.1f).toInt(),
            (device.displayHeight * 0.45f).toInt(),
            12
        )
        pause(1_200)
    }

    private fun swipeRight() {
        device.swipe(
            (device.displayWidth * 0.15f).toInt(),
            (device.displayHeight * 0.45f).toInt(),
            (device.displayWidth * 0.85f).toInt(),
            (device.displayHeight * 0.45f).toInt(),
            20
        )
        pause(900)
    }
}
