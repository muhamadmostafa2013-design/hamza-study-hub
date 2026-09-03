# Hamza Study Hub — M2 Architecture

## Product goal
A personal education operating system for Hamza: collect school signals automatically, understand what is required, verify facts against evidence, turn homework into actionable steps, and measure progress over time.

## Agent pipeline
1. **School Intake Agent** — normalizes Teams, Untis/WebUntis, WhatsApp-parent messages, screenshots, shared content and future iSchool data.
2. **Homework Understanding Agent** — classifies the update and extracts German/English instructions into clear Arabic steps without inventing missing facts.
3. **Task Planner Agent** — turns homework instructions into a checklist and progress percentage.
4. **Evidence Verifier Agent** — labels evidence trust, marks uncertain dates/details, and keeps community WhatsApp information separate from official school facts.
5. **Progress Agent** — seeds per-homework progress and feeds subject/weekly analytics.

Planned next agents:
- Priority Agent
- Learning Insight / Repeated Error Agent
- Parent Brief Agent
- Schedule & Study Session Agent

## Source adapters
The core pipeline is source-independent. New services implement `SchoolSourceAdapter`.

Current / planned IDs:
- `WEBUNTIS`
- `UNTIS_NOTIFICATION`
- `TEAMS_NOTIFICATION`
- `WHATSAPP_PARENT`
- `SCREENSHOT`
- `SHARED_CONTENT`
- `ISCHOOL`

This means iSchool can later be added as another adapter without rewriting the agent logic or progress layer.

## Evidence rules
- WebUntis direct sync: official evidence.
- Teams/Untis notification: official signal, but may contain incomplete instructions.
- Parent WhatsApp group: community signal; never silently overwrites an official deadline.
- Screenshot/shared text: user-imported evidence; useful but may require verification.

## Storage direction
M2 remains local-first for stability. The next persistence milestone should migrate canonical homework/progress records from JSONL into a structured local database, then optionally sync selected family data through Supabase.

## Release strategy
Debug builds are for CI/testing only. Daily-use builds should be signed with one permanent release key so every future APK installs as an update without deleting the previous version.
