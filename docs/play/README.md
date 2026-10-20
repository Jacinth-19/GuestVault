# Play listing assets

| File | Play field | Spec |
|---|---|---|
| `icon-512.png` | App icon | 512×512, 32-bit PNG, alpha allowed, ≤ 1 MB |
| `feature-graphic.png` | Feature graphic | exactly 1024×500, no alpha |
| `screenshots/01…08.png` | Phone screenshots | 1080×2160 (2:1), no alpha, 2–8 files |

Everything here is generated, not hand-edited:

```bash
python3 -m pip install pillow
python3 scripts/make_store_assets.py      # writes this folder from docs/media + the logo geometry
python3 scripts/ci/validate_store_assets.py
```

The **Play Readiness** workflow runs the validator on every push, so a screenshot that is
regenerated at the wrong aspect ratio fails CI instead of the store submission.

## Why the screenshots are trimmed, not padded

The emulator captures are 1080×2400 (2.22:1) and Play rejects anything above 2:1. The generator
removes the same number of pixels from the top and the bottom — which is exactly the status bar
and the gesture bar — leaving 1080×2160 of pure app UI. No screen element is lost, and there is
no letterboxing that would read as a device frame.

## Signing

Release artifacts must be signed with a **Play upload key**. A debug-signed bundle is
rejected at upload time:

> *"You uploaded an APK or Android App Bundle that was signed in debug mode."*

The key is never committed. Create it once, from a Codespace or any machine with JDK 17
and `gh`:

```bash
bash scripts/play/create-upload-key.sh
```

That writes `~/upload-keystore.jks`, verifies it opens, and stores four repository
secrets:

| Secret | Contents |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | `base64 -w0 upload-keystore.jks` |
| `UPLOAD_KEYSTORE_PASSWORD` | keystore password |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | key password |

CI decodes the keystore with `scripts/play/prepare-signing.sh` and asserts the result
with `scripts/play/verify-signing.sh`, which fails if the certificate says
`CN=Android Debug`.

### Without the key

Sideloadable APKs still build and publish. The **App Bundle is withheld** — building one
without a key would only produce a file Play refuses. Configure the secrets and cut a
new tag to get an `.aab`.

### Verify locally

```bash
keytool -printcert -jarfile GuestVault-<version>.aab | head -3
```

`Owner:` must not be `CN=Android Debug`.

**Never commit the keystore.** This repository is public. With Play App Signing a lost
upload key can be reset from the console; a leaked one lets someone else publish as you
until you do.

## Upload checklist

1. Download the `guestvault-release-bundle` artifact from the **Play Readiness** run.
2. Play Console → your app → **Testing → Internal testing** → create release → upload the `.aab`.
3. Copy the listing text from [`../STORE_LISTING.md`](../STORE_LISTING.md) and upload the three
   graphic assets above.
4. Complete **App content**: privacy policy URL, ads, content rating, target audience,
   data safety, foreground service (`specialUse` + video link).
5. Roll out to internal testers, then closed testing (12 testers × 14 days for a new personal
   account), then production.
