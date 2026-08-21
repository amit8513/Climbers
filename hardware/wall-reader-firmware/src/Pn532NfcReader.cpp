#include "Pn532NfcReader.h"

Pn532NfcReader::Pn532NfcReader(uint8_t irqPin, uint8_t resetPin)
    : nfc_(irqPin, resetPin) {}

bool Pn532NfcReader::begin() {
  nfc_.begin();
  uint32_t versionData = nfc_.getFirmwareVersion();
  if (!versionData) {
    connected_ = false;
    return false;
  }
  nfc_.SAMConfig();
  connected_ = true;
  return true;
}

bool Pn532NfcReader::isConnected() {
  return connected_;
}

bool Pn532NfcReader::pollForTag(uint8_t* uid, uint8_t& uidLength, uint8_t maxUidLength) {
  if (!connected_) return false;

  uint8_t buffer[7];
  uint8_t length = 0;
  // Short timeout keeps the caller's loop responsive for feedback latency
  // and Wi-Fi/state-machine housekeeping between polls.
  bool found = nfc_.readPassiveTargetID(PN532_MIFARE_ISO14443A, buffer, &length, 30);
  if (!found) return false;

  uidLength = min(length, maxUidLength);
  memcpy(uid, buffer, uidLength);
  return true;
}
