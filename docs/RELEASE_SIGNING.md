# Stable Android updates — one-time setup

Hamza Study Hub now has a signed release workflow. The release key must remain the same forever; Android uses it to decide that a new APK is a trusted update of the installed app.

## Required GitHub Actions secrets
Add these repository secrets:

- `ANDROID_KEYSTORE_BASE64` — Base64 of the permanent `.jks` keystore file
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The release workflow restores the key only inside the temporary GitHub Actions runner, builds the APK, then the runner is destroyed.

## Versioning
The workflow automatically uses the GitHub Actions run number as `versionCode`, ensuring each future release is newer than the previous one.

## First stable installation
The currently installed prototype was a debug build using a different signing identity. You may need to uninstall it one final time before installing the first signed release.

After the first signed release is installed, future signed releases can be installed directly over it using **Update / Aktualisieren** without deleting the app or its private data.

## Never do this
- Never commit the `.jks` file to GitHub.
- Never commit passwords or Base64 key material to the repository.
- Keep an offline backup of the release key. Losing the key means future APKs cannot update the installed app.
