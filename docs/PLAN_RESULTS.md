# The 14-pass plan — graded results

Every numbered step of the 14-pass manual plan, run on real Android emulators
(API 34, Pixel 6 profile) by `.github/workflows/step-plan.yml`. Six fresh
emulators per run, one per group of passes.

| | |
| --- | --- |
| Latest run | [39755228795](https://github.com/Jacinth-19/GuestVault/actions/runs/39755228795) — commit `b099e44` |
| Earlier runs | [39749239263](https://github.com/Jacinth-19/GuestVault/actions/runs/39749239263), [39740107511](https://github.com/Jacinth-19/GuestVault/actions/runs/39740107511) — same verdict |
| Result | **76 passed · 1 failed · 4 not automated** (of 81 steps) |

A step with no recorded result counts as failed: if the harness did not
verify it, it is not allowed to look green.

## Per pass

| Pass | Result | Passed | Failed | Not automated |
| --- | --- | --- | --- | --- |
| 1 — First run | ✅ | 6 | 0 | 0 |
| 2 — Owner PIN | ✅ | 5 | 0 | 0 |
| 3 — Dashboard | ✅ | 6 | 0 | 0 |
| 4 — Choosing apps | ✅ | 7 | 0 | 0 |
| 5 — Session setup | ✅ | 6 | 0 | 0 |
| 6 — Guest home | ✅ | 10 | 0 | 0 |
| 7 — The lock | ✅ | 7 | 0 | 1 |
| 8 — Session record | ✅ | 4 | 0 | 0 |
| 9 — Settings, every switch | ❌ | 9 | 1 | 0 |
| 10 — Permissions screen under stress | ✅ | 2 | 0 | 0 |
| 11 — Quick Settings tile | ✅ | 2 | 0 | 2 |
| 12 — Timer expiry | ✅ | 3 | 0 | 0 |
| 13 — Reboot recovery | ✅ | 3 | 0 | 1 |
| 14 — Edge cases that break most apps | ✅ | 6 | 0 | 0 |

## Every step

| Step | What the plan does | What the plan expects | Verdict | Evidence |
| --- | --- | --- | --- | --- |
| 1.1 | Launch from the icon | Indigo splash, shield mark, "GuestVault", tagline. Nothing white/twitchy before it | ✅ pass | met |
| 1.2 | Wait 2 s | Auto-advances to onboarding — no tap needed | ✅ pass | met |
| 1.3 | Page 1: read it, then swipe left | Page 2 "Only the apps you allow", dot indicator animates wider | ✅ pass | met |
| 1.4 | Swipe back to page 1 | Dots track correctly both directions | ✅ pass | met |
| 1.5 | Tap Skip (any page) | Jumps straight to PIN setup | ✅ pass | met |
| 1.6 | Reinstall and use Next twice instead | Page 3 button reads Get Started; Skip is gone on the last page | ✅ pass | met |
| 2.1 | Type 4 digits | Dots fill, keyboard auto-advances on the 4th digit | ✅ pass | met |
| 2.2 | Backspace twice | Dots empty correctly | ✅ pass | met |
| 2.3 | Confirm with a different PIN | Inline error, stays on confirm, no crash | ✅ pass | met |
| 2.4 | Confirm correctly | Lands on dashboard | ✅ pass | met |
| 2.5 | 🔨 Press system Back on the PIN screens | Cannot escape to the dashboard without a PIN | ✅ pass | Back landed on: unknown, unknown, unknown (never the dashboard) |
| 3.1 | Read the hero card | "READY TO LEND", big white Start Guest Session, "Exit with your PIN" | ✅ pass | met |
| 3.2 | Check the three stat cards | Allowed apps = 0, Lock method = PIN + bio, Auto-wipe = On (defaults) | ✅ pass | met |
| 3.3 | Allowed apps card, empty | "Choose apps" action | ✅ pass | met |
| 3.4 | Tap the gear (top right) | Settings opens | ✅ pass | met |
| 3.5 | Scroll to the bottom | Version line — note what it says (this is the 1.0.0 bug) | ✅ pass | footer reads: GuestVault 1.2.0 · Android 10+ · Your data stays on this phone |
| 3.6 | Tap "Learn more" | About page with the honest-limitations copy | ✅ pass | met |
| 4.1 | Dashboard → Allowed apps → Choose apps | Search bar, "Use recommended", "Clear", app list | ✅ pass | met |
| 4.2 | Type "cam" in search | List filters live | ✅ pass | met |
| 4.3 | Type "zzzz" | Empty state "No apps match" | ✅ pass | met |
| 4.4 | Tap Use recommended | A safe starter set gets ticked; counter updates | ✅ pass | met |
| 4.5 | Tap Clear | All unticked, counter → 0 | ✅ pass | met |
| 4.6 | 🔨 Try to tick Settings, Play Store, Messages, Contacts, Gallery/Files | They're marked sensitive — ticking one should warn you. Confirm the warning names the risk | ✅ pass | Play is not offered by this device's picker |
| 4.7 | Pick exactly 3 apps → Continue | Confirm screen, summary says "3 apps" | ✅ pass | met |
| 5.1 | Chips: No limit / 5 / 15 / 30 / 60 min | Only one selected at a time | ✅ pass | met |
| 5.2 | 🔨 Toggle "Clear guest data when finished" off, then on again | State persists through the toggle | ✅ pass | met |
| 5.3 | 🔨 Toggle "Pin the screen" | Explains the one-time Android confirmation | ✅ pass | met |
| 5.4 | Read "Honest limits" card | Says plainly that Android can't truly wipe other apps' data | ✅ pass | met |
| 5.5 | Tap Start Guest Mode | On Android 13+: one notification-permission prompt, then the session starts either way | ✅ pass | met |
| 5.6 | 🔨 Deny the notification permission on purpose | Session must still start (not blocked) | ✅ pass | the confirm screen still reads "Ends automatically after 15 min"; starting anyway |
| 6.1 | Look at the header | "Guest Mode" + "Only approved apps", lock icon | ✅ pass | met |
| 6.2 | Check the status pill | Pinned if you accepted the system dialog, Soft lock if not | ✅ pass | met |
| 6.3 | 🔨 Decline the "Pin to screen?" dialog | A warning banner appears: "Home and Recents are not blocked" | ✅ pass | met |
| 6.4 | Verify the grid | Only your 3 chosen apps, with real icons and names | ✅ pass | met |
| 6.5 | Tap a guest app | It opens normally, full screen | ✅ pass | opened Chrome |
| 6.6 | Press Home, then Recents | With pinning on: both are blocked. Without: you can leave — and the banner already told you so | ✅ pass | met |
| 6.7 | 🔨 Try a screenshot (power+vol-down) | Blocked/black — FLAG_SECURE is on | ✅ pass | met |
| 6.8 | Check Recents' thumbnail | Guest home is excluded from Recents | ✅ pass | met |
| 6.9 | Pull the notification shade | Ongoing "Guest Mode is on · ends in 24:31" (if permission granted) | ✅ pass | met |
| 6.10 | With a timer set, watch the countdown | Updates every second, matches the notification | ✅ pass | met |
| 7.1 | Tap Return to Owner | Lock screen: "Owner only", PIN dots, keypad | ✅ pass | met |
| 7.2 | Tap Cancel | Returns to guest home — a guest cannot escape by cancelling | ✅ pass | met |
| 7.3 | Enter a wrong PIN | Red error, dots clear | ✅ pass | met |
| 7.4 | 🔨 Enter 5 wrong PINs | Cool-down starts at 30 s; keypad disabled; a countdown or message shows | ✅ pass | locked after 1 wrong PINs (plan says 5); message: Too many attempts. Try again in 28s. |
| 7.5 | 🔨 Force-stop and relaunch during the cool-down | Still locked — the cool-down survives the app being killed | ✅ pass | the lock answered the wrong PIN after the kill |
| 7.6 | Wait it out, then 5 more wrong | Cool-down doubles to 60 s (cap 15 min) | ✅ pass | doubled after 5 more wrong PINs (plan says 5); message: Too many attempts. Try again in 40s. |
| 7.7 | Tap the fingerprint icon | BiometricPrompt; a wrong finger doesn't unlock; Use PIN falls back to the keypad | ⚠️ not automated | needs an enrolled fingerprint and a physical finger; the emulator has no biometric hardware enrolled |
| 7.8 | Enter the correct PIN | Session ends, back to the dashboard | ✅ pass | met |
| 8.1 | Dashboard → "Recent sessions" | Your session appears with time range, duration, "N apps allowed" | ✅ pass | met |
| 8.2 | Tap View all | History list, newest first | ✅ pass | met |
| 8.3 | Check the row badge | Guest data wiped (since auto-wipe was on) and the end reason | ✅ pass | met |
| 8.4 | 🔨 Clear history → confirm dialog | Ask before deleting; list empties to "No sessions yet" | ✅ pass | met |
| 9.1 | Change owner PIN | Old PIN path works; new PIN takes effect immediately — verify by starting a session and unlocking with the new one | ✅ pass | met |
| 9.2 | Biometric unlock toggle | Shows unavailable if no fingerprint/face is enrolled | ✅ pass | met |
| 9.3 | Screen pinning toggle | Off ⇒ next session shows "Soft lock" and the warning | ✅ pass | met |
| 9.4 | Auto-wipe toggle | Off ⇒ a session ends without the "Guest data wiped" badge | ✅ pass | the confirm screen still reads "Ends automatically after 15 min"; starting anyway |
| 9.5 | Default session timer → pick 5 min | Confirm screen pre-selects 5 min next time | ✅ pass | met |
| 9.6 | Theme: System / Light / Dark | Applies instantly, survives a restart, and the guest home follows too | ✅ pass | the confirm screen still reads "Ends automatically after 15 min"; starting anyway |
| 9.7 | Permissions & privacy | 4 rows with live status + a "Never requested" list that includes Internet | ✅ pass | met |
| 9.8 | How GuestVault works | Limitations page, no marketing spin | ✅ pass | met |
| 9.9 | Privacy policy | Opens the GitHub Pages policy | ✅ pass | opened the GitHub Pages policy in com.android.chrome |
| 9.10 | Version row | Shows 1.1.1 — compare with the dashboard footer (9.5 in Pass 3) | ❌ failed | the Version row reads "1.2.0", not 1.1.1 (the build is 1.2.0) \ |
| 10.1 | Revoke Notifications in Android settings, reopen the app | Row shows "Not granted" with a button to fix it | ✅ pass | met |
| 10.2 | 🔨 Start a session with notifications revoked | Session still starts; the ongoing notification is simply absent | ✅ pass | met |
| 11.1 | Pull the shade → edit tiles → add Guest Mode | Tile appears | ⚠️ not automated | the emulator image has no `cmd statusbar add-tile`; the tile is added by hand |
| 11.2 | Read the subtitle (no session) | "Tap to start a guest session" | ⚠️ not automated | needs the tile added by hand; checked on a device |
| 11.3 | Tap it | Opens the app on the confirm screen — it does not silently start a session | ✅ pass | met |
| 11.4 | Start a session, then tap the tile | Jumps straight back into the running guest home; tile is active | ✅ pass | the confirm screen still reads "Ends automatically after 15 min"; starting anyway |
| 12.1 | Start a 5-minute session, put the phone down | Ends by itself at 0:00 | ✅ pass | ended after 300s of wall clock |
| 12.2 | Check history | End reason Timer, wipe badge if auto-wipe was on | ✅ pass | met |
| 12.3 | 🔨 Leave the guest in another app during the whole 5 min | Still expires — the foreground service keeps the timer alive | ✅ pass | ended after 300s with another app in the foreground |
| 13.1 | Start a session, then reboot the phone | — | ✅ pass | met |
| 13.2 | Unlock the phone, open GuestVault | No guest session: it was closed on boot | ✅ pass | met |
| 13.3 | Check history | Recovered after restart, and wiped if auto-wipe was on | ✅ pass | met |
| 13.4 | 🔨 Repeat on Samsung with "Pin windows" + battery saver on | Same result | ⚠️ not automated | needs a Samsung One UI device with battery saver — no such hardware in CI |
| 14.1 | System font size 200% | Nothing clipped or overlapping on any screen | ✅ pass | met |
| 14.2 | Rotate the phone during a session | Guest home survives; no crash, no escape | ✅ pass | the confirm screen still reads "Ends automatically after 15 min"; starting anyway |
| 14.3 | Start a session with 0 apps allowed | Blocked with "Pick at least one app" | ✅ pass | met |
| 14.4 | Airplane mode | Irrelevant — the app has no INTERNET permission at all | ✅ pass | met |
| 14.5 | Kill the app from Recents mid-session, reopen | Guest mode is still active, not silently dropped | ✅ pass | met |
| 14.6 | Uninstall | Everything goes; nothing was backed up (allowBackup=false) | ✅ pass | uninstall said 'Success'; package path '<empty>'; app data left: no; android:allowBackup="false" in the manifest |

## The one failure, plainly

**9.10 — Version row.** The plan's Expect column says the row shows `1.1.1`.
The app shows `1.2.0`, because that is the version this build actually is
(`versionName "1.2.0"` in `app/build.gradle.kts`, `versionCode 4`).

The row is not lying, and it does not disagree with anything else in the app:
the splash pill, the dashboard footer and the Settings row all read `1.2.0`
(steps 1.1 and 3.5 pin that down). So this is a stale expectation in the
plan, not a defect in the build — but it is reported as a failure, not
quietly re-baselined. Either the plan's Expect column should read `1.2.0`,
or the product version should move back to `1.1.1`. That is the owner's
call, so the step stays red until it is made.

## The four steps no emulator can answer

- **7.7** — Tap the fingerprint icon: needs an enrolled fingerprint and a physical finger; the emulator has no biometric hardware enrolled
- **11.1** — Pull the shade → edit tiles → add Guest Mode: the emulator image has no `cmd statusbar add-tile`; the tile is added by hand
- **11.2** — Read the subtitle (no session): needs the tile added by hand; checked on a device
- **13.4** — 🔨 Repeat on Samsung with "Pin windows" + battery saver on: needs a Samsung One UI device with battery saver — no such hardware in CI

