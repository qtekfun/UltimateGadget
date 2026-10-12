# Design: Map and Route Manager (friendly UI)

Status: design proposal. No code implemented yet.

Goal: let the user download offline maps (by region) and send GPX routes to the Huawei watch
from our own UI, without using the "install firmware" menu. The base is already validated on real
hardware: `OpenStreetMap → offvmp → <mapId>.bin → watch` and `GPX → watch` work with the support the
core already ships; this is purely UI work + a data repo, respecting the spec (new UI in `:ui`
Compose, core touched as little as possible).

---

## 1. Key constraint: generating the `.bin` files

The map `.bin` is generated with **offvmp** (Python, MIT): `OSM .pbf/GeoJSON → build_country.py →
encode.py → <mapId>.bin`. An Android app **cannot run that pipeline** (it is Python + requires
osmium/OSM data/CPU/storage, and porting the encoder to Kotlin/NDK would mean rewriting and maintaining a
non-trivial codec, with a risk of format divergence and of generating corrupt files that — per
Me7c7's research — can make the watch misbehave).

### Options evaluated

- **(a) A separate data repo with `.bin` pre-generated per region + a downloadable index.** The app only
  **downloads** an already-made file and sends it to the watch. It is the model the spec already cites
  (UltimatePhone/UltimateMaps). The app never runs offvmp.
- **(b) A tool/CI that regenerates the data repo periodically** (GitHub Actions running
  offvmp over Geofabrik extracts, publishing the `.bin` + `index.json` as a release/pages).
- **(c) Port the encoder to the device.** Rejected: high complexity, codec maintenance,
  phone resource use, and the corruption risk is worse on the client than in a controlled pipeline.

### Recommendation: **(a) + (b)**

An independent data repository (e.g. `UltimateGadget-maps`) contains the `.bin` per region and
an `index.json`. A CI workflow regenerates them (monthly or on demand) by running offvmp over
public OSM extracts. The app consumes the index, downloads the chosen region and installs it reusing
the core flow. Advantages: the app runs no heavy or third-party code; generation is
auditable and reproducible in CI; the format is controlled in one place; the watch only receives files
already validated. The contour (`_contour`) and the `global` map go into the same index as variants.

> Routes: **they do not depend on the data repo.** A GPX is imported from the phone itself and sent directly.
> That is why Phase 1 tackles routes (immediate value, zero infrastructure).

---

## 2. Core hook points (already exist, reused)

The new UI **does not reimplement the protocol**: it builds a `Uri` to a local file and calls the same
entry point the installer uses today.

- Single entry point: `GBDevice`/service → `GBDeviceService.onInstallApp(Uri, Bundle)`
  (`app/.../impl/GBDeviceService.java:319`) → `HuaweiBRSupport.onInstallApp`
  (`app/.../service/devices/huawei/HuaweiBRSupport.java:166`) →
  `HuaweiSupportProvider.onInstallApp(Uri, Bundle)` (`app/.../service/devices/huawei/HuaweiSupportProvider.java:2596`).
  That method already **dispatches** based on the Uri content:
  - **GPX/route**: `HuaweiGpxRouteInstallHandler.isValid()` → `HuaweiGPSTrackConverter.getTrack(...)` →
    `HuaweiP2PFitnessData.sendTrack(track)` (`HuaweiSupportProvider.java:2599-2606`). The route name
    and navigation flags go in the `Bundle` via `HuaweiGpxRouteInstallHandler.EXTRA_TRACK_NAME`
    / `EXTRA_NAVIGATION_ENABLED` / `EXTRA_STRAIGHT_NAVIGATION_ENABLED`
    (`app/.../devices/GpxRouteInstallHandler.java:32-34`; validity = parseable GPX,
    `GpxRouteInstallHandler.java:74`; compatibility = `supportsRouteUpload()`,
    `HuaweiGpxRouteInstallHandler.isCompatible`).
  - **Offline map**: `HuaweiFwHelper.isOfflineMap` (`HuaweiFwHelper.java:82,100` → magic `offvmp`,
    name `<mapId>.bin`/`_contour`/`global`) → `HuaweiP2PMapkitService.startUpload(fileName, uriHelper)`
    (`HuaweiSupportProvider.java:2609-2612`).
- Mapkit service (`service/devices/huawei/p2p/HuaweiP2PMapkitService.java`):
  - `startUpload`→`startUpload2`→`uploadMapToDevice` (ping `0xca`, query space, negotiate and upload;
    `:253,:220,:253`), `MapInfo{mapId,mapType(0 regular/1 contour/2 global),version}` (`:134`),
    `queryUploadedMaps()` (`:121`) whose response is parsed in `handleMapList` (`:329`) and lists the
    maps the watch already has (id/type/version) — **this gives the "on-watch/updatable" status**,
    `queryFreeSpace()` (`:109`), and `deleteMapsList`/`deleteMaps` (`:162,:189`) to delete from the watch.
- Validation/installation: `HuaweiInstallHandler.validateInstallation` offline-map branch
  (`app/.../devices/huawei/HuaweiInstallHandler.java:116-153`) checks connected/initialized and
  `supportsOfflineContourMap()` for contours.
- Capabilities (`app/.../devices/huawei/HuaweiState.java`): `supportsOfflineMap()` bit 201 (`:868`),
  `supportsOfflineContourMap()` bit 202 (`:874`), `supportsRouteUpload()` bit 70 (`:880`),
  `supportsRouteV2()` bit 216 (`:886`). Verified: GT Runner 2 = all four; GT7 = offlineMap only.
- Region→mapId id table: `~/re/external/HDataResearch/offvmp/docs/filenames.md` (source of the
  `index.json`; country, region, `File name(w/o .bin)` = decimal mapId).

**Small gap to cover in the core (minimal):** today `queryUploadedMaps()` only logs the list
(`handleMapList`, `:343`). To render status in the UI, that list must be exposed (a callback or
`LiveData`/event with `List<MapInfo>`). And `deleteMaps()` is wired to an example id (`:189`):
add a public `deleteMap(mapId, mapType)`. Nothing else in the core changes.

---

## 3. UX (`:ui` module, Compose)

Two screens reachable from the device card (entry conditioned on `supportsOfflineMap()`
and `supportsRouteUpload()`).

### "Maps" screen

- List grouped by country (from `index.json`), search by name.
- Each region shows: name, size, available version, and **status** derived from crossing the index
  with `queryUploadedMaps()`:
  - *Not downloaded* → **Download** button.
  - *Downloaded, not on watch* → **Send to watch**.
  - *On watch* (version == index) → **Up to date** + **Delete from watch**.
  - *On watch, version < index* → **Update**.
- "include relief (contour)" toggle if `supportsOfflineContourMap()`.
- Download progress (HTTP) and upload progress (reuses `onUploadProgress`).
- Actions: Download (to the app cache), Send (builds the file `Uri` and calls `onInstallApp`),
  Delete local, Delete from watch (`deleteMap`).

### "Routes" screen

- List of imported GPX (stored in the app's storage).
- **Import** from a file (SAF `ACTION_OPEN_DOCUMENT`), with an option to **create** a simple
  route (future: draw on a map).
- Per route: name, number of points, distance; **Send to watch** buttons (with navigation toggles →
  `EXTRA_NAVIGATION_ENABLED`/`EXTRA_STRAIGHT_NAVIGATION_ENABLED`) and **Delete**.
- Send = `onInstallApp(gpxUri, bundleWithNameAndFlags)`.

Core touched as little as possible: only expose the watch's map list and a `deleteMap`. Everything else lives
in `:ui`.

---

## 4. Phased plan (small and reviewable)

- **Phase 1 — Local routes (immediate value, no data repo).**
  "Routes" screen: import a GPX from the phone, list them, send to the watch reusing
  `HuaweiGpxRouteInstallHandler`/`onInstallApp`, delete local. Entry visible only if
  `supportsRouteUpload()`.
  *Acceptance:* import a GPX and send it; it appears on the watch as a navigable route; without going through
  "install firmware".

- **Phase 2 — Send already-downloaded maps.**
  "Maps" screen that lists `<mapId>.bin` files present in a local folder and lets you send them
  (reuses `startUpload`). No data repo yet (the user places the `.bin`).
  *Acceptance:* send a local `.bin` and see it listed by `queryUploadedMaps()` on the watch.

- **Phase 3 — "on watch" status + deletion.**
  Expose `queryUploadedMaps()` to the UI and add `deleteMap(mapId,mapType)`. Show
  downloaded/on-watch/updatable status and allow deleting from the watch.
  *Acceptance:* the UI correctly reflects which maps the watch has and their version; deletion works.

- **Phase 4 — Data repo + in-app download.**
  Publish `index.json` + `.bin` (repo `UltimateGadget-maps`, CI with offvmp). The app downloads by
  region from the UI.
  *Acceptance:* pick "Community of Madrid" from the list, download and send with no manual files.

- **Phase 5 — Regeneration CI.**
  Workflow that rebuilds the data repo from Geofabrik periodically and updates `index.json`
  (version = YYMMDDHH, same as `encode.py`).
  *Acceptance:* a CI run produces new maps and the app sees an updatable version.

---

## 5. Data-repo index schema (`index.json`)

```json
{
  "schema": 1,
  "generated": "2026-10-09T07:00:00Z",
  "base_url": "https://<host>/ultimategadget-maps/v1/",
  "regions": [
    {
      "country": "Spain",
      "region": "Community of Madrid",
      "map_id": "1113715378388944103",
      "version": 26100907,
      "bbox": [-4.579, 39.885, -3.053, 41.165],
      "map":     { "path": "es/1113715378388944103.bin",         "bytes": 943334 },
      "contour": { "path": "es/1113715378388944103_contour.bin", "bytes": 512000 }
    }
  ]
}
```

- `map_id` = `File name(w/o .bin)` from `offvmp/docs/filenames.md` (string: it fits in a long but is handled
  as text to avoid losing precision in JSON).
- `version` = YYMMDDHH integer, the same one `encode.py` writes and the watch returns in
  `queryUploadedMaps()` → direct comparison for "updatable".
- `contour` optional (only if it exists and the watch supports it).
- App consumption: download `index.json` (cached), group by `country`, cross `map_id`+`version`
  with the watch's list for status, download `base_url`+`path` to the cache and send.

---

## 6. App files to create/touch

Create (`:ui` module, new):
- `ui/maps/MapManagerScreen.kt`, `MapManagerViewModel.kt`, `MapRepositoryIndex.kt` (model + fetch of
  `index.json`), `MapDownloadWorker` (download to cache).
- `ui/routes/RouteManagerScreen.kt`, `RouteManagerViewModel.kt`, `GpxImport.kt`.
- Navigation/entry from the device card (conditioned on capabilities).

Touch in the core (minimal, Phase 3):
- `service/devices/huawei/p2p/HuaweiP2PMapkitService.java`: expose the `handleMapList` list
  (callback/event) and add a public `deleteMap(long mapId, byte mapType)` (generalize `deleteMaps`).
- Possibly: a small `GBDeviceEvent`/event to publish the watch's map list to the UI.

No change to the `onInstallApp` flow: the new UI reuses it by passing the local file's `Uri`.
