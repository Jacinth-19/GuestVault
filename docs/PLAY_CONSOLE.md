# Google Play Console: start to production

For an account that does not exist yet. Every field is named the way Play Console
labels it. Copy-paste strings live in [`STORE_LISTING.md`](STORE_LISTING.md); this
document is the *order* to do things in, because the order determines how long you
wait.

**Total wait before Production: ~2 weeks, and almost all of it is one timer.**

---

## Phase 0 — Create the developer account (do this first, it gates everything)

Play Console will not accept an upload until the account is verified, and verification
can take 1–3 days.

1. Go to **<https://play.google.com/console/signup>**
2. Choose **Personal** (or Organisation if you have a registered business — an
   organisation account needs a D-U-N-S number and takes longer).
3. Pay the **one-time \$25** registration fee.
4. Complete **identity verification**: legal name, address, phone, and a government ID
   or passport photo. **The name must match the ID exactly** — mismatches are the
   single most common cause of a rejected verification.
5. Wait for the confirmation email. Do not start Phase 2 before it arrives.

> The developer name you enter here is shown publicly on the listing and cannot be
> changed without a support ticket. Use the name you want on the store page.

### While you wait: make the upload key (5 minutes)

You do **not** need a laptop. Open a Codespace from the repository page
(**Code → Codespaces → Create codespace on main**) and run:

```bash
bash scripts/play/create-upload-key.sh
```

The script does four things, and pauses only twice:

1. It asks for a password — **press Enter** and it generates a 32-character one, so
   there is nothing to type on a phone.
2. It signs you in with a device code: `gh` prints eight characters, you open
   <https://github.com/login/device> on any browser and paste them. (A Codespace's
   built-in token can only *read* secrets, so this one-time sign-in is what lets the
   script write them.)
3. It creates `~/upload-keystore.jks`, verifies it opens, and writes
   `~/upload-keystore.jks.credentials.txt` (alias + password, mode 600).
4. It stores the four repository secrets — `UPLOAD_KEYSTORE_BASE64`,
   `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD` — and lists
   them back so you can see they landed.

Then download both files from the Codespace file explorer (right-click the file in the
Explorer → **Download**) and put them in a password manager. **If the key leaks, someone
can publish updates as you. If you lose it, Play App Signing can reset it — a leak is
worse than a loss.** Neither file can be committed: `.gitignore` covers
`*.jks`, `upload-keystore*` and `*.credentials.txt`.

---

## Phase 1 — Create the app

**Play Console → All apps → Create app**

| Field | Value |
|---|---|
| App name | `GuestVault – Guest Mode` (30 chars max) |
| Default language | English (United States) |
| App or game | **App** |
| Free or paid | **Free** |
| Declarations | Tick both (Play policies, US export laws) |

---

## Phase 2 — App content (the part reviewers actually read)

Left sidebar → **Policy and programs → App content**. Complete every row; the ones that
are not applicable must still be *answered*, not skipped.

| Section | Answer |
|---|---|
| **Privacy policy** | `https://jacinth-19.github.io/GuestVault/privacy.html` — live and verified |
| **App access** | *All functionality is available without special access.* No login exists. |
| **Ads** | *No, my app does not contain ads.* |
| **Content rating** | Questionnaire → category **Utility / Productivity**; answer **No** to every content question. Expected: **Everyone / PEGI 3**. Takes ~5 min. |
| **Target audience** | Age **18 and over**. Do **not** tick any child age band — that pulls in the Families policy, which this app is not built for. |
| **News app** | No |
| **COVID-19 contact tracing** | No |
| **Data safety** | *No data collected, no data shared.* Nothing to declare in the next screens. This is true: the app has no `INTERNET` permission. |
| **Data deletion** | Not applicable — no account exists. Point at the privacy policy's "Your control and data deletion". |
| **Government app** | No |
| **Financial features** | No |
| **Health apps** | No |
| **Photo and video permissions** | None declared |
| **Advertising ID** | Not used |

### Foreground service declaration — required, manual review

**App content → Foreground service permissions → Special use**

Paste the justification text from
[`STORE_LISTING.md` → Foreground service declaration](STORE_LISTING.md#foreground-service-declaration).
**The form will not save without a demo video link**, so upload
`docs/media/walkthrough.mp4` to YouTube (unlisted is fine) first and paste that URL.

> If this declaration is rejected, the fallback is documented in the same section:
> drop the service, keep the deadline in DataStore, enforce it on next foreground.

---

## Phase 3 — Store listing

**Grow → Store presence → Main store listing**

| Field | Source |
|---|---|
| App name | `GuestVault – Guest Mode` |
| Short description | [`STORE_LISTING.md`](STORE_LISTING.md) — 80 char limit, 76 used |
| Full description | [`STORE_LISTING.md`](STORE_LISTING.md) — plain text, keep the line breaks |
| App icon | `docs/play/icon-512.png` |
| Feature graphic | `docs/play/feature-graphic.png` |
| Phone screenshots | `docs/play/screenshots/` — all 8, **in filename order** |
| Video | YouTube link (optional, but it helps the FGS review) |
| Category | **Tools** |
| Tags | Utility, Privacy, Security |
| Contact email | your support address — it is public |
| Website | `https://github.com/Jacinth-19/GuestVault` |

Everything in `docs/play/` is generated and validated by CI
(`scripts/ci/validate_store_assets.py`), so the dimensions are already correct:
icon 512×512 RGBA, feature graphic 1024×500 with no alpha, screenshots 1080×2160
(exactly 2:1, no alpha).

---

## Phase 4 — Upload the bundle

**Testing → Internal testing → Create new release**

1. Upload `GuestVault-1.1.1.aab` from
   [the latest release](https://github.com/Jacinth-19/GuestVault/releases) — under
   *Assets*, not the APKs.
2. **Enrol in Play App Signing** when prompted (mandatory for bundles). Play generates
   the app signing key; your `upload-keystore.jks` only signs what you upload.
3. Release notes: copy the 1.1.0 block from [`STORE_LISTING.md`](STORE_LISTING.md),
   changing the version to 1.1.1.
4. Add yourself as an internal tester, roll out, install from the opt-in link.

> **If the AAB is missing from the release**, the workflow detected that the upload
> key is not configured and withheld it on purpose — Play rejects debug-signed
> bundles. Run `scripts/play/create-upload-key.sh`, push a new tag, and it will appear.

### Verify the upload key actually signed it

```bash
keytool -printcert -jarfile GuestVault-1.1.1.aab | head -3
```

The `Owner:` line must **not** say `CN=Android Debug`. The release workflow also runs
this check and fails the build if it does.

> **A tag can silently do nothing.** If the tagged commit carries a CI-skip marker
> (`[skip ci]` and friends), GitHub suppresses *every* workflow for it — the tag push
> produces no run and no error. That is how the first `v1.1.1` attempt vanished. Tag a
> commit with a normal message, or start the workflow by hand:
> `gh workflow run release.yml -f version=1.1.1`. CI now runs
> `scripts/ci/check_workflow_hygiene.py`, which fails if such a marker is ever committed.

---

## Phase 5 — Closed testing: the 14-day clock

New **personal** developer accounts must run a closed test with **at least 12 testers
for 14 continuous days** before Production is unlocked. Start this the day your account
verifies — it is the longest wait in the whole process.

1. **Testing → Closed testing → Create track**
2. Upload the same bundle (or promote the internal one).
3. **Testers → Create email list** — 12 real Google accounts. Ask friends; they only
   need to accept the invite and install once.
4. **Feedback** is required by Play: fill in the "how can testers give feedback" field.
5. Roll out to the closed track and send the opt-in link to all 12.
6. **They must install and stay opted in for 14 consecutive days.** Dropping below 12
   resets the requirement.

Use the fortnight to gather the feature requests already ranked in
[`COMPETITORS.md`](COMPETITORS.md).

---

## Phase 6 — Production

**Production → Create new release** → promote the closed-testing build → review the
warnings (there should be none) → **Start rollout to Production**.

First review typically takes **1–7 days**. Expect one of these questions, and answer
with what the app already says about itself:

| Reviewer asks | Answer |
|---|---|
| "Android already has Guest mode. Why this app?" | Samsung/Xiaomi and many OEM ROMs ship without multi-user. This is a launcher-level guest mode for those devices; it says so in the listing and in-app. |
| "Why a foreground service?" | The declared `specialUse` timer. Video shows it at 00:41–01:20. |
| "Why does it read installed apps?" | `<queries>` for the MAIN/LAUNCHER intent, so the owner can build the allow-list. No `QUERY_ALL_PACKAGES`. |
| "How is the PIN stored?" | Salted PBKDF2-SHA256, 120k iterations, inside `EncryptedSharedPreferences`. Never in plain text, never transmitted. |

---

## Checklist

Copy this into an issue and tick as you go.

- [ ] Play developer account created, \$25 paid, identity verified
- [ ] Upload key created (`scripts/play/create-upload-key.sh`), keystore backed up
- [ ] All four `UPLOAD_*` secrets present in repo settings
- [ ] App created in Play Console
- [ ] App content: privacy policy, app access, ads, content rating, target audience, data safety
- [ ] Foreground service `specialUse` declared **with** a YouTube demo link
- [ ] Store listing filled, all 8 screenshots uploaded
- [ ] `GuestVault-1.1.1.aab` uploaded to Internal testing, Play App Signing enrolled
- [ ] Release-signed verified with `keytool -printcert`
- [ ] Closed testing live with 12+ testers → **14-day clock started**
- [ ] Production rollout submitted

---

## Reference

| What | Where |
|---|---|
| Listing copy, data-safety answers, FGS text | [`STORE_LISTING.md`](STORE_LISTING.md) |
| What a reviewer would reject and why it was fixed | [`PLAY_REVIEW.md`](PLAY_REVIEW.md) |
| Guest-escape analysis | [`SECURITY_AUDIT.md`](SECURITY_AUDIT.md) |
| Listing assets and the signing commands | [`play/README.md`](play/README.md) |
| Privacy policy (live) | <https://jacinth-19.github.io/GuestVault/privacy.html> |
