#include "FeedbackController.h"

FeedbackController::FeedbackController(uint8_t readyPin, uint8_t busyPin, uint8_t errorPin, uint8_t buzzerPin)
    : readyPin_(readyPin), busyPin_(busyPin), errorPin_(errorPin), buzzerPin_(buzzerPin) {}

void FeedbackController::begin() {
  pinMode(readyPin_, OUTPUT);
  pinMode(busyPin_, OUTPUT);
  pinMode(errorPin_, OUTPUT);
  pinMode(buzzerPin_, OUTPUT);
  showReady();
}

void FeedbackController::setLeds(bool ready, bool busy, bool error) {
  digitalWrite(readyPin_, ready ? HIGH : LOW);
  digitalWrite(busyPin_, busy ? HIGH : LOW);
  digitalWrite(errorPin_, error ? HIGH : LOW);
}

void FeedbackController::beep(uint16_t freqHz, uint16_t durationMs) {
  tone(buzzerPin_, freqHz, durationMs);
}

void FeedbackController::showReady() {
  setLeds(true, false, false);
}

void FeedbackController::showSuccess() {
  // Ready LED stays lit (the reader IS still ready-ish from the member's
  // point of view) with a short confirming beep layered on top.
  setLeds(true, false, false);
  beep(2000, 120);
}

void FeedbackController::showBusy() {
  setLeds(false, true, false);
  beep(1200, 80);
}

void FeedbackController::showError() {
  setLeds(false, false, true);
  beep(400, 300);
}
