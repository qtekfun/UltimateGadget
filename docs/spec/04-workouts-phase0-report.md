# Workouts — Phase 0 report (sources and pack format)

Measured on 2026-10-20 from the public sources; raw downloads are kept outside the repo.

## 1. Candidate sources

| Source | Text licence | Images | Verdict |
|---|---|---|---|
| free-exercise-db (876 exercises, 1746 JPG, 101 MB) | Unlicense | The maintainer states he does not know where the images come from; the upstream project it derives from says they were scraped from the internet and advises against commercial use, and a reverse-image search points to a commercial bodybuilding site whose terms forbid reuse | **Rejected.** The Unlicense covers only the dataset's own work, not the scraped images (and the text may derive from the same material). Not packaged |
| wger (932 exercises, 383 images, 78 videos) | Per exercise: CC-BY-SA 4.0 (779), CC-BY-SA 3.0 (132), CC0 (21) | Per image: CC-BY-SA 4.0 (295) or 3.0 (88), with author; 46 are marked AI-generated | **Chosen.** Every item carries its licence and author, so attribution can be reproduced faithfully |

wger's own software is AGPL-3.0, but its exercise data is a separate work under the per-item Creative Commons licences above.

## 2. What wger gives us

- English for all 932 exercises; **Spanish for 645** (the remaining 287 need our translation).
- Images for **279 exercises** (383 images, mostly PNG, 232 MB in the original sizes). The other 653 exercises have text only.
- Videos exist for some exercises but total about 3.5 GB: **not packaged**.
- Categories, primary/secondary muscles and equipment are structured, so they map to our `Exercise` fields.

## 3. Licensing conclusions and obligations

- The data stays **out of the app's source repository and out of the APK**. It is distributed as a separate pack (release asset), so the code remains AGPL-3.0 and the pack carries its own CC licences.
- The pack is a derivative (resized/recompressed images, stripped HTML), so under CC-BY-SA it must be shared under the same licence: **CC-BY-SA 4.0 for the pack as a whole**, with each item's author and original licence kept in `exercises.json`. CC-BY-SA 3.0 items are one-way compatible with 4.0 only through the licensor's terms, so they are kept labelled as 3.0 and shown with their own notice; we do not relicense them.
- The app must show the author and licence of an exercise and its images on the exercise detail screen, and list the pack's attribution in the About screen.
- AI-generated images are flagged in the pack; we show them with that label (or can leave them out).
- Our own Spanish translations of the 287 missing exercises are our work and are published under the pack's licence.
- Pending outside the code: a final look by the owner at this reasoning, since it is not legal advice.

## 4. Pack format (schema 1)

A zip with `manifest.json` (id, version, schema, languages, counts, licences, attribution text), `exercises.json` and `media/*.webp`.

Exercise: `id` (source uuid, stable), `name` and `description` as `{en, es}`, `category`, `muscles`, `musclesSecondary`, `equipment`, `license`, `authors`, and `media` entries (`file`, `main`, `ai`, `license`, `authors`). Images are resized to at most 480 px and stored as WebP (quality 72).

## 5. Prototype result

`build_pack.py` (now in the separate private data repository `ultimate-gadget-workouts-data`) builds the pack from a wger dump:

- 932 exercises, 382 of 383 images converted (one AVIF skipped because the local image library lacks AVIF support).
- Media 3.7 MB, **zip 4.0 MB** — small enough that a single pack covers everything; a size well under the 15–30 MB guessed earlier, because the images are re-encoded.

## 6. Decisions taken

1. The separate data repository exists (private for now; it goes public after a review of the pack).
2. Spanish: the 287 exercises wger has no Spanish text for have a draft translation in the data repository, flagged `draftTranslations: ["es"]` in the pack until the owner reviews it. Pack result: 932 exercises with English and Spanish names and descriptions.
3. Images: only 279 of 932 exercises have one. Generating our own is a **separate side project** (an image generator, to be designed on its own); until it exists, exercises without an image show a muscle-group icon and the app does not depend on images.
