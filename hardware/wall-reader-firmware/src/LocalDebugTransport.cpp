#include "LocalDebugTransport.h"
#include <Arduino.h>

LocalDebugTransport::LocalDebugTransport(bool productionMode) : productionMode_(productionMode) {}

bool LocalDebugTransport::begin() {
  Serial.println("[transport] LocalDebugTransport active - events are logged over Serial only, nothing leaves the device");
  return true;
}

bool LocalDebugTransport::sendTapEvent(const WallTapEvent& event) {
  Serial.print("{\"eventId\":\"");
  Serial.print(event.eventId);
  Serial.print("\",\"readerDeviceId\":\"");
  Serial.print(event.readerDeviceId);
  Serial.print("\",\"wallId\":\"");
  Serial.print(event.wallId);
  Serial.print("\",\"firmwareVersion\":\"");
  Serial.print(event.firmwareVersion);
  Serial.print("\",\"timestampMs\":");
  Serial.print((unsigned long)event.timestampMs);
  Serial.print(",\"uidLength\":");
  Serial.print(event.rawUidLength);

  // WALLREADER_PRODUCTION_MODE hard-gates raw UID logging. Do not add a
  // second flag that can re-enable this, and do not persist the UID to
  // NVS/SPIFFS anywhere in this codebase - it must stay stack-only.
  if (!productionMode_) {
    Serial.print(",\"rawUidHex\":\"");
    Serial.print(wallTapEventUidHex(event));
    Serial.print("\"");
  }
  Serial.println("}");
  return true;
}
