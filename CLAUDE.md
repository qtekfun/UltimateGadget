# UltimateGadget

AGPL-3.0 fork of Gadgetbridge (`com.qtekfun.ultimategadget`), the seventh app in the Ultimate family. Goal: Gadgetbridge with a new interface (`:ui` module, Compose) and golf mode, maps and A-GNSS updates working on Huawei watches without Huawei Health or HMS on the phone. Required watch: Huawei Watch GT Runner 2.

Source of truth: `docs/spec/00-spec.md` (decisions, phases, A/B/C conflict) and `docs/spec/01-prompt-phase0-spike.md`.

## Spec decisions (do not change without asking)
- Gadgetbridge core: modified as little as possible so upstream can still be integrated. The new UI lives in `:ui`, separately.
- All the brands Gadgetbridge ships are kept.
- Priority: protocol and golf first; the new UI (Phase 4) does not start until golf and maps are proven.
- Privacy: zero Huawei Health and zero HMS on the phone. No analytics or third-party services not listed in the spec.
- Maps: the A/B/C conflict stays open until Phase 0 is closed. OSM preferred (`golf=*`) in a separate data repo.
- A-GNSS: scheduled download (Wi-Fi only, configurable) and a manual button; a "valid until…" indicator.
- Sync: Nextcloud with encrypted credentials (Phase 5).
- License: AGPL-3.0, source published. Binaries, maps and Huawei keys are not redistributed.

Phases: 0 spike, 1 minimal golf + A-GNSS, 2 maps, 3 rounds, 4 UI, 5 Nextcloud, 6 releases. Each phase ends with verified acceptance criteria.

## Working rules
- Spec-driven. Small, reviewable changes, one commit per task, clear messages.
- If a spec decision cannot be met, stop and ask with options.
- Never write credentials, tokens or Huawei keys into the repo, logs or reports. Captures and analysis go outside the repo (`~/re/`) and in `.gitignore`.
- Nothing in `/tmp` (nor the scratchpad): downloads, clones and captures go to `~/re/<topic>/`.
- No factory reset, do not unpair the watch or touch the phone without asking.
- Analysis only on your own watch, phone and account, for interoperability. If something requires breaking Huawei's encryption or authentication beyond observing your own session, stop and ask. The test phones are not rooted.

## Build and tests
- JDK 21, Gradle wrapper 9.x, Android SDK with platform 37.
- Build: `./gradlew assembleMainlineDebug`
- Tests: `./gradlew testMainlineDebugUnitTest`
- The `TemperatureUtilsTest` tests expect an English locale; with `es_ES`, 2 fail (the `36,5` format). Locally: `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"`. CI already sets it.
- Baseline verified on upstream `c8d0913`: compiles; 1933 tests, 2 failures only due to locale.
- CI: `.github/workflows/ci.yml` (build + tests). `.woodpecker/` is upstream's and is not used.

## Folders and changes
- Do not touch unless necessary: `app/src/main/java/.../devices/` and `.../service/devices/` of other brands, `external/`, `GBDaoGenerator/`, translations (`values-*`).
- Our own changes so far: `applicationId`, the Pebble provider authority and the display name, only in the `mainline` flavor, to install alongside Gadgetbridge.
- New Huawei golf/A-GNSS code: new P2P services next to the existing support (`service/devices/huawei/p2p/`), isolated and without rewriting the core.
- `play` flavor (all-in-one, with `INTERNET`, decided by the user): downloads inside the app itself via `:mapsdl-core` and the `InAppNetwork` contract (stub in `app/src/noplay`, real in `app/src/play`). Do not touch `mainline`: no `INTERNET`. See spec §4.
- Upstream: remote `upstream` (Codeberg, `master` branch); integrate with periodic merges.
