#include "HttpWallTapTransport.h"
#include <Arduino.h>
#include <HTTPClient.h>
#include <WiFi.h>

HttpWallTapTransport::HttpWallTapTransport(const char* backendUrl) : backendUrl_(backendUrl) {}

bool HttpWallTapTransport::begin() {
  Serial.println("[transport] HttpWallTapTransport constructed but unauthenticated - "
                  "device auth/signature/replay-protection protocol is not yet defined. "
                  "Do not rely on this outside explicit future protocol work.");
  return true;
}

bool HttpWallTapTransport::sendTapEvent(const WallTapEvent& event) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[transport] HttpWallTapTransport: no Wi-Fi, cannot send");
    return false;
  }

  // Illustrative only: matches the fields onWallTapEvent will need for
  // backend-side wristband-credential resolution, per
  // docs/ROUTE_ATTRIBUTION_PLAN.md. "signature" is intentionally null - no
  // auth/signing protocol exists yet. Do not point this at a real endpoint
  // or treat a 200 as a verified event until that protocol is designed.
  String payload = String("{") +
      "\"eventId\":\"" + event.eventId + "\"," +
      "\"readerDeviceId\":\"" + event.readerDeviceId + "\"," +
      "\"wallId\":\"" + event.wallId + "\"," +
      "\"firmwareVersion\":\"" + event.firmwareVersion + "\"," +
      "\"timestampMs\":" + String((unsigned long)event.timestampMs) + "," +
      "\"rawUidHex\":\"" + wallTapEventUidHex(event) + "\"," +
      "\"signature\":null" +
      "}";

  HTTPClient http;
  http.begin(backendUrl_);
  http.addHeader("Content-Type", "application/json");
  int statusCode = http.POST(payload);
  http.end();

  if (statusCode <= 0) {
    Serial.printf("[transport] HttpWallTapTransport POST failed: %d\n", statusCode);
    return false;
  }
  return statusCode >= 200 && statusCode < 300;
}
