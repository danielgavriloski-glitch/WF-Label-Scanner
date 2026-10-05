# MBI 4 migration and signing

This branch builds a separate Android package, com.mbidesign.app.next, while keeping the original com.mbidesign.app installed. Both use the existing mbimetaldizain Firebase app configuration and employees, attendance, leaveRequests, absenceDecisions, settings and audit collections. There is no destructive database migration. Existing user names and passwords are reused.

Verify login and historical records on one device before distributing broadly. Records still pending offline synchronization in the original app must be synchronized there first. Firebase backend access rules and App Check/API restrictions were not changed and require production runtime verification.

The original APK used a GitHub runner-generated Android Debug key that was not saved by its workflow. Do not claim an in-place upgrade to the original package. The final MBI 4 APK is signed with a persistent private key. Its private backup is saved separately as MBI_v4_private_signing_backup.zip; never commit the key or its passwords. Future MBI 4 updates must retain com.mbidesign.app.next, increase versionCode and use that exact key.

New employee profiles can be marked isAccountant. The app UI limits accountant reports to 8h/day and 40h/week and has no raw attendance/event editor for that role. These are UI restrictions; production backend authorization still needs a separately deployed rules/summary design before claiming raw overtime data is inaccessible through the API.

Annual leave supports annualLeaveYear and leaveBalances/{employeeId}_{year}. Existing untagged v3 balances are treated as the 2026 migration baseline. When the administrator sets a new year's current balance, the previous baseline is archived first. Annual approvals use transactions, check remaining entitlement and do not count a pending request twice.

Sick leave periods and reasons live in sickLeaves. Each attached PDF/photo is in its documents subcollection. Photos are downsampled/compressed; PDFs have a 600 KB limit. The camera captures a document photograph; automatic edge detection/OCR is not implemented.

Validation: debug APK build and four accountant-cap unit tests passed. Final release build and signing verification are required for each APK. Device login, camera/files, Firestore rules, old/new record comparison and leave permissions must be tested with the user's accounts.
