# MarK Booking — safe navigation redesign plan (based on v6.18)

## Verified source
Android module: `td-booking-mark/admin`; applicationId `mk.td.booking6`, versionCode 78, versionName 6.18.
UI: `src/main/assets/index.html`; existing sections: overview, listCard, editor, printCard, finance, financePrint, dashboard, staffView, reportsView, backupView, reportPrint, settingsView.
Extra modules: `td_manual_crew.js`, `booking_full_replica.js`, `backup_export_v618.js`.
Storage keys must remain unchanged: `tdBookingsV1`, `tdBookingFinanceV1`, `tdPackageSettings`.
Existing calendar render currently opens first booking directly, not a selected-day list.

## Implementation sequence
1. Add selected-day state; render day counts (not just dot); clicking day shows all bookings for that date, sorted by start time, showing bride/groom, start and package. No booking limit. Search name/date/package without mutating stored data.
2. Add distinct navigation states: Calendar > Event summary > Event finances / crew / edit; global hamburger menu for Dashboard, Finances, Crew, Reports, Backup, Settings. Reuse existing functions and DOM, avoid rewriting ledger.
3. Support event types wedding/christening/sunet/photo session/other; preserve existing bride/groom fields for legacy bookings; introduce optional eventType/title and type-specific validation and contract templates. Do not treat non-weddings as weddings in generated documents.
4. Keep financial calculations and manual crew ledger intact. Preserve offline-first sync and backup; verify conflict handling.
5. Run tests: 0/1/100 events on a day, search, locked contracts, all payments, staff, backup restore, LAN sync, Google Calendar, migration of existing bookings, restart persistence.
6. Build signed upgrade only after confirming installed applicationId/signing key; test on separate device or backed-up installation.

## Safety
Do not modify production branch. Make and verify independent backup before any migration. Never clear localStorage or change storage keys. Do not change signed contracts silently.
