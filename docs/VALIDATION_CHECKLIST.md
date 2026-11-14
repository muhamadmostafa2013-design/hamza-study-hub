# Hamza Study Hub — release verification checklist

This list separates **automated checks** from tests that require Hamza's school account and a physical phone. Do not put school credentials or private student work in logs, screenshots, issues or GitHub Actions.

## Automated (Android CI)

1. **Compile:** `gradle :app:assembleDebug` succeeds on JDK 17 / SDK 37.
2. **Unit and UI smoke tests:** `gradle :app:testDebugUnitTest` succeeds and reports test counts.
3. **Android lint:** `gradle :app:lintDebug` returns no blocking errors.
4. **Graph URL boundary:** a malicious `@odata.nextLink` cannot redirect the bearer token to another origin/API root; unsafe redirects are not followed.
5. **WebUntis QR validation:** userinfo, plaintext HTTP, localhost, IP-literal and non-standard ports are rejected.
6. **Feed persistence:** parallel notification writes and parent edits retain all items.
7. **Study progress:** checklist steps survive re-enrichment and school sync; progress metrics exclude locally finished tasks from overdue counts.
8. **Classification regression:** regular homework with a due date does not get mislabeled as a deadline change.

## On-device / school-dependent (not automatically verified)

9. **Real account sign-in:** test Microsoft Entra release redirect/signing hash, delegated permission/consent, initial sync and token refresh; test WebUntis QR login and a changed assignment (with parent's permission).
10. **Parent daily workflow:** verify Android 8+ / Android 13+ notification behavior, offline/online recovery, real school attachment links, Arabic RTL checklist usability, private photo capture and an update from a previously signed APK without deleting existing work.

## Security/privacy release gate

- Check that no secret, QR payload, session cookie or student image appears in the repository or CI logs.
- On an existing installation, verify plaintext WebUntis preferences are removed after successful migration; changing/revoking the QR requires re-connection.
- Validate external school links and permissions manually; never bypass school-admin consent.
- Verify physical-device restore behavior before relying on this as the only copy of Hamza's school records.
- CI success **is necessary, not sufficient** for a production-ready signed APK.

## Outstanding integration work

- Reconcile unmerged feature branches / open pull requests against `main` rather than merging divergent history blindly.
- Add a durable local database (Room) and explicit retention, backup/export, and deletion controls.
- Add instrumented end-to-end tests for MSAL, WebUntis response variations, WorkManager and release upgrades.
- Confirm actual Graph API coverage/resources permitted by the school; Teams messages and chats require additional opt-in permissions and are not part of basic assignment sync.
- Review accessibility, localization, parental controls, offline state and performance on lower-end Android devices.
