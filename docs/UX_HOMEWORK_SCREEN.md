# Homework screen — one-glance UI pass (14 Nov 2026)

## Why
Previously, raw Teams/Untis notification text, an entire auto-generated checklist, book links,
source navigation, timestamp and multiple review actions all competed for attention above the fold.

## New default (Arabic, RTL)
1. Header **واجبات حمزة**, compact `مطلوب` and `عاجل` counters.
2. Three explicit tabs: **الواجبات** (default open homework only), **مراجعة** (unresolved recent alerts), **الكل** (full historical feed).
3. Homework cards are ordered **overdue → today → tomorrow → later → unknown date**.
4. Every card surfaces only: **subject**, **due label**, **two-line preview**, **next unfinished checklist step**, plus a primary **فتح الواجب** action when the source is available.
5. **التفاصيل** reveals all original instructions, the remaining checklist, book linking, capture-work and mark-reviewed actions.
6. School source settings, books and synchronization diagnostics are moved below the assignments instead of occupying the first viewport.

## Truth and privacy
- A missing date is displayed as **موعد غير محدد**, never guessed.
- `مكتمل` means locally checked or source-completed; it must **not** imply the assignment was submitted to school.
- The focus feed intentionally hides stale historic updates using the existing projector; **الكل** keeps the entire saved history accessible.
- This is an information hierarchy change; no school submissions or elevated permissions.

## Verification
- Pure presenter tests: priority ordering, missing due dates, exclusion of general notices / locally complete tasks, preview truncation, next step selection.
- Robolectric UI tests: details initially collapsed, expand shows full text and actions, default feed hides school notices while **الكل** can retrieve them, checkbox and review actions persist.
- CI: Android unit/Robolectric tests, lint and debug APK build.
- Physical Android device review is still required for long German titles, small screens, font scaling, RTL, scrolling after marking checklists and native TalkBack navigation.

## Manual acceptance
On a small Android screen, a parent should answer **what subject, when due, what next** without tapping details. Checking a task should update the local percentage without deleting the assignment or automatically declaring it submitted to Teams/WebUntis.
