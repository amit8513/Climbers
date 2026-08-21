#include "WallReaderController.h"
#include <string.h>
#include <esp_system.h> // esp_random()

WallReaderController::WallReaderController(NfcReaderAdapter* reader,
                                             WallTapTransport* transport,
                                             FeedbackController* feedback,
                                             const char* readerDeviceId,
                                             const char* wallId,
                                             const char* firmwareVersion,
                                             uint32_t duplicateSuppressionWindowMs,
                                             uint32_t busyCooldownWindowMs)
    : reader_(reader),
      transport_(transport),
      feedback_(feedback),
      readerDeviceId_(readerDeviceId),
      wallId_(wallId),
      firmwareVersion_(firmwareVersion),
      duplicateSuppressionWindowMs_(duplicateSuppressionWindowMs),
      busyCooldownWindowMs_(busyCooldownWindowMs) {}

void WallReaderController::begin() {
  feedback_->begin();
  if (!reader_->begin()) {
    handleReaderInitFailure();
    return;
  }
  transport_->begin();
  mode_ = ReaderMode::READY;
  feedback_->showReady();
}

void WallReaderController::handleReaderInitFailure() {
  mode_ = ReaderMode::ERROR;
  feedback_->showError();
  Serial.println("[reader] NFC reader init failed - check wiring/power (see docs/WIRING.md)");
}

bool WallReaderController::sameUid(const uint8_t* uid, uint8_t len) const {
  if (len != lastUidLength_) return false;
  return memcmp(uid, lastUid_, len) == 0;
}

void WallReaderController::generateEventId(char* out17Bytes) {
  uint32_t a = esp_random();
  uint32_t b = esp_random() ^ (eventCounter_++);
  snprintf(out17Bytes, 17, "%08lX%08lX", (unsigned long)a, (unsigned long)b);
}

void WallReaderController::update() {
  if (mode_ == ReaderMode::ERROR) {
    // Periodically retry init so a transient wiring/power glitch recovers
    // without a manual reboot.
    if (millis() - lastReinitAttemptMs_ > 3000) {
      lastReinitAttemptMs_ = millis();
      if (reader_->begin()) {
        mode_ = ReaderMode::READY;
        feedback_->showReady();
        Serial.println("[reader] recovered from ERROR");
      }
    }
    return;
  }

  uint8_t uid[WallTapEvent::kMaxUidLength];
  uint8_t uidLength = 0;
  bool tagPresent = reader_->pollForTag(uid, uidLength, WallTapEvent::kMaxUidLength);

  unsigned long now = millis();

  if (mode_ == ReaderMode::BUSY && now >= busyUntilMs_) {
    mode_ = ReaderMode::READY;
    feedback_->showReady();
  }

  if (!tagPresent) return;

  bool isDuplicate = sameUid(uid, uidLength) &&
                      (now - lastTapUidSeenAtMs_ < duplicateSuppressionWindowMs_);
  if (isDuplicate) {
    // Same physical tap still lingering near the reader - extend the
    // window but do not re-fire, and do not touch feedback/mode.
    lastTapUidSeenAtMs_ = now;
    return;
  }

  if (mode_ == ReaderMode::BUSY) {
    // A genuinely different tap arrived while a prior one is still
    // "active" - signal BUSY, do not process it as a new event.
    feedback_->showBusy();
    return;
  }

  WallTapEvent event{};
  event.readerDeviceId = readerDeviceId_;
  event.wallId = wallId_;
  event.firmwareVersion = firmwareVersion_;
  event.timestampMs = (uint64_t)now;
  generateEventId(event.eventId);
  memcpy(event.rawUid, uid, uidLength);
  event.rawUidLength = uidLength;

  memcpy(lastUid_, uid, uidLength);
  lastUidLength_ = uidLength;
  lastTapUidSeenAtMs_ = now;

  feedback_->showSuccess();
  bool sent = transport_->sendTapEvent(event);
  if (!sent) {
    Serial.println("[transport] sendTapEvent failed");
  }

  mode_ = ReaderMode::BUSY;
  busyUntilMs_ = now + busyCooldownWindowMs_;

  // `event`, including its raw UID, goes out of scope here.
}
