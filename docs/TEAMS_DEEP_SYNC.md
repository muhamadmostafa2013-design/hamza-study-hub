# Hamza Study Hub — Teams Deep Sync

## Goal
Do not depend on Android notification delivery for Microsoft Teams homework.

Hamza Study Hub uses two complementary paths:

1. **Fast signal path — Android NotificationListener**
   - Low latency.
   - Works immediately with the Teams app.
   - Incomplete by nature: Android/Teams may suppress, group, truncate or never emit some notifications.

2. **Reliable catch-up path — Microsoft Graph Deep Sync**
   - Read-only school-account sync.
   - Pulls assignments even when no Android notification was received.
   - Imports historical assignments on first sync and updates changed assignments by stable Graph IDs.
   - Fetches detailed assignment instructions and resources from the class namespace.

The two paths merge into the same canonical feed and are deduplicated by official IDs when available.

## Graph APIs

### Assignment discovery
`GET /education/me/assignments`

This gives a student-centric list across classes. The response includes the IDs required to fetch full assignment details.

### Full assignment details + resources
`GET /education/classes/{classId}/assignments/{assignmentId}?$expand=resources`

The detailed request is used because the user-level assignment listing intentionally omits some rich fields such as instructions.

## Least-privilege direction

Start with delegated work/school permissions for the signed-in student account:

- `EduAssignments.ReadBasic` for basic assignment reads.
- Move to `EduAssignments.Read` only if richer assignment/resource data requires it in the school's tenant.

Optional later layers:

- Channel posts: `ChannelMessage.Read.All` (subject to school tenant consent/policy).
- Chats: `Chat.Read` if parent/student use case genuinely requires it.

Channel/chat monitoring is deliberately separate from homework assignment sync because it requires broader permissions and may be blocked by school administration.

## Authentication

Use MSAL delegated authentication. Never embed a client secret in the Android APK.

Required one-time setup before production connection:

1. Register Hamza Study Hub as a public/native client in Microsoft Entra.
2. Add the Android redirect URI for package `com.hamza.studyhub` and the permanent release-signing certificate hash.
3. Request only the delegated permissions above.
4. Sign in with Hamza's school Microsoft 365 account.
5. If the school's tenant requires admin consent, request approval from the school IT administrator instead of bypassing policy.

## Sync semantics

For each Graph assignment:

- Canonical source: `TEAMS_GRAPH`.
- Evidence trust: `OFFICIAL`.
- Stable external ID: `teams-assignment-{classId}-{assignmentId}`.
- Change detection: `lastModifiedDateTime`.
- First sync: historical import.
- Later syncs: fetch details only when new/changed.
- Missing instructions/resources: keep assignment visible and mark `Needs Attention`; never invent the missing task.

## Background behavior

After MSAL is wired:

- Immediate deep sync after successful sign-in.
- Sync when the app opens if stale.
- WorkManager periodic catch-up (Android minimum periodic interval is 15 minutes).
- NotificationListener remains active between syncs for fast signals.
- A future backend can add Microsoft Graph change-notification webhooks for lower latency without aggressive polling.

## Failure handling

- 401: refresh/reacquire delegated token.
- 403: show `School permission required`; do not silently downgrade or scrape Teams.
- Network unavailable: keep local data and retry later.
- Partial Graph response: preserve source evidence and mark incomplete details.
- Duplicate notification + Graph assignment: prefer the Graph assignment as canonical official evidence and link/merge the notification signal later.

## Privacy

Only the student's school content needed for planning/progress should be stored. Do not bulk-archive unrelated chats. Raw message monitoring is opt-in and requires a separate permission review.
