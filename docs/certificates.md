# Certificates: local-only workflow

Status: design starter only. This file does not add a Certificates screen or import support.

## Proposed workflow

1. Open Certificates on the device and explicitly choose **Add PDF**.
2. Use Android's system document picker to select one file. Do not scan folders or request broad storage access.
3. Copy the selected PDF into app-private, no-backup storage under a generated identifier. Keep the optional display label and the local index in the same private storage. Do not retain access to the source after copying.
4. List and preview the locally stored copies in the app. Removing a copy should not modify the original file.

Do not sync, upload, export, or include records in analytics, crash logs, backups, device-transfer payloads, or app releases. These local copies may be lost on uninstall or device replacement; the owner should keep their original PDFs privately.

## Repository rule

This repository is public. Do not commit actual PDFs, scans, filenames, names, school details, dates, screenshots, or achievement records. This applies to branches, issues, pull requests, CI logs and artifacts as well as the main branch. Use only synthetic fixtures if tests need an example.

## Before implementation is accepted

- Confirm a valid selected PDF can be reopened offline after the app restarts.
- Confirm cancel, invalid or oversized file, and interrupted import leave no partial copy.
- Verify records are excluded from backup and device transfer and do not appear in sync, logs, analytics, or release artifacts.
- Confirm deleting a local copy leaves its source untouched.
