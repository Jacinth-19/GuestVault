# Competitive analysis: guest mode / kid mode / app-lock apps

Feature comparison used as a roadmap. Listings inspected October 2026.

## Who is in this space

| App | Package | Positioning |
|---|---|---|
| **Android Guest / multi-user** (built-in) | system | True profile isolation on Pixel, AOSP and some OEMs — absent or crippled on most Samsung/Xiaomi/One UI variants |
| **GuestVault** (this app) | `com.guestvault` | Launcher-level guest mode that works on any phone, with an honest limitations page |
| **Kids Mode** | `com.kidzoye.parentalcontrol` | Kid launcher + screen-time budget, aimed at parents |
| **Kid-mode On** | `hk.ithkservice.kidlock` | Themed kid launcher, one-tap parent exit |
| **Kids Place** | `com.kiddoware.kidsplace` | Mature parental-control suite with multi-child profiles |
| **AppLock family** (AppLock, Alpha Applock, …) | `com.appLock.*`, `com.alpha.applock` | Per-app lock, intruder selfie, disguised icon |

GuestVault and the AppLock family solve different problems (lending vs. locking) but the
security mechanics overlap almost completely: PIN pad, brute-force limits, hidden entrance,
failure evidence.

## Feature matrix

Legend: ✅ has it · ➖ partial / paid · ❌ no · 🅖 **GuestVault gap worth closing**

| Capability | Android Guest | **GuestVault** | Kids Mode | Kid-mode On | Kids Place | AppLock family |
|---|---|---|---|---|---|---|
| Works on any OEM ROM | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| True OS-level profile isolation | ✅ | ❌ (documented) | ❌ | ❌ | ❌ | ❌ |
| Custom restricted launcher | ✅ | ✅ | ✅ | ✅ (themed) | ✅ | ❌ |
| App allow-list | ✅ | ✅ | ✅ | ✅ | ✅ | per-app lock |
| Block Settings/Play Store by default | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| PIN exit | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Biometric exit | ✅ | ✅ | ❌ | ❌ | ➖ | ✅ |
| Screen pinning / kiosk | ✅ | ✅ (opt-in) | ➖ | ✅ | ➖ | ❌ |
| Session timer | ➖ (screen timeout) | ✅ | ✅ daily budget | ➖ | ✅ per app | ❌ |
| Auto-wipe guest data | ✅ (profile delete) | ➖ own cache only | ❌ | ❌ | ❌ | ❌ |
| Session history for the owner | ❌ | ✅ | ✅ usage reports | ➖ | ✅ | ✅ (unlock log) |
| **Website allow-list / safe browser** | ➖ | 🅖 | ✅ | ➖ | ✅ | ❌ |
| **Per-app time limits** | ❌ | 🅖 | ✅ | ❌ | ✅ | ❌ |
| **Daily screen-time budget** | ❌ | 🅖 | ✅ | ❌ | ✅ | ❌ |
| **Schedules / bedtime** | ❌ | 🅖 | ✅ | ❌ | ✅ | ❌ |
| **Multiple named guest profiles** | ✅ (user profiles) | 🅖 | ➖ | ❌ | ✅ multi-child | ❌ |
| **Intruder photo on failed unlock** | ❌ | 🅖 | ❌ | ❌ | ❌ | ✅ |
| **Disguised / hidden app entrance** | ❌ | 🅖 | ❌ | ❌ | ❌ | ✅ |
| **Quick Settings quick-start tile** | ❌ | ✅ (new) | ❌ | ❌ | ❌ | ➖ |
| Emergency unlock / trusted contact | ❌ | ❌ | ➖ | ❌ | ❌ | ➖ |
| No internet permission at all | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| No ads / no subscription | ✅ | ✅ | ➖ | ➖ | ➖ | ❌ |
| Open source | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Permissions explained in-app | ✔ | ✅ (new) | ❌ | ❌ | ➖ | ❌ |

## Where GuestVault already wins

1. **It runs where the OS feature is missing.** That is the entire reason the app exists, and
   none of the kid-mode apps frame themselves that way for *adult* phone lending.
2. **No network permission.** Every commercial competitor ships ads or analytics SDKs and
   therefore `INTERNET`. Lending a phone to a stranger while a tracker profile is being built
   is a bad trade; saying "this app cannot reach the network" is a genuine differentiator on
   the store listing.
3. **Honesty as a feature.** Competitors imply isolation they do not deliver. The limitations
   page, the "Soft lock vs Pinned" pill and the new "Home and Recents are not blocked" banner
   convert a weakness into trust.
4. **Owner-facing session history** with end reason — the built-in guest profile deletes itself
   and tells the owner nothing.

## Where it is behind — ranked roadmap

Ordered by (user value × implementation cost ÷ risk of confusing the core promise).

| # | Feature | Why it matters | Sketch |
|---|---|---|---|
| 1 | **Website allow-list / safe browser** | The single biggest hole: lending a phone "just for YouTube" almost always means the browser is allowed, and the browser is the whole internet | Ship an in-app WebView guest browser (`INTERNET` would then be needed → offer it as an optional, clearly-labelled component, or keep the listing network-free and document the trade-off) or curate a list of allowed domains via a `WebViewClient` that blocks everything else |
| 2 | **Per-app + daily time limits** | Parents and lenders both want "30 minutes of games, then it locks" | Extend `GuestSession` with `Map<packageName, minutes>`; the existing FGS already ticks every second, so enforcement is nearly free |
| 3 | **Schedules** | "Guest mode auto-starts at 19:00 on Fridays" | `WorkManager` periodic check → start session with the saved profile |
| 4 | **Named guest profiles** | Repeat visitors ("Mum", "the kids") | Persist `Profile(name, packages, duration, autoWipe)`; the picker becomes a profile chooser |
| 5 | **Intruder photo** | Deterrence and evidence; the most-praised AppLock feature | `CameraX` `ImageCapture` on the 3rd consecutive failure, stored app-privately, surfaced in session history. Needs `CAMERA` — hold it behind an explicit opt-in so the default build stays permission-light |
| 6 | **Disguised entrance** | Prevents "just uninstall it" and casual discovery | Launcher alias (activity-alias) toggled by `PackageManager.setComponentEnabledSetting`, plus a calculator-style gate |
| 7 | **Emergency unlock** | Owner locked out by a forgotten PIN | Time-delayed unlock after N hours, or a one-time code stored offline by the owner |

## Positioning statement for the listing

> Android's built-in Guest mode exists on some phones. GuestVault brings a practical version to
> the rest — including Samsung devices where multi-user is disabled. It is a launcher, not a
> second OS: it shows only the apps you approve, ends on your PIN, and never touches the
> network. What it cannot do (wipe another app's data, replace a real user profile) is written
> in the app, in plain language.

That sentence is also the answer to the reviewer question "why does this exist when Android has
guest mode?" — worth keeping in the store description verbatim.
