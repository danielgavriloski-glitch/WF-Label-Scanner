# TD Booking MarK v6.14 — signed Android APK

[**Download signed APK (Android)**](./TD-Booking-MarK-v6.14-SIGNED-UPDATE.apk)

- Package: `mk.td.booking6` (same as previous TD Booking 6)
- Android versionCode: `74`, versionName: `6.14`
- Signing certificate SHA-256: `76651c8cf1522f1fab74d211162eeb8122695e07490ad57ecbdd578b09a58212` (original TD Booking 6 certificate)
- APK SHA-256: `477108f8743b31e52ce8d0fe6fcf9d678e2f23c4be218bcbbd4cd7bbbf54f28b`
- Includes the MarK M launcher icon, TD default colors, optional user-logo/theme controls, and Android native encrypted LAN sync bridge.
- PC counterpart is in `../td-booking-mark/pc/booking_sync.py`. Automatic pairing/sync requires the compatible PC MarK HTTPS server on port 8766; the standalone APK does not start the PC service.
- Sync currently sends wedding booking/project details to the PC, **not** two-way synchronization of every financial/employee field.
- Android build succeeded on GitHub Actions and APK v2 signature and ZIP integrity were verified locally; **not yet tested on an actual Android device**.

**Important:** Install the APK over the existing app, **do not uninstall**, to preserve local booking data. Export a TD Booking backup before applying updates. The keystore is intentionally not in this public repository.

[Build job](https://github.com/danielgavriloski-glitch/WF-Label-Scanner/actions/runs/39013467440)
