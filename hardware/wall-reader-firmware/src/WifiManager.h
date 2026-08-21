#pragma once

#include <Arduino.h>
#include <WiFi.h>

// Non-blocking connect/reconnect. Deliberately never blocks the caller's
// loop() for more than one iteration - NFC polling and local feedback must
// keep working regardless of Wi-Fi state, since LocalDebugTransport doesn't
// need a network at all. See docs/TEST_PROTOCOL.md's
// "Wi-Fi disconnected/reconnected" case.
class WifiManager {
public:
  WifiManager(const char* ssid, const char* password, uint32_t connectTimeoutMs, uint32_t retryIntervalMs);

  void begin();
  // Call every loop() iteration.
  void update();
  bool isConnected();

private:
  const char* ssid_;
  const char* password_;
  uint32_t connectTimeoutMs_;
  uint32_t retryIntervalMs_;

  bool connecting_ = false;
  unsigned long connectAttemptStartMs_ = 0;
  unsigned long lastRetryAttemptMs_ = 0;

  void startConnectAttempt();
};
