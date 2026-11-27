# Selected school-summary preview: acceptance contract

The next increment accepts a caregiver-selected excerpt, not a connection to a mailbox or a school account. Start with one German excerpt reviewed in Arabic. Keep the chosen original task text and its provenance; do not keep unrelated headers, addresses, or links. A copied digest is a user-selected snapshot, not independently verified live data.

1. Paste a synthetic excerpt to see editable candidates, mark an ambiguous date as review required, confirm one record, then find it on Home.
2. Do not mark a task done or delete it merely because a later snapshot omits it. Dedupe only within the same source, school/course/item identity and local profile when those are known; matching titles alone are insufficient.
3. Keep provider status with its observed date and caregiver completion separate. A newer official due date cannot change caregiver completion.
4. Do not assume every calendar event is homework. Date-only stays date-only; absolute time keeps its instant; floating time is not silently converted into a fixed instant.
5. Do not open links on import. Offer the original only on an explicit action after safe URL validation, with source sign-in handled by the provider.

All scenarios in `school-source-preview-fixtures.json` are original synthetic design fixtures, not claims of actual provider email formatting, parser implementation, or pass results. Replay the first case through an Android UI before making a demo claim. Pilot aim: parent captures and finds the item within a minute, with no incorrect deadline saved from ambiguous test inputs.

Primary references: [Classroom guardian snapshots](https://support.google.com/edu/classroom/answer/6386354?hl=en), [Classroom API UTC deadline](https://developers.google.com/workspace/classroom/reference/rest/v1/courses.courseWork), [Teams guardian summaries](https://support.microsoft.com/en-us/education/assignments/send-weekly-assignment-summaries-to-parents-and-guardians), [Moodle calendar export](https://docs.moodle.org/503/en/Calendar_export), [iCalendar DATE-TIME](https://www.rfc-editor.org/rfc/rfc5545.html#section-3.3.5).
