#pragma once

#include <Arduino.h>

// One physical wristband tap. The raw UID lives ONLY in this struct, on the
// stack, for the lifetime of one tap being processed by
// WallReaderController::update() - it is never written to NVS/SPIFFS, and
// LocalDebugTransport only logs it when WALLREADER_PRODUCTION_MODE == 0.
// See docs/WALL_TAP_EVENT_CONTRACT.md for the full field-by-field contract.
struct WallTapEvent {
  static const uint8_t kMaxUidLength = 10;

  const char* readerDeviceId;
  const char* wallId;
  const char* firmwareVersion;

  // Device-relative timestamp (millis since boot). Wall-clock timestamping
  // needs NTP sync, which isn't set up in this phase - Wi-Fi may be down
  // anyway when a tap happens. Revisit once HttpWallTapTransport is real.
  uint64_t timestampMs;

  // 16 hex chars + null terminator. Generated fresh per event; see
  // WallReaderController::generateEventId.
  char eventId[17];

  uint8_t rawUid[kMaxUidLength];
  uint8_t rawUidLength;
};

// Renders rawUid as uppercase hex. Callers must respect
// WALLREADER_PRODUCTION_MODE themselves - this helper does not.
String wallTapEventUidHex(const WallTapEvent& event);
