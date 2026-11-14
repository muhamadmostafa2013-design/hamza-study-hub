# Homework screen — visual hierarchy and usability review

## Goal

Hamza and his parent should understand, within a glance: **what is due, what to do next, and what needs confirmation**. This change improves the existing native Android view rather than replacing the underlying Teams/WebUntis imports or learning records.

## Before / after

| Before | After |
| --- | --- |
| Every school notification mixed in the default homework feed | Default filter focuses on open homework; Review and All remain one tap away |
| Cards displayed long original text immediately | Brief instruction preview (max 3 lines); complete text revealed on demand |
| Multiple actions, source metadata and book details competed equally | Open assignment is the primary action; book/reference/admin details are progressive |
| Full checklist overwhelmed each card | First two **incomplete** actions visible, all remaining steps in details |
| Latest notification appeared first even when another assignment was due sooner | Prioritization: recently overdue, today, tomorrow, next 3 days, later, then older/unknown |
| Due date shown as an undifferentiated number | Grounded Arabic urgency labels, with unknown dates explicitly unknown |
| Every source had similar visual credibility | Short evidence label: official school system, school notification, manual import, or community |

## Acceptance criteria (automated)

- [x] Display title, subject, due date and short instructions above admin controls.
- [x] Keep checkboxes accessible in the collapsed state, with at least 48dp target.
- [x] Expand details to show full instructions, remaining steps, book linking, source metadata and review controls.
- [x] Preserve expanded state when ticking a step.
- [x] Exclude completed homework and general announcements from the default list.
- [x] Keep a visible way to review all school messages.
- [x] Avoid invented due dates and distinguish outdated unverified deadlines.
- [x] Verify sorting, filtering, compact mobile layout and disclosure with Robolectric tests.

## Manual release verification still required

- Take screenshots on **360dp** and **large-screen** physical/emulated Android devices with Arabic RTL and enlarged font/display scaling; inspect clipping, readability, disabled states and focus.
- Confirm real school homework descriptions and nested Teams attachment links on the child's device.
- Test a 10+ item feed, very long German/English descriptions, low connectivity, no due date, completed checklist and old imported items.
- Verify TalkBack reading order, touch targets, contrast in light/dark system theme, keyboard navigation, orientation changes and screen reader announcements.
- Confirm checklists remain saved after force-close and Teams/WebUntis re-sync.

## Boundaries

Automated view tests validate hierarchy/visibility, sorting and compact-width layout; **they do not replace a human visual review of actual device screenshots**. The app's original signed production build has not yet been installed or tested here. Do not treat this document as a signed-release certificate.
