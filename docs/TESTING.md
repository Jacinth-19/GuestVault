# Testing GuestVault

This is the test plan the project actually runs against, written so someone who
has never seen the code can pick up a phone and verify every promise the app
makes. It has three parts:

1. [Automated coverage](#part-1--automated-coverage) — what CI proves on every push.
2. [Manual test plan](#part-2--manual-test-plan) — 18 passes, screen by screen,
   including the notification system and the brute-force lock.
3. [Play Store submission checks](#part-3--play-store-submission-checks) — the
   things that only fail at review time.

The golden rule while testing: **any behaviour that lets a guest reach the owner
dashboard, the launcher, Settings, or another user's data is a release blocker.**
Everything else is a bug with a normal priority.

---

## Test environments

| Environment | Why it matters |
| --- | --- |
| Android 10 (API 29) | Minimum supported version. No `POST_NOTIFICATIONS` permission, old notification shade, no `specialUse` FGS rules. |
| Android 13 (API 33) | Runtime notification permission; the first version where declining it is easy. |
| Android 14+ (API 34/36) | Foreground-service types enforced, `specialUse` declaration reviewed by Play. |
| A Samsung device (One UI 5/6/7) | The device family the app exists for. Aggressive background killing, its own Gallery/Messages/Contacts packages. |
| A clean emulator | Deterministic screenshots and the CI walkthrough. |

Reset state between passes:

```bash
adb shell pm clear com.guestvault          # forget settings, PIN and history
adb uninstall com.guestvault               # full clean install
adb shell cmd uimode night no              # light theme
adb shell cmd uimode night yes             # dark theme
adb shell settings put global animator_duration_scale 1   # animations back on
```

---

## Part 1 — Automated coverage

### Commands

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug          # fast local gate
./gradlew :app:assembleDebugAndroidTest                      # instrumented APK
python3 scripts/assets/build_lottie.py                       # regenerate onboarding art
python3 scripts/assets/preview_lottie.py                     # contact sheet, no device needed
python3 scripts/ci/check_lottie.py                           # validate it
python3 scripts/ci/check_workflow_hygiene.py                 # no CI-skip markers in workflows
```

> The repository's Gradle wrapper is bootstrapped by CI if `gradle-wrapper.jar`
> is missing; on a fresh clone run `gradle wrapper --gradle-version 8.11.1` once.

### The splash screenshot

`docs/media/00-splash.png` is the hardest screenshot in the set, because the
screen is only up for 1.4 s and `adb screencap` on a software-rendered emulator
is slower than that. Two mechanisms make it deterministic:

1. **A held splash.** The walkthrough launches with
   `--ei com.guestvault.extra.SPLASH_HOLD_MS 4500`, which
   `MainActivity` honours **only in debug builds**. Nothing about the screen
   changes; the capture just gets a longer exposure.
2. **A gate, not a guess.** `scripts/ci/pick_splash.py` scores every frame and
   publishes only one that is indigo *and* contains a wordmark row — at least
   **6 separate ink runs** spanning ≥ 35 % of the width. This matters because
   Android 12+ draws its own system splash in the same colour with a large white
   shield: it passes "indigo", it passes "has white pixels", and it is *not* the
   branded splash. Its widest row has 3 ink runs, so it is rejected.

If the gate rejects the whole burst, the run keeps the screenshot already in
`docs/media/` and uploads `splash-capture` as an artifact: every candidate frame
plus `shots/splash-capture.json`, which records the thresholds and the reason
each frame failed.

`preview_lottie.py` rasterises the Lottie files to `/tmp/lottie-preview/*.png`
(six frames each) with Pillow, so an animation can be reviewed without an
emulator. It is a preview, not a renderer: easing is linearised and trim paths
are unsupported — which is fine, because the generator does not emit them.

### What the unit tests cover

| Area | Test | Proves |
| --- | --- | --- |
| PIN storage | `PinHasherTest` | The raw PIN is never stored; salted PBKDF2 hashes differ per PIN and per salt. |
| Brute force | `LockoutPolicyTest` | 3 wrong PINs start a 30 s cool-down, each further round doubles it, capped at 15 min, and only a correct PIN resets the counter. |
| Timer reminders | `TimerRemindersTest` | 5-min and 1-min warnings fire once each, short sessions skip the 5-min one, a dozing device gets only the most relevant reminder. |
| Session model | `SessionModelTest` | `durationMinutes = 0` means no limit; `remainingMillis()` never goes negative; end reasons round-trip through storage. |
| Formatting | `FormattersTest` | Countdown, duration and day labels stay stable. |

### What the instrumented walkthrough covers (`GuestVaultFlowTest`)

Runs on an emulator in the **Screenshots & Demo Video** workflow and produces
`screenshots/01…18`, published to `docs/media/`.

1. Cold start → splash → onboarding (three pages) → PIN creation.
2. Dashboard → Settings → allowed apps → back.
3. Confirm screen → screen pinning off → 15-minute timer → **Start Guest Mode**.
4. Guest home: asserts the owner dashboard text (`Start Guest Session`) is **not**
   present anywhere on screen.
5. Pulls down the notification shade and captures the ongoing session notice.
6. **Return to Owner** → lock screen → one wrong PIN (`Incorrect PIN. 2 attempts
   left.`) → correct PIN → dashboard.
7. Dark-mode capture, About screen, Permissions screen.

### Workflows

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `android.yml` | push to `main` or `arena/**`, PR, manual | Lint, unit tests, debug APK artifact. The fast gate. |
| `media.yml` | push to `main`, PR to `main`, manual | Emulator walkthrough, screenshots, demo video; commits refreshed media on `main`. |
| `play.yml` | push to `main` or `arena/**`, manual | Release AAB + Play-safe manifest assertions + a **four-API emulator matrix** (29, 34, 35, 36) running the same walkthrough. |
| `release.yml` | `v*` tag, manual | Signed AAB/APK attached to a GitHub release. |
| `pages.yml` | manual | Publishes the privacy policy to GitHub Pages. |

The emulator job is not a substitute for a real Samsung. It proves the flow and
the screenshots; it cannot prove that One UI will not kill the foreground
service, and it cannot show the "Pin GuestVault to this screen?" OEM dialog.

---

## Part 2 — Manual test plan

Eighteen passes. Each one states the setup, the steps and the expected result.
Work through them in order on a fresh install first, then repeat the ones marked
**⚠︎ OEM** on a Samsung device.

### Pass 1 — First install and the splash

1. Install the APK. Launch from the launcher icon.
   **Expect:** a solid indigo (`#312E81`) screen — no white flash, no gradient.
   A white shield with an indigo padlock, about 30 % of the screen width, settles
   from 0.94× to 1.0× over 600 ms.
2. Watch the wordmark: "GuestVault" in white with a small translucent pill to its
   right reading the real version (e.g. `1.2.0`).
   **Expect:** the pill matches **Settings → About** and the dashboard footer. If
   they disagree, that is a release blocker — it is exactly the bug this screen
   exists to prevent.
3. The tagline "Borrow my phone. Keep your privacy." fades in below, with a
   hairline **indeterminate** progress line: a short teal highlight sweeps across
   the track on a loop. It must never sit full and static.
4. **Expect:** after roughly 1.4 s the splash fades to onboarding. It must never
   sit forever and must never skip onboarding on a fresh install.
5. Kill the app (`adb shell am force-stop com.guestvault`) and relaunch.
   **Expect:** the same splash, then straight to the PIN screen / dashboard —
   onboarding is not shown twice.
6. On Android 12+, watch the hand-off carefully.
   **Expect:** the system splash and the app splash are the same indigo, so there
   is no white rectangle between them.
7. Turn the phone upside down / rotate.
   **Expect:** the splash does not restart its animation badly; content stays
   centred and readable.

### Pass 2 — Onboarding

1. Fresh install → splash finishes.
   **Expect:** page 1, "Lend your phone safely": a phone travels between two
   hands and a shield with a keyhole locks over it (`lend_hand.json`). It must
   read as a hand-off, not as an icon in a circle.
2. Let the animation run to the end.
   **Expect:** it settles on its final frame and stays there.
3. Swipe left.
   **Expect:** page 2, "Only approved apps": nine tiles appear, the six
   unapproved ones dim and grow a padlock, and the approved row stays lit and is
   ringed in teal (`app_grid.json`).
4. Swipe left again.
   **Expect:** page 3, "Your data stays private": the shield closes with a
   keyhole and a key spins in and docks, with a teal flash
   (`shield_key.json`). The primary button now reads **Get Started**.
5. Go back with a right swipe and press **Skip** on page 1 or 2.
   **Expect:** onboarding is marked complete and you go to PIN setup.
6. Turn animations off (Developer options → Animator duration scale: off, or
   Accessibility → Remove animations), repeat steps 1–4.
   **Expect:** each illustration shows its **final frame**, not a blank box.
7. Temporarily break an asset (see the note in `scripts/assets/build_lottie.py`):
   delete `app_grid.json` and rebuild.
   **Expect:** page 2 falls back to the static gradient icon — the app does not
   crash, and the page still explains itself.

### Pass 3 — Owner PIN

1. Onboarding → PIN setup.
   **Expect:** "Create your owner PIN", a 4-digit pad, no system keyboard.
2. Enter four digits.
   **Expect:** "Confirm your PIN", and the digits you entered are **not** shown.
3. Enter four different digits.
   **Expect:** a mismatch message and the confirmation resets; you are not
   silently given a PIN you do not know.
4. Enter the same four digits twice.
   **Expect:** the dashboard opens.
5. Force-stop and relaunch, then look at the dashboard: no PIN was ever stored in
   plain text. Verify with
   `adb shell run-as com.guestvault cat shared_prefs/*.xml` — you should see a
   hash and a salt, never `4726`.

### Pass 4 — Dashboard

Use a fresh install with a PIN and at least three allowed apps.

1. Check the header: "GuestVault", tagline, a Settings icon.
2. Check the status card: "READY TO LEND" and the greeting.
3. Check the primary button: **Start Guest Session** ("Takes a few seconds · Exit
   with your PIN").
4. Check the stats row: **Allowed apps**, **Lock method** ("PIN + bio" or "PIN"),
   **Auto-wipe** ("On"/"Off").
5. Check the allowed-apps card: icons plus "Manage", or "No apps selected yet" /
   "Choose apps" on a fresh install.
6. Scroll to "How protected is this?" → **Learn more**.
   **Expect:** the About screen, including "What needs system access".
7. Scroll to the footer.
   **Expect:** "GuestVault `<version>` · Android 10+ · Your data stays on this
   phone", with the version matching the splash pill.
8. Press **Recent sessions → View all**.
   **Expect:** "No sessions yet" until you have completed one.
9. Switch the phone to dark mode.
   **Expect:** dark surfaces (`#0F172A`), and the status-bar icons flip to light.
   Nothing turns into black-on-black text.

### Pass 5 — Selecting allowed apps

1. Dashboard → **Allowed apps → Manage** (or Settings → Allowed apps).
   **Expect:** "Guests only see what you pick", a search field, category chips and
   a count ("N apps selected").
2. Scroll the list.
   **Expect:** GuestVault itself is **not** listed. Neither is any home
   launcher — allowing one would let the guest escape immediately.
3. Search for "settings".
   **Expect:** Settings can be found, but is marked **Sensitive** with a warning.
   Allowing it is a deliberate choice, not an accident.
4. Select a browser, a camera and the calculator. Press **Save**.
   **Expect:** the count updates and the dashboard's Allowed apps row shows 3.
5. Re-open the picker and use **Use recommended**.
   **Expect:** the safe defaults are selected; gallery, messages, contacts, email,
   Play Store and Settings stay off.
6. Select nothing and press **Save**.
   **Expect:** the app warns you; a session with zero apps is refused at the
   confirm screen ("Choose at least one app before starting.").

### Pass 6 — Starting a session

1. Dashboard → **Start Guest Session**.
   **Expect:** "Start guest session" / "One last look before you hand over",
   "Guest sees N apps" with **Edit**.
2. Cycle the session-length chips: No limit, 5 min, 15 min, 30 min, 1 hour.
   **Expect:** the choice is remembered as the default for the next session.
3. Toggle **Clear guest data when finished** off and on.
   **Expect:** the subtitle stays honest — GuestVault wipes *its own* guest cache;
   other apps' data needs Device Owner mode, which the app does not have.
4. Toggle **Pin the screen**.
   **Expect:** on the first session, Android shows "Pin GuestVault to this
   screen?"; declining still leaves the soft lock in place.
5. Read the "Honest limits" card.
   **Expect:** it names the real limitations (no separate Android user, other
   apps' data untouched, notifications and quick settings still exist).
6. If notifications are not yet granted, press **Start Guest Mode** now.
   **Expect:** the system notification prompt appears, and the session starts
   either way. Declining notifications must not block the session.

### Pass 7 — The restricted guest home

1. Confirm the session.
   **Expect:** "Welcome to Guest Mode", "This phone is in a temporary, limited
   space.", the timer ("Ends in 14:59" or "No time limit"), and "Only approved
   apps".
2. Check the app grid.
   **Expect:** exactly the apps you allowed. Nothing else, no app drawer.
3. Press **Back**.
   **Expect:** you do not leave Guest Mode; the owner lock appears with the hint
   "Enter your PIN to leave Guest Mode". Press **Cancel** to return.
4. Press Home and Recents (with screen pinning on).
   **Expect:** Android keeps you in GuestVault. If pinning was declined, the
   guest home shows the honest banner "Home and Recents are not blocked".
5. Open an allowed app, then return with Recents or Back.
   **Expect:** you come back to the guest home, not the owner dashboard.
6. **⚠︎ OEM:** on One UI, also try the edge panel, split-screen and the
   notification shade's quick settings.
   **Expect:** quick settings are *not* blocked by the app — this is a documented
   limitation. Screen pinning is what stops them; verify it does.
7. Try to reach the owner dashboard by opening GuestVault from the launcher while
   inside guest mode (only possible if pinning is off).
   **Expect:** the app opens the guest home again — never the dashboard.

### Pass 8 — Notifications

Four channels, each with one job. Check them in Android settings
(**Settings → Notifications** on the phone, and the in-app
**Settings → Permissions & privacy → Notification channels**).

#### 8a. Channels

1. Fresh install, grant notifications when asked.
2. Open Settings → Permissions & privacy → **Notification channels**.
   **Expect:** four rows, all **On**: *Guest session*, *Timer reminders*,
   *Session summaries*, *Security alerts*.
3. Open Android's notification settings for GuestVault and check each channel:
   * Guest session — importance **Low**, no sound, no vibration, no badge.
   * Timer reminders — **Default**.
   * Session summaries — **Default**.
   * Security alerts — **High**, sound and vibration on, appears on the lock
     screen.
4. Turn one channel off in Android settings, come back to the app's Permissions
   screen (it refreshes on resume).
   **Expect:** that row reads **Off** with a **Turn on** shortcut.

#### 8b. The ongoing session notice

1. Start a 15-minute session. Pull down the shade.
   **Expect:** a silent, non-dismissable notification titled "Guest Mode is on"
   with "Ends in 14:31 · return with your PIN" **visible in the collapsed
   state**. There is deliberately no progress bar: in the collapsed layout it
   would replace that line, and the countdown is the point of the notice.
2. Swipe it sideways.
   **Expect:** it cannot be dismissed while the session runs.
3. Lock the phone. Look at the lock screen.
   **Expect:** the content is hidden behind a private notification (`VISIBILITY_PRIVATE`);
   no app names, no names of people, nothing personal.
4. Tap it.
   **Expect:** the guest home opens (not the dashboard).

#### 8c. Timer reminders

1. Start a 15-minute session and wait.
   **Expect:** at **5:00** remaining, "5 minutes left"; at **1:00** remaining,
   "1 minute left". Each fires **once** — no repeats every second.
2. Tap **End now** on the reminder.
   **Expect:** the owner PIN pad opens. It must not be possible to end a session
   without the PIN.
3. Start a **5-minute** session.
   **Expect:** no "5 minutes left" banner at t=0. The only reminder is
   "1 minute left" at 1:00.
4. Set **Settings → Notifications → Timer reminders** off, start a 5-minute
   session, wait for the 1-minute mark.
   **Expect:** no reminder, and the session still ends on time.
5. Put the phone to sleep through the 5-minute mark of a 15-minute session, wake
   it at 40 seconds left.
   **Expect:** exactly one notification — "1 minute left" — not a stale
   "5 minutes left" first.

#### 8d. Session summary

1. Set **Settings → Notifications → Session summary** on.
2. Start and end a session with some apps allowed and auto-wipe on.
   **Expect:** "Guest session ended" reading
   "`18 min` · `4 apps` · `12 MB cleared` · `returned by owner`" — the last part
   reflects how the session ended (`timer ended`, `cleared by owner`,
   `recovered after restart`), and the wipe segment reads "no data cleared" when
   auto-wipe was off.
3. Tap it.
   **Expect:** the owner dashboard, then history.
4. Turn the summary setting off and end another session.
   **Expect:** no summary. The setting survives a restart.
5. End a session while inside a session-notification tap.
   **Expect:** the ongoing notice and any timer reminders disappear together;
   the summary is the only thing left in the shade.

#### 8e. Security alerts

1. Start a session → **Return to Owner** → enter a wrong PIN twice.
   **Expect:** only the in-app counter moves ("Incorrect PIN. 2 attempts left.",
   then "1 attempts left" — the app deliberately does not nag, and no
   notification has been posted yet).
2. Enter a third wrong PIN.
   **Expect:** the in-app cool-down starts ("Too many attempts. Try again in 30s.")
   **and** one heads-up notification appears: "Failed unlock attempts — 3 wrong
   PINs. Owner mode is locked for 30 seconds." The notification opens the PIN pad
   and does not bypass it.
3. Keep entering wrong PINs during the cool-down.
   **Expect:** the keypad is disabled; no second notification for the same round.
4. Wait for the cool-down to end.
   **Expect:** "You can try again — The cool-down has ended. 3 attempts left
   before the next one." The in-app keypad unlocks.
5. Enter three more wrong PINs.
   **Expect:** a second cool-down, now 60 seconds, and one new notification.
6. Unlock with the correct PIN while a lockout notification is showing.
   **Expect:** it disappears immediately.
7. Check the lock screen for these alerts.
   **Expect:** private content — no digits, no hints, no attempts count visible to
   a bystander.

#### 8f. Notification privacy rules (all channels)

1. Rotate the phone and check every notification you can produce.
   **Expect:** none of them contains an app name, a person's name, a photo
   thumbnail or a location.
2. Revoke notifications (`adb shell pm revoke com.guestvault
   android.permission.POST_NOTIFICATIONS`), then run a full session.
   **Expect:** the session still starts, still expires, and ending it does not
   crash. The app must never depend on a notification to function.
3. Confirm the app has **no internet permission**:
   `adb shell dumpsys package com.guestvault | grep -i internet` → empty.

### Pass 9 — Owner lock and brute force

1. Guest home → **Return to Owner**.
   **Expect:** the owner lock: shield, "Owner only", "Enter your PIN or use your
   fingerprint to end the guest session." and the 4-digit pad.
2. Enter the correct PIN.
   **Expect:** the session ends, and you land on the dashboard with the session
   in history.
3. Start a session again and enter the wrong PIN three times.
   **Expect:** cool-down as described in 8e, with the countdown ticking down.
4. Kill the app during the cool-down and relaunch (or rotate the phone).
   **Expect:** the cool-down **survives** — it is stored on disk, not in memory.
   Reopening the lock screen must not hand out three fresh attempts.
5. During the cool-down, press **Cancel** and try to open an app.
   **Expect:** you are back in the guest home; the lock cannot be dismissed into
   owner mode.
6. Repeat wrong PINs until the cool-down is measured in minutes (30 s → 60 s →
   120 s → … capped at 15 min).
   **Expect:** the cap holds and the UI shows the real remaining time.
7. Use **End session & clear data** on the lock screen.
   **Expect:** the button switches to "Cancel data clear"; unlocking with it wipes
   the guest cache and records the session with reason "Session & data cleared".
8. With biometrics enrolled, tap the fingerprint icon and use a **non-enrolled
   fingerprint**.
   **Expect:** it fails, the PIN still works, and no attempt counter moves for a
   fingerprint failure (it is not a PIN guess).

### Pass 10 — Session history

1. End at least three sessions: one by PIN, one by **End session & clear data**,
   one by letting the timer expire.
2. Dashboard → **Recent sessions → View all**.
   **Expect:** one row per session with date/time, "N apps · N min", a reason pill
   ("Owner", "Timer", "Cleared", "Recovered") and "Wiped" when applicable.
3. Press **Clear** → confirm.
   **Expect:** "No sessions yet".
4. Verify the history contains **no app names, no timestamps of individual app
   launches, no content**.

### Pass 11 — Settings

Work down every row.

1. **Change owner PIN** → enter the old PIN? (If the flow asks for it, verify a
   wrong current PIN is rejected.) → set a new PIN → confirm → lock screen uses the
   new PIN and the old one is rejected.
2. **Biometric unlock** — on a device with no enrolment the switch is disabled and
   the subtitle says so; on a device with enrolment, toggling works and the lock
   screen hides the fingerprint icon when off.
3. **Screen pinning** — off, start a session: the guest home shows the "Soft lock"
   pill and the honest banner. On: "Pinned" once `startLockTask()` succeeds.
4. **Auto-wipe on exit** — on: ending a session reports a cleared size. Off: history
   shows no "Wiped".
5. **Default session timer** — pick 15 min; the confirm screen preselects 15 min.
6. **Session history** — opens Pass 10.
7. **Notifications → Timer reminders / Session summary** — see Pass 8.
8. **Notifications → Android notification settings** — opens the OS screen for
   GuestVault, not a generic settings page.
9. **Appearance → Theme** — System / Light / Dark all apply immediately,
   including the status bar icons.
10. **About → Permissions & privacy** — Pass 12.
11. **About → How GuestVault works** — the limitations card, and the version pill
    ("Version `<x>` · MIT") matching the splash.
12. **About → Privacy policy** — opens in-app text and offers the public URL.
13. Rotate the phone in every one of these screens. No state should be lost.

### Pass 12 — Permissions & privacy screen

1. Open **Settings → Permissions & privacy**.
   **Expect:** the banner "No internet permission" first.
2. Check "Permissions on this device": Notifications (Granted / Not granted),
   Biometrics (Available / Not set up), Foreground service (Automatic), Start at
   boot (Automatic).
3. Check "Notification channels": the four channels with **On**/**Off**. Turn one
   off in Android settings, come back.
   **Expect:** it updates on resume without reopening the app.
4. Check "Never requested": Internet, Location, Contacts, SMS & call log, Photos &
   files, Accessibility service, Device admin, Usage access.
   **Expect:** each explains *why not*. This screen is what a Play reviewer reads
   first.
5. Revoke notifications entirely and return.
   **Expect:** the channel block is replaced by "All channels blocked" with an
   explanation, and the Notifications row offers **Open settings**.

### Pass 13 — Quick Settings tile

1. Pull the shade twice → edit tiles → drag **Guest Mode** into the active tiles.
2. Tap the tile with no session running.
   **Expect:** GuestVault opens on the dashboard (or the confirm screen), and the
   session still requires a PIN to end. The tile must never start a session
   silently.
3. Tap it with a session running.
   **Expect:** the guest home opens, not a second session.

### Pass 14 — Timer expiry and auto-return

1. Start a **5-minute** session and leave the phone alone.
   **Expect:** at 1:00 a reminder; at 0:00 the guest UI closes and the owner
   dashboard returns, with the session in history as "Timer".
2. During the session, lock the screen and let the timer expire.
   **Expect:** the guest home is gone when you unlock, and the owner dashboard is
   what you see.
3. Start a session with **No limit**, then end it manually.
   **Expect:** no reminder notifications at all.

### Pass 15 — Reboot and crash recovery

1. Start a session and reboot the phone (`adb reboot`).
   **Expect:** after boot, GuestVault does not silently pretend nothing happened.
   The boot receiver closes and records the session as "Recovered" (or the
   dashboard offers **Resume Guest Mode**) and never exposes the owner dashboard
   to whoever is holding the phone.
2. Start a session, then `adb shell am force-stop com.guestvault` and relaunch.
   **Expect:** same rule — the app cannot forget an active session.
3. Kill the app mid-PIN-entry on the lock screen.
   **Expect:** the attempt counter is unchanged (it is only advanced on a real
   failure), and the cool-down is preserved.

### Pass 16 — Edge cases

1. **Zero apps allowed** → start is refused with a clear message.
2. **One app allowed** → guest home shows one tile, no empty-grid layout glitch.
3. **Uninstalled app** that was on the allow-list → starting a session must not
   crash; the tile is skipped.
4. **Airplane mode** → everything works; nothing depends on the network (there is
   no INTERNET permission to begin with).
5. **Large font scale** (200%) → onboarding scrolls instead of clipping; the
   confirm-screen button stays reachable.
6. **RTL locale** (e.g. Arabic) → no mirrored digits on the PIN pad, no clipped
   text.
7. **Low storage** → the wipe reports 0 KB instead of crashing.
8. **Second Android user / work profile** → GuestVault only ever sees its own
   user's apps.
9. **Screen recorder / screenshot inside guest mode** → blocked in release builds
   (`FLAG_SECURE`), allowed in debug builds so CI can capture documentation.

### Pass 17 — Accessibility

1. TalkBack: every icon button announces something meaningful ("Settings", "Lock
   screen", "PIN key 1", "Version 1.2.0").
2. The PIN pad is reachable and usable with a switch device.
3. Contrast: white on `#312E81` and `#64748B` on white meet WCAG AA for body text.
4. Animations off → splash still shows and completes; onboarding shows final
   frames (Pass 2, step 6).

### Pass 18 — Release build (R8)

1. Install the **release** APK (minified), not the debug one.
2. Repeat Passes 1, 3, 6, 7, 9 and 8b.
   **Expect:** no `ClassNotFoundException`, no missing Lottie resources, no empty
   onboarding illustrations. R8 keeps the Lottie and Compose model classes; if a
   rule is missing, this is where it shows up.
3. Confirm screenshots of the guest home are blocked (`FLAG_SECURE`).

---

## Part 3 — Play Store submission checks

Do these the day you upload, not the day you submit.

- [ ] `targetSdk = 36` (Play requires API 36 for new apps and updates from
      31 August 2026).
- [ ] Version code is higher than the last upload; version name matches the tag
      (`v1.2.0`) and the in-app version pill.
- [ ] The AAB is signed by Play App Signing, not by a debug key.
- [ ] `specialUse` foreground-service declaration in Play Console still has its
      description **and** the demo video link. Adding channel *types* is fine;
      adding a new FGS **permission** requires a new declaration.
- [ ] Data safety form: no data collected, no data shared, no encryption needed —
      and **no INTERNET permission** to contradict it.
- [ ] Privacy policy URL is public, non-geofenced and reachable without login:
      <https://jacinth-19.github.io/GuestVault/privacy.html>
- [ ] The policy is linked in-app (About → Privacy policy) as well as in Console.
- [ ] Screenshots: 2–8 phone shots, JPEG/24-bit PNG, 320–3840 px, long side ≤ 2×
      short side, no alpha channel.
- [ ] Feature graphic exactly 1024×500; icon 512×512 32-bit PNG ≤ 1 MB.
- [ ] The store listing does **not** claim GuestVault creates a real Android user
      profile, isolates other apps' data, or blocks quick settings. The in-app
      "Honest limits" card and the README say the same thing.
- [ ] Closed testing: a personal developer account needs 12 testers for 14
      continuous days before production.
- [ ] Every screenshot in the listing shows a build you would actually ship
      (no debug banners, no placeholder text).

---

## Reporting a bug

Include: build (`versionName` + `versionCode`), Android version, OEM, whether
screen pinning was on, and the exact screen. If it is a confinement bug — the
guest reached something they should not have — mark it **blocker** and attach a
screen recording.

```bash
adb logcat -d > logcat.txt
adb shell dumpsys activity activities > activities.txt
```

The CI walkthrough attaches `logcat.txt`, `logcat-app.txt` and `emulator.log` to
every run, which is usually enough to reproduce a failure without a device.
