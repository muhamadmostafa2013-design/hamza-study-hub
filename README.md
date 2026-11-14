# Hamza Study Hub

Arabic-first, **local-first Android homework and study companion** for Hamza, with read-only school imports and parent-reviewed study planning.

> **Status (14 November 2026):** An actively developed Android app, **not a verified production deployment**. See the CI checks, [manual validation checklist](docs/VALIDATION_CHECKLIST.md), and open pull requests before installing it for everyday use.

## What works in the current mainline architecture

- **School inbox:** opt-in Android notification capture from Microsoft Teams and WebUntis.
- **Microsoft Teams Graph:** read-only delegated school-account assignment catch-up; requires a valid app registration, school account and tenant permission.
- **WebUntis:** school QR/TOTP integration for reading homework; requires a valid user-generated school QR code.
- **Homework understanding:** deterministic classification, Arabic action steps and evidence-trust labels (school facts vs shared/parent information).
- **Local study checklist:** parent/child can tick steps and view progress; completed steps are preserved across app refresh and official assignment updates.
- **Parent overview:** attention flags, due dates, learning attempts and progress analytics.
- **Book library / student work:** attach books and save private photos of Hamza's work for later analysis.
- **Background catch-up:** WorkManager with network constraints. WorkManager timings are not exact.
- **Data safety:** Android Keystore encryption for WebUntis QR/TOTP material, atomic local homework feed updates and API redirect safeguards.

## Getting started

**Prerequisites:** Android SDK 37, JDK 17, Gradle 9.5.0; the app supports Android API 26 and above.

```bash
gradle :app:testDebugUnitTest
gradle :app:lintDebug
gradle :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. It has a different application ID from the release version and **will not update an existing signed production install**.

A signed release requires GitHub Actions release-signing secrets. See [release signing](docs/RELEASE_SIGNING.md). **Never commit keystores, passwords, access tokens or school QR codes.**

## Connect school services

1. **WebUntis:** scan the *official* QR generated for Hamza's school account. The QR contains a login seed; do not share it or upload screenshots. The app encrypts it with an Android Keystore key. Previously saved plaintext QR settings are migrated on first access; if the keystore cannot decrypt a value, re-connect rather than disabling encryption.
2. **Microsoft Teams:** register a public/native application in Microsoft Entra, configure the exact Android package/signature redirect, grant the necessary delegated assignment permission and get any required school IT consent. The registered tenant/application configuration must match the school account; tokens are managed by MSAL.
3. **Android notifications:** opt in to notification-listener access only if wanted. Notifications can be incomplete; Teams Graph/WebUntis direct sync provides the more reliable official information where permission permits.

The app does **not** bypass school IT permissions, automatically submit assignments or upload child data to a third-party backend.

## Data and privacy

The student work images, JSONL school feed and study records are stored privately on the Android device. The app currently lacks a full cloud sync, secure multi-device restore and data-export experience. Direct sync sends requests to the selected school service and Microsoft Graph only. A QR code hostname is validated for HTTPS and DNS shape; **parents must still verify that they scanned their school's genuine QR**.

## Current limitations

- Study suggestions are evidence-grounded rules, **not an online AI tutor**. Do not assume missing instructions/dates were discovered by AI.
- Automated unit/lint/build checks do not prove that a particular school's Microsoft tenant or WebUntis server will accept real credentials.
- The daily study-plan / school bag / automatic update features have separate, currently unmerged development work; these need careful reconciliation and end-to-end validation before release.
- Manual device testing is required for Android permissions, QR login, Graph paging, photo capture, airplane-mode behavior and signed APK upgrades.

## Docs

- [Architecture](docs/ARCHITECTURE_M2.md)
- [Teams synchronization](docs/TEAMS_DEEP_SYNC.md)
- [Study supervisor](docs/STUDY_SUPERVISOR_AGENT.md)
- [Release signing](docs/RELEASE_SIGNING.md)
- [Verification checklist](docs/VALIDATION_CHECKLIST.md)

## Development workflow

Implement changes in feature branches, use CI (`Android CI`: unit/Robolectric tests, Android lint, debug APK build), review the diff and merge only after verification. Record known school-service and on-device validation gaps in the pull request, rather than describing the project as complete based only on a green CI badge.
