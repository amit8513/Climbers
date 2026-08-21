# Wall reader firmware — Phase 1.25 hardware spike

ESP32 firmware for the NFC wristband reader mounted behind the climbing
wall, per `../../docs/ROUTE_ATTRIBUTION_PLAN.md`'s Phase 1.25 (hardware
spike). This is a **separate PlatformIO project**, independent of the
Android Gradle build in the rest of this repo.

Read in this order:

1. `docs/WIRING.md` — pin connections for the PN532 reader, LEDs, buzzer.
2. `docs/BUILD_AND_FLASH.md` — PlatformIO setup, build, flash, Serial log.
3. `docs/WALL_TAP_EVENT_CONTRACT.md` — the `WallTapEvent` fields and what
   the future (unimplemented) backend request will need.
4. `docs/TEST_PROTOCOL.md` — the physical test checklist to run once
   hardware is connected. **This is the actual deliverable of Phase
   1.25** — everything else here just exists to make that test possible.

## What this firmware does

- Detects a wristband NFC tap (PN532, I2C).
- Debounces duplicate reads of the same tap and signals BUSY for a genuinely
  new tap while a prior one is still settling.
- Drives READY/BUSY/ERROR LEDs and a buzzer.
- Reconnects Wi-Fi automatically without blocking tap detection.
- Logs a structured `WallTapEvent` over Serial via `LocalDebugTransport` —
  no backend involved.

## What this firmware deliberately does NOT do

Per the Phase 1.25 scope, none of the following exist in this phase:

- No connection to Firebase/Firestore, and no `onWallTapEvent` Cloud
  Function (`HttpWallTapTransport` is compiled but disabled by default and
  has no real auth/signature protocol — see
  `docs/WALL_TAP_EVENT_CONTRACT.md`).
- No Camera Edge Device / camera integration of any kind.
- No video recording.
- No route detection or pose analysis.
- No persisted or logged raw UID in a production-mode build
  (`WALLREADER_PRODUCTION_MODE=1` — see `Config.h`).
- No server secrets or Firebase service-account credentials anywhere in
  this firmware.

## Status

Firmware, wiring docs, and test protocol are written; **no real-hardware
test results exist yet**. Per this project's phased-approval process (see
`../../NEXT_STEPS.md`), Phase 1.25 is not complete until the test protocol
has actually been run against real hardware and the results are recorded
back into `../../NEXT_STEPS.md`.
