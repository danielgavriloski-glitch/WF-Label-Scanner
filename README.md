# WF Label Scanner Android v0.2 AUTO

Native Android app for automatic continuous scanning of one package label at a time and exporting structured results to Excel.

## Workflow
1. Open the app; the camera and continuous scanner start immediately.
2. Put ONE package label in frame and hold it still briefly.
3. The app reads OCR text and barcode locally, saves the package automatically, and beeps.
4. Remove the label. The app rearms and waits for the next label, preventing duplicate saves.
5. Continue package by package; the active Nalog stays selected.
6. Use the manual save button only when OCR needs a correction.
7. Use Review / Control to inspect or delete saved rows.
8. Export the final `.xlsx` Excel file.

## Stored data
- Nalog / Auftrag / Order
- Package / Box / Karton / Kolli number
- Article / Artikel
- Size / Größe
- Quantity / Menge
- Customer / Kunde
- Barcode / EAN
- Full OCR text from the label
- Photo path and date/time

The full OCR text is always kept so information is not lost if v0.1 does not automatically understand a field.

## Excel output
- `Paketi`: one row for every scanned package.
- `Rezime`: totals grouped by Nalog + Size, including quantity and number of packages.

## Offline recognition
OCR and barcode recognition use bundled ML Kit models and run on-device.

## Build on Windows
1. Install current Android Studio.
2. Extract this ZIP.
3. Double-click `SETUP_WINDOWS.bat` once if the Gradle wrapper JAR is missing.
4. Open this project folder in Android Studio.
5. If asked, install Android SDK 36.
6. Wait for Gradle Sync to finish.
7. To make a shareable APK: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
8. When Android Studio says the APK was generated, click **Locate**. The file is usually `app-debug.apk`.

Minimum Android: API 23.
