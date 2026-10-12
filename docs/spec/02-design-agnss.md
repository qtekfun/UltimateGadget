# A-GNSS design (Huawei, without Huawei Health)

Status: design agreed 2026-10-10. Implemented incrementally (see end).
Recon and evidence: `~/re/agnss/agnss-notes.md` (outside the repo). Real decoded package and
RTCM3 codec validated byte-perfect against the watch (GT Runner 2).

## What we know (recon facts)
- **Watch side ALREADY implemented in the core** (upstream, author Me7c7): service `0x1f`,
  `HuaweiEphemerisManager` + `SendEphemeris*` + `packets/Ephemeris.java`. The watch INITIATES; UG serves
  an `ephemeris.zip` from `getExternalFilesDir(null)` (chunked, CRC16, by tag/uuid/version).
- **The GT Runner 2 only requests `HW_AGNSS` (see 3)** — never `HW_PGNSS`. Verified with a passive
  handshake (control only, 0 ephemeris uploaded). → the proprietary PGNSS format is not needed.
- **`HW_AGNSS` = standard RTCM 3.3**: frames 1019 (GPS) / 1020 (GLONASS) / 1042 (BeiDou) /
  1046 (Galileo). No encryption or .so: HH only decompresses the server response. UG serves the RTCM
  **decompressed**. Codec validated (decode→encode identical across the 113 real frames).

## Architecture decision: the download goes in the companion (mapdownloader)
Decisive reason: **the Gadgetbridge core has `INTERNET` removed on purpose**
(`AndroidManifest.xml`: `tools:node="remove"` + Internet Helper). Giving UG internet breaks that pillar
and contradicts the rule "modify the core as little as possible". Therefore:
- **mapdownloader** (already has internet) = **A-GNSS producer**: downloads public ephemeris and
  encodes the RTCM. The encoder is pure Kotlin (bit-packing + RINEX text parsing; no native deps).
- **UltimateGadget** = **server**: assembles the `ephemeris.zip` and serves it to the watch through the
  existing `HuaweiEphemerisManager`. It touches neither internet nor the core.
- The spec ("downloaded and sent by UltimateGadget … scheduled Wi-Fi-only download, manual button,
  valid-until indicator") is met at the UltimateGadget **family** level (companion + app).

## Delivering the zip: bound service ("Internet Helper" model)
Android does not let the companion write into `Android/data/com.qtekfun.ultimategadget/files/`
(scoped storage). Chosen: **UG binds to a companion service** and receives the bytes.

- The companion exposes a `Service` (e.g. `AgnssHelperService`) with a **Messenger or AIDL** interface,
  protected by a **custom signature permission** (`com.qtekfun.ultimategadget.permission.AGNSS`,
  `protectionLevel="signature"`). Both apps sign with the same `UG_KEYSTORE` (and the same debug key),
  so the signature matches. The service also verifies the calling package.
- Contract (async, do not block the binder — the companion may have to download):
  `requestAgnss(maxAgeSeconds, constellationsMask) -> { rtcmBytes, validUntilMillis,
  generatedAtMillis, satCountsByConstellation, resultCode }`.
  - If the companion's cache is fresh (≤ maxAge) it returns instantly; otherwise it downloads RINEX
    (Wi-Fi only unless the user forces it) + encodes + caches + returns.
- **UG assembles the `ephemeris.zip`** with the `rtcmBytes`: `time`=now, `ephemeris_config.json`
  `{"higeo/v1/gnssinfo?type=0x0004/HW_AGNSS":{"ver":3,"uuid":"<uuid>","files":["HW_AGNSS_RTCM_33"]}}`,
  and `<uuid>/HW_AGNSS_RTCM_33` = `rtcmBytes`. This way UG controls the freshness of `time` and the
  protocol format (its concern). The name `HW_AGNSS_RTCM_33` reproduces HH's (`hexdigest(MAC)+name`).

### Non-blocking flow
The watch retries every ~20 s while it lacks A-GNSS (observed). So UG does not block:
1. Watch asks (operationInfo==1). If UG has a fresh zip (toe within validity) → it serves it
   (re-seals `time`=now). The manager's 30-min check is "recent download", not the real ephemeris
   validity (~2-4 h); that is why re-sealing is correct.
2. If none/stale → UG launches `requestAgnss` **async** and ignores this request. The watch's next
   retry (~20 s) already finds the zip ready.
3. Companion not installed / no network → UG ignores (current behavior) and the UI warns. No crash.

### Prefetch and scheduler (spec: scheduled, Wi-Fi, configurable)
- The companion runs a **periodic WorkManager job** (Wi-Fi only, configurable interval) that
  refreshes its cache, so the `requestAgnss` at connection time is instant and survives being
  briefly offline. UG can also trigger a prefetch when the watch **connects**.
- The schedule lives in the companion's UI (it has the network). UG shows status + "update now"
  (= `requestAgnss(maxAge=0)`) + "valid until".

## "Valid until" indicator
The companion computes `validUntilMillis` = max(toe) + the fit interval (~2-4 h) of the included
ephemeris, and returns it. UG shows it on the A-GNSS screen.

## Encoder (companion) — RINEX nav → RTCM3
- Source: public multi-GNSS broadcast ephemeris (RINEX 3 `*_MN.rnx`, IGS/BKG anonymous, ~1 MB).
  Confirm an open endpoint without login (BKG). Pick the latest ephemeris per satellite (toe ≤ now).
- Messages: 1019 (GPS), 1020 (GLONASS, **sign-magnitude** in several fields), 1042 (BeiDou),
  1046 (Galileo I/NAV). Field and scale tables validated in `~/re/scripts/rtcm_codec2.py`.
- Frame: `0xD3` + 6 reserved bits + 10 length bits + payload + **CRC24Q** (poly 0x1864CFB).
- Output: raw RTCM (no gzip; UG serves the decompressed form). Validate against HH's real package.

## UG — changes (small, isolated, outside the core except re-sealing)
- Enable the A-GNSS option for Huawei (today `supportsAgps` excludes HuaweiCoordinator): an Ultimate
  screen with status / "valid until" / "update now" / a warning if the companion is missing.
- Client for the `AgnssHelperService` (bind) + assembly of the `ephemeris.zip` + re-sealing of `time`.
- In `HuaweiEphemerisManager`: serve the zip (already exists) and expose "valid until". Remove/gate the
  `UG-AGNSS` diagnostic logging in `packets/Ephemeris.java` before merging.

## Security / privacy
- Signature permission for the service; verification of the calling package.
- 100% public data (IGS/BKG), zero Huawei/HMS, no Huawei binaries or keys.
- Nothing from Huawei is redistributed. The RTCM is generated from open data.

## Brick risk (sending to the watch)
Low: it is standard RTCM 3.3, identical in format to what HH sends, and the watch is asking for it.
Even so, the **first real send to the watch is gated**: done with the user present (Inc 2).

## Increments
- **Inc 1 (offline, safe):** RINEX→RTCM3 encoder + `ephemeris.zip` assembly in the companion.
  Offline validation: decode our output and compare with HH's real package (types/CRC/
  sqrtA ranges). Without touching the watch.
- **Inc 2 (gated, user present):** first real send to the watch. To de-risk, first push a
  companion-generated zip via adb (dev) and confirm the watch accepts it; then wire up the
  UG↔companion bind and repeat through the production path.
- **Inc 3:** scheduler (Wi-Fi, configurable) + prefetch on connect + "valid until" indicator + UI.
