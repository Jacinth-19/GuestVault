<div align="center">

<img src="docs/media/logo.svg" width="96" alt="GuestVault icon" />

# GuestVault

**Borrow my phone. Keep your privacy.**

A practical, privacy-first Guest Mode for Android phones that don't ship one.

[![Android CI](https://github.com/Jacinth-19/GuestVault/actions/workflows/android.yml/badge.svg)](https://github.com/Jacinth-19/GuestVault/actions/workflows/android.yml)
[![Screenshots & Demo](https://github.com/Jacinth-19/GuestVault/actions/workflows/media.yml/badge.svg)](https://github.com/Jacinth-19/GuestVault/actions/workflows/media.yml)
![API](https://img.shields.io/badge/API-29%2B-312E81?style=flat-square)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square)
![Compose](https://img.shields.io/badge/Compose-Material%203-4285F4?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-0D9488?style=flat-square)

</div>

---

GuestVault lets you hand your phone to a friend, a child or a stranger without handing over your life.
Pick the apps they may use, hand over the phone, and take it back with your PIN or fingerprint.
Everything else — gallery, messages, files, settings, Play Store — stays behind the lock.

<div align="center">

<img src="docs/media/preview.gif" width="300" alt="GuestVault app preview" />

<sub>The owner dashboard → allow-list → restricted guest home → PIN-protected exit</sub>

</div>

## 🎬 Demo

**▶ [Watch the full walkthrough video (79 s, MP4)](docs/media/walkthrough.mp4)** — recorded on a Pixel 6
emulator by the `Screenshots & Demo Video` workflow: onboarding → PIN → dashboard → allow-list →
session setup → guest home → wrong PIN → unlock → history → dark mode.

[![GuestVault walkthrough](docs/media/preview.gif)](docs/media/walkthrough.mp4)

<sub>Every image and the video below are produced by CI from the real app — no mockups.</sub>

## 📸 Screens

**Getting started**

| Splash & onboarding | Only approved apps | Your data stays private | Create the owner PIN |
|:--:|:--:|:--:|:--:|
| <img src="docs/media/01-onboarding-welcome.png" width="190"/> | <img src="docs/media/02-onboarding-apps.png" width="190"/> | <img src="docs/media/03-onboarding-keys.png" width="190"/> | <img src="docs/media/04-pin-create.png" width="190"/> |

**Owner mode**

| Dashboard | Choose allowed apps | Confirm & start | Settings |
|:--:|:--:|:--:|:--:|
| <img src="docs/media/06-dashboard-light.png" width="190"/> | <img src="docs/media/08-select-apps.png" width="190"/> | <img src="docs/media/09-confirm-session.png" width="190"/> | <img src="docs/media/07-settings.png" width="190"/> |

**Guest mode**

| Restricted guest home | Session options | PIN-protected exit | Wrong PIN → cool-down |
|:--:|:--:|:--:|:--:|
| <img src="docs/media/11-guest-home.png" width="190"/> | <img src="docs/media/10-confirm-options.png" width="190"/> | <img src="docs/media/12-lock-screen.png" width="190"/> | <img src="docs/media/13-lock-wrong-pin.png" width="190"/> |

**After the session**

| Dashboard + history | Dark theme | About & limitations | PIN confirmation |
|:--:|:--:|:--:|:--:|
| <img src="docs/media/14-dashboard-history.png" width="190"/> | <img src="docs/media/15-dashboard-dark.png" width="190"/> | <img src="docs/media/16-about-dark.png" width="190"/> | <img src="docs/media/05-pin-confirm.png" width="190"/> |

## ✨ Features

**Owner**
- 3-page onboarding, then a 4-digit owner PIN
- Material 3 dashboard (light + dark, following the system by default)
- Allow-list with search, categories, "recommended" and "sensitive" markers
- Session setup: allowed-app summary, optional timer, auto-wipe, screen pinning
- Settings: change PIN, biometrics, screen pinning, default timer, theme, history

**Guest**
- A clean, launcher-style home that only shows the apps you approved
- Live countdown when a session timer is set
- "Return to Owner" always one tap away — but it needs your PIN or fingerprint

**Protection**
- PIN is stored only as a salted **PBKDF2-SHA256** hash inside `EncryptedSharedPreferences`
- 5 wrong attempts → 30 second cool-down
- Optional Android **screen pinning** (`startLockTask`) so Home/Recents can't escape the guest UI
- Session is persisted to disk *before* the guest UI opens, so a crash can't "forget" a stranger is holding the phone
- Foreground service keeps timed sessions running even if the guest swipes the app away
- Auto-wipe of GuestVault's cache and guest scratch space when a session ends
- No internet permission, no analytics, no accounts, no backups

## 🖥️ Screens

| Screen | What it does |
|---|---|
| **Splash** | Brand mark and a fade into onboarding / dashboard. |
| **Onboarding** | Explains the three promises: lend safely, only approved apps, owner holds the keys. |
| **PIN setup** | Create and confirm the 4-digit owner PIN. Change-PIN verifies the current one first. |
| **Dashboard** | Hero CTA, allowed apps preview, lock method, auto-wipe state, recent sessions. |
| **Allowed apps** | Searchable list of every launchable app, with categories and sensitive-app warnings. |
| **Confirm session** | Summary, duration chips, auto-wipe and screen-pinning switches. |
| **Guest home** | Restricted grid, timer, "Return to Owner". No app drawer, no settings. |
| **Owner lock** | Full-screen PIN pad + biometric key, attempt counter, optional data wipe. |
| **Settings** | Security, session, appearance and privacy controls. |
| **History / About** | Privacy-safe session summaries and an honest limitations page. |

## 🔐 How the "guest mode" workaround works

Android only exposes real user profiles to privileged apps. GuestVault therefore layers five
ordinary-app techniques so the result behaves like a guest profile:

1. **Restricted launcher** — `GuestHomeActivity` runs in its own task and composes a home screen
   that only contains the allow-listed packages. There is no app drawer, no search, and no route
   back to the owner dashboard.
2. **Allow-list enforced at launch time** — the guest UI asks `SessionController` (the only writer
   of the session) whether a package belongs to the session before it calls `startActivity`.
   System launchers are never offered in the picker, because allowing one would let a guest escape.
3. **Screen pinning** — with the setting enabled, the guest activity calls `startLockTask()`.
   Android shows a one-time confirmation and then blocks Home/Recents. If the owner declines,
   GuestVault's soft lock remains in place.
4. **Secure exit** — returning to owner mode always goes through `GuestLockScreen`:
   PIN (PBKDF2 verified off the main thread) or `BiometricPrompt`, then the session is ended
   and the guest task is finished.
5. **Session integrity** — the session (allowed packages, start time, timer, wipe preference) is
   written to DataStore *before* the guest UI opens and only cleared after a successful unlock
   or timer expiry. A foreground service keeps the timer honest.

## ⚠️ What an ordinary app cannot do (and GuestVault doesn't pretend to)

| Capability | Normal app | GuestVault |
|---|---|---|
| Create a real Android guest user | ❌ system permission | Replaced by a restricted launcher |
| Fully block Home / Recents | ⚠️ only with screen pinning | Offered per session; needs the system confirmation |
| Read or wipe another app's private data | ❌ | Only when GuestVault is provisioned as **Device Owner** |
| Block the notification shade / quick settings | ❌ (needs kiosk/Device Owner) | Documented limitation; pinning reduces the risk |
| Watch what the guest types or opens | ❌ without an Accessibility Service | **Not implemented on purpose** |

If you need hardware-level isolation, use Android's built-in guest user or a fully managed
(kiosk) device — GuestVault is the best experience a Play-Store-style app can offer, and it says so
in the UI (Settings → About → *What needs system access*).

## 🏗️ Architecture

```
app/src/main/java/com/guestvault
├── MainActivity.kt                # owner entry point + theme
├── GuestVaultApp.kt               # @HiltAndroidApp, notification channel
├── core/                          # Formatters, AppLauncher, legal copy, notifications
│   └── security/PinHasher.kt      # PBKDF2-SHA256, constant-time compare
├── data/
│   ├── repository/                # DataStore settings, encrypted PIN, app catalog
│   └── session/                   # SessionController + GuestDataWiper
├── di/AppModule.kt                # Hilt bindings
├── domain/
│   ├── model/                     # GuestApp, GuestSession, OwnerSettings, SessionRecord…
│   ├── repository/                # interfaces
│   └── usecase/                   # ObserveSettings, StartGuestSession, VerifyPin…
├── guest/                         # restricted launcher + lock + session service
│   ├── GuestHomeActivity.kt
│   ├── GuestHomeScreen.kt
│   ├── GuestLockScreen.kt
│   ├── GuestSessionService.kt     # foreground service, timer enforcement
│   ├── GuestViewModel.kt / LockViewModel.kt
│   └── BiometricGate.kt
└── ui/
    ├── components/                # buttons, cards, PIN pad, app icons, dialogs
    ├── navigation/                # Navigation Compose graph
    ├── screens/                   # splash, onboarding, dashboard, selectapps, confirm,
    │                              # settings, history, pin, about
    └── theme/                     # Color, Type, Shape, Theme
```

**Patterns** — Clean Architecture (data → domain → presentation), MVVM with unidirectional state,
use cases for every owner action, Hilt for DI, DataStore for preferences, `StateFlow` +
`collectAsStateWithLifecycle` in the UI.

**Stack** — 100% Kotlin · Jetpack Compose · Material 3 · Hilt · Navigation Compose · DataStore ·
androidx.security (encrypted prefs) · BiometricPrompt · coroutines/Flow · minSdk 29 · targetSdk 35.

## 🧪 Testing & CI

| Workflow | What it runs |
|---|---|
| [**Android CI**](.github/workflows/android.yml) | Assembles the debug APK, runs JVM unit tests and Android Lint, uploads the APK and reports. |
| [**Screenshots & Demo Video**](.github/workflows/media.yml) | Boots a Pixel 6 emulator, records the walkthrough with `screenrecord`, runs the UiAutomator end-to-end test and commits fresh screenshots + video to `docs/media`. |

- **Unit tests** (`app/src/test`): PIN hashing (round-trip, salt uniqueness, tamper rejection),
  session timer math, history model, formatters.
- **Instrumented E2E** (`GuestVaultFlowTest`): onboarding → PIN creation → dashboard → settings →
  allow-list → session setup → guest home → wrong PIN → correct PIN → dashboard history → dark mode.
  The same test captures every screenshot in this README, and the flow is recorded to
  `docs/media/walkthrough.mp4`. It runs against a real emulator on every push.

Run them yourself:

```bash
./gradlew :app:testDebugUnitTest           # JVM tests
./gradlew :app:connectedDebugAndroidTest   # needs an emulator/device
```

> The E2E test uses the owner PIN `4726`. It is idempotent: it detects a previous onboarding and
> reuses the same PIN, so you can run it repeatedly on a warm emulator.

## 🚀 Build & run

### GitHub Actions (no local SDK needed)

Every push builds the app. Open the latest green **Android CI** run → *Artifacts* →
**guestvault-debug-apk**, then:

```bash
adb install -r app-debug.apk
```

The **Screenshots & Demo Video** workflow can be started from the *Actions* tab (or runs on `main`);
it commits refreshed images and the MP4 to `docs/media`.

### Locally

```bash
git clone https://github.com/Jacinth-19/GuestVault.git
cd GuestVault
./gradlew :app:assembleDebug
```

> If `gradle/wrapper/gradle-wrapper.jar` is missing in your checkout, run `gradle wrapper` once
> (or download the `gradle-wrapper` artifact from CI). Everything else — AGP 8.7, Kotlin 2.0,
> Compose BOM, Hilt — is pinned in `gradle/libs.versions.toml`.

**Requirements:** JDK 17, Android SDK 35, a device or emulator on Android 10+ (API 29).

## 🔒 Privacy

- **No internet permission.** GuestVault cannot upload anything, by construction.
- Settings and the PIN hash stay in app-private storage; `android:allowBackup="false"`.
- Session history stores *when* a session ran, how many apps were allowed and *how it ended* —
  never what the guest opened or typed.
- Auto-wipe clears GuestVault's own cache and scratch directory. Other apps' data can only be
  cleared when GuestVault is Device Owner.
- Full text: Settings → Privacy policy (also available in `core/LegalText.kt`).

## 🗺️ Roadmap

- [ ] Device Owner provisioning helper for true per-app data wipes
- [ ] Multiple guest profiles
- [ ] Quick Settings tile to start a session
- [ ] Optional emergency unlock code
- [ ] Per-session app time limits

## 📄 License

MIT — see [LICENSE](LICENSE).

<div align="center"><sub>Built with Kotlin, Jetpack Compose, Material 3, Hilt and DataStore.</sub></div>
