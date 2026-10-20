# Store listing copy and console answers

Paste-ready text for Google Play Console. Character limits are Play's own; the counts shown are
for the strings below.

---

## Main store listing

### App name — 30 characters max

```
GuestVault – Guest Mode
```
*(22 characters. Avoid "Android Guest Mode" — it implies OS-level isolation.)*

### Short description — 80 characters max

```
Lend your phone safely. Guests see only the apps you allow, locked by your PIN.
```
*(76 characters.)*

### Full description — 4000 characters max

```
Borrow my phone. Keep your privacy.

GuestVault adds a practical Guest Mode to phones that don't have one — including
Samsung, Xiaomi and other devices where Android's multi-user guest account is
disabled or missing.

Hand your phone to a visitor, a child, a delivery rider or a stranger who needs to
make a call, and they see only what you chose. Everything else on your phone stays
out of reach.

HOW IT WORKS

1. Pick the apps a guest may open.
2. Optionally set a time limit — 5, 15, 30 or 60 minutes.
3. Tap "Start Guest Session". GuestVault opens a clean, restricted home screen with
   only those apps. No app drawer, no settings, no short cuts to your photos,
   messages or accounts.
4. When the guest is done, they tap "Return to Owner". Only your PIN — or your
   fingerprint, if you enable it — ends the session and brings back your phone.

WHAT YOU GET

• A restricted guest home screen that replaces your launcher for the session
• An allow-list you control, with sensitive apps clearly labelled
• Exit locked behind your 4-digit PIN, with optional fingerprint or face unlock
• Brute-force protection: five wrong attempts start a cool-down that doubles each
  round, and it survives restarting the phone or the app
• Optional session timer that ends the session by itself
• Optional screen pinning so Android blocks Home and Recents during the session
• Optional auto-wipe of GuestVault's guest cache when the session ends
• Session history for you: when a session ran, how long, how it ended
• Light and dark themes
• A Quick Settings tile to start a session in one tap
• A permissions screen that shows exactly what the app can and cannot access

PRIVACY BY DESIGN

• No account. No sign-up. No cloud sync.
• No INTERNET permission at all — GuestVault literally cannot send your data
  anywhere. There are no ads, no analytics and no third-party trackers.
• Your PIN is never stored. Only a slow, salted PBKDF2 hash inside Android's
  encrypted storage.
• Session history records how many apps were allowed — never what the guest did.
• No accessibility service, no device administrator, no usage-access permission.
• Everything is deleted when you uninstall the app.

DESIGNED TO BE HONEST

Android does not let a normal app create a real user profile or clear another app's
data. GuestVault doesn't pretend otherwise:

• It replaces your launcher during a session rather than creating a second Android
  user, so Home and Recents are only blocked while screen pinning is active. If
  pinning is declined, the guest screen says so plainly.
• Auto-wipe clears GuestVault's own cache and guest folder. A browser's history or
  a gallery's thumbnails inside an allowed app are that app's data, not
  GuestVault's.
• An allowed app can still do anything it normally does. Choose maps, a music app
  or a browser deliberately.

The app explains all of this in plain language, inside the app, before your first
session.

GOOD FOR

• Letting a visitor make a call or check directions
• Handing a phone to a child for one game or video
• Letting a delivery or repair person use your phone at the door
• Using your phone as a shared device at a reception desk or in a shop

GuestVault requires Android 10 or newer. It is open source under the MIT licence.
```

### Category & contact

| Field | Value |
|---|---|
| App category | **Tools** |
| Tags | Utility, Privacy, Security |
| Contact email | *(your support address — required, shown publicly)* |
| Website | `https://github.com/Jacinth-19/GuestVault` |
| Privacy policy | `https://jacinth-19.github.io/GuestVault/privacy.html` |

### Graphics

| Asset | File | Notes |
|---|---|---|
| App icon | `docs/play/icon-512.png` | 512×512, 32-bit PNG, 38 KB |
| Feature graphic | `docs/play/feature-graphic.png` | 1024×500, no alpha |
| Phone screenshots | `docs/play/screenshots/*.png` | 8 × 1080×2160 (exactly 2:1) |
| Promo video | `docs/media/walkthrough.mp4` → upload to YouTube | 94 s, unlisted is fine |

Suggested screenshot order (the files are already prefixed 01–08 in visual order):

1. Onboarding — "Lend your phone safely"
2. Owner dashboard
3. Select allowed apps
4. Confirm & start session
5. Guest home — "Welcome to Guest Mode"
6. Owner lock screen
7. Permissions & privacy
8. Dark-theme dashboard

---

## Release notes — 500 characters max

```
GuestVault 1.1.0
• Works with Android 16
• New Permissions & privacy screen: see every permission, including the ones the
  app never asks for
• New Quick Settings tile to start a session
• Stronger lock: wrong-PIN cool-down now survives restarts and doubles each round
• Screenshots and screen recording are blocked during a session
• A phone that restarts now closes and wipes a session left open
• Removed the Device Administrator permission entirely
```

---

## Foreground service declaration

**App content → Foreground service permissions → Special use**

> **What does the service do?**
> GuestVault runs a user-started "guest session": the owner lends the phone and a
> restricted home screen shows only the apps they approved. The foreground service keeps that
> session alive while the guest is inside another app, ticks the optional session timer, and
> closes the session (returning the phone to its owner) when the timer expires. It also keeps
> the ongoing "Guest Mode is on" notification visible so the guest always knows the phone is in
> a limited mode.
>
> **Why is no other foreground service type appropriate?**
> It is not media playback, location, camera, microphone, data sync or a connected-device
> service. It is a user-visible device-state timer that must survive the guest switching apps.
>
> **Demo video:** *(paste the YouTube link to the walkthrough)*
> At 00:41 the session starts and the ongoing notification appears; at 00:52 the timer is
> visible on the guest screen; at 01:20 the owner PIN ends the session and the notification
> disappears.

> Note for the repo: the video does not yet show the notification shade. If the reviewer asks
> for it, record a 20-second clip that pulls down the shade during a session — the only shot
> that is missing from the current walkthrough.

**Emergency fallback if this declaration is rejected:** drop the service, keep the deadline in
DataStore, and enforce it when the app next returns to the foreground (`AlarmManager` optional).
Timer firing accuracy becomes best-effort; nothing else in the app changes.

---

## App content answers

| Section | Answer |
|---|---|
| **App access** | All functionality is available without special access. There is no login, no account and no restricted area. |
| **Ads** | This app does not contain ads. |
| **Content rating** | Questionnaire: no violence, no sexuality, no profanity, no controlled substances, no gambling, no user-generated content, no user-to-user interaction, no location sharing, **no personal information sharing**. Expected rating: Everyone / PEGI 3. |
| **Target audience** | 18+ (a lending tool for adults). Do **not** include children as a target group — that triggers the Families policy, which this app is not designed for. |
| **News app** | No. |
| **COVID-19 contact tracing** | No. |
| **Data safety** | No data collected, no data shared. No data types selected. Data is processed on-device only. *(Consistent with the missing `INTERNET` permission.)* |
| **Data deletion request URL** | Not applicable — no account exists; uninstalling erases everything. Point to the privacy policy section "Your control and data deletion". |
| **Government app** | No. |
| **Financial features** | No. |
| **Health apps** | No. |
| **Photo and video permissions** | None. |
| **Advertising ID** | Not used. |
| **Play App Signing** | Enabled (mandatory with an AAB). |

---

## Promo video plan

The existing 94 s `walkthrough.mp4` already fits Play's ideal 30 s–2 min window. Before
uploading to YouTube:

1. Trim the first two seconds (emulator boot flash).
2. Add a title card and an end card with `github.com/Jacinth-19/GuestVault`, using the
   feature-graphic palette.
3. Upload unlisted; keep the URL stable — it goes into both the listing and the FGS form.

A shorter 30 s cut for the listing's top slot is optional: Play shows the video as a link, not
autoplay, so one 90 s walkthrough is enough.

---

## Launch checklist

- [ ] Upload `guestvault-release-bundle.aab` to **Internal testing** first
- [ ] Replace the debug signing key with an upload keystore (see `docs/play/README.md`)
- [ ] Fill in the store listing from this file and upload the graphics
- [ ] Declare the foreground service with the video link
- [ ] Complete Data safety, content rating, target audience, ads declarations
- [ ] Add the privacy policy URL
- [ ] Closed track: 12 testers × 14 continuous days (new personal accounts)
- [ ] Promote to production
