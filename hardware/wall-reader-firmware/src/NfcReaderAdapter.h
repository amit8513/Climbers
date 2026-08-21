#pragma once

#include <Arduino.h>

// Mirrors the Kotlin-side NfcReaderAdapter interface from
// docs/ROUTE_ATTRIBUTION_PLAN.md (§2 correction) so the eventual
// software/hardware split stays conceptually aligned. This is the firmware
// side: one concrete chip driver per supported reader.
class NfcReaderAdapter {
public:
  virtual ~NfcReaderAdapter() = default;

  // Initializes the chip. Returns false on failure (wiring/power/i2c fault).
  virtual bool begin() = 0;

  virtual bool isConnected() = 0;

  // Non-blocking-ish poll (short internal timeout only, see the concrete
  // implementation) for a tag present since the last call. Returns true and
  // fills uid/uidLength when one was read.
  virtual bool pollForTag(uint8_t* uid, uint8_t& uidLength, uint8_t maxUidLength) = 0;
};
