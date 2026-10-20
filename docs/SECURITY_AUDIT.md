# Guest-escape audit

Threat model: **the guest is a motivated stranger holding an unlocked phone.** They can tap,
swipe, press hardware keys, rotate, reboot, and use anything Android exposes to a normal user.
They cannot use ADB, root the device, or install tools.

Goal of the audit: find every path from the restricted guest home back to the owner's real
phone, plus every way to brute-force the way back.

Severity reflects *how easy the escape is* on a device where the owner accepted the defaults
(screen pinning on, biometrics available).

---

## 1. What was exploitable, and what changed

### C-1 · Back button finished the guest activity — **critical, fixed**

`GuestHomeRoute` used `BackHandler(enabled = !showLock)`. While the owner lock was on screen the
handler was disabled, so Back fell through to the activity default and `finish()`ed
`GuestHomeActivity`. The guest landed on the real launcher with the session still active —
two taps, no pinning bypass needed.

```
Return to Owner → Back → (activity finishes) → launcher → Settings → uninstall GuestVault
```

`MainActivity`'s `ON_RESUME` redirect only runs when GuestVault is opened again; it cannot
recall an activity the guest never returns to. Fixed by keeping the handler enabled
unconditionally (`BackHandler(enabled = true)`), so Back always opens the owner lock and is
consumed. Re-verified in `GuestVaultFlowTest`.

### C-2 · Rotate or kill the activity to reset the PIN counter — **critical, fixed**

`LockViewModel` held `failedAttempts` and `lockoutUntil` as plain fields. A configuration
change recreated the ViewModel and the guest got five fresh attempts; dismissing the task from
Recents did the same. With 10,000 possible PINs and no cost per attempt, the lock was
decorative.

Fixed with a persisted guard:

- `domain/model/UnlockGuard.kt` — `(failedAttempts, lockedUntil)` on disk via DataStore;
- `guest/LockoutPolicy.kt` — pure policy, 5 attempts per round, cool-down **doubling** 30 s → 60 s
  → 120 s … capped at **15 minutes**, never reset by anything except a correct PIN or biometric;
- `LockViewModel` reads the guard before enabling the keypad (`guardLoaded`) so a fast tap
  cannot race the load, and writes it before evaluating the attempt.

Unit-tested in `app/src/test/java/com/guestvault/guest/LockoutPolicyTest.kt`, including the
"cool-down expires but the counter does not" case that the old code got wrong.

### H-1 · PIN pad and guest UI were screenshot-able — **high, fixed**

No `FLAG_SECURE` anywhere. A guest could record the owner entering the PIN (shoulder-surf
upgrade), and the owner-lock frame leaked into the Recents thumbnail.

`GuestHomeActivity` now sets `FLAG_SECURE` when `BuildConfig.SECURE_WINDOWS` is true, which is
the **release** build only — the debug build keeps it off so the UiAutomator walkthrough can
still capture store screenshots. This is a compile-time constant, not a runtime switch the guest
can influence.

### H-2 · Reboot stranded the session — **high, fixed**

Sessions are persisted (correctly, so a crash cannot "forget" a guest), but nothing closed one
across a reboot. After a restart the owner would open GuestVault and be bounced into guest mode
by the redirect, with no guest present and no obvious way out; meanwhile the guest's logins in
allowed apps survived.

Added `core/BootCompletedReceiver` (`BOOT_COMPLETED` + `MY_PACKAGE_REPLACED`) which ends the
session with `EndReason.RECOVERED` and runs the normal wipe path when auto-wipe is on. It is a
`goAsync()` receiver that touches only DataStore — no activity or foreground service is started,
so background-start restrictions do not apply.

### M-1 · Declining the screen-pinning prompt looked like success — **medium, fixed**

`startLockTask()` was wrapped in `runCatching` and the UI showed "Pinned" based on the lock-task
state, but nothing told the owner that Home/Recents stayed available when the system dialog was
declined. The guest home now shows an explicit banner — *"Home and Recents are not blocked"* —
and re-reads the state 700 ms after the request so the pill is accurate.

---

## 2. Residual risk (by design, documented)

These are **not** bugs to fix silently; the app states them in
`Settings → How GuestVault works` and the store listing.

| ID | Issue | Why it remains | Mitigation |
|---|---|---|---|
| R-1 | Home/Recents reachable if pinning is declined or unsupported | Android requires the one-time system confirmation, and an OEM can disable pinning | Warning banner; listing says so; Device Owner/kiosk is the only airtight option and is out of scope for Play |
| R-2 | Notification shade and Quick Settings stay usable when not pinned | System UI is not an app surface a normal app can block | Pinning blocks them; the QS tile cannot end a session |
| R-3 | Allowed apps can reach their own surfaces (a browser can download, a share sheet can reach contacts) | That is what allowing an app means | The picker labels sensitive apps; README advises picking browsers carefully |
| R-4 | 4-digit PIN = 10,000 combinations | Product decision (spec) | PBKDF2-SHA256 ×120k inside EncryptedSharedPreferences; online guessing is rate-limited; offline guessing needs root first |
| R-5 | `BIOMETRIC_WEAK` accepts class-2 face unlock | Many mid-range devices only offer weak | PIN always available; biometrics are a convenience, not the only factor |
| R-6 | Guest can read the ongoing notification | Required for a foreground service | Notification is deliberately content-free |

---

## 3. Attack checklist (what the guest could try)

| Attempt | Result |
|---|---|
| Back from guest home / from lock screen | Opens owner lock, consumed |
| Rotate the device on the lock screen | Counter persists |
| Kill the task from Recents during a session | Session persists; relaunch returns to guest mode |
| Take a screenshot of the PIN pad | Black frame (`FLAG_SECURE`, release) |
| Screen-record the unlock | Black frame (`FLAG_SECURE`, release) |
| Reboot and reopen GuestVault | Session closed as `RECOVERED` at boot |
| Reinstall / force-stop GuestVault from Settings | Requires leaving the guest UI first — blocked while pinned |
| Launch GuestVault from its notification | Redirects to guest mode, never to the dashboard |
| Open the Quick Settings tile during a session | Opens the guest home; cannot end the session |
| Start `GuestHomeActivity` from another app | `android:exported="false"` |
| Guess 5 PINs, rotate, guess 5 more | Second round costs 60 s, then 120 s … capped at 15 min |
| Inspect the PIN on disk | Salted PBKDF2 hash only, inside EncryptedSharedPreferences |
| Pull data over the network | No `INTERNET` permission exists |

---

## 4. Changes that make regressions visible

- `scripts/ci/assert_play_manifest.py` fails CI if `BIND_DEVICE_ADMIN`, `INTERNET`,
  accessibility, storage, contacts, SMS, location or usage-access permissions reappear in the
  shipping manifest.
- `LockoutPolicyTest` covers the escalation maths.
- The instrumented walkthrough still asserts the guest home does **not** contain
  "Start Guest Session" (owner UI leak check) and exercises a wrong PIN before unlocking.

---

## 5. Recommended next steps (not in this pass)

1. **6-digit PIN option** with a modern PIN pad — removes the 10,000-combination ceiling
   entirely (R-4).
2. **Intruder photo** on repeated failures, stored app-privately and shown only to the owner —
   the single most requested feature in this app category (see [`COMPETITORS.md`](COMPETITORS.md)).
3. **Delay-then-wipe**: after N failed rounds, offer the owner an auto-wipe of the guest cache
   rather than only a longer cool-down.
4. **Lock-task failure telemetry in-app** (already surfaced) plus a settings hint that points
   owners at "Pin windows" in Android settings on OEMs that hide it (R-1).
