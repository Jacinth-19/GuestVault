# Play listing assets

| File | Play field | Spec |
|---|---|---|
| `icon-512.png` | App icon | 512×512, 32-bit PNG, alpha allowed, ≤ 1 MB |
| `feature-graphic.png` | Feature graphic | exactly 1024×500, no alpha |
| `screenshots/01…08.png` | Phone screenshots | 1200×2400 (2:1), no alpha, 2–8 files |

Everything here is generated, not hand-edited:

```bash
python3 -m pip install pillow
python3 scripts/make_store_assets.py      # writes this folder from docs/media + the logo geometry
python3 scripts/ci/validate_store_assets.py
```

The **Play Readiness** workflow runs the validator on every push, so a screenshot that is
regenerated at the wrong aspect ratio fails CI instead of the store submission.

## Why the screenshots are padded, not cropped

The emulator captures are 1080×2400 (2.22:1). Play rejects anything above 2:1. Cropping would
cut the PIN pad and the bottom CTA, so the generator scales to 2400 px tall and centres the
result on a 1200×2400 brand-coloured canvas. No UI is lost and the status bar stays intact.

## Signing

Release builds in CI are signed with the **debug** key so the GitHub release APKs install
without secrets. That is acceptable for sideloading and for an internal/closed Play track
(Play re-signs the bundle with the app signing key), but production requires a real upload key:

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then store it as repository secrets and read it in `app/build.gradle.kts`:

| Secret | Purpose |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | `base64 -w0 upload-keystore.jks` |
| `UPLOAD_KEYSTORE_PASSWORD` | keystore password |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | key password |

Never commit the keystore. Losing it means losing the ability to update the app under the same
listing (unless Play App Signing key upgrade is used).

## Upload checklist

1. Download the `guestvault-release-bundle` artifact from the **Play Readiness** run.
2. Play Console → your app → **Testing → Internal testing** → create release → upload the `.aab`.
3. Copy the listing text from [`../STORE_LISTING.md`](../STORE_LISTING.md) and upload the three
   graphic assets above.
4. Complete **App content**: privacy policy URL, ads, content rating, target audience,
   data safety, foreground service (`specialUse` + video link).
5. Roll out to internal testers, then closed testing (12 testers × 14 days for a new personal
   account), then production.
