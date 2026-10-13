# UltimateGadget — Roadmap

Product ideas against Huawei Health, Garmin Connect, Fitbit, Zepp and Samsung Health, with the
project's privacy-first / 100%-local focus. `[x]` marks done, `[~]` in progress.

## Done / in progress
- [x] New UI (Compose, Material 3 dark + AMOLED, Bricolage/Figtree fonts, custom icon).
- [x] Device list with cards + detail with options; live connection status; battery.
- [x] Add device (pairing flow).
- [x] Watch offline maps from OpenStreetMap (generate/install/list/delete). The spec's plan C.
- [x] GPX routes to the watch (sending) + map/route manager in the new look.
- [x] Configurable health dashboard with real DB data (steps, heart rate/SpO2, sleep/stress, last workout) + per-card detail.
- [x] View a workout's route on the map.
- [x] Route planner (draw on the phone → send to the watch).
- [x] A-GNSS/AGPS for brands supported by Gadgetbridge (Amazfit/ZeppOS, Garmin). Huawei blocked (proprietary format).
- [x] CI-signed release (vX.Y.Z tags).
- [x] Settings with the dark brand look.
- [x] Pull-to-refresh on the dashboard (syncs).
- [x] MapLibre+PMTiles map viewer (reusable core) in the planner and workouts.


## Next (high value, reuses what's done)
- [x] Phone maps: PMTiles import + the :mapdownloader companion app that downloads them from the ultimate-maps-data catalog (the main app is offline).
- [x] New screens: notifications, watchfaces, reports/goals, alarms/reminders/world clocks, advanced sleep, music manager, per-workout GPX/FIT export, performance (PAI/load/VO2max/HRV), timers (local to the phone), home widgets + QS tiles.
- [x] Bugs: back-in-settings no longer closes the app; map insets; routes with a decimal comma; watchface activation (confirm 0x05).
- [x] Device settings: dark brand skin + categories (Huawei only).
- [ ] Snap-to-roads in the planner (OSRM) and waypoint editing.
- [x] "Device settings": dark brand skin + categories (Huawei).
- [x] Weekly/monthly reports with charts. Goals, streaks and medals. [ ] PDF export pending.
- [x] Derived scores: readiness, load (TRIMP/ACWR), VO2max, HRV, PAI.
- [x] Advanced sleep: stages, score, nocturnal SpO2, naps.

## Data and privacy (differentiator)
- [x] Notifications: apps that notify the watch + Do not disturb (schedule/days) in the Ultimate look.
- [x] Device options grouped into sections (consistent with commercial apps).
- [ ] Health Connect (share health with other apps, locally).
- [ ] Nextcloud (the spec's Phase 5) and optional destinations (Strava/openScale).
- [x] Export/import of the database backup (local). [ ] Per-workout GPX/FIT and Huawei Health import (pending).
- [ ] Multi-device dashboard (aggregate and compare).

## Device
- [x] Watchface showcase/installer. [ ] Basic designer (later).
- [x] Alarms, reminders, world clocks and timers (local). [ ] Watch app/widget management.
- [x] Music manager (list/upload/delete). [ ] Playlists.

## Golf (our own niche)
- [ ] Course library from OpenStreetMap (golf=*), scorecard, stroke tracking, distances.
- [ ] Unlock the Huawei course-file/ephemeris format (requires a sample from a rooted device).

## Ollee Watch One (own watch — extend support)
- [ ] Flesh out Ollee support (`devices/ollee/`, `service/devices/ollee/`): upstream already has the `AA 55`/CRC16 protocol, alarms, activity records, watchfaces and clock; extend features (notifications, heart-rate pulsometer, more alarms, settings). **Blocked for the "official app" RE path:** the Ollee companion (`app.ollee.ollee_watch.ollee_watch`, Play-only) refuses to run without Play Store / a Google account, so it can't be used on the rooted Pixel 8 (no Google account) to capture/decompile. Revisit with a Play-connected phone, or go clean-room: pair to UltimateGadget and probe the GATT/protocol directly. Watch BT must be woken manually (hold ALARM on the clock face, or tap until LAP/SPLIT blinks).

## Ambitious / later
- [x] Home widget (steps/battery/HR) and QS tiles (sync, find watch).
- [ ] Notification rules (filters by app/schedule).
- [ ] Optional dynamic Material You.
- [x] New UI as the launch screen (with first-launch/permissions gating).
