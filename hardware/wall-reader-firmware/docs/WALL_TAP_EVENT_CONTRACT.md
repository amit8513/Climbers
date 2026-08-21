# WallTapEvent contract

## Struct (`src/WallTapEvent.h`)

| Field | Type | Notes |
|---|---|---|
| `readerDeviceId` | string | From `Config.h` / build flags, identifies this physical reader |
| `wallId` | string | From `Config.h` / build flags, identifies the wall this reader is assigned to |
| `firmwareVersion` | string | For diagnosing which build produced an event |
| `timestampMs` | uint64 | **Device-relative** (`millis()` since boot), not wall-clock. Real epoch timestamping needs NTP sync, deferred until `HttpWallTapTransport` is real and Wi-Fi is assumed available at tap time |
| `eventId` | 16 hex chars | Random (`esp_random()`) + a monotonic counter, generated fresh per event. Intended to double as the future replay-protection nonce |
| `rawUid` / `rawUidLength` | bytes | The tag UID. **Transient only** — lives on the stack for the duration of one `WallReaderController::update()` call. Never written to NVS/SPIFFS anywhere in this codebase |

## What "transient only" means in practice

- `WallReaderController::update()` builds one `WallTapEvent` on the stack,
  hands it to `transport_->sendTapEvent(event)`, and lets it go out of
  scope. No global/static buffer retains it between taps beyond
  `lastUid_`/`lastUidLength_`, which exist only for duplicate-suppression
  comparison — not for logging/export.
- `LocalDebugTransport` only serializes `rawUidHex` into its Serial line
  when `WALLREADER_PRODUCTION_MODE == 0`. In a production-mode build, that
  field is omitted entirely — not redacted/hashed, omitted.
- `HttpWallTapTransport` (disabled by default, see below) does include the
  raw UID hex in its request body, because the eventual backend needs it
  for wristband-credential resolution — **transmission over an
  authenticated HTTPS request is not the same thing as on-device
  persistence/logging**, and that transport is not enabled in this phase
  regardless.

## `LocalDebugTransport` logged shape (this phase's actual output)

```json
{"eventId":"1A2B3C4D5E6F7890","readerDeviceId":"reader-spike-01","wallId":"wall-spike-01","firmwareVersion":"0.1.0-phase1.25-spike","timestampMs":48213,"uidLength":4,"rawUidHex":"04A1B2C3"}
```

`rawUidHex` is present only in non-production builds.

## `HttpWallTapTransport` future request shape — NOT FINAL, NOT IMPLEMENTED

`HttpWallTapTransport` is compiled but not wired into `main.cpp`
(`WALLREADER_ENABLE_HTTP_TRANSPORT` defaults to `0`), and no
`onWallTapEvent` Cloud Function exists yet. Its current body is
illustrative only:

```json
{
  "eventId": "1A2B3C4D5E6F7890",
  "readerDeviceId": "reader-spike-01",
  "wallId": "wall-spike-01",
  "firmwareVersion": "0.1.0-phase1.25-spike",
  "timestampMs": 48213,
  "rawUidHex": "04A1B2C3",
  "signature": null
}
```

Open questions the actual protocol still needs to answer (do not guess
these into existence in firmware code — decide them explicitly when the
backend is built):

- **Device authentication**: per-device credential/key provisioned to each
  `NfcReaderDevice`, distinct from any shared server secret. Per the plan
  doc, no Firebase service-account key or HMAC server secret may ever live
  on the ESP32.
- **Signature**: HMAC over a pre-shared per-device secret, or a lightweight
  key-pair signature — undecided. Whatever it is, it must cover at least
  `eventId` + `timestampMs` + `readerDeviceId` so a captured request can't
  be replayed against a different reader/time.
- **Replay protection**: `eventId` is already generated as a per-event
  nonce so the backend has something to dedupe against; the actual
  dedupe/expiry window is a backend decision.
- **Timestamp trust**: device clocks aren't NTP-synced in this phase; the
  backend likely needs to use its own receipt time as the authoritative
  timestamp and treat the device's `timestampMs` as advisory/relative only.
- **Wristband credential resolution**: happens **backend-side** — this
  firmware never knows whether a UID belongs to a known member; it only
  forwards the UID transiently.
