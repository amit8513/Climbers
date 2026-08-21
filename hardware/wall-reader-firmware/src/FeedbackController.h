#pragma once

#include <Arduino.h>

// Drives the three status LEDs (READY/BUSY/ERROR) and the piezo buzzer.
// Every show*() call is expected to complete in well under the <250ms
// latency target from docs/TEST_PROTOCOL.md - digitalWrite is immediate and
// tone() returns immediately (it runs off an LEDC hardware timer), so
// callers should invoke these before doing anything slower (transport
// calls, Wi-Fi, etc).
class FeedbackController {
public:
  FeedbackController(uint8_t readyPin, uint8_t busyPin, uint8_t errorPin, uint8_t buzzerPin);

  void begin();
  void showReady();
  void showSuccess();
  void showBusy();
  void showError();

private:
  uint8_t readyPin_, busyPin_, errorPin_, buzzerPin_;

  void setLeds(bool ready, bool busy, bool error);
  void beep(uint16_t freqHz, uint16_t durationMs);
};
