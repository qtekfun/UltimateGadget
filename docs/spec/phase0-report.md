# Phase 0 report — Protocol spike (UltimateGadget)

Date: 2026-10-09. Target watch: Huawei Watch GT Runner 2 (a Huawei Watch GT 7 was also tested).
All analysis is on our own hardware, phone and account, for interoperability. Huawei's servers were not
accessed, and its app, code, keys or data were neither instrumented nor redistributed.

## 1. Executive summary

- **Golf — activation: SOLVED.** UltimateGadget talks to the watch's golf mini-app over the P2P channel
  (service `0x34`, module `hw.unitedevice.golf`, package `com.huawei.health.wear.golf`) **without Huawei Health**.
  The watch replies to our own golf messages and golf mode appears on screen with the watch connected only to
  UltimateGadget.
- **Golf — loading courses: PARTIAL.** Documented the message format (36-byte `GolfMsgHeader` header +
  payload) and the correct flow (the watch asks by GPS → the phone replies with the course list using the same
  `msgId`). Getting the watch to store a test course has not been achieved yet. The content of the course-map
  file (`.bin`) is still unknown (no sample; on vector watches it is sent as-is).
- **A-GNSS: BLOCKED by a proprietary format, with a partial public path.** Documented the exact tag the Runner
  requests (`higeo/v1/gnssinfo?type=0x0004/HW_AGNSS`, v3) and the format (standard RTCM 3, reproducible from
  public data). The transport already exists in Gadgetbridge. But the watch requests a **set** of files, some
  in Huawei's proprietary format (`HW_PGNSS_*`) that is not reproducible, and it requires them all. There is a
  documented risk of leaving the watch in a boot loop with incorrect data.
- **Offline maps (outside Phase 0's strict scope, but key): CLEAN PATH FOUND.** Gadgetbridge already
  uploads offline maps to the watch; `offvmp` (MIT) generates them from OpenStreetMap. Zero Huawei.
- **A/B/C decision: see section 6.**

## 2. Environment and method

- Fork at `com.qtekfun.ultimategadget`, compiles and passes CI. Golf probe and debug logs only in debug builds.
- Bluetooth HCI capture of our own phone (Pixel 8) with Huawei Health, and static analysis of our own APK with `jadx`
  (without running it). No root on any phone.

## 3. Golf — activation (main acceptance criterion)

Exact message/transport, reproduced from our own client:

- Channel: P2P service `0x34`. Golf is a **watch mini-app**, addressed by module+package+fingerprint:
  - Module (phone side): `hw.unitedevice.golf`
  - Package (watch app): `com.huawei.health.wear.golf`, fingerprint `SystemApp` ("sport"/vector watches).
  - The watch detects it as vector by `productType` 57/78 or feature 159 (Runner 2: vector).
- Sequence observed in our client (2026-10-08):
  1. P2P `ping` to the golf package → code 202 = exists (a control to a non-existent package → 200).
  2. Golf message type 16 (`GOLF_LOCAL_COURSE_LIST`) → the watch replies with its real list (45 courses on the
     Runner, 0 on the GT7).
- **Result: golf mode appears on the watch with UltimateGadget and without Huawei Health.** (Still to verify
  with a control that it is our message, and not the mere connection, that makes it appear.)

Capabilities (service `0x01` cmd `0x37`): golf = bit 105, golf-auto-download = bit 285.
- GT Runner 2: golf=YES. GT7: golf=NO (the firmware does not advertise it, even though its app answers the P2P →
  a software-trimmed model; the phone cannot add a capability the firmware does not expose).

## 4. Golf — course maps

- Messages (from `GolfHiWearBusinessType`): 8 GPS_INFO_SEND, 9/10 version, 11 COURSE_FILE, 12 COURSE_LIST_FILE,
  13 MAP_REQUEST, 14 PUSH_SHAKE, 15 DELETE, 16 LOCAL_COURSE_LIST, 20 MAP_KEY, 23/24 CLUB_INFO, 25 TOUCH_DOWNLOAD.
- **The flow is initiated by the watch**: when it looks for a "nearby course" it sends `GPS_INFO_SEND` (type 8) with its position;
  the phone replies `COURSE_LIST_FILE` (type 12) with the **same** `msgId`. An unsolicited push is ignored.
  This was implemented in the fork, but could not be tested (it requires a real GPS fix, impossible indoors).
- Origin of the maps in Huawei Health: Huawei cloud with an RSA-OAEP handshake and an access signature (`ak=`) over
  `higeo/v2/geoFile`. For vector watches (Runner 2) the file is sent to the watch **as-is** (not encrypted at the
  golf level); for "lite" watches it goes encrypted with AES and a key sent separately (`MAP_KEY`).
- **Pending unknown**: the content of the course map `.bin`. No sample. Hypothesis to verify: it could
  be the same vector container as the offline maps (see section 7).

## 5. A-GNSS (GPS updates)

- Real trigger observed (2026-10-09, when opening golf on a course): service `0x1f`, the watch requests the tag
  **`higeo/v1/gnssinfo?type=0x0004/HW_AGNSS`, version 3**, consultDeviceTime 30000.
- Transport to the watch: **already implemented in Gadgetbridge** (`HuaweiEphemerisManager`): an `ephemeris.zip` in
  external storage with `time`, `ephemeris_config.json` and the per-tag files; it is chunked and sent over BT.
- File formats (Me7c7's public MIT research, issue #3928 / HDataResearch repo):
  - `HW_AGNSS_RTCM_33`: **standard RTCM 3** frames (1019 GPS, 1020 GLONASS, 1042 BeiDou, 1046 Galileo) with CRC-24Q.
    **Reproducible from public broadcast ephemeris (IGS RINEX).**
  - `HW_PGNSS_*`: **Huawei proprietary format** (derived from `HiEE.dat` the app downloads from the server). Not
    reproducible without their conversion algorithm.
  - `MTK_EPO_*`: MediaTek EPO format (publicly downloadable from `epodownload.mediatek.com`).
- Key limitations (per the Gadgetbridge maintainer):
  - The watch requests the files **in sequence and requires all of them**; you cannot send only the RTCM.
  - TTL of 1–30 min: distributing pre-generated files is infeasible.
  - **The watch does not validate the data**: incorrect data can cause a boot loop and require a factory reset.
    Documented at least once.
- **Status: blocked.** The RTCM part is public and reproducible, but the set includes the proprietary
  `HW_PGNSS_*` format we do not know how to generate, and the watch requires them all. The recommendation is to
  follow the community work in #3928 and integrate it from upstream once it matures.

## 6. A/B/C decision

Reminder: A = the app talks to Huawei; B = pre-download on our own machine + data repo; C = OpenStreetMap.

- **Golf maps → C (OpenStreetMap), with the `.bin` format still to be researched.** B and A imply Huawei's
  handshake/signature and do not fit "zero Huawei on the phone". C is viable if the course-map format is
  confirmed (section 7).
- **General offline maps → C, solved.** OSM + `offvmp` + support already present in Gadgetbridge.
- **A-GNSS → no clean path in the short term.** The RTCM part is C (public data), but `HW_PGNSS_*`
  (proprietary) that the watch requires is missing. A (Huawei servers/account) is not recommended. Real
  mitigation: the GPS fix already improves a lot with upstream's current support (other users report 1–2 min
  versus 10+).

## 7. Clean path for maps (finding) and its possible application to golf

- Gadgetbridge uploads offline maps with `HuaweiP2PMapkitService`: files `<mapId>.bin` / `<mapId>_contour.bin`,
  recognized by `HuaweiFwHelper.parseAsOfflineMap()`. Capabilities: offlineMap=201, contour=202.
  GT Runner 2: both YES. GT7: offlineMap YES, contour NO.
- `offvmp` (Me7c7, MIT) converts OpenStreetMap extracts to that `.bin` format. Chain:
  `OSM .pbf → osmium → GeoJSON → build_country.py → encode.py → <id>.bin`.
- **Hypothesis to verify (Phase 2):** if the Runner 2's vector golf map uses the same container as the offline
  maps, courses could be generated from OSM (`golf=*`) without Huawei. It needs a sample to confirm.

## 8. Risks

- Huawei account ban: does not apply as long as its app/servers are not used (they are not).
- Watch boot loop from incorrect A-GNSS/golf data: a real, documented risk. Do not send generated files
  without validation and without explicitly accepting the risk.
- Huawei encryption/signatures (map handshake, A-GNSS HMS auth): no attempt is made to reproduce them.

## 9. Repository status

- Public fork that compiles; CI green; P2P golf probe and (debug) logs added.
- Documented: golf activation, golf message format, A-GNSS tag and format, offline-map path.
