# UltimateGadget — Initial specification

Fork of Gadgetbridge (com.qtekfun.ultimategadget), the seventh app in the Ultimate family. Goal: Gadgetbridge with a much more polished interface and a working golf mode on Huawei watches (downloading course maps, GPS distances to the green, syncing rounds), without depending on Huawei Health or HMS on the phone.

## 1. Decisions from the interview

| Topic | Decision |
|---|---|
| Base | Fork of Gadgetbridge with the UI in a separate module (`:ui`, Jetpack Compose). The core is touched as little as possible so upstream changes can still be integrated |
| Scope | Everything Gadgetbridge ships is kept (all brands) |
| Required watch | Huawei Watch GT Runner 2 |
| Priority | Functionality first (protocol and golf); the new UI comes once the hard parts are proven |
| Privacy | Zero Huawei Health and zero HMS on the phone; everything local except the essentials |
| Map source | Huawei servers (starting decision, see the open conflict) |
| Health | Activities and GPS routes (GPX/FIT), steps, sleep and heart rate with nicer charts, notifications, watchfaces and watch apps |
| GPS updates | Wants the watch's A-GNSS updates (ephemeris/almanac), downloaded and sent by UltimateGadget without Huawei Health |
| Sync | Nextcloud (data and settings), with encrypted credentials as in the other apps |
| Test devices | Pixel 8, OPPO Find X9 Ultra (global ROM) and a Huawei phone with Huawei Health and the watch connected over adb for analysis |

## 2. Golf: what it must do

1. Download course maps to the watch without going through Huawei Health.
2. GPS distances to the green working on the watch with the maps loaded.
3. Sync rounds (scorecard, strokes, tracks) to the app.
4. Our own map source (OpenStreetMap, `golf=*` tags) in a separate data repo, as in UltimatePhone and UltimateMaps.
5. **Core problem:** golf mode appears on the watch only while it is connected to the original app and disappears when it disconnects. We must find out what Huawei Health sends the watch to enable it (capabilities, app list, service, flag) and replicate it from UltimateGadget.

### GPS updates (A-GNSS)

Goal: have the watch receive up-to-date satellite assistance data to get a fast GPS fix, especially before golf, a run or a route, without Huawei Health.

- Find out where the data comes from (Huawei servers, or open/public sources compatible with the watch's format), its format, its expiry and how it is transferred to the watch.
- Automatic, scheduled download (Wi-Fi only, configurable) and a manual update button, with a "GPS data valid until…" indicator.
- If the source is Huawei's, the same conflict and the same A/B/C decision from section 3 apply. If an equivalent open source exists, it is preferred.
- It goes into Phase 1 alongside golf, because it shares the file-transfer channel.

## 3. Open conflict (resolved in Phase 0)

You have asked for both "maps from Huawei's servers" and "zero Huawei Health and zero HMS". That is only compatible if UltimateGadget talks directly to Huawei's servers without the original app, which probably requires signing in with a Huawei account and reproducing its authentication. Phase 0 must decide between:

- **A.** The app talks to Huawei directly (explicit opt-in account).
- **B.** The download is done once on your machine/server with your account and the data repo publishes the already-converted maps; the phone never touches Huawei.
- **C.** Maps from OpenStreetMap, and Huawei's only to validate the format.

Option B fits best with "zero Huawei on the phone" and with the data-repo model you already use.

## 4. License and legal framework

- Gadgetbridge is AGPL-3.0: UltimateGadget must stay AGPL-3.0 with its source published.
- Reverse engineering is done on your own watch and your own account, for interoperability. Prefer a secondary Huawei account: there is a risk of being blocked for breaching their terms of use.
- Binaries, maps and Huawei keys are not redistributed in the repository.

### Third-party services in the companion map app

The main app keeps "zero HMS and zero third-party services". The companion map-download app
(`mapdownloader`, the only one with internet access) additionally includes an **optional** utility to
obtain the Bluetooth key (auth key) of Amazfit/Zepp and Xiaomi watches directly from the Huami/Zepp
servers, replicating the huami-token method the official Gadgetbridge guide points to. Agreed
decision:

- It lives only in the map app, never in the main app.
- The Zepp/Amazfit account email and password are used only for the login (HTTPS against
  Huami/Zepp) and are **not stored** anywhere or written to logs.
- It is opt-in and only for your own account, for interoperability; the device-listing host is
  selectable by region.

### `play` variant (all-in-one)

Agreed decision, deliberately **outside the rule** "zero network in the main app": the `play` flavor
(`com.qtekfun.ultimategadget.play`, installs alongside `mainline`) carries the `INTERNET` permission and does in
the app itself what the companion does: downloading base maps from the catalog, Huawei golf courses
(sent directly to the watch), A-GNSS and the Amazfit/Zepp Bluetooth key. It is not a store build.

- `mainline` and `banglejs` do not change: `mainline` still has no `INTERNET` and uses the separate companion.
- The network logic lives in the `:mapsdl-core` module, shared with the companion; only `play` links
  it. The rest of the code accesses it through the `InAppNetwork` contract (an inert, network-free stub).
- The Zepp/Amazfit key follows the same rules: own account only, credentials only for the HTTPS
  login, not stored or logged (screen with `FLAG_SECURE`, state in memory only).

## 5. Phases

- **Phase 0 — Protocol spike (on Z's machine, with the watch over adb).** See `01-prompt-phase0-spike.md`. Deliverable: a report with the conclusion on how golf is enabled and how maps are transferred, and the A/B/C decision.
- **Phase 1 — Minimal golf and GPS.** Enable golf mode on the watch from UltimateGadget, load a test course and send an A-GNSS update.
- **Phase 2 — Maps.** Download and conversion of courses, separate data repo, region selection.
- **Phase 3 — Rounds.** Sync of scorecards, strokes and tracks; GPX/FIT export.
- **Phase 4 — New UI.** `:ui` module in Compose: home, health, activities, golf, watchfaces, settings. Dark/light theme and Material You.
- **Phase 5 — Nextcloud.** Backup of data and settings with encrypted credentials.
- **Phase 6 — Releases.** CI and publishing like UltimateGallery.

## 6. Phase 0 acceptance criteria

- Documented the exact message (service/command and values) that enables golf mode on the GT Runner 2.
- Reproduced that message from our own client (the modified Gadgetbridge code) and golf mode appears on the watch without Huawei Health connected.
- Documented the format of a course map and how it reaches the watch.
- Documented how A-GNSS updates reach the watch (origin, format, expiry, transfer) and whether a package can be sent from our own client.
- A/B/C decision justified with the evidence.

## 7. Backlog / future ideas

Items noted to tackle later (no phase assigned yet):

- **Firmware updates for Huawei watches.** Goal: be able to download the firmware with the map sidecar app (which does have internet) and transfer it to the watch from UltimateGadget, without Huawei Health or HMS. It will probably require reverse engineering the download origin/format and the transfer-and-install protocol on the watch. Analyze only on your own watch and account, for interoperability; stop and ask if it requires breaking Huawei's encryption/authentication beyond observing your own session. Do not redistribute firmware binaries.
- **Watchface editor.** Our own editor in the new UI to create/customize watchfaces (beyond installing/activating the existing ones). Scope still to be defined (elements, backgrounds, complications) and the GT Runner 2 watchface format.
