# Hamza Study Hub — Book Library + Learning Loop (M3)

## Goal

Turn homework tracking into a longitudinal learning system for Hamza.

The system should answer four questions:

1. What exactly is the homework referring to?
2. Which private book/page/exercise does it belong to?
3. How did Hamza attempt it?
4. Which weakness is repeating, improving, or newly appearing?

This is **not** an automatic grading system. The default output is a learning observation for the parent, backed by the photographed attempt and the private reference material supplied by the family.

## End-to-end flow

```text
Teams / WebUntis / Untis
        ↓
Homework Understanding Agent
        ↓
Book Reference Agent
  e.g. "Deutsch S. 37 Nr. 3-5"
        ↓
Private Book Library
  subject + title + edition + page index
        ↓
Resolved Assignment
  book → page → exercise → skill
        ↓
Hamza solves the work
        ↓
Parent photographs solved page
        ↓
Student Work Analyzer
  OCR evidence + task context + private reference
        ↓
Error Pattern Agent
        ↓
Learning Trace
        ↓
Repeated Error / Progress Agent
        ↓
Parent Brief + targeted short intervention
```

## M3A — Private Book Library

### Book record

Each family-provided book is stored with:

- `bookId`
- title
- subject
- edition / school year when known
- local document URI
- page count when known
- aliases used by teachers

The first implementation keeps the content private and local. The app indexes only material explicitly supplied by the family.

### Assignment resolver

When an assignment contains a reference such as:

- `S. 37 Nr. 3`
- `Seite 42 Aufgabe 2-4`
- `Page 18 Ex. 5`
- `صفحة 12 تمرين 3`

`BookReferenceAgent` extracts the page and exercise without guessing the book. `BookAssignmentResolver` then combines:

- school subject
- teacher text
- registered book aliases
- page reference

If more than one book is plausible, the app asks for confirmation once instead of silently choosing.

## M3B — Student Work Capture

After a resolved homework item, the app should show:

> بعد ما حمزة يخلص، صوّر الصفحة المحلولة علشان نتابع طريقة الحل والأخطاء المتكررة.

The captured attempt stores:

- assignment fingerprint
- subject
- book/page/exercise
- attempt number
- capture time
- image URI
- OCR text when available

The original image is evidence for the family learning profile. It is not used to create a public dataset.

## M3C — Work analysis

Analysis should compare three pieces of evidence:

1. Teacher instruction / assignment
2. Family-provided private reference page
3. Hamza's photographed attempt

The analyzer should output evidence-linked observations such as:

- misunderstood the instruction
- concept gap
- procedure gap
- spelling
- grammar
- calculation
- omission
- careless error
- presentation issue

A single observation is **not** a weakness. Repetition across independent assignments is required before the system labels a repeated weakness.

## M3D — Learning profile

Example parent output:

```text
Deutsch
- Verb endings with "du": repeated in 3 assignments
- Current status: WATCH / REPEATED_WEAKNESS
- Last evidence: S. 42, Aufgabe 4
- Suggested intervention: 7 minutes, 3 short examples

Maths
- Carrying in subtraction: improving
- 1 unresolved observation in the latest 4 attempts
```

The system should retain the sequence:

```text
assignment → attempt → observed error → intervention → later evidence
```

That makes it possible to measure whether a weakness actually improves.

## Guardrails

- Do not invent a book, page, exercise, answer, or teacher deadline.
- If the edition is uncertain, ask for confirmation.
- Keep private books and student work private by default.
- Do not treat WhatsApp/community messages as official school evidence.
- Do not convert observations into a school grade by default.
- Preserve the original evidence behind each learning observation.
- AI API keys must never be embedded in the APK.

## Implementation status in this branch

### Implemented foundation

- `BookAsset` and private catalog contract
- German/English/Arabic page and exercise reference parser
- book assignment resolver with ambiguity handling
- `BookReferenceAgent` wired into the main agent pipeline
- `StudentAttempt` data model
- evidence-linked `LearningErrorSignal`
- repeated-error trend engine
- secure AI reasoning purposes for student-work analysis and targeted practice

### Next implementation slice

1. Book import UI (PDF/images via Android document picker)
2. Persistent local Book Library database
3. Per-page lightweight index
4. Solved-page photo capture/import UI
5. ML Kit text extraction from student work
6. Secure reasoning backend for task/attempt comparison
7. Parent learning dashboard
8. Family sync for Hadeer's phone
9. Historical Teams import
10. iSchool adapter

## Acceptance test for M3A

Given a homework message:

`Deutsch: Arbeitsbuch S. 37 Nr. 3-5`

The processed item should contain:

- `bookReferenceDetected = true`
- `bookPage = 37`
- `bookExercises = ["3-5"]`
- `learningCaptureSuggested = true`

No book title is considered resolved until it matches a family-registered Book Library entry with sufficient confidence.
