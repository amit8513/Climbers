#pragma once

#include "NfcReaderAdapter.h"
#include <Adafruit_PN532.h>

// I2C-mode PN532 driver. See docs/WIRING.md for the pinout this expects.
class Pn532NfcReader : public NfcReaderAdapter {
public:
  Pn532NfcReader(uint8_t irqPin, uint8_t resetPin);

  bool begin() override;
  bool isConnected() override;
  bool pollForTag(uint8_t* uid, uint8_t& uidLength, uint8_t maxUidLength) override;

private:
  Adafruit_PN532 nfc_;
  bool connected_ = false;
};
