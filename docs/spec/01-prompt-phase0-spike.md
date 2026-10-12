# Phase 0 prompt — Huawei golf protocol spike (UltimateGadget)

Paste this prompt into Claude Code, on your Fedora machine, with the Huawei phone connected over adb, Huawei Health installed and the Watch GT Runner 2 paired with it. Read `00-spec.md` first.

---

## Context

I am building UltimateGadget, a fork of Gadgetbridge (AGPL-3.0). I want the Huawei Watch GT Runner 2's golf mode to work without Huawei Health: download course maps, GPS distances to the green and sync rounds. Key observation: golf mode appears on the watch only while it is connected to the original app and disappears when it disconnects. We must find out what Huawei Health sends the watch to enable it and how the maps are transferred.

Everything is done on my own watch, my own phone and a secondary Huawei account, for interoperability purposes. None of this is published with Huawei binaries, keys or maps.

## Safety rules (mandatory)

1. Do not factory-reset the watch or the phone, or unpair the watch, without asking me first.
2. Do not ask for or write Huawei account passwords, tokens or keys into repository files. If they appear in a capture, obfuscate them in reports and add the captures to `.gitignore`.
3. Work in a new, empty directory for each downloaded thing (APK, captures, extractions) and analyze it without running it.
4. Do not try to bypass Huawei servers' authentication, encryption or licensing beyond observing what my own app does with my own account. If something requires breaking protections, stop and tell me.
5. Before any destructive action on the phone or the watch, confirm with me.

## Steps

### 1. Environment
- Check `adb devices`, the phone model, the Android version and the Huawei Health version (`adb shell dumpsys package <package>`).
- Install or verify `jadx`, `apktool`, `tshark`/Wireshark and `python3`.
- Clone Gadgetbridge (upstream) into a working directory and locate the Huawei support (`devices/huawei` package), in particular: packet building, services/ids, capability negotiation and GT Runner 2 support. Summarize how it is organized before touching anything.

### 2. Bluetooth traffic capture
- Enable the Bluetooth HCI log on the phone (developer options) and restart Bluetooth.
- With Huawei Health connected to the watch, run isolated scenarios, noting the time of each:
  1. Connect the watch from scratch (full reconnection).
  2. Open golf mode in the app and confirm it appears on the watch.
  3. Download a golf course from the app.
  4. Disconnect the watch and confirm golf mode disappears.
  5. Complete a short or simulated round and sync it.
- Extract the log (`adb bugreport` or `adb pull` of `btsnoop_hci.log`) and analyze it with tshark. Separate the scenarios by timestamps.

### 3. Analysis of golf-mode activation
- Compare the capture of scenario 1 with scenario 2/4 to isolate the message or capability set that makes golf appear.
- Cross-check against Gadgetbridge's Huawei support: which service/command does it correspond to? Is Gadgetbridge missing a capability, an app install or a state sync?
- Deliverable: the exact message(s) (service, command, fields) and what was expected versus what Gadgetbridge does today.

### 4. Analysis of map transfer
- With the scenario 3 captures, identify how the map travels (channel, fragmentation, format, headers, checksums, encryption).
- With `jadx` on the Huawei Health APK (extracted from my own phone with `adb pull`), look for strings and classes related to golf, courses, maps and file transfer, and relate them to what was observed in the capture.
- Find out where the app gets the course data (endpoint, authentication, format) without issuing requests beyond what my own app already does.

### 4b. GPS updates (A-GNSS)
- Add an extra scenario to the captures: force or wait for the GPS/satellite data update from Huawei Health (in its watch settings, if that option exists) and note the time.
- Identify the transfer message, the data format (ephemeris/almanac, constellations, headers, validity/expiry) and the origin: search the APK with `jadx` for strings related to GPS, GNSS, ephemeris, almanac and assistance, and relate them to the capture.
- Find out whether the server is Huawei's or a public source, and whether the download requires an account, without issuing requests different from what my own app already does.
- Check how Gadgetbridge handles this case for other brands (AGPS) to reuse its download and scheduled-update mechanism.
- Deliverable: origin, format, expiry and transfer message, and whether it can be reproduced from Gadgetbridge.

### 5. Proof of concept
- In a Gadgetbridge fork, implement the minimum to send the golf-activation message to the watch and confirm golf mode appears without Huawei Health connected.
- If feasible, try sending a test course by reusing a file captured from my own download.
- Do the same with an A-GNSS package captured from my own download and check whether the watch accepts it.

### 6. Final report (`phase0-report.md`)
Include: what activates golf, the format and transport of the map and the GPS updates, where the data comes from, what requires a Huawei account, whether the proof of concept worked, risks (account ban, encryption, signatures) and the **A/B/C recommendation** from section 3 of `00-spec.md` with the evidence that supports it. No credentials or keys.

## Stop criterion
If any step requires breaking Huawei's encryption or authentication beyond observing my own session, stop, summarize what you found and ask me how to continue.
