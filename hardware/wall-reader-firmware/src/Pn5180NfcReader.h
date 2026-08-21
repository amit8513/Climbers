#pragma once

#include "NfcReaderAdapter.h"

// NOT IMPLEMENTED in Phase 1.25. The PN5180 uses SPI (not I2C) and a
// different library/API (e.g. the ATrappmann/PN5180-Library Arduino
// library) than the PN532 driver in this directory. If the spike hardware
// decision moves from PN532 to PN5180, implement this against the same
// NfcReaderAdapter interface so WallReaderController and everything above
// it needs zero changes.
class Pn5180NfcReader : public NfcReaderAdapter {
public:
  bool begin() override { return false; }
  bool isConnected() override { return false; }
  bool pollForTag(uint8_t*, uint8_t&, uint8_t) override { return false; }
};
