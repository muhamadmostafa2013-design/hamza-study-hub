# Automatic app updates — Hamza Study Hub

## Behavior

The production Android APK (`com.hamza.studyhub`) checks the repository's latest **public GitHub Release**. On Wi-Fi, WorkManager performs an initial check and then best-effort periodic work approximately every 12 hours. Android can defer the scheduled work to save battery.

When a newer version exists, the app automatically downloads the signed APK to **private app cache**. Before offering installation it compares the expected version and package ID, requires a strictly higher Android versionCode and checks the APK signing certificate against the installed application's certificate. If GitHub publishes an asset SHA-256 digest, it verifies that as well.

The system announces a prepared update through a notification when notifications are permitted. Otherwise the app displays an install offer when the Home screen opens. The user taps **Install**, then Android's package installer asks for final approval. Android does **not** allow this ordinary sideloaded app to install silently.

The home screen also keeps a **Check app updates** button, which works over a network even if Wi-Fi-only background checks are deferred.

## Important upgrade rules

1. Install the signed production APK from the GitHub Releases page. Debug APKs use a **different application ID** (`.debug`) and will not update in place or inherit local data.
2. Once per device, Android may require enabling "Allow from this source" for this app. Android always shows the final install/update confirmation.
3. Normal updates from a compatible signed production APK use the same package ID and certificate, preserving the app's private school feed and settings.
4. If the signing certificate differs, the app refuses the APK rather than attempting to uninstall/reinstall and losing local data.
5. Background downloads use unmetered connections (typically Wi-Fi), so mobile data is not consumed without a manual user action. If notifications are disabled, the app still finds staged updates on next launch.

## Deployment

The GitHub Actions signed-release workflow must run on the new release branch with an explicit `[release]` marker, validate tests, sign with the **existing** Android keystore secrets, verify the signature and publish versioned release assets. A debug build is **not** a production release. The newest released GitHub version must have a higher versionCode than the preceding release.

## Device release gate

Before treating this as a production-safe migration, install the new signed APK *over* a copy of the v0.5.2 production app with real but non-sensitive test data, confirm package signatures and versionCode, verify that previous data remains, confirm update notifications and manual permission behavior on Android 13+, and test interrupted downloads / no internet.
