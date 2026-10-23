package com.guestvault.plan

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
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

    /**
     * The PIN the app currently has.
     *
     * Steps run as separate instrumentation processes, so a PIN changed in 9.1
     * would otherwise be forgotten and every later unlock would spend attempts
     * on the old one until the cool-down locked the whole pass out. The value is
     * kept in the app's own data directory, so `pm clear` (and a fresh install)
     * wipes the note exactly when it wipes the PIN.
     */
    protected var pinDigits: List<String> = pin.map(Char::toString)
        private set

    protected fun rememberPin(digits: List<String>) {
        pinDigits = digits
        runCatching {
            File(context.filesDir, PIN_FILE).writeText(digits.joinToString(""))
        }
    }

    private fun restorePin() {
        val saved = runCatching { File(context.filesDir, PIN_FILE).readText().trim() }.getOrNull()
        if (saved != null && saved.length == 4 && saved.all(Char::isDigit)) {
            pinDigits = saved.map(Char::toString)
        }
    }

    /** Call from `@Before`. Leaves the device awake, unlocked and on the launcher. */
    open fun prepare() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        context = ApplicationProvider.getApplicationContext()

        StepResults.initialise(context)
        restorePin()

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
    private var pendingNote: String = ""

    /** Attaches evidence to the step currently being verified. */
    protected fun note(text: String) {
        pendingNote = text
    }

    /** A setup failure is reported against every step of the pass, not just swallowed. */
    protected var setupError: String? = null

    protected fun arrange(block: () -> Unit) {
        if (setupError != null) return
        try {
            block()
        } catch (t: Throwable) {
            val message = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
            setupError = "$message (screen: ${screen()} | ${visibleText().take(140)})"
        }
    }

    protected fun verify(
        id: String,
        expect: String,
        note: String = "",
        body: () -> Unit
    ) {
        val started = System.currentTimeMillis()
        pendingNote = ""
        if (setupError != null) {
            StepResults.fail(id, expect, "setup failed before this step: $setupError", 0)
            return
        }
        try {
            body()
            val evidence = note.ifBlank { pendingNote.ifBlank { "met" } }
            StepResults.pass(id, expect, evidence, System.currentTimeMillis() - started)
        } catch (t: Throwable) {
            val message = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
            val onScreen = runCatching { visibleText().take(200) }.getOrDefault("")
            val focus = runCatching {
                // No shell pipes here: executeShellCommand does not run `sh`,
                // so `| grep` would be passed to dumpsys as an argument.
                shell("dumpsys window").lineSequence()
                    .firstOrNull { it.contains("mCurrentFocus") }
                    ?.replace("mCurrentFocus=", "")?.trim()?.take(60).orEmpty()
            }.getOrDefault("")
            val evidence = buildString {
                append(message)
                if (pendingNote.isNotBlank()) append(" (").append(pendingNote).append(")")
                if (onScreen.isNotBlank()) append(" | screen: ").append(onScreen)
                if (focus.isNotBlank()) append(" | focus: ").append(focus)
            }
            StepResults.fail(id, expect, evidence, System.currentTimeMillis() - started)
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

    protected fun pinnedDialogVisible(): Boolean =
        has(By.textContains("App is pinned")) ||
            (has(By.text("No thanks")) && has(By.text("Got it"))) ||
            device.findObjects(By.textContains("to this screen")).any {
                it.text?.contains("to this screen", ignoreCase = true) == true
            }

    /** Pinning is re-requested every time the guest task is recreated. */
    /**
     * The system asks to pin the guest task a moment *after* it resumes, so a
     * single check right after `am start` misses it. Poll for a few seconds.
     */
    protected fun dismissPinnedDialog(timeoutMs: Long = 10_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var dismissed = false
        while (System.currentTimeMillis() < deadline) {
            if (!pinnedDialogVisible()) {
                if (dismissed) return true
                pause(300)
                continue
            }
            when {
                has(By.text("No thanks")) -> { tapText("No thanks"); dismissed = true; pause(800) }
                has(By.text("Got it")) -> { tapText("Got it"); dismissed = true; pause(800) }
                else -> pause(300)
            }
        }
        return dismissed
    }

    protected fun launchApp() {
        device.pressHome()
        pause(400)
        val started = runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        pause(1_200)
        // A cold start after `pm clear` can be slow, and `startActivity` from the
        // instrumentation is not always honoured. Fall back to the shell and
        // wait for something we recognise instead of guessing with a sleep.
        if (!started || screen() == "unknown") {
            shell("am start -n ${context.packageName}/.MainActivity")
            pause(1_500)
        }
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

    /** What the app is showing right now, or "unknown". */
    private fun has(selector: BySelector): Boolean =
        runCatching { device.hasObject(selector) }.getOrDefault(false)

    protected fun screen(): String = when {
        pinnedDialogVisible() -> "guestHome"
        has(By.text("Start Guest Session")) -> "dashboard"
        has(By.text("Welcome to Guest Mode")) -> "guestHome"
        has(By.text("Create your owner PIN")) -> "pinCreate"
        has(By.text("Confirm your PIN")) -> "pinConfirm"
        has(By.desc("PIN key 1")) -> "lock"
        has(By.text("Skip")) || has(By.text("Lend your phone safely")) -> "onboarding"
        else -> "unknown"
    }

    /** One onboarding move: Get Started on the last page, Skip anywhere else. */
    protected fun advanceOnboarding() {
        if (tapTextIfPresent("Get Started")) {
            pause(1_200)
            return
        }
        if (tapTextIfPresent("Skip")) {
            pause(1_200)
            return
        }
        if (tapTextIfPresent("Next")) pause(1_200)
    }

    protected fun tapTextIfPresent(text: String): Boolean {
        val node = device.findObject(By.text(text)) ?: return false
        return runCatching {
            node.click()
            pause(500)
            true
        }.getOrDefault(false)
    }

    protected fun tapDescIfPresent(desc: String): Boolean {
        val node = device.findObject(By.desc(desc)) ?: return false
        return runCatching {
            node.click()
            pause(500)
            true
        }.getOrDefault(false)
    }

    /**
     * Walks whatever screen is showing to the owner dashboard.
     *
     * Screen-driven rather than scripted: each iteration looks at what is
     * actually on screen and does the one thing that moves the state forward.
     * That survives a page that animates slowly, a cold start that takes ten
     * seconds, or a session left running by the previous pass.
     */
    protected fun ensureOwnerHome() {
        repeat(16) {
            when (screen()) {
                "dashboard" -> return
                "onboarding" -> advanceOnboarding()
                "pinCreate", "pinConfirm" -> {
                    // The probe can be a frame ahead of the screen; only type
                    // when the keypad is really there, otherwise look again.
                    tryEnterPin(pinDigits)
                    // Later phases run in a new process: record which PIN this is.
                    rememberPin(pinDigits)
                    pause(1_200)
                }
                "guestHome" -> {
                    dismissPinnedDialog()
                    tapTextIfPresent("Return to Owner")
                    pause(1_200)
                }
                "lock" -> {
                    if (tryEnterPin(pinDigits)) {
                        // The wipe and the hand-over back to the owner take a
                        // moment; wait for the outcome instead of sampling.
                        runCatching { device.wait(Until.hasObject(By.text("Start Guest Session")), 12_000) }
                    }
                    pause(800)
                }
                else -> {
                    // A nested screen (picker, settings, history) is not "home".
                    // Relaunching only brings the same screen forward, so walk
                    // back out of it first.
                    if (device.hasObject(By.text("Allow anyway"))) {
                        tapText("Cancel")
                        pause(600)
                    } else if (device.hasObject(By.desc("Back"))) {
                        device.findObject(By.desc("Back"))?.click()
                        pause(900)
                    } else {
                        launchApp()
                        pause(1_200)
                    }
                }
            }
        }
        expectTrue(
            screen() == "dashboard",
            "owner app never reached the dashboard (screen: ${screen()}; " +
                "text=${visibleText().take(110)}; " +
                "coolDown=${has(By.textContains("Too many attempts"))}) | " +
                "files=${runCatching { shell("run-as ${context.packageName} ls -R files").take(160) }.getOrDefault("?")} | " +
                "log=${recentLog(5)}"
        )
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

    /**
     * Types a PIN only while the keypad is on screen. Used by the setup state
     * machine, where a stale probe must not fail the pass.
     */
    protected fun tryEnterPin(digits: List<String>): Boolean {
        // A cool-down ignores taps; waiting is the difference between "the PIN
        // was rejected" and "the app never saw it".
        if (has(By.textContains("Too many attempts"))) {
            val deadline = System.currentTimeMillis() + 120_000
            while (System.currentTimeMillis() < deadline &&
                has(By.textContains("Too many attempts"))
            ) {
                Thread.sleep(1_000)
            }
            pause(600)
        }
        for (digit in digits) {
            val selector = By.desc("PIN key $digit")
            val found = runCatching { device.wait(Until.hasObject(selector), 5_000) }.getOrDefault(false)
            if (!found) return false
            runCatching { device.findObject(selector)!!.click() }
            pause(180)
        }
        pause(600)
        return true
    }

    protected fun enterPin(digits: List<String>) {
        // The lock screen renders its keypad before the stored guard has loaded;
        // taps during that window are dropped, which looked like a wrong PIN.
        runCatching { device.wait(Until.hasObject(By.desc("PIN key 1")), 8_000) }
        pause(400)

        // While a cool-down is running the keypad ignores every tap; typing into
        // it would leave the dots empty and look like a broken screen.
        if (has(By.textContains("Too many attempts"))) {
            val deadline = System.currentTimeMillis() + 120_000
            while (System.currentTimeMillis() < deadline &&
                has(By.textContains("Too many attempts"))
            ) {
                Thread.sleep(1_000)
            }
            pause(800)
        }

        digits.forEach { digit ->
            val selector = By.desc("PIN key $digit")
            var found = runCatching { device.wait(Until.hasObject(selector), 8_000) }.getOrDefault(false)
            if (!found) {
                pause(800)
                found = runCatching { device.wait(Until.hasObject(selector), 6_000) }.getOrDefault(false)
            }
            if (!found) {
                // The flow can move on mid-entry (the fourth digit submits, the
                // session ends, a screen closes). There is nothing left to type.
                if (!has(By.desc("PIN key 1"))) return
                expectTrue(
                    false,
                    "PIN key $digit not found (screen: ${screen()} | ${visibleText().take(140)})"
                )
            }
            // A tap can be swallowed while Compose re-lays out (the dots row
            // grows). A dropped digit makes the *next* attempt verify the wrong
            // four digits, which reads as "Incorrect PIN" for a correct PIN.
            runCatching { device.findObject(selector)!!.click() }
            pause(420)
        }
        pause(900)
    }

    /**
     * Types the PIN and, if the lock answers "Incorrect PIN" anyway, waits for
     * the error to clear and types it once more. Two attempts is the most that
     * can be spent here: the third wrong attempt starts the cool-down.
     */
    protected fun enterPinWithRetry(digits: List<String>) {
        // A cool-down left over from an earlier step would swallow every tap.
        if (has(By.textContains("Too many attempts"))) {
            val deadline = System.currentTimeMillis() + 70_000
            while (System.currentTimeMillis() < deadline && has(By.textContains("Too many attempts"))) {
                Thread.sleep(1_000)
            }
            pause(800)
        }
        enterPin(digits)
        repeat(2) {
            if (!has(By.textContains("Incorrect PIN"))) return
            pause(1_200)
            enterPin(digits)
        }
    }

    // ------------------------------------------------------------- finders

    protected fun pause(ms: Long) = Thread.sleep(ms)

    protected fun shell(command: String): String =
        runCatching { device.executeShellCommand(command) }.getOrDefault("")

    /**
     * The last few lines that explain a disappearing activity: task-manager
     * decisions, guest-app crashes, permission denials.
     */
    protected fun recentLog(limit: Int = 7): String {
        val dump = shell("logcat -d -t 300")
        return dump.lineSequence()
            .filter {
                (it.contains("guestvault", ignoreCase = true) && !it.contains("guestvault-verify")) ||
                    it.contains("FATAL EXCEPTION") ||
                    it.contains("ActivityTaskManager") ||
                    it.contains("Force finishing") ||
                    it.contains("ANR in")
            }
            .toList()
            .takeLast(limit)
            .joinToString(" ~ ") { line ->
                line.substringAfter(": ", line).trim().take(120)
            }
    }

    /**
     * A duration chip may start off-screen in the confirm screen's LazyRow.
     * Scrolls the row horizontally until the label is reachable.
     */
    protected fun tapChip(label: String): Boolean {
        if (has(By.text(label))) {
            tapText(label, timeout = 3_000)
            return true
        }
        // Scroll the page down to the chip row, then use one of its chips as an
        // anchor so the horizontal fling lands on the LazyRow, not on the page.
        scrollToText("No limit", maxSwipes = 6)
        val anchor = runCatching {
            device.findObject(By.text("No limit")) ?: device.findObject(By.text("5 min"))
        }.getOrNull()
        if (anchor == null) return false
        val y = runCatching { anchor.visibleCenter.y }.getOrDefault(device.displayHeight / 2)
        repeat(4) {
            device.swipe(
                (device.displayWidth * 0.85f).toInt(), y,
                (device.displayWidth * 0.2f).toInt(), y,
                10
            )
            pause(600)
            if (has(By.text(label))) {
                tapText(label, timeout = 3_000)
                return true
            }
        }
        return false
    }

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
        val clicked = runCatching { device.findObject(By.text(text))!!.click() }.isSuccess
        if (!clicked) {
            pause(400)
            runCatching { device.findObject(By.text(text))?.click() }
        }
        pause(450)
    }

    protected fun tapDesc(desc: String, timeout: Long = 15_000) {
        expectTrue(waitForDesc(desc, timeout), "Timed out waiting for description \"$desc\"")
        val clicked = runCatching { device.findObject(By.desc(desc))!!.click() }.isSuccess
        if (!clicked) {
            pause(400)
            runCatching { device.findObject(By.desc(desc))?.click() }
        }
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
        // Swipes can be swallowed by rows with their own gestures (a switch, a
        // channel with an action). Accessibility scrolling goes straight to the
        // list and is the fallback that reaches the end of a long Compose list.
        repeat(6) {
            if (device.hasObject(By.text(text))) return true
            scrollForward()
            pause(400)
        }
        return device.hasObject(By.text(text))
    }

    /**
     * One accessibility scroll down the page's own scrollable list.
     *
     * The largest scrollable is the page; a smaller one is a nested row (a
     * horizontal chip strip, a channel with its own action) and scrolling that
     * leaves the page where it was.
     */
    protected fun scrollForward(percent: Float = 0.9f) {
        val lists = runCatching { device.findObjects(By.scrollable(true)) }.getOrDefault(emptyList())
        val list = lists.maxByOrNull {
            runCatching { it.visibleBounds.height() }.getOrDefault(0)
        } ?: return
        runCatching { list.scroll(Direction.DOWN, percent) }
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

    /**
     * Polls the same text scan the failure evidence uses.
     *
     * `By.text` is an exact match on one node; a caption can be a merged node
     * whose text is "No time limit" plus its own descendants. Polling the scan
     * keeps the assertion and the evidence from disagreeing.
     */
    protected fun waitForTextScan(text: String, timeoutMs: Long = 10_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (visibleText().contains(text, ignoreCase = true)) return true
            Thread.sleep(250)
        }
        return visibleText().contains(text, ignoreCase = true)
    }

    /** Scrolls the page down until [predicate] holds, alternating methods. */
    /**
     * Scrolls until [predicate] holds, rotating three ways of asking.
     *
     * One method can silently stop working: the accessibility action is a no-op
     * on a list that is not the semantics scrollable, and an injected gesture is
     * swallowed when it starts on a row that takes the drag itself (a switch, a
     * channel with its own action). Trying all three in turn is what keeps a
     * long Settings page from stalling half-way down.
     */
    protected fun scrollUntil(timeoutMs: Long = 25_000, predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var round = 0
        var lastPage = ""
        var stillRounds = 0
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return true
            when (round % 3) {
                0 -> scrollForward()
                1 -> {
                    val x = device.displayWidth / 2
                    device.swipe(
                        x, (device.displayHeight * 0.80f).toInt(),
                        x, (device.displayHeight * 0.25f).toInt(), 16
                    )
                }
                // Injected by the system, so it cannot be intercepted by a
                // Compose pointer handler on the row under the finger.
                else -> systemSwipeUp()
            }
            round++
            pause(400)
            // If three different scroll methods all leave the page byte-for-byte
            // identical, the list is wedged (a system window is eating input, the
            // emulator is degrading). Burning the rest of the timeout is useless:
            // return early so the caller can take a different route.
            val page = visibleText().take(300)
            if (page == lastPage) {
                stillRounds++
                if (stillRounds >= 3) return predicate()
            } else {
                stillRounds = 0
                lastPage = page
            }
        }
        return predicate()
    }

    /** The biggest scrollable on screen: the page, not a card inside it. */
    protected fun tallestScrollable(): UiObject2? = runCatching {
        device.findObjects(By.scrollable(true))
            .maxByOrNull { it.visibleBounds.width() * it.visibleBounds.height() }
    }.getOrNull()

    /**
     * Scrolls the page itself to the bottom.
     *
     * `scrollForward()` asks the accessibility tree to scroll; a Compose
     * `LazyColumn` only moves through that action while it is the semantics
     * scrollable, and coalesced gestures are swallowed by rows that handle drags.
     * Driving the scrollable node directly is the one route that survives both.
     */
    protected fun scrollToBottom(maxSteps: Int = 12, predicate: () -> Boolean): Boolean {
        if (predicate()) return true
        var node = tallestScrollable()
        repeat(maxSteps) {
            if (predicate()) return true
            val moved = node?.let { n ->
                runCatching { n.scroll(Direction.DOWN, 0.9f) }.getOrDefault(false)
            } ?: false
            if (!moved) {
                systemSwipeUp()
                node = tallestScrollable()
            }
            pause(320)
        }
        return predicate()
    }

    /** A scroll injected by `input`, independent of UiAutomator's gesture path. */
    protected fun systemSwipeUp(fraction: Float = 0.55f) {
        val x = (device.displayWidth * 0.5f).toInt()
        val fromY = (device.displayHeight * 0.78f).toInt()
        val toY = (fromY - device.displayHeight * fraction).toInt()
        shell("input swipe $x $fromY $x $toY 220")
    }

    /** Every visible text on screen — never throws: a stale node must not cost a whole pass its verdicts. */
    protected fun visibleText(): String = runCatching {
        device.findObjects(By.textContains(""))
            .mapNotNull { node -> runCatching { node.text }.getOrNull() }
            .joinToString(" | ")
    }.getOrDefault("")

    protected fun shot(name: String) {
        pause(350)
        runCatching { device.takeScreenshot(File(screenshots, name)) }
    }

    private companion object {
        const val PIN_FILE = "plan-pin.txt"
    }
}
