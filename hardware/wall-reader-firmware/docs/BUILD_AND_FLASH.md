# Build & flash — Phase 1.25 spike

## Prerequisites

- [PlatformIO Core](https://platformio.org/install/cli) (CLI) or the
  PlatformIO IDE extension for VS Code.
- USB cable to the ESP32 dev board, drivers installed (CP2102/CH340
  depending on board).

## One-time setup

1. Open `hardware/wall-reader-firmware/` as the PlatformIO project root
   (it's independent of the Android Gradle project — no interaction with
   the rest of this repo's build).
2. Edit `src/Config.h`:
   - `WALLREADER_WIFI_SSID` / `WALLREADER_WIFI_PASSWORD` — real test
     network credentials. **Do not commit real credentials** — if you need
     them tracked, use a PlatformIO env var or a gitignored local override
     header instead of editing the committed defaults.
   - `WALLREADER_READER_DEVICE_ID` / `WALLREADER_WALL_ID` — identify this
     physical unit. If flashing multiple readers, prefer overriding these
     per PlatformIO environment via `build_flags` (see the commented-out
     second `[env:...]` block in `platformio.ini`) rather than editing
     `Config.h` before each flash.

## Build & flash

```sh
cd hardware/wall-reader-firmware
pio run                       # build only
pio run -t upload             # build + flash over USB
pio device monitor -b 115200  # Serial log (or: pio run -t upload -t monitor)
```

On first successful boot you should see:

```
=== Wall Reader Firmware (Phase 1.25 spike) ===
readerDeviceId=reader-spike-01 wallId=wall-spike-01 firmwareVersion=0.1.0-phase1.25-spike
productionMode=0 (spike build - raw UID hex is logged for test verification only)
[wifi] connecting to '...'
[transport] LocalDebugTransport active - events are logged over Serial only, nothing leaves the device
```

If instead you see `[reader] NFC reader init failed`, recheck the I2C
wiring in `docs/WIRING.md` before anything else — this is the PN532 not
responding to `getFirmwareVersion()`.

## Reading tap events

Every accepted tap prints one JSON line to Serial, e.g.:

```
{"eventId":"1A2B3C4D5E6F7890","readerDeviceId":"reader-spike-01","wallId":"wall-spike-01","firmwareVersion":"0.1.0-phase1.25-spike","timestampMs":48213,"uidLength":4,"rawUidHex":"04A1B2C3"}
```

See `docs/WALL_TAP_EVENT_CONTRACT.md` for the field meanings, and
`docs/TEST_PROTOCOL.md` for what to actually do with this once hardware is
connected.

## Producing a "production-mode" build

Not needed for this spike, but to confirm raw UID logging is fully
disabled:

```sh
pio run -e esp32dev --build-flag "-DWALLREADER_PRODUCTION_MODE=1" -t upload
```

(Or add a dedicated `[env:esp32dev_prod]` block with
`-DWALLREADER_PRODUCTION_MODE=1` in `build_flags` if you'll do this
repeatedly.) Confirm the boot log prints `productionMode=1` and that tap
event lines no longer contain `rawUidHex`.

## Troubleshooting library resolution

If PlatformIO can't resolve `adafruit/Adafruit PN532`, search "Adafruit
PN532" in the [PlatformIO Registry](https://registry.platformio.org/) and
adjust the version pin in `platformio.ini`'s `lib_deps`.
