# Phase 1.25 hardware spike — test protocol

Run this once the firmware is flashed (`docs/BUILD_AND_FLASH.md`) and wired
(`docs/WIRING.md`). Watch `pio device monitor -b 115200` throughout — every
accepted tap prints one JSON line (see `docs/WALL_TAP_EVENT_CONTRACT.md`),
and mode changes (`[reader] ...`, `[transport] ...`, `[wifi] ...`) print
their own log lines.

This is a validation spike, not production sign-off — if any test fails
outright (not just misses a target), the architecture in
`../../docs/ROUTE_ATTRIBUTION_PLAN.md` needs revisiting before Phase 1.5
begins, per that doc's phase-order note.

## Setup

- [ ] Reader mounted/held behind the actual wood thickness you intend to
      test (record the thickness/species — it affects read range).
- [ ] At least one real NFC wristband/tag, plus one *different* tag to use
      as an "unknown wristband."
- [ ] Serial monitor open and logging (redirect to a file if you want a raw
      log alongside your filled-in results below).

## 1. Bare reader (no wood) — baseline

Tap the wristband directly against the bare reader, 20 times, pausing ~2s
between taps so each is a distinct event (not suppressed as a duplicate).

- Successful reads: ___ / 20
- Notes:

## 2. Behind target wood thickness — the real test

With the reader mounted behind the wall material at the intended
installation depth:

**100 consecutive taps** at the intended tap position, ~2s apart.

- Successful reads: ___ / 100
- Average read latency (tap → Serial log line): ___ ms
- Max read latency observed: ___ ms
- **Pass target: ≥ 98/100**

## 3. Wrist rotation

At the intended tap position, 10 taps at each rotation:

| Rotation | Successful / 10 |
|---|---|
| 0° | |
| 45° | |
| 90° | |
| 180° | |

## 4. Distance from wall face

10 taps at each distance (adjust distances to what's physically meaningful
for your reader/antenna):

| Distance | Successful / 10 |
|---|---|
| Touching | |
| 1 cm | |
| 2 cm | |
| 3 cm | |
| ... | |

- **Maximum reliable distance** (last distance with ≥ 9/10): ___

## 5. Duplicate tap behavior

Hold the wristband against the tap position continuously for 5 seconds in
one motion (one physical "tap").

- [ ] Exactly one event logged (not multiple)
- Number of events actually logged: ___
- **Pass target: no duplicate event from one normal tap**

Repeat 5 times with a fresh single continuous tap each time.

## 6. Rapid second tap while BUSY

Tap wristband A, then immediately (within ~1s) tap a **different**
wristband B before the BUSY window (`WALLREADER_BUSY_COOLDOWN_WINDOW_MS`,
default 1500ms) elapses.

- [ ] Wristband A's tap is logged as an event
- [ ] Wristband B's tap produces a BUSY feedback signal (yellow LED +
      distinct beep), NOT a second logged event
- [ ] Once the BUSY window elapses, tapping B again succeeds normally

## 7. Wi-Fi disconnected / reconnected

- [ ] With Wi-Fi connected and working, disable the AP (or move the device
      out of range).
- [ ] Confirm taps still work normally (LED/buzzer feedback, Serial log
      lines) — LocalDebugTransport needs no network.
- [ ] Re-enable/move back into range.
- Reconnect time (AP available again → `[wifi] connected` log line): ___ s
- **Pass target: reader automatically recovers, no manual reset needed**

## 8. ESP32 reboot

- [ ] Power-cycle or reset the board.
- [ ] Confirm it re-initializes the NFC reader and returns to READY
      (green LED) without manual intervention.
- [ ] Confirm a tap immediately after boot works normally.

## 9. Unknown wristband

Tap a wristband that isn't your "known" test tag.

- [ ] An event is still logged (this firmware does not know or care
      whether a UID is "known" — credential resolution is backend-side,
      per the plan doc). Confirm this firmware doesn't special-case it.

## 10. LED/buzzer response latency

Using slow-motion video or a stopwatch against the Serial log timestamp:

- Time from physical tap to LED/buzzer feedback change: ___ ms
- **Pass target: feels immediate, < 250 ms**

(Feedback fires before the transport call in `WallReaderController::update`,
so this should already be near-instant regardless of Wi-Fi state — flag it
if it isn't, since that would point to something blocking earlier in the
loop.)

## Summary — fill in after all tests

| Metric | Result | Target | Pass? |
|---|---|---|---|
| Successful reads (through wood, 100 taps) | ___/100 | ≥ 98/100 | |
| Duplicate events from one tap | ___ | 0 | |
| LED/buzzer latency | ___ ms | < 250 ms | |
| Wi-Fi auto-recovery | Y/N | Y | |
| Raw UID in production-mode log | Y/N | N (verify separately, see BUILD_AND_FLASH.md) | |
| Maximum reliable distance | ___ | (record, no fixed target) | |

## If something fails

Note *which* specific assumption broke (range, latency, false reads,
reconnect, whatever) — that's the input the plan doc's Phase 1.25 gate
needs before Phase 1.5 (Camera Edge Device bootstrap) can be authorized.
Don't move on to writing more firmware/backend code based on an assumption
this spike just disproved.
