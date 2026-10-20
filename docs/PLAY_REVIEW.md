# Play review: what a reviewer would reject, and what changed

Acting as a Google Play reviewer. Everything below was checked against the code in this
repository at the time of writing, not against the README.

| | Before this pass | After |
|---|---|---|
| `targetSdk` / `compileSdk` | 35 | **36** (Play blocks API < 36 submissions since 31 Aug 2026) |
| Device Admin receiver | declared, `BIND_DEVICE_ADMIN` | **removed** |
| FGS declaration | `specialUse`, subtype `guest_session_timer` | reworded, declaration text in [`STORE_LISTING.md`](STORE_LISTING.md) |
| Release artifact | APK only | **AAB** (`bundleRelease`) + APK for sideloading |
| Privacy policy | dialog inside the app only | public page + in-app link |
| In-app permission disclosure | none | **Permissions & privacy** screen |
| Lock-screen screenshots | possible | `FLAG_SECURE` on shipping builds |
| Wrong-PIN counter | ViewModel fields | persisted, escalating cool-down |
| Back on the owner lock | finished the activity | consumed |
| Store graphics | 17 shots at 2.22:1 (rejected) | 8 shots at 1080×2160 (exactly 2:1) |
| Reboot during a session | session silently stranded | closed and wiped at boot |

---

## 1. Blocking issues that were fixed

### 1.1 Target API level (would block the upload)

From **31 August 2026**, Play Console refuses new apps and updates that target below
Android 16 (API 36). The project was on 35, so the bundle could not have been submitted at
all. Raised `compileSdk`/`targetSdk` to 36 with AGP 8.9.1 and Gradle 8.11.1, which are the
minimum versions with stable API 36 support.

Side checks that come with a 36 bump:

- **16 KB page sizes** — no native libraries are packaged (`app/build` contains no `.so`), so
  this requirement is satisfied trivially.
- **Edge-to-edge is mandatory** at API 35+; both activities already call
  `enableEdgeToEdge()` and every screen uses `Scaffold` insets or explicit
  `statusBarsPadding()` / `navigationBarsPadding()`.
- **Predictive back** still routes through the Compose `BackHandler` in the guest space; the
  owner flow keeps the default system animation.

### 1.2 Device Administration (highest *policy* risk)

The manifest declared `core.GuestDeviceAdmin` with `BIND_DEVICE_ADMIN` and a
`<uses-policies><force-lock/>` set. Under Play's **Deceptive Device Settings** policy that
requires, in addition to a normal review:

1. the exact sentence *"This app uses the Device Administrator permission."* in the store
   description, **and**
2. a prominent in-app disclosure shown in the normal course of use — not buried in Settings —
   naming each policy and justifying it.

Historically apps are removed, not just rejected, for getting this wrong. The receiver also
bought almost nothing: `force-lock` is redundant with `LockTask`, and the only real capability
(`clearApplicationUserData` for other packages) requires full Device Owner provisioning, which
a Play-installed app cannot self-provision.

**Removed:** the receiver, the permission, `res/xml/device_admin.xml`, and the Device Owner
branch in `GuestDataWiper`. The honest limitation is now stated in the app and in the listing
instead of being papered over.

### 1.3 Foreground service type (would block the review)

With `targetSdk` ≥ 34, every foreground service must be declared in
**Play Console → App content → Foreground service permissions**, and `specialUse` is manually
reviewed with a description *and* a demo-video link — the form will not save without both.

Done in this pass:

- the subtype property is now human-readable
  (`Time-limited guest session timer that returns the phone to its owner`) rather than
  `guest_session_timer`;
- the declaration text and video script are written out in
  [`STORE_LISTING.md`](STORE_LISTING.md#foreground-service-declaration).

Contingency if review disagrees: replace the service with an `AlarmManager` deadline plus
in-app enforcement on next foreground. That removes the declaration entirely at the cost of a
timer that fires late if the phone is idle. Documented, not implemented — the current design
is the better user experience and the justification is genuine.

### 1.4 No App Bundle

`release.yml` only produced APKs. New apps must ship as an AAB with Play App Signing. Added
`:app:bundleRelease` to the new **Play Readiness** workflow, uploaded as
`guestvault-release-bundle`. The APK stays for GitHub Releases and sideloading.

> Signing note: both artifacts are currently signed with the **debug** key. That is fine for
> sideloading and for uploading to an internal/closed track (Play re-signs with the app signing
> key), but the upload key must be replaced before production. See
> [`play/README.md`](play/README.md#signing).

### 1.5 Privacy policy

Every Play app needs a public, non-PDF privacy policy URL, even with zero collection. The app
only had an in-app dialog. Added [`privacy.html`](privacy.html) — a self-contained page with no
trackers — intended for GitHub Pages at
`https://jacinth-19.github.io/GuestVault/privacy.html`, plus the same text reachable in-app
from Settings.

---

## 2. Store assets: the previous set would have been rejected

Play's preview-asset rules: **2–8** phone screenshots, 320–3840 px per side, and the long side
may be at most **2×** the short side. All 17 existing screenshots were **1080×2400 = 2.22:1**.
They look fine in the README and fail in the listing.

`scripts/make_store_assets.py` now generates the whole set deterministically from the app's own
brand assets, and `scripts/ci/validate_store_assets.py` fails CI if any rule is broken:

| Asset | Spec | Status |
|---|---|---|
| `docs/play/icon-512.png` | 512×512, 32-bit PNG, alpha allowed, ≤ 1 MB | 38 KB, RGBA |
| `docs/play/feature-graphic.png` | exactly 1024×500, no alpha | RGB, 81 KB |
| `docs/play/screenshots/*.png` | 1200×2400 (exactly 2:1), no alpha, UI only | 8 files |
| Promo video | 30 s – 2 min, hosted on YouTube | `docs/media/walkthrough.mp4` (94 s) — upload manually |

The screenshots are padded to 2:1 rather than cropped, so no UI is lost. They contain no
device frames, no marketing copy and no alpha, per Play's guidance.

---

## 3. App-content declarations (console work, not code)

These cannot be automated from the repository; the answers are prepared in
[`STORE_LISTING.md`](STORE_LISTING.md).

| Section | Answer |
|---|---|
| App access | All functionality available without credentials — no login exists |
| Ads | No ads |
| Content rating | IARC questionnaire; tool/utility, no user-generated content |
| Target audience | 18+ (a lending tool for adults; not designed for children) |
| News app | No |
| COVID-19 tracing | No |
| Data safety | **No data collected, no data shared** — and the app has no `INTERNET` permission to contradict it |
| Government app | No |
| Financial features | No |
| Health | No |
| Photo/video permissions | None declared |
| Foreground service | `specialUse` + description + demo video |
| Privacy policy URL | GitHub Pages link above |

One nuance reviewers do check: *Data safety* asks about data **collected** (leaving the
device). GuestVault stores an allow-list, a PIN hash and session metadata locally and transmits
nothing, so "no data collected" is correct — and the missing `INTERNET` permission is the
evidence.

---

## 4. Account-level gates that are outside the code

- **New personal developer account:** 12 testers for 14 continuous days on a closed track
  before production access is granted. Budget two weeks.
- **Play App Signing:** required with an AAB. Generate an upload keystore, store it as a
  repository secret, and stop signing releases with the debug key.
- **Store listing localization:** at minimum `en-US`; the copy is written to be reused for
  `en-GB`.
- **Data safety re-submission** after any future SDK that adds networking.

---

## 5. What a reviewer would still flag (and the honest answer)

| Observation | Why it is acceptable |
|---|---|
| "Guest Mode" is a launcher, not an Android user profile | Stated plainly in the listing, the onboarding and the Limitations page. The app never claims OS-level isolation. |
| Home/Recents are only blocked when screen pinning is accepted | Android requires the one-time system dialog; the guest UI now shows an explicit warning banner when pinning was declined, so the owner is never misled. |
| Other apps' data is not wiped | Disclosed in-app and in the listing. Wiping foreign app data needs Device Owner, which is exactly what was removed for policy reasons. |
| Notification is visible to the guest | Intended — it is the guest's only signal that the phone is in a restricted mode, and it is required for a foreground service. |
| Sensitive apps (Settings, Contacts, Files) can still be allow-listed | The owner is the trust boundary; the picker labels them **Sensitive** so the choice is deliberate. |

---

## 6. Verification

Measured on this branch, not asserted:

| Check | Result |
|---|---|
| `:app:assembleDebug` + unit tests + lint | pass (1m19s) |
| `:app:bundleRelease` (AAB, R8, signed) | pass (4m07s) |
| `:app:lintRelease` with zero errors | pass — the only error was `StartActivityAndCollapseDeprecated`, fixed with a justified suppression for the pre-Android-14 overload |
| Merged-manifest assertion (no Device Admin / internet / storage / contacts / location) | pass |
| Store graphics validation | pass — icon 512² RGBA 38 KB, feature graphic 1024×500 RGB 75 KB, 8 screenshots 1080×2160 |
| Guest walkthrough on **API 29** | pass (3m54s) |
| Guest walkthrough on **API 34** | pass (4m52s) |
| Guest walkthrough on **API 35** | pass (3m56s) |
| Guest walkthrough on **API 36** | pass (4m36s) |
| Full walkthrough + screenshots (Pixel 6) | pass (7m17s) |

The walkthrough at every API level covers: onboarding → PIN creation → dashboard → settings →
allow-list → session confirm → guest home → wrong PIN → correct PIN → history → dark mode →
permissions screen. It also asserts that the owner dashboard never appears inside guest mode.
