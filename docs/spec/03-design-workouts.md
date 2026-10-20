# UltimateGadget — Workouts (design)

A built-in workout planner and logger in the spirit of Hevy, living in the same app as the watches, so plans, sessions and watch data are in one place. Status: **design, nothing implemented**.

## 1. Decisions from the interview

| Topic | Decision |
|---|---|
| Scope | Any sport, not only strength. Strength is the first-class case |
| Main goal | Design plans and run them in the app. Linking a finished session to a plan is the secondary goal ("this workout was that plan") |
| Set kinds | Reps × weight; time; distance. Machines/equipment selectable per exercise. Programs of several weeks with assigned days |
| During a session | Screen stays on and the session survives the app being killed; last-time values next to the inputs; rest timer; personal records |
| Watch data | Heart rate and calories of the watch during the session, joined afterwards. Matching is automatic by time **with confirmation**. No control of the session from the watch, no automatic rep counting |
| Entry | Manual logging on the phone |
| Exercise catalog and media | Resource packs published as release assets on GitHub (downloadable and importable by hand). In the `play` flavor the pack is downloaded automatically (it already has `INTERNET`). `mainline` stays without network |
| Languages | Exercise names and descriptions in English **and** Spanish, complete |
| Import / export | Hevy CSV (import), our own JSON (full export and import). No Health Connect (out of scope) |
| Placement | New **Workouts** tab and a "today's workout" card on Home |

## 2. Principles

- The Gadgetbridge core is not touched beyond a hook or two: the feature is a new package (`activities/ultimate/workouts/`) plus its own storage, so upstream merges stay easy.
- Brand-neutral, like the common health measurements: workouts know nothing about watch brands. A small adapter per source supplies heart-rate/calorie samples for a time window.
- Everything is stored on disk (SQLite), nothing important only in memory. A session in progress is persisted after every edit.
- All user-visible text in string resources (EN base, ES), as in the rest of the app.

## 3. Data model

Own database file (not the greenDAO schema, which is bumped per release and shared with the core). It must be included in the app's existing backup/export so a restore does not lose workouts.

- `Exercise`: id, source (`pack` | `custom`), names (en, es), muscles (primary/secondary), equipment/machine, category, measure (`REPS_WEIGHT` | `REPS` | `TIME` | `DISTANCE` | `DISTANCE_TIME`), instructions (en, es), media refs. Custom exercises are created by the user and may carry a photo.
- `Routine`: id, name, notes, ordered `RoutineExercise` (exercise, superset group, rest seconds, ordered planned `SetTemplate`: kind warm-up/normal/drop/failure, target reps or range, weight, RIR/RPE, time, distance).
- `Program`: id, name, weeks, ordered days → routine or rest day; repeats or ends; "today's workout" is derived from the start date.
- `Session`: id, start/end (UTC + zone), optional `routineId` / `programId` + week/day, title, notes, status (`ACTIVE` | `DONE`), optional linked watch activity (`deviceId` + activity key), summary (volume, duration, avg/max HR, calories) cached after the join.
- `SessionExercise` / `SessionSet`: what was actually done (planned vs done values, set kind, done flag, timestamps for rest accounting).
- `PersonalRecord`: derived and recomputed, never hand-edited (best weight per rep count, best estimated 1RM, best set volume, longest time/distance).
- Units: stored in kg and metres; displayed in the unit the user chooses (kg/lb, km/mi).

## 4. Resource packs

- A pack is a zip: `manifest.json` (pack id, version, schema, counts, licenses), `exercises.json` (English and Spanish fields), and WebP media (two frames per exercise, shown as a cross-fade "animation"; a real animation format only if size allows).
- Published as assets of a GitHub release in a separate data repository, like the course data. The app never ships third-party media in the APK or the source repo.
- `mainline`: import from a local file (storage picker). `play`: offered at first run and updated through the `InAppNetwork` contract, Wi-Fi only by default.
- Without a pack the app is fully usable: a small built-in text-only starter list plus custom exercises.
- Source and licence: candidates are open exercise datasets (for example free-exercise-db, wger). **Each source's licence must be checked in Phase 0 before anything is packaged**, including whether its media can be redistributed under AGPL-compatible terms and what attribution the pack must carry. Spanish text has to be produced by us (or taken from a source that already has it) and reviewed.

## 5. Import and export

- **Our JSON**: versioned schema, contains custom exercises, routines, programs, sessions and sets; exercise references by stable id plus name fallback so a different pack still resolves. Import can merge or replace and reports what it skipped.
- **Hevy CSV import**: maps title, start/end, exercise name, set type, weight, reps, distance, duration, RPE. Exercises are matched to the catalog by normalised name, unmatched ones become custom exercises (the user sees the list before confirming). Weight unit is asked, since the file does not always say.
- Round trip is tested: export → import → identical data.

## 6. Watch link

- When a session ends, the app looks for watch activities from any connected/known device overlapping the session window and proposes the best match; the user confirms or picks another or none.
- Once confirmed, heart-rate samples and calories for the window are read through a source adapter (per brand, registered like the health-measurement sources) and cached in the session summary; the HR curve is shown over the session.
- If nothing matches (watch not worn / not synced yet), the session stays unlinked and can be linked later from its detail screen.

## 7. Screens (Compose, Ultimate theme)

Workouts tab: **Today** (program day or quick start), **Routines**, **Programs**, **History**, **Exercises**, **Stats**. Session screen: exercise list with sets, last-time values, rest timer (notification and vibration when it ends), PR badges, finish/discard. Exercise detail: media, muscles, instructions, history and PRs. Stats: volume per week/muscle, PR list, frequency.

## 8. Phases (each ends with tests, PR and release)

1. **Phase 0 — Sources**: choose and legally verify the catalog/media sources, define the pack format, build the pack tool in the data repo, produce a first pack. Acceptance: licences documented per source, pack imports in a prototype, size per pack reported.
2. **Phase 1 — Core**: database, custom exercises, routines, session logging with persistence (survives kill), rest timer, last-time values, history, our-JSON export/import, Workouts tab. Acceptance: unit tests for model/units/PR maths, JSON round trip, a session recovered after process death on a real phone.
3. **Phase 2 — Packs**: pack import (mainline), automatic download (play), exercise browser with media and cross-fade, EN/ES names. Acceptance: pack installed and updated on both flavors; app still works with no pack.
4. **Phase 3 — Programs and Home**: multi-week programs, today's workout card on Home, set kinds, supersets, time/distance sets, machines. Acceptance: a 4-week program advances correctly across weeks and rest days.
5. **Phase 4 — Watch link and stats**: activity matching with confirmation, HR/calorie join, PRs, stats screens. Acceptance: verified against a real session with a real watch (Huawei and Withings).
6. **Phase 5 — Hevy import**: CSV import with preview. Acceptance: a real Hevy export imports with the expected counts and unmatched exercises listed.

## 9. Open points

- Licence and redistribution terms of the chosen catalog/media sources (Phase 0).
- Who produces and reviews the Spanish text; how corrections are contributed.
- Whether the workout database joins the Nextcloud sync when that phase exists.
- Whether a user-supplied photo/video for custom exercises is stored in the database or as files next to it (files preferred, referenced by path).
