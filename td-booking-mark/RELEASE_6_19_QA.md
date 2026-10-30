# MarK Booking 6.19 — QA and release safety

This is an **isolated test branch**. Do **not** replace the current installed app until the Android build, device checks and original signing certificate are verified.

## Identity (must remain compatible)

- Android application ID: `mk.td.booking6` — unchanged.
- Current test branch: `feature/td-booking-calendar-navigation-redesign`.
- New versionCode: `79`, versionName: `6.19` (previous 78 / 6.18).
- Booking and financial storage keys remain `tdBookingsV1` and `tdBookingFinanceV1`.
- Existing records without `eventType` are treated as wedding records. No automatic data migration.

## QA commands

From repository root:

```bash
node td-booking-mark/admin/tests/event_contracts.test.cjs
cd td-booking-mark
gradle :admin:testDebugUnitTest :admin:assembleDebug --no-daemon
```

GitHub Actions workflow: `.github/workflows/mark-booking-qa.yml`.

A successful workflow run can upload `MarK-Booking-6.19-QA-DEBUG-NOT-FOR-UPGRADE`. This is a **debug test artifact only**. Each fresh debug signing certificate can differ from the user's current release APK. **Do not uninstall the installed app to install the debug build: existing local booking data could be lost.**

## Mandatory before a real upgrade APK

1. Check the installed APK signature SHA-256 against the **release signing key**. Use `apksigner verify --print-certs old.apk` to examine the certificate; the candidate upgrade must use the compatible signing identity.
2. The original keystore and its credentials must stay private. Never commit keys or passwords to GitHub, even in a public test branch.
3. Build and sign a release APK with the matching key, verify signature and package ID, and confirm its versionCode is higher than the installed version.
4. Export a **full backup** from the current installation and preserve it outside the phone before upgrading.
5. Perform a real upgrade without uninstalling and test wedding records, finance, staff payments, locked contracts, all 5 event types, backups, Google Calendar and phone ↔ PC full sync.
6. Do not merge this branch into production until the above has passed.

## Verified in code (not Android device-tested)

- Display of signed contracts for legacy weddings and 5 event types.
- Signatures: 3 for weddings, 2 for other event types.
- JavaScript syntax checks, month rollover, event labels and full sync snapshot preservation of `eventType`.
- Google Calendar title creation was updated in Kotlin; **Android build and runtime verification are still required**.
